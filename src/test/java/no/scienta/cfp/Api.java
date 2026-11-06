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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** A small HTTP client for the API tests: sends JSON, parses the answer, and creates test data. */
final class Api {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();
    private final int port;

    Api(int port) {
        this.port = port;
    }

    /** A response: its status, and its body parsed as JSON (null when empty or not JSON). */
    static final class Res {
        final int status;
        final String body;
        final JsonNode json;

        Res(int status, String body) {
            this.status = status;
            this.body = body;
            JsonNode parsed;
            try {
                parsed = body == null || body.isEmpty() ? null : JSON.readTree(body);
            } catch (Exception e) {
                parsed = null;
            }
            this.json = parsed;
        }

        String text(String field) {
            return json.get(field).asText();
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
        return speaker("Grace Hopper");
    }

    long speaker(String name) {
        Res r = register(name, "speaker-" + UUID.randomUUID() + "@example.com");
        assertEquals(201, r.status, r.toString());
        return r.json.get("id").asLong();
    }

    Res register(String name, String email) {
        return send("POST", "/speakers", "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}");
    }

    Res submit(long speakerId) {
        return submit(speakerId, "A talk", "PRESENTATION", "BEGINNER");
    }

    HttpRequest submitRequest(long speakerId, String title, String format, String level) {
        return request("POST", "/talks", talkJson(speakerId, title, format, level));
    }

    Res submit(long speakerId, String title, String format, String level) {
        return send("POST", "/talks", talkJson(speakerId, title, format, level));
    }

    private static String talkJson(long speakerId, String title, String format, String level) {
        return "{\"title\":\"" + title.replace("\"", "\\\"") + "\",\"format\":\"" + format + "\",\"level\":\"" + level
                + "\",\"speakerId\":" + speakerId + "}";
    }

    long talk(long speakerId, String title, String format, String level) {
        Res r = submit(speakerId, title, format, level);
        assertEquals(201, r.status, r.toString());
        return r.json.get("id").asLong();
    }

    /** PUT /talks/{id}/ratings/{reviewer} with the body as given, so tests can send invalid bodies too. */
    Res rating(Object talkId, Object reviewerId, String json) {
        return send("PUT", "/talks/" + talkId + "/ratings/" + reviewerId, json);
    }

    HttpRequest ratingRequest(long talkId, long reviewerId, int score) {
        return request("PUT", "/talks/" + talkId + "/ratings/" + reviewerId, "{\"score\":" + score + "}");
    }

    /** The talk as GET /talks/{id} shows it. */
    Res talkById(long talkId) {
        Res r = get("/talks/" + talkId);
        assertEquals(200, r.status, r.toString());
        return r;
    }

    /** The ids in a JSON array of talks or speakers, in the order given. */
    static List<Long> ids(Res r) {
        assertEquals(200, r.status, r.toString());
        List<Long> ids = new ArrayList<>();
        r.json.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    long talk(long speakerId) {
        Res r = submit(speakerId);
        assertEquals(201, r.status, r.toString());
        return r.json.get("id").asLong();
    }

    Res rate(long talkId, long reviewerId, int score) {
        Res r = rating(talkId, reviewerId, "{\"score\":" + score + "}");
        assertEquals(200, r.status, r.toString());
        return r;
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
        r.json.get("coSpeakerIds").forEach(n -> ids.add(n.asLong()));
        return ids;
    }
}
