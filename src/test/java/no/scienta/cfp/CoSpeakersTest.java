package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
