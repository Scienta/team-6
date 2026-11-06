package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Talk submissions (specs/talks.md, base feature): formats decide the length, at most 3 talks per speaker, also when
 * the submissions arrive at the same time. The submittedAt/submittedOn formats are pinned in {@link FormatsTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TalksTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(port);
    }

    @Test
    void aSubmittedTalkCanBeLookedUpAgain() {
        long speaker = api.speaker();
        Api.Res created = api.submit(speaker, "Legacy is a feeling", "WORKSHOP", "ADVANCED");
        assertEquals(201, created.status, created.toString());
        assertEquals("SUBMITTED", created.text("status"));

        Api.Res found = api.talkById(created.json.get("id").asLong());
        assertEquals("Legacy is a feeling", found.text("title"));
        assertEquals("WORKSHOP", found.text("format"));
        assertEquals("ADVANCED", found.text("level"));
        assertEquals(speaker, found.json.get("speakerId").asLong());
        assertEquals("SUBMITTED", found.text("status"));
        assertEquals(created.body, found.body);
    }

    @Test
    void theFormatDecidesTheLength() {
        long a = api.speaker();
        long b = api.speaker();
        assertEquals(10, minutes(api.submit(a, "t", "LIGHTNING_10", "BEGINNER")));
        assertEquals(20, minutes(api.submit(a, "t", "LIGHTNING_20", "BEGINNER")));
        assertEquals(45, minutes(api.submit(a, "t", "PRESENTATION", "BEGINNER")));
        assertEquals(120, minutes(api.submit(b, "t", "WORKSHOP", "BEGINNER")));
    }

    @Test
    void nobodyElsePicksTheLength() {
        Api.Res r = api.send("POST", "/talks", "{\"title\":\"t\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\","
                + "\"speakerId\":" + api.speaker() + ",\"durationMinutes\":999,\"duration\":999}");
        assertEquals(45, minutes(r));
    }

    @Test
    void formatsAndLevelsAreExactlyTheCapitalisedNames() {
        long speaker = api.speaker();
        for (String format : Arrays.asList("presentation", "Presentation", "KEYNOTE", "")) {
            Api.Res r = api.submit(speaker, "t", format, "BEGINNER");
            assertEquals(400, r.status, "format '" + format + "' -> " + r);
        }
        for (String level : Arrays.asList("beginner", "EXPERT", "")) {
            Api.Res r = api.submit(speaker, "t", "PRESENTATION", level);
            assertEquals(400, r.status, "level '" + level + "' -> " + r);
        }
        assertEquals(0, Api.ids(api.get("/speakers/" + speaker + "/talks")).size(), "a refused talk isn't stored");
    }

    @Test
    void aTalkNeedsATitleAndAKnownSpeaker() {
        assertEquals(400, api.submit(api.speaker(), " ", "PRESENTATION", "BEGINNER").status);
        assertEquals(400, api.submit(999999, "t", "PRESENTATION", "BEGINNER").status);
    }

    @Test
    void unknownTalkOrSpeakerIs404() {
        assertEquals(404, api.get("/talks/999999").status);
        assertEquals(404, api.get("/speakers/999999/talks").status);
    }

    @Test
    void aSpeakerListsTheirOwnTalksOldestFirst() {
        long speaker = api.speaker();
        long first = api.talk(speaker);
        long second = api.talk(speaker);
        long third = api.talk(speaker);
        api.talk(api.speaker()); // someone else's
        assertEquals(Arrays.asList(first, second, third), Api.ids(api.get("/speakers/" + speaker + "/talks")));
    }

    @Test
    void theFourthTalkIsRefused() {
        long speaker = api.speaker();
        api.talk(speaker);
        api.talk(speaker);
        api.talk(speaker);
        assertEquals(409, api.submit(speaker).status);
        assertEquals(3, Api.ids(api.get("/speakers/" + speaker + "/talks")).size());
    }

    @Test
    void concurrentSubmissionsNeverExceedThreeTalks() {
        long speaker = api.speaker();
        List<HttpRequest> requests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            requests.add(api.submitRequest(speaker, "Talk " + i, "PRESENTATION", "BEGINNER"));
        }
        int created = 0;
        for (Api.Res r : api.concurrently(requests)) {
            assertTrue(r.status == 201 || r.status == 409, r.toString());
            if (r.status == 201) {
                created++;
            }
        }
        assertEquals(3, created);
        assertEquals(3, Api.ids(api.get("/speakers/" + speaker + "/talks")).size());
    }

    private static int minutes(Api.Res r) {
        assertEquals(201, r.status, r.toString());
        return r.json.get("durationMinutes").asInt();
    }
}
