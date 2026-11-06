package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.SpringVersion;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Behaviour the Spring Boot 4 upgrade must not change (workflow/specs/spring-boot-4.md, pins P1-P16). These pass on
 * Boot 2.7 / Java 11 and must still pass after the upgrade. The rest of the pins are in examples/spring-boot-4.hurl.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UpgradeContractTest {

    private static final Pattern SUBMITTED_AT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00");

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
    void submittedAtAndOnFormatsOnEveryTalkView() {
        long speaker = api.speaker();
        Api.Res created = api.submit(speaker);
        assertEquals(201, created.status, created.toString());
        long id = created.json.get("id").asLong();

        assertTalkDates(created.json, "POST /talks");
        assertTalkDates(api.get("/talks/" + id).json, "GET /talks/{id}");
        assertTalkDates(api.get("/speakers/" + speaker + "/talks").json.get(0), "GET /speakers/{id}/talks");
        assertTalkDates(api.get("/talks?speakerId=" + speaker).json.get(0), "GET /talks?speakerId");
    }

    private static void assertTalkDates(JsonNode talk, String where) {
        String at = talk.get("submittedAt").asText();
        assertTrue(SUBMITTED_AT.matcher(at).matches(), "P1 " + where + ": submittedAt " + at);
        long skew = Math.abs(java.time.OffsetDateTime.parse(at).toInstant().toEpochMilli() - System.currentTimeMillis());
        assertTrue(skew < 120_000, "P1 " + where + ": submittedAt is the real UTC instant, not local time labelled +00:00: " + at);
        assertEquals(at.substring(0, 10), talk.get("submittedOn").asText(), "P2 " + where + ": submittedOn");
    }

    @Test
    void averageRoundsHalfUpAndIsAnExplicitNullWhenUnrated() {
        long talk = api.talk(api.speaker());
        JsonNode unrated = api.get("/talks/" + talk).json;
        assertTrue(unrated.has("averageRating") && unrated.get("averageRating").isNull(), "P11: averageRating is null, not missing: " + unrated);
        assertEquals(0, unrated.get("ratingCount").asInt());

        api.rate(talk, 1, 4);
        api.rate(talk, 2, 4);
        api.rate(talk, 3, 4);
        api.rate(talk, 4, 5);
        JsonNode rated = api.get("/talks/" + talk).json;
        assertEquals("4.3", rated.get("averageRating").asText(), "P11: 4, 4, 4, 5 averages 4.25, shown as 4.3");
        assertEquals(4, rated.get("ratingCount").asInt());
    }

    @Test
    void ratedAtDisplayHasAnOrdinarySpaceBeforeAmPm() {
        long talk = api.talk(api.speaker());
        Api.Res r = api.send("PUT", "/talks/" + talk + "/ratings/1", "{\"score\":3}");
        assertEquals(200, r.status, r.toString());
        String display = r.text("ratedAtDisplay");
        assertTrue(display.matches("\\d{1,2}/\\d{1,2}/\\d{2}, \\d{1,2}:\\d{2} [AP]M"), "P3: " + display.replace("\u202f", "<U+202F>"));
    }

    @Test
    void trailingSlashesWorkLikeWithout() {
        Api.Res created = api.send("POST", "/speakers/", "{\"name\":\"Ada Lovelace\",\"email\":\"ada-" + java.util.UUID.randomUUID() + "@example.com\"}");
        assertEquals(201, created.status, "P4: POST /speakers/ " + created);
        long id = created.json.get("id").asLong();
        Api.Res found = api.get("/speakers/" + id + "/");
        assertEquals(200, found.status, "P4: GET /speakers/{id}/ " + found);
        assertEquals("Ada Lovelace", found.text("name"));
    }

    @Test
    void legacyJsonWithCommentsAndUnknownFieldsIsAccepted() {
        Api.Res r = api.send("POST", "/speakers", "{\n  // from the legacy web client\n  \"name\": \"Barbara Liskov\",\n"
                + "  \"email\": \"barbara-" + java.util.UUID.randomUUID() + "@example.com\",\n  \"twitter\": \"@bl\"\n}");
        assertEquals(201, r.status, "P16: " + r);
        assertTrue(r.json.has("bio") && r.json.get("bio").isNull(), "P16: bio is written as null: " + r);
    }

    @Test
    void actuatorInfoReportsTheRunningVersions() {
        Api.Res r = api.get("/actuator/info");
        assertEquals(200, r.status, r.toString());
        JsonNode versions = r.json.get("versions");
        assertEquals(SpringBootVersion.getVersion(), versions.get("springBoot").asText(), "P15 springBoot");
        assertEquals(SpringVersion.getVersion(), versions.get("springFramework").asText(), "P15 springFramework");
        assertEquals(System.getProperty("java.version"), versions.get("java").asText(), "P15 java");
    }

    // ------------------------------------------------------------------ pins added after the independent review

    @Test
    void errorBodiesKeepTheirMessageAndValidationErrors() {
        String email = "dup-" + java.util.UUID.randomUUID() + "@example.com";
        assertEquals(201, api.send("POST", "/speakers", "{\"name\":\"Ada\",\"email\":\"" + email + "\"}").status);
        Api.Res dup = api.send("POST", "/speakers", "{\"name\":\"Ada\",\"email\":\"" + email.toUpperCase() + "\"}");
        assertEquals(409, dup.status, dup.toString());
        assertEquals("email already registered", dup.text("message"), "P17: error bodies keep \"message\": " + dup);

        Api.Res missing = api.get("/talks/999999999");
        assertEquals(404, missing.status);
        assertEquals("talk not found", missing.text("message"), "P17: " + missing);

        Api.Res invalid = api.send("POST", "/speakers", "{\"name\":\" \",\"email\":\"x-" + java.util.UUID.randomUUID() + "@example.com\"}");
        assertEquals(400, invalid.status, invalid.toString());
        assertTrue(invalid.json.has("errors") && invalid.json.get("errors").isArray(), "P17: validation 400s keep \"errors\": " + invalid);
    }

    @Test
    void jsonResponsesDeclareUtf8() throws Exception {
        java.net.http.HttpResponse<String> r = java.net.http.HttpClient.newHttpClient().send(
                api.request("GET", "/speakers/" + api.speaker(), null), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals("application/json;charset=UTF-8", r.headers().firstValue("Content-Type").orElse(""), "P18: Content-Type");
    }

    @Test
    void fieldsKeepTheirOrder() {
        long speaker = api.speaker();
        assertEquals(java.util.Arrays.asList("id", "name", "email", "bio"), fieldNames(api.get("/speakers/" + speaker).json), "P19 Speaker");
        long talk = api.talk(speaker);
        assertEquals(java.util.Arrays.asList("id", "title", "format", "level", "speakerId", "durationMinutes", "status",
                "submittedAt", "submittedOn", "averageRating", "ratingCount", "coSpeakerIds"),
                fieldNames(api.get("/talks/" + talk).json), "P19 Talk");
        Api.Res rating = api.send("PUT", "/talks/" + talk + "/ratings/1", "{\"score\":3}");
        assertEquals(java.util.Arrays.asList("talkId", "reviewerId", "score", "ratedAtDisplay"), fieldNames(rating.json), "P19 Rating");
    }

    private static java.util.List<String> fieldNames(JsonNode node) {
        java.util.List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void trailingContentAfterTheJsonIsIgnored() {
        Api.Res r = api.send("POST", "/speakers", "{\"name\":\"Trailing\",\"email\":\"t-" + java.util.UUID.randomUUID() + "@example.com\"} x");
        assertEquals(201, r.status, "P20: content after the JSON value is ignored, as on Jackson 2: " + r);
        long talk = api.talk(api.speaker());
        Api.Res rated = api.send("PUT", "/talks/" + talk + "/ratings/7", "{\"score\":4} junk");
        assertEquals(200, rated.status, "P20: " + rated);
    }

    @Test
    void rankingBreaksTiesOnRatingCountThenAge() {
        // Speaker A: three talks that all show 4.0. More ratings first, then the oldest.
        long a = api.speaker();
        long a1 = api.talk(a), a2 = api.talk(a), a3 = api.talk(a);
        api.rate(a1, 1, 4);
        api.rate(a2, 1, 4);
        api.rate(a2, 2, 4);
        api.rate(a3, 1, 5);
        api.rate(a3, 2, 3);
        assertEquals(java.util.Arrays.asList(a2, a3, a1), ranked(a), "P12: same average, more ratings first, then oldest");

        // Speaker B: 4.33 and 4.25 both show 4.3, so the rounded average ties and the count decides; unrated last.
        long b = api.speaker();
        long b1 = api.talk(b), b2 = api.talk(b), b3 = api.talk(b);
        api.rate(b1, 1, 4);
        api.rate(b1, 2, 4);
        api.rate(b1, 3, 5);
        api.rate(b2, 1, 4);
        api.rate(b2, 2, 4);
        api.rate(b2, 3, 4);
        api.rate(b2, 4, 5);
        assertEquals(java.util.Arrays.asList(b2, b1, b3), ranked(b), "P12: the rounded average ties (4.3), more ratings first; unrated last");
    }

    private java.util.List<Long> ranked(long speaker) {
        Api.Res r = api.get("/talks?sort=rating&speakerId=" + speaker);
        assertEquals(200, r.status, r.toString());
        java.util.List<Long> ids = new java.util.ArrayList<>();
        r.json.forEach(t -> ids.add(t.get("id").asLong()));
        return ids;
    }
}
