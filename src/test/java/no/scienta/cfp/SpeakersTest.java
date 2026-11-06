package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Speakers (specs/speakers.md, base feature): emails are unique regardless of letter case, also under concurrent
 * registrations. Also the admin endpoints that aren't in the spec (update, search), as they behave today.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpeakersTest {

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
    void aRegisteredSpeakerCanBeLookedUp() {
        String email = email("ada");
        Api.Res created = api.register("Ada Lovelace", email);
        assertEquals(201, created.status, created.toString());

        Api.Res found = api.get("/speakers/" + created.json.get("id").asLong());
        assertEquals(200, found.status, found.toString());
        assertEquals("Ada Lovelace", found.text("name"));
        assertEquals(email, found.text("email"));
        assertTrue(found.json.get("bio").isNull(), found.toString());
    }

    @Test
    void anUnknownSpeakerIs404() {
        assertEquals(404, api.get("/speakers/999999").status);
    }

    @Test
    void anEmailRegistersOnceRegardlessOfLetterCase() {
        String email = email("grace");
        assertEquals(201, api.register("Grace Hopper", email).status);
        assertEquals(409, api.register("Grace Hopper", email.toUpperCase()).status);
    }

    @Test
    void concurrentRegistrationsOfOneEmailHaveOneWinner() {
        String email = email("racer");
        List<HttpRequest> requests = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String variant = i % 2 == 0 ? email : email.toUpperCase();
            requests.add(api.request("POST", "/speakers", "{\"name\":\"Racer " + i + "\",\"email\":\"" + variant + "\"}"));
        }
        int created = 0;
        for (Api.Res r : api.concurrently(requests)) {
            assertTrue(r.status == 201 || r.status == 409, r.toString());
            if (r.status == 201) {
                created++;
            }
        }
        assertEquals(1, created);
    }

    @Test
    void searchMatchesNamesRegardlessOfCase() {
        String name = "Lovelace" + UUID.randomUUID().toString().substring(0, 8);
        assertEquals(201, api.register("Ada " + name, email("ada")).status);

        Api.Res found = api.get("/speakers/search?q=" + name.toLowerCase());
        assertEquals(200, found.status, found.toString());
        assertTrue(found.body.contains("\"name\":\"Ada " + name + "\""), found.body);
    }

    // ---------------------------------------------------------------- PUT /speakers/{id} (admin UI, not in the spec)

    @Test
    void theAdminCanFixNameEmailAndBio() {
        long id = api.speaker("Ada Lovelce");
        String email = email("ada");
        Api.Res r = update(id, "Ada Lovelace", email, "Wrote the first program.");
        assertEquals(200, r.status, r.toString());

        Api.Res found = api.get("/speakers/" + id);
        assertEquals("Ada Lovelace", found.text("name"));
        assertEquals(email, found.text("email"));
        assertEquals("Wrote the first program.", found.text("bio"));
    }

    @Test
    void anUpdateCannotTakeAnotherSpeakersEmailInAnyCase() {
        String taken = email("taken");
        assertEquals(201, api.register("First", taken).status);
        long id = api.speaker("Second");
        String before = api.get("/speakers/" + id).body;

        assertEquals(409, update(id, "Second", taken.toUpperCase(), null).status);
        assertEquals(before, api.get("/speakers/" + id).body, "a refused update changes nothing");
    }

    @Test
    void aSpeakerKeepsTheirOwnEmailInAnotherCase() {
        String email = email("own");
        Api.Res created = api.register("Own", email);
        long id = created.json.get("id").asLong();
        assertEquals(200, update(id, "Own", email.toUpperCase(), null).status);
    }

    @Test
    void anInvalidUpdateIsRefused() {
        long id = api.speaker();
        String before = api.get("/speakers/" + id).body;
        assertEquals(400, update(id, "", email("x"), null).status, "empty name");
        assertEquals(400, update(id, null, email("x"), null).status, "no name");
        assertEquals(400, update(id, "Name", "not-an-email", null).status, "bad email");
        assertEquals(400, update(id, "Name", email("x"), "a".repeat(1001)).status, "bio over 1000");
        assertEquals(before, api.get("/speakers/" + id).body, "a refused update changes nothing");
        assertEquals(200, update(id, "Name", email("x"), "a".repeat(1000)).status, "bio of exactly 1000");
    }

    @Test
    void updatingAnUnknownSpeakerIs404() {
        assertEquals(404, update(999999, "Name", email("x"), null).status);
    }

    private Api.Res update(long id, String name, String email, String bio) {
        return api.send("PUT", "/speakers/" + id,
                "{\"name\":" + quoted(name) + ",\"email\":" + quoted(email) + ",\"bio\":" + quoted(bio) + "}");
    }

    private static String quoted(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private static String email(String who) {
        return who + "-" + UUID.randomUUID() + "@example.com";
    }
}
