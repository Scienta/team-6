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
 * Programme scheduling (workflow/specs/scheduling.md). Test names follow the rules R1..R10; the open product owner
 * questions are marked PO-?n until answered.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SchedulingTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProgrammeDao programmeDao;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(port);
        jdbc.update("UPDATE programme_entry SET talk_id = NULL");
    }

    // ------------------------------------------------------------ helpers

    /** An accepted talk: rated by two reviewers, then accepted. */
    private long accepted(long speaker, String format) {
        long talk = api.talk(speaker, "Talk", format, "BEGINNER");
        api.rate(talk, 1, 4);
        api.rate(talk, 2, 5);
        assertEquals(200, api.decide(talk, "ACCEPTED").status);
        return talk;
    }

    private long accepted() {
        return accepted(api.speaker(), "PRESENTATION");
    }

    // Fixtures for the concurrency tests, straight into the database: they test racing PUT/DELETEs on the programme,
    // not how a talk gets accepted (the rule tests above build theirs through the API). ~1 ms instead of 5 requests.

    private long speakerRow() {
        return jdbc.queryForObject("INSERT INTO speaker (name, email) VALUES ('Fixture', ?) RETURNING id", Long.class,
                "fixture-" + java.util.UUID.randomUUID() + "@example.com");
    }

    private long acceptedRow(long speaker) {
        return jdbc.queryForObject("INSERT INTO talk (title, format, duration, level, status, speaker_id, submitted_at) "
                + "VALUES ('Talk', 'PRESENTATION', 45, 'BEGINNER', 'ACCEPTED', ?, ?) RETURNING id",
                Long.class, speaker, System.currentTimeMillis());
    }

    private long acceptedRow() {
        return acceptedRow(speakerRow());
    }

    private void coSpeakerRow(long talk, long speaker) {
        jdbc.update("INSERT INTO talk_co_speaker (talk_id, speaker_id) VALUES (?, ?)", talk, speaker);
    }

    private Api.Res put(String room, String slot, Object talkId) {
        return api.send("PUT", "/programme/" + room + "/" + slot, "{\"talkId\":" + talkId + "}");
    }

    private Api.Res get(String room, String slot) {
        return api.get("/programme/" + room + "/" + slot);
    }

    private Api.Res delete(String room, String slot) {
        return api.send("DELETE", "/programme/" + room + "/" + slot, null);
    }

    private void assertEntry(Api.Res res, String room, String slot, String start, String end, long talkId) {
        assertEquals(200, res.status, res.toString());
        assertEquals(room, res.text("room"));
        assertEquals(slot, res.text("slot"));
        assertEquals(start, res.text("start"));
        assertEquals(end, res.text("end"));
        assertEquals(talkId, res.number("$.talkId"));
        List<String> fields = new ArrayList<>();
        res.json.fieldNames().forEachRemaining(fields::add);
        assertEquals(List.of("room", "slot", "start", "end", "talkId"), fields);
    }

    private void assertEmpty(String room, String slot) {
        assertEquals(404, get(room, slot).status, room + "/" + slot + " should be empty");
    }

    // ------------------------------------------------------------ rules

    @Test
    void venueHasFixedRoomsAndSlots() { // R1, R2
        String[][] slots = {{"S1", "09:00", "10:00"}, {"S2", "10:15", "11:15"}, {"S3", "11:30", "12:30"},
                {"S4", "13:30", "15:30"}, {"S5", "15:45", "16:45"}};
        for (String room : List.of("R1", "R2", "R3")) {
            for (String[] s : slots) {
                long talk = accepted();
                assertEntry(put(room, s[0], talk), room, s[0], s[1], s[2], talk);
                assertEntry(get(room, s[0]), room, s[0], s[1], s[2], talk);
            }
        }
    }

    @Test
    void putGetTakeOutAndMove() { // R3
        long talk = accepted();
        assertEntry(put("R1", "S1", talk), "R1", "S1", "09:00", "10:00", talk);
        assertEntry(get("R1", "S1"), "R1", "S1", "09:00", "10:00", talk);

        Api.Res out = delete("R1", "S1");
        assertEquals(204, out.status, out.toString()); // PO-?3
        assertTrue(out.body.isEmpty());
        assertEmpty("R1", "S1");

        assertEntry(put("R2", "S3", talk), "R2", "S3", "11:30", "12:30", talk);
        long other = accepted();
        assertEntry(put("R1", "S1", other), "R1", "S1", "09:00", "10:00", other);
    }

    @Test
    void onlyAcceptedTalks() { // R4
        long submitted = api.talk(api.speaker());
        assertEquals(409, put("R1", "S1", submitted).status);

        long waitlisted = api.talk(api.speaker());
        assertEquals(200, api.decide(waitlisted, "WAITLISTED").status);
        assertEquals(409, put("R1", "S1", waitlisted).status);

        long rejected = api.talk(api.speaker());
        assertEquals(200, api.decide(rejected, "REJECTED").status);
        assertEquals(409, put("R1", "S1", rejected).status);

        long speaker = api.speaker();
        long withdrawn = accepted(speaker, "PRESENTATION");
        assertEquals(200, api.withdraw(withdrawn, speaker).status);
        assertEquals(409, put("R1", "S1", withdrawn).status);

        assertEmpty("R1", "S1");
    }

    @Test
    void workshopNeedsTheLongSlot() { // R5
        long workshop = accepted(api.speaker(), "WORKSHOP");
        for (String slot : List.of("S1", "S2", "S3", "S5")) {
            assertEquals(409, put("R1", slot, workshop).status, slot);
            assertEmpty("R1", slot);
        }
        assertEntry(put("R1", "S4", workshop), "R1", "S4", "13:30", "15:30", workshop);

        for (String format : List.of("LIGHTNING_10", "LIGHTNING_20", "PRESENTATION")) {
            long talk = accepted(api.speaker(), format);
            String room = format.equals("LIGHTNING_10") ? "R2" : format.equals("LIGHTNING_20") ? "R3" : "R2";
            String slot = format.equals("PRESENTATION") ? "S1" : "S4";
            assertEquals(200, put(room, slot, talk).status, format + " fits " + slot);
        }
    }

    @Test
    void oneTalkPerRoomAndSlot() { // R6
        long first = accepted();
        long second = accepted();
        assertEquals(200, put("R1", "S1", first).status);
        assertEquals(409, put("R1", "S1", second).status);
        assertEntry(get("R1", "S1"), "R1", "S1", "09:00", "10:00", first);
        assertEquals(200, put("R2", "S1", second).status, "another room in the same slot is fine");
    }

    @Test
    void aTalkIsOnTheProgrammeOnce() { // R7
        long talk = accepted();
        assertEquals(200, put("R1", "S1", talk).status);
        assertEquals(409, put("R2", "S2", talk).status);
        assertEquals(409, put("R2", "S1", talk).status);
        assertEmpty("R2", "S2");
        assertEmpty("R2", "S1");
        assertEquals(204, delete("R1", "S1").status);
        assertEquals(200, put("R2", "S2", talk).status, "taken out, it can go somewhere else");
    }

    @Test
    void nobodyInTwoRoomsAtOnce() { // R8
        long ada = api.speaker("Ada");
        long bob = api.speaker("Bob");
        long cyd = api.speaker("Cyd");
        long dee = api.speaker("Dee");

        long adaTalk = accepted(ada, "PRESENTATION");
        assertEquals(200, api.send("PUT", "/talks/" + adaTalk + "/co-speakers/" + bob, null).status);
        assertEquals(200, put("R1", "S1", adaTalk).status);

        long adaAgain = accepted(ada, "PRESENTATION"); // main / main
        assertEquals(409, put("R2", "S1", adaAgain).status);

        long bobMain = accepted(bob, "PRESENTATION"); // co / main
        assertEquals(409, put("R2", "S1", bobMain).status);

        long cydWithAda = accepted(cyd, "PRESENTATION"); // main / co
        assertEquals(200, api.send("PUT", "/talks/" + cydWithAda + "/co-speakers/" + ada, null).status);
        assertEquals(409, put("R3", "S1", cydWithAda).status);

        long deeWithBob = accepted(dee, "PRESENTATION"); // co / co
        assertEquals(200, api.send("PUT", "/talks/" + deeWithBob + "/co-speakers/" + bob, null).status);
        assertEquals(409, put("R3", "S1", deeWithBob).status);

        assertEmpty("R2", "S1");
        assertEmpty("R3", "S1");

        assertEquals(200, put("R2", "S2", adaAgain).status, "the same speaker in another slot is fine");
        assertEquals(200, put("R2", "S1", accepted()).status, "someone else in the same slot is fine");
    }

    @Test
    void laterChangesLeaveTheProgramme() { // R9
        long speaker = api.speaker();
        long talk = accepted(speaker, "PRESENTATION");
        assertEquals(200, put("R1", "S1", talk).status);

        assertEquals(200, api.send("PUT", "/talks/" + talk + "/co-speakers/" + api.speaker(), null).status);
        assertEquals(200, api.withdraw(talk, speaker).status);
        assertEntry(get("R1", "S1"), "R1", "S1", "09:00", "10:00", talk);
    }

    @Test
    void speakersAreComparedAsTheyAreNow() { // R8, PO-?6
        long ada = api.speaker("Ada");
        long scheduled = accepted();
        assertEquals(200, put("R1", "S5", scheduled).status);
        // Ada joins the scheduled talk afterwards (R9: the programme stays), and now speaks in S5
        assertEquals(200, api.send("PUT", "/talks/" + scheduled + "/co-speakers/" + ada, null).status);
        assertEquals(409, put("R2", "S5", accepted(ada, "PRESENTATION")).status);
        assertEmpty("R2", "S5");
    }

    @Test
    void placingIntoATakenPlaceIsRefusedByTheDatabase() { // R6 backstop, without the slot lock
        long first = accepted();
        long second = accepted();
        assertEquals(1, programmeDao.place("R3", "S3", first));
        assertEquals(0, programmeDao.place("R3", "S3", second), "a taken place is never overwritten");
        assertEntry(get("R3", "S3"), "R3", "S3", "11:30", "12:30", first);
    }

    @Test
    void repeatingAPutIsHarmless() { // PO-?1: 200 unchanged; scheduling/check-1 failed on 409 (tick 140)
        long speaker = api.speaker();
        long talk = accepted(speaker, "PRESENTATION");
        assertEquals(200, put("R1", "S1", talk).status);
        assertEntry(put("R1", "S1", talk), "R1", "S1", "09:00", "10:00", talk);
        assertEntry(get("R1", "S1"), "R1", "S1", "09:00", "10:00", talk);
        // still 200 after a withdrawal: the repeat check runs before R4, and the entry stays (R9)
        assertEquals(200, api.withdraw(talk, speaker).status);
        assertEntry(put("R1", "S1", talk), "R1", "S1", "09:00", "10:00", talk);
        assertEquals(409, put("R2", "S2", talk).status, "elsewhere it's both scheduled and no longer accepted");
    }

    @Test
    void emptyRoomAndSlot() { // PO-?2, PO-?3
        assertEquals(404, get("R3", "S5").status);
        assertEquals(404, delete("R3", "S5").status);
        long talk = accepted();
        assertEquals(200, put("R3", "S5", talk).status);
        assertEquals(204, delete("R3", "S5").status);
        assertEquals(404, delete("R3", "S5").status, "a second take-out finds nothing");
    }

    @Test
    void invalidRequests() {
        long talk = accepted();
        // PO-?4 rooms and slots outside the venue
        for (String[] place : new String[][] {{"R4", "S1"}, {"R1", "S6"}, {"r1", "S1"}, {"R1", "s1"}, {"R0", "S0"}}) {
            assertEquals(404, put(place[0], place[1], talk).status, place[0] + "/" + place[1]);
            assertEquals(404, get(place[0], place[1]).status, place[0] + "/" + place[1]);
            assertEquals(404, delete(place[0], place[1]).status, place[0] + "/" + place[1]);
        }
        // bad talkId
        for (String body : List.of("{}", "{\"talkId\":null}", "{\"talkId\":\"1\"}", "{\"talkId\":1.0}",
                "{\"talkId\":true}", "{\"talkId\":0}", "{\"talkId\":-1}", "{\"talkId\":[1]}")) {
            assertEquals(400, api.send("PUT", "/programme/R1/S1", body).status, body);
        }
        assertEquals(400, api.send("PUT", "/programme/R1/S1", null).status, "no body");
        assertEquals(400, api.send("PUT", "/programme/R1/S1", "{\"talkId\":").status, "malformed JSON");
        Api.Res text = api.sendAs("PUT", "/programme/R1/S1", "text/plain", "{\"talkId\":" + talk + "}");
        assertEquals(415, text.status, text.toString());
        // PO-?5 unknown talk: 400, as an unknown speakerId in POST /talks
        assertEquals(400, put("R1", "S1", 999999).status);
        assertEquals(400, put("R1", "S1", 2147483648L).status);
        assertEquals(400, put("R1", "S1", "99999999999999999999").status);
        assertEmpty("R1", "S1");
        // trailing slash
        assertEquals(200, api.send("PUT", "/programme/R1/S1/", "{\"talkId\":" + talk + "}").status);
        assertEquals(200, api.get("/programme/R1/S1/").status);
    }

    // ------------------------------------------------------------ R10 concurrency

    @Test
    void concurrentPutsIntoOneSlot() {
        for (int round = 0; round < 3; round++) {
            jdbc.update("UPDATE programme_entry SET talk_id = NULL");
            List<Long> talks = new ArrayList<>();
            List<HttpRequest> requests = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                long t = acceptedRow();
                talks.add(t);
                requests.add(api.request("PUT", "/programme/R2/S3", "{\"talkId\":" + t + "}"));
            }
            List<Api.Res> answers = api.concurrently(requests);
            long winners = answers.stream().filter(r -> r.status == 200).count();
            answers.forEach(r -> assertTrue(r.status == 200 || r.status == 409, r.toString()));
            assertEquals(1, winners);
            long winner = talks.get(answers.indexOf(answers.stream().filter(r -> r.status == 200).findFirst().get()));
            assertEquals(winner, get("R2", "S3").number("$.talkId"));
        }
    }

    @Test
    void concurrentPutsOfOneTalk() {
        for (int round = 0; round < 3; round++) {
            jdbc.update("UPDATE programme_entry SET talk_id = NULL");
            long talk = acceptedRow();
            List<HttpRequest> requests = new ArrayList<>();
            for (String room : List.of("R1", "R2", "R3")) {
                for (String slot : List.of("S1", "S2", "S3", "S4", "S5")) {
                    requests.add(api.request("PUT", "/programme/" + room + "/" + slot, "{\"talkId\":" + talk + "}"));
                }
            }
            List<Api.Res> answers = api.concurrently(requests);
            answers.forEach(r -> assertTrue(r.status == 200 || r.status == 409, r.toString()));
            assertEquals(1, answers.stream().filter(r -> r.status == 200).count());
            assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM programme_entry WHERE talk_id = ?", Integer.class, talk));
        }
    }

    @Test
    void concurrentSpeakerClash() {
        for (int round = 0; round < 5; round++) {
            jdbc.update("UPDATE programme_entry SET talk_id = NULL");
            long shared = speakerRow();
            List<HttpRequest> requests = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                long talk;
                if (i % 2 == 0) {
                    talk = acceptedRow(speakerRow()); // shared as co-speaker
                    coSpeakerRow(talk, shared);
                } else {
                    talk = acceptedRow(i < 6 ? shared : speakerRow()); // shared as main speaker
                    if (i >= 6) {
                        coSpeakerRow(talk, shared);
                    }
                }
                String room = List.of("R1", "R2", "R3").get(i % 3);
                requests.add(api.request("PUT", "/programme/" + room + "/S2", "{\"talkId\":" + talk + "}"));
            }
            List<Api.Res> answers = api.concurrently(requests);
            answers.forEach(r -> assertTrue(r.status == 200 || r.status == 409, r.toString()));
            assertEquals(1, answers.stream().filter(r -> r.status == 200).count(), "everyone shares one speaker");
            int placed = jdbc.queryForObject("SELECT COUNT(*) FROM programme_entry WHERE slot = 'S2' AND talk_id IS NOT NULL", Integer.class);
            assertEquals(1, placed);
        }
    }

    @Test
    void putRacingTakeOutEndsConsistent() {
        for (int round = 0; round < 10; round++) {
            jdbc.update("UPDATE programme_entry SET talk_id = NULL");
            long first = acceptedRow();
            long second = acceptedRow();
            assertEquals(200, put("R1", "S4", first).status);
            List<Api.Res> answers = api.concurrently(List.of(
                    api.request("DELETE", "/programme/R1/S4", null),
                    api.request("PUT", "/programme/R1/S4", "{\"talkId\":" + second + "}")));
            assertEquals(204, answers.get(0).status, answers.get(0).toString());
            Api.Res now = get("R1", "S4");
            if (answers.get(1).status == 200) {
                assertEquals(second, now.number("$.talkId"), "the put won after the take-out");
            } else {
                assertEquals(409, answers.get(1).status, answers.get(1).toString());
                assertEquals(404, now.status, "the put lost to the first talk, which was then taken out");
            }
        }
    }
}
