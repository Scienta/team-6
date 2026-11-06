package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Programme export (workflow/specs/programme-export.md, DESIGN.md D2000–D2086). Test names follow the rules R1..R9 and
 * the product owner's answers PO-1..4.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProgrammeExportTest {

    private static final String HEADER = "slot,start,end,room,talkId,title,format,speakers\n";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    Api api;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        api = new Api(port);
        jdbc.update("UPDATE programme_entry SET talk_id = NULL");
    }

    // ------------------------------------------------------------ helpers

    /** An accepted talk by a speaker with this name, through the API (rated twice, accepted). */
    private long accepted(long speaker, String title, String format) {
        // Api's JSON helper only escapes quotes; line breaks in a title must be JSON-escaped here
        long talk = api.talk(speaker, title.replace("\r", "\\r").replace("\n", "\\n"), format, "BEGINNER");
        api.rate(talk, 1, 4);
        api.rate(talk, 2, 5);
        assertEquals(200, api.decide(talk, "ACCEPTED").status);
        return talk;
    }

    private long accepted(String speakerName, String title) {
        return accepted(api.speaker(speakerName), title, "PRESENTATION");
    }

    private void place(String room, String slot, long talk) {
        Api.Res r = api.send("PUT", "/programme/" + room + "/" + slot, "{\"talkId\":" + talk + "}");
        assertEquals(200, r.status, r.toString());
    }

    /** GET with an explicit Accept header; the raw body bytes decoded as UTF-8. */
    private HttpResponse<byte[]> get(String path, String accept) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
            if (accept != null) {
                b.header("Accept", accept);
            }
            return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String csv() {
        HttpResponse<byte[]> r = get("/programme.csv", null);
        assertEquals(200, r.statusCode());
        return new String(r.body(), StandardCharsets.UTF_8);
    }

    private Api.Res json() {
        Api.Res r = api.get("/programme");
        assertEquals(200, r.status, r.toString());
        return r;
    }

    // ------------------------------------------------------------ R9, R1

    @Test
    void emptyProgrammeIsHeaderOnly() {
        assertEquals(0, json().size("$"));
        assertEquals(HEADER, csv(), "just the header line, ending in \\n");
    }

    // ------------------------------------------------------------ R1-R4

    @Test
    void oneRowPerScheduledTalk() {
        long talk = accepted("Grace Hopper", "Compilers");
        place("R2", "S4", talk);
        Api.Res r = json();
        assertEquals(1, r.size("$"), "one row per scheduled talk, nothing for the 14 empty places");
        Map<String, Object> row = r.at("$[0]");
        assertEquals(List.of("slot", "start", "end", "room", "talkId", "title", "format", "speakers"),
                new ArrayList<>(row.keySet()), "the spec's field order");
        assertEquals("S4", row.get("slot"));
        assertEquals("13:30", row.get("start"));
        assertEquals("15:30", row.get("end"));
        assertEquals("R2", row.get("room"));
        assertEquals(talk, ((Number) row.get("talkId")).longValue());
        assertEquals("Compilers", row.get("title"));
        assertEquals("PRESENTATION", row.get("format"));
        assertEquals(List.of("Grace Hopper"), row.get("speakers"));
        assertEquals(HEADER + "S4,13:30,15:30,R2," + talk + ",Compilers,PRESENTATION,Grace Hopper\n", csv());
    }

    @Test
    void readsLikeADay() {
        place("R3", "S2", accepted("A", "a"));
        place("R1", "S5", accepted("B", "b"));
        place("R1", "S1", accepted("C", "c"));
        place("R2", "S2", accepted("D", "d"));
        place("R1", "S2", accepted("E", "e"));
        Api.Res r = json();
        assertEquals(List.of("S1", "S2", "S2", "S2", "S5"), r.at("$[*].slot"));
        assertEquals(List.of("R1", "R1", "R2", "R3", "R1"), r.at("$[*].room"));
    }

    // ------------------------------------------------------------ R5, R4 "as they are now"

    @Test
    void speakersMainFirstThenCoSpeakers() {
        long main = api.speaker("Grace Hopper");
        long talk = accepted(main, "Pairing", "PRESENTATION");
        long ada = api.speaker("Ada Lovelace");
        long alan = api.speaker("Alan Turing");
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/co-speakers/" + alan, null).status);
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/co-speakers/" + ada, null).status);
        place("R1", "S1", talk);
        assertEquals(List.of("Grace Hopper", "Alan Turing", "Ada Lovelace"), json().at("$[0].speakers"));
        assertTrue(csv().endsWith(",Pairing,PRESENTATION,Grace Hopper; Alan Turing; Ada Lovelace\n"), csv());
    }

    @Test
    void showsTalksAndSpeakersAsTheyAreNow() {
        long main = api.speaker("Grace Hopper");
        long talk = accepted(main, "Pairing", "PRESENTATION");
        place("R1", "S1", talk);
        long ada = api.speaker("Ada Lovelace");
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/co-speakers/" + ada, null).status);
        Api.Res speaker = api.get("/speakers/" + main);
        Api.Res renamed = api.send("PUT", "/speakers/" + main,
                "{\"name\":\"Grace B. Hopper\",\"email\":\"" + speaker.text("email") + "\"}");
        assertEquals(200, renamed.status, renamed.toString());
        assertEquals(List.of("Grace B. Hopper", "Ada Lovelace"), json().at("$[0].speakers"));
    }

    // ------------------------------------------------------------ R6-R8

    @Test
    void csvIsByteExactRfc4180WithUnixLineEndings() {
        long quoted = accepted("Åse Ødegård", "Kotlin, \"really\" fast");
        long multiline = accepted("Bjørn", "Line one\nline two");
        long crlf = accepted("Carl", "Windows\r\nline");
        long plain = accepted("Dora", "Plain 'title'; with semicolon");
        place("R1", "S1", quoted);
        place("R2", "S1", multiline);
        place("R3", "S1", crlf);
        place("R1", "S2", plain);
        String expected = HEADER
                + "S1,09:00,10:00,R1," + quoted + ",\"Kotlin, \"\"really\"\" fast\",PRESENTATION,Åse Ødegård\n"
                + "S1,09:00,10:00,R2," + multiline + ",\"Line one\nline two\",PRESENTATION,Bjørn\n"
                + "S1,09:00,10:00,R3," + crlf + ",\"Windows\r\nline\",PRESENTATION,Carl\n"
                + "S2,10:15,11:15,R1," + plain + ",Plain 'title'; with semicolon,PRESENTATION,Dora\n";
        HttpResponse<byte[]> r = get("/programme.csv", null);
        assertEquals(expected, new String(r.body(), StandardCharsets.UTF_8));
        assertFalse(r.body().length >= 3 && (r.body()[0] & 0xff) == 0xEF, "no UTF-8 BOM");
    }

    // PO-2: text/csv with the charset; PO-4: a download filename. R6: also when the client asks for JSON.
    @Test
    void csvIgnoresAcceptAndIsUtf8() {
        for (String accept : new String[] {null, "*/*", "application/json", "text/csv"}) {
            HttpResponse<byte[]> r = get("/programme.csv", accept);
            assertEquals(200, r.statusCode(), "Accept: " + accept);
            assertEquals("text/csv;charset=UTF-8", r.headers().firstValue("Content-Type").orElse("").replace(" ", ""),
                    "Accept: " + accept);
            assertEquals("attachment; filename=programme.csv", r.headers().firstValue("Content-Disposition").orElse(""));
            assertTrue(new String(r.body(), StandardCharsets.UTF_8).startsWith(HEADER));
        }
        assertEquals(200, get("/programme.csv/", null).statusCode(), "trailing slash");
    }

    // PO-3: the JSON view is 406 when the client won't take JSON
    @Test
    void jsonViewIs406WhenTheClientWontTakeJson() {
        assertEquals(406, get("/programme", "text/csv").statusCode());
        for (String accept : new String[] {null, "*/*", "application/json", "text/csv, application/json"}) {
            HttpResponse<byte[]> r = get("/programme", accept);
            assertEquals(200, r.statusCode(), "Accept: " + accept);
            assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"), "Accept: " + accept);
        }
        assertEquals(200, get("/programme/", null).statusCode(), "trailing slash");
    }

    // ------------------------------------------------------------ R2, PO-1

    @Test
    void jsonAndCsvAgree() {
        place("R2", "S3", accepted("A", "Alpha"));
        place("R1", "S3", accepted("B", "Beta, with comma"));
        place("R3", "S1", accepted("C", "Gamma"));
        List<String> jsonLines = new ArrayList<>();
        List<Map<String, Object>> rows = json().at("$");
        for (Map<String, Object> row : rows) {
            jsonLines.add(row.get("slot") + "|" + row.get("room") + "|" + row.get("talkId"));
        }
        List<String> csvLines = new ArrayList<>();
        String[] lines = csv().split("\n");
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split(",");
            csvLines.add(f[0] + "|" + f[3] + "|" + f[4]);
        }
        assertEquals(jsonLines, csvLines, "same rows, same order");
    }

    // PO-1: a withdrawn talk still on the programme is listed, as the spec says
    @Test
    void withdrawnTalkStaysListed() {
        long speaker = api.speaker("Grace Hopper");
        long talk = accepted(speaker, "Leaving", "PRESENTATION");
        place("R1", "S1", talk);
        assertEquals(200, api.withdraw(talk, speaker).status);
        assertEquals(List.of(talk), ((List<Number>) json().at("$[*].talkId")).stream().map(Number::longValue).toList());
        assertTrue(csv().contains(",Leaving,"));
    }
}
