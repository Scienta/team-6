package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.jayway.jsonpath.JsonPath;

/**
 * A small HTTP client for the API tests: sends JSON, reads the answer with JsonPath, and creates test data.
 * JsonPath (in spring-boot-starter-test) rather than Jackson, so the tests don't depend on the Jackson major version
 * (Jackson 2 in Boot 2.7/3, Jackson 3 in Boot 4).
 */
final class Api {

    private final HttpClient http = HttpClient.newHttpClient();
    private final int port;

    Api(int port) {
        this.port = port;
    }

    /** A response: its status and body, with JsonPath accessors ({@code $.status}, {@code $[0].id}, ...). */
    static final class Res {
        final int status;
        final String body;

        Res(int status, String body) {
            this.status = status;
            this.body = body;
        }

        <T> T at(String path) {
            return JsonPath.read(body, path);
        }

        String text(String field) {
            return String.valueOf((Object) at("$." + field));
        }

        long number(String path) {
            return ((Number) at(path)).longValue();
        }

        int size(String path) {
            return ((List<?>) at(path)).size();
        }

        @Override
        public String toString() {
            return status + " " + body;
        }
    }

    HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    Res send(String method, String path, String json) {
        try {
            HttpResponse<String> r = http.send(request(method, path, json), HttpResponse.BodyHandlers.ofString());
            return new Res(r.statusCode(), r.body());
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + path, e);
        }
    }

    Res get(String path) {
        return send("GET", path, null);
    }

    /** Sends all requests at once and waits for every answer, in the order given. */
    List<Res> concurrently(List<HttpRequest> requests) {
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (HttpRequest r : requests) {
            sent.add(http.sendAsync(r, HttpResponse.BodyHandlers.ofString()));
        }
        List<Res> out = new ArrayList<>();
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> r = f.join();
            out.add(new Res(r.statusCode(), r.body()));
        }
        return out;
    }

    long speaker() {
        Res r = send("POST", "/speakers", "{\"name\":\"Grace Hopper\",\"email\":\"grace-" + UUID.randomUUID() + "@example.com\"}");
        assertEquals(201, r.status, r.toString());
        return r.number("$.id");
    }

    Res submit(long speakerId) {
        return send("POST", "/talks", "{\"title\":\"A talk\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speakerId + "}");
    }

    long talk(long speakerId) {
        Res r = submit(speakerId);
        assertEquals(201, r.status, r.toString());
        return r.number("$.id");
    }

    void rate(long talkId, long reviewerId, int score) {
        Res r = send("PUT", "/talks/" + talkId + "/ratings/" + reviewerId, "{\"score\":" + score + "}");
        assertEquals(200, r.status, r.toString());
    }

    Res decide(long talkId, String status) {
        return send("PUT", "/talks/" + talkId + "/status", "{\"status\":\"" + status + "\"}");
    }

    Res withdraw(long talkId, long speakerId) {
        return send("POST", "/talks/" + talkId + "/withdraw", "{\"speakerId\":" + speakerId + "}");
    }

    String status(long talkId) {
        Res r = get("/talks/" + talkId);
        assertEquals(200, r.status, r.toString());
        return r.text("status");
    }

    List<Long> coSpeakers(long talkId) {
        Res r = get("/talks/" + talkId);
        assertEquals(200, r.status, r.toString());
        List<Long> ids = new ArrayList<>();
        for (Object n : r.<List<?>>at("$.coSpeakerIds")) {
            ids.add(((Number) n).longValue());
        }
        return ids;
    }
}
