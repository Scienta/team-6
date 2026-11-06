package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Co-speakers (workflow/specs/co-speakers.md): a talk has at most 2 co-speakers, also when the adds arrive at the
 * same time, and a double-click doesn't add one twice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoSpeakersTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final Pattern CO = Pattern.compile("\"coSpeakerIds\":\\[([^\\]]*)\\]");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(port);
    }

    private Api.Res put(long talk, long speaker) {
        return api.send("PUT", "/talks/" + talk + "/co-speakers/" + speaker, null);
    }

    private Api.Res delete(long talk, long speaker) {
        return api.send("DELETE", "/talks/" + talk + "/co-speakers/" + speaker, null);
    }

    @Test
    void coSpeakersShowInTheOrderAdded() {
        long talk = api.talk(api.speaker());
        long a = api.speaker();
        long b = api.speaker();
        assertEquals(Arrays.asList(), api.coSpeakers(talk), "R4: [] when none");
        assertEquals(200, put(talk, b).status);
        Api.Res res = put(talk, a);
        assertEquals(200, res.status, res.toString());
        assertEquals(b, res.number("$.coSpeakerIds[0]"), "R4: the PUT answers with the full Talk");
        assertEquals(Arrays.asList(b, a), api.coSpeakers(talk));
        assertEquals(200, delete(talk, b).status);
        assertEquals(Arrays.asList(a), api.coSpeakers(talk));
        assertEquals(200, put(talk, b).status);
        assertEquals(Arrays.asList(a, b), api.coSpeakers(talk), "removed and added again goes last");
    }

    @Test
    // PO-2: adding the main speaker as co-speaker is a 400 and changes nothing (harness co-speakers/check-2
    // rejected 409 and 200)
    void addingTheMainSpeakerIs400() {
        long main = api.speaker();
        long talk = api.talk(main);
        Api.Res res = put(talk, main);
        assertEquals(400, res.status, res.toString());
        assertEquals(Arrays.asList(), api.coSpeakers(talk));

        long a = api.speaker();
        long b = api.speaker();
        put(talk, a);
        put(talk, b);
        res = put(talk, main);
        assertEquals(400, res.status, "also when the talk already has 2 co-speakers: " + res);
        assertEquals(Arrays.asList(a, b), api.coSpeakers(talk));
    }

    @Test
    void atMostTwoCoSpeakers() {
        long talk = api.talk(api.speaker());
        long a = api.speaker();
        long b = api.speaker();
        put(talk, a);
        put(talk, b);
        assertEquals(409, put(talk, api.speaker()).status);
        assertEquals(200, put(talk, a).status, "R5: re-adding one of the two is still fine");
        assertEquals(Arrays.asList(a, b), api.coSpeakers(talk));
    }

    @Test
    void unknownTalkOrSpeakerIs404() {
        long talk = api.talk(api.speaker());
        long other = api.speaker();
        assertEquals(404, put(999_999, other).status);
        assertEquals(404, put(talk, 999_999).status);
        assertEquals(404, delete(999_999, other).status);
        assertEquals(400, api.send("PUT", "/talks/abc/co-speakers/" + other, null).status);
    }

    // PO-1: removing someone who isn't a co-speaker of the talk is a 404, and changes nothing
    @Test
    void removingANonCoSpeakerIs404() {
        long talk = api.talk(api.speaker());
        long a = api.speaker();
        long notOne = api.speaker();
        put(talk, a);
        assertEquals(404, delete(talk, notOne).status);
        assertEquals(404, delete(talk, 999_999).status, "an unknown speaker isn't a co-speaker either");
        assertEquals(200, delete(talk, a).status);
        assertEquals(404, delete(talk, a).status, "a second remove finds nothing to remove");
        assertEquals(Arrays.asList(), api.coSpeakers(talk));
    }

    @Test
    void ownTalkListShowsOnlyMainSpeakerTalks() {
        long main = api.speaker();
        long co = api.speaker();
        long talk = api.talk(main);
        long own = api.talk(co);
        put(talk, co);
        Api.Res list = api.get("/speakers/" + co + "/talks");
        assertEquals(1, list.size("$"));
        assertEquals(own, list.number("$[0].id"));
        Api.Res filtered = api.get("/talks?speakerId=" + co);
        assertEquals(1, filtered.size("$"));
        assertEquals(own, filtered.number("$[0].id"));
    }

    @Test
    void coSpeakingDoesNotCountTowardsTheLimit() {
        long co = api.speaker();
        long a = api.talk(api.speaker());
        long b = api.talk(api.speaker());
        put(a, co);
        put(b, co);
        api.talk(co);
        api.talk(co);
        api.talk(co); // 3 own talks while co-speaking on 2 others
        assertEquals(409, api.submit(co).status);
        assertEquals(200, put(api.talk(api.speaker()), co).status, "at the limit, still can co-speak");
    }

    @Test
    void concurrentAddsNeverExceedTwoCoSpeakers() throws Exception {
        String main = newSpeaker();
        String talk = newTalk(main);
        List<String> others = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            others.add(newSpeaker());
        }
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (String other : others) {
            sent.add(http.sendAsync(request("PUT", "/talks/" + talk + "/co-speakers/" + other, null),
                    HttpResponse.BodyHandlers.ofString()));
        }
        int added = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertTrue(res.statusCode() == 200 || res.statusCode() == 409, res.statusCode() + " " + res.body());
            if (res.statusCode() == 200) {
                added++;
            }
        }
        assertEquals(2, added);
        assertEquals(2, coSpeakers(get("/talks/" + talk)).length);
    }

    @Test
    void doubleClickAddsOnce() throws Exception {
        String talk = newTalk(newSpeaker());
        String other = newSpeaker();
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sent.add(http.sendAsync(request("PUT", "/talks/" + talk + "/co-speakers/" + other, null),
                    HttpResponse.BodyHandlers.ofString()));
        }
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            assertEquals(200, f.join().statusCode());
        }
        String[] ids = coSpeakers(get("/talks/" + talk));
        assertEquals(1, ids.length);
        assertEquals(other, ids[0].trim());
    }

    private String get(String path) throws Exception {
        HttpResponse<String> res = http.send(request("GET", path, null), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), res.body());
        return res.body();
    }

    private static String[] coSpeakers(String body) {
        Matcher m = CO.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1).isEmpty() ? new String[0] : m.group(1).split(",");
    }

    private String newTalk(String speaker) throws Exception {
        return post("/talks", "{\"title\":\"Pairing\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}");
    }

    private String newSpeaker() throws Exception {
        return post("/speakers", "{\"name\":\"Grace Hopper\",\"email\":\"grace-" + UUID.randomUUID() + "@example.com\"}");
    }

    private String post(String path, String json) throws Exception {
        HttpResponse<String> res = http.send(request("POST", path, json), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, res.statusCode(), res.body());
        Matcher m = ID.matcher(res.body());
        assertTrue(m.find(), res.body());
        return m.group(1);
    }

    private HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
