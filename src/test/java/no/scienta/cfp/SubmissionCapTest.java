package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Submission cap change request (specs/submission-cap-cr.md, workflow/specs/submission-cap-cr.md, DESIGN.md D1105,
 * D1106, D1306, D1350, D1354): co-speaking counts towards the 3-talk limit. Test names follow R1..R6 and PO-1..5.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubmissionCapTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(port);
    }

    // ------------------------------------------------------------ helpers

    /** A talk by a new speaker, so it never uses up the speaker under test's places. */
    private long othersTalk() {
        return api.talk(api.speaker());
    }

    private Api.Res addCo(long talk, long speaker) {
        return api.send("PUT", "/talks/" + talk + "/co-speakers/" + speaker, null);
    }

    private HttpRequest addCoRequest(long talk, long speaker) {
        return api.request("PUT", "/talks/" + talk + "/co-speakers/" + speaker, null);
    }

    /** A speaker with this many talks of their own (all SUBMITTED). */
    private long speakerWithOwnTalks(int n) {
        long speaker = api.speaker();
        for (int i = 0; i < n; i++) {
            api.talk(speaker);
        }
        return speaker;
    }

    /** Talks counting towards the speaker's limit, straight from the database. */
    private int counting(long speaker) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM talk WHERE status NOT IN ('REJECTED', 'WITHDRAWN') AND "
                + "(speaker_id = ? OR id IN (SELECT talk_id FROM talk_co_speaker WHERE speaker_id = ?))",
                Integer.class, speaker, speaker);
    }

    // ------------------------------------------------------------ R1, R3, PO-1

    @Test
    void coSpeakingCountsTowardsTheLimit() {
        long speaker = api.speaker();
        assertEquals(200, addCo(othersTalk(), speaker).status);
        assertEquals(200, addCo(othersTalk(), speaker).status);
        assertEquals(201, api.submit(speaker).status, "2 co-speaker places + 1 own = 3");
        assertEquals(409, api.submit(speaker).status, "R1: co-speaking counts, so a 2nd own talk is a 4th");
    }

    @Test
    void atTheLimitCannotBeAddedAsCoSpeaker() {
        long speaker = speakerWithOwnTalks(3);
        long talk = othersTalk();
        assertEquals(409, addCo(talk, speaker).status, "PO-1");
        assertEquals(List.of(), api.coSpeakers(talk), "nothing changes");
    }

    @Test
    void rejectedOrWithdrawnTalksAreOutsideTheLimit() {
        long speaker = speakerWithOwnTalks(3);
        long rejected = othersTalk();
        assertEquals(200, api.decide(rejected, "REJECTED").status);
        assertEquals(200, addCo(rejected, speaker).status, "R3: a rejected talk doesn't count");

        long owner = api.speaker();
        long withdrawn = api.talk(owner);
        assertEquals(200, api.withdraw(withdrawn, owner).status);
        assertEquals(200, addCo(withdrawn, speaker).status, "R3: a withdrawn talk doesn't count");
        assertEquals(3, counting(speaker));
    }

    // ------------------------------------------------------------ R2

    @Test
    void freedPlacesCanBeUsedAtOnce() {
        // removing a co-speaker frees their place
        long speaker = speakerWithOwnTalks(2);
        long talk = othersTalk();
        assertEquals(200, addCo(talk, speaker).status);
        assertEquals(409, api.submit(speaker).status);
        assertEquals(200, api.send("DELETE", "/talks/" + talk + "/co-speakers/" + speaker, null).status);
        assertEquals(201, api.submit(speaker).status, "freed by the removal");

        // a withdrawn talk frees its co-speakers' places
        long co = speakerWithOwnTalks(2);
        long owner = api.speaker();
        long shared = api.talk(owner);
        assertEquals(200, addCo(shared, co).status);
        assertEquals(409, api.submit(co).status);
        assertEquals(200, api.withdraw(shared, owner).status);
        assertEquals(201, api.submit(co).status, "freed by the withdrawal");

        // a rejected own talk frees the main speaker's place
        long main = api.speaker();
        long first = api.talk(main);
        api.talk(main);
        api.talk(main);
        assertEquals(409, addCo(othersTalk(), main).status);
        assertEquals(200, api.decide(first, "REJECTED").status);
        assertEquals(200, addCo(othersTalk(), main).status, "freed by the rejection");
    }

    // ------------------------------------------------------------ PO-4

    @Test
    void waitlistedAndAcceptedCount() {
        long speaker = api.speaker();
        long waitlisted = api.talk(speaker);
        long accepted = api.talk(speaker);
        api.talk(speaker);
        assertEquals(200, api.decide(waitlisted, "WAITLISTED").status);
        api.rate(accepted, 1, 4);
        api.rate(accepted, 2, 5);
        assertEquals(200, api.decide(accepted, "ACCEPTED").status);
        assertEquals(409, api.submit(speaker).status);
        assertEquals(409, addCo(othersTalk(), speaker).status);
    }

    // ------------------------------------------------------------ PO-2, PO-3

    @Test
    void reAddingAnExistingCoSpeakerIsANoOpAtTheLimit() {
        long speaker = speakerWithOwnTalks(2);
        long talk = othersTalk();
        assertEquals(200, addCo(talk, speaker).status);
        assertEquals(3, counting(speaker));
        assertEquals(200, addCo(talk, speaker).status, "PO-2");
        assertEquals(List.of(speaker), api.coSpeakers(talk));
    }

    @Test
    void mainSpeakerCheckWinsOverTheLimit() {
        long speaker = api.speaker();
        long own = api.talk(speaker);
        api.talk(speaker);
        api.talk(speaker);
        assertEquals(400, addCo(own, speaker).status, "PO-3");
    }

    // ------------------------------------------------------------ R5, PO-5

    @Test
    void overTheLimitKeepsEverythingButGetsNothingNew() {
        // 3 own talks + 2 co-speaker places from before the change: 5 counting. Only SQL can still make this.
        long speaker = api.speaker();
        long own = api.talk(speaker);
        api.talk(speaker);
        api.talk(speaker);
        long a = othersTalk();
        long b = othersTalk();
        jdbc.update("INSERT INTO talk_co_speaker (talk_id, speaker_id) VALUES (?, ?), (?, ?)", a, speaker, b, speaker);
        assertEquals(5, counting(speaker));

        assertEquals(List.of(speaker), api.coSpeakers(a), "R5: keeps everything");
        assertEquals(409, api.submit(speaker).status, "nothing new");
        assertEquals(409, addCo(othersTalk(), speaker).status, "nothing new");
        long rejected = othersTalk();
        assertEquals(200, api.decide(rejected, "REJECTED").status);
        assertEquals(200, addCo(rejected, speaker).status, "PO-5: a rejected talk is fine, even over the limit");

        api.send("DELETE", "/talks/" + a + "/co-speakers/" + speaker, null);
        api.send("DELETE", "/talks/" + b + "/co-speakers/" + speaker, null);
        assertEquals(409, api.submit(speaker).status, "back at 3: still nothing new");
        assertEquals(200, api.decide(own, "REJECTED").status);
        assertEquals(201, api.submit(speaker).status, "below the limit again");
    }

    // ------------------------------------------------------------ R6

    @Test
    void ownTalkListUnchanged() {
        long speaker = api.speaker();
        long own = api.talk(speaker);
        assertEquals(200, addCo(othersTalk(), speaker).status);
        assertEquals(List.of(own), Api.ids(api.get("/speakers/" + speaker + "/talks")));
    }

    // ------------------------------------------------------------ R4 concurrency

    @Test
    void submissionsAndInvitationsRacingForTheLastPlace() {
        for (int round = 0; round < 3; round++) {
            long speaker = speakerWithOwnTalks(2);
            List<HttpRequest> race = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                race.add(api.submitRequest(speaker, "Rush " + i, "PRESENTATION", "BEGINNER"));
                race.add(addCoRequest(othersTalk(), speaker));
            }
            int won = 0;
            for (Api.Res r : api.concurrently(race)) {
                assertTrue(r.status == 201 || r.status == 200 || r.status == 409, r.toString());
                if (r.status != 409) {
                    won++;
                }
            }
            assertEquals(1, won, "exactly one takes the last place");
            assertEquals(3, counting(speaker));
        }
    }

    @Test
    void invitationsOnDifferentTalksRacingForTheLastPlace() {
        for (int round = 0; round < 3; round++) {
            long speaker = speakerWithOwnTalks(2);
            List<HttpRequest> race = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                race.add(addCoRequest(othersTalk(), speaker)); // different talks: different talk locks
            }
            long won = api.concurrently(race).stream().filter(r -> r.status == 200).count();
            assertEquals(1, won, "only the co-speaker's speaker-row lock can serialise these");
            assertEquals(3, counting(speaker));
        }
    }
}
