package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The legacy admin reports, /admin/stats and /admin/talks.csv. They aren't in any spec; these tests pin what they
 * answer today, so an upgrade can't change them unnoticed. One known dataset, created once and only read.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminReportsTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    Api api;
    long workshop;
    long lightning;
    Api.Res workshopTalk;
    Api.Res lightningTalk;

    @BeforeAll
    void data() {
        api = new Api(port);
        long ada = api.speaker("Ada Lovelace");
        api.speaker("Has No Talks");
        workshop = api.talk(ada, "Semi;colon \"quoted\"", "WORKSHOP", "ADVANCED");
        lightning = api.talk(ada, "Lightning talk", "LIGHTNING_10", "BEGINNER");
        for (int reviewer = 1; reviewer <= 3; reviewer++) {
            api.rate(workshop, reviewer, 4);
        }
        api.rate(workshop, 4, 5); // 4.25 -> 4.3
        api.rate(lightning, 1, 4); // 4.0
        workshopTalk = api.talkById(workshop);
        lightningTalk = api.talkById(lightning);
    }

    @Test
    void statsCountSpeakersTalksAndHours() {
        Api.Res r = api.get("/admin/stats");
        assertEquals(200, r.status, r.toString());
        assertEquals(2, r.json.get("speakers").asInt());
        assertEquals(1, r.json.get("speakersWithTalks").asInt());
        assertEquals(2, r.json.get("talks").asInt());
        assertEquals("{\"WORKSHOP\":1,\"LIGHTNING_10\":1}", r.json.get("byFormat").toString());
        assertEquals("{\"ADVANCED\":1,\"BEGINNER\":1}", r.json.get("byLevel").toString());
        assertEquals("{\"SUBMITTED\":2}", r.json.get("byStatus").toString());
        assertEquals(2, r.json.get("totalHours").asInt(), "130 minutes, whole hours cut off");
        assertTrue(r.text("generated").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), r.text("generated"));
    }

    /** The mean of the talks' (rounded) averages, cut off: (4.3 + 4.0) / 2 = 4.15 -> 4.1. PO? see UtilsTest. */
    @Test
    void statsAverageIsTheMeanOfTalkAveragesCutOff() {
        assertEquals("4.1", api.get("/admin/stats").json.get("averageRating").asText());
    }

    @Test
    void csvIsASemicolonSpreadsheetForTheCommittee() throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/admin/talks.csv")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"), r.headers().toString());
        assertEquals("attachment; filename=talks.csv", r.headers().firstValue("Content-Disposition").orElse(""));

        String expected = "id;title;format;minutes;level;speaker;status;submitted;rating;ratings\n"
                + workshop + ";\"Semi;colon 'quoted'\";Workshop;120;Advanced;Ada Lovelace;SUBMITTED;"
                + submitted(workshopTalk) + ";4,25;4\n" // ratings-cr: the talk's average, two decimals
                + lightning + ";Lightning talk;Lightning 10;10;Beginner;Ada Lovelace;SUBMITTED;"
                + submitted(lightningTalk) + ";4,00;1\n";
        assertEquals(expected, r.body());
    }

    private static String submitted(Api.Res talk) {
        return Utils.norwegianDate(Date.from(OffsetDateTime.parse(talk.text("submittedAt")).toInstant()));
    }
}
