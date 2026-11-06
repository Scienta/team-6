package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Workshop registration (workflow/specs/workshop-registration.md). Test names follow the rules R1..R8 and the product
 * owner's answers PO-1..4.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkshopRegistrationTest {

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

    // ---------------------------------------------------------------- helpers

    /** A WORKSHOP talk in the given status. ACCEPTED needs two reviewers' ratings first (acceptance R3). */
    private long workshop(String status) {
        long talk = api.talk(api.speaker(), "Hands-on", "WORKSHOP", "BEGINNER");
        if (status.equals("ACCEPTED")) {
            api.rate(talk, 1, 4);
            api.rate(talk, 2, 5);
        }
        if (!status.equals("SUBMITTED")) {
            assertEquals(200, api.decide(talk, status).status);
        }
        return talk;
    }

    private long acceptedWorkshop() {
        return workshop("ACCEPTED");
    }

    private static String body(String name, String email) {
        return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}";
    }

    private static String uniqueEmail() {
        return "att-" + UUID.randomUUID() + "@example.com";
    }

    private Api.Res register(long workshop, String name, String email) {
        return api.send("POST", "/workshops/" + workshop + "/registrations", body(name, email));
    }

    private HttpRequest registerRequest(long workshop, String name, String email) {
        return api.request("POST", "/workshops/" + workshop + "/registrations", body(name, email));
    }

    private Api.Res list(long workshop) {
        return api.get("/workshops/" + workshop + "/registrations");
    }

    private int listSize(long workshop) {
        Api.Res r = list(workshop);
        assertEquals(200, r.status, r.toString());
        return r.size("$");
    }

    // ---------------------------------------------------------------- R1, PO-4

    @Test
    void emailsThatLookLikeEmailsAreAccepted() {
        long workshop = acceptedWorkshop();
        for (String email : new String[] {"a@b.co", "ada.lovelace+cfp@mail.example.co.uk", "ÆØÅ@eksempel.no"}) {
            assertEquals(201, register(workshop, "Ada", email).status, email);
        }
    }

    @Test
    void attendeeRegistersWithNameAndEmail() {
        long workshop = acceptedWorkshop();
        Api.Res r = register(workshop, "Ada Lovelace", "Ada@Example.COM");
        assertEquals(201, r.status, r.toString());
        assertTrue(r.number("$.id") > 0);
        assertEquals(workshop, r.number("$.workshopId"));
        assertEquals("Ada Lovelace", r.text("name"));
        assertEquals("Ada@Example.COM", r.text("email"), "R8: the email as the attendee typed it");
    }

    @Test
    void nameAndEmailAreRequired() {
        long workshop = acceptedWorkshop();
        String path = "/workshops/" + workshop + "/registrations";
        String[] bad = {
            "{}",
            "{\"name\":\"Ada\"}",
            "{\"email\":\"ada@example.com\"}",
            "{\"name\":\"\",\"email\":\"ada@example.com\"}",
            "{\"name\":\"   \",\"email\":\"ada@example.com\"}",
            "{\"name\":1,\"email\":\"ada@example.com\"}",
            "{\"name\":\"Ada\",\"email\":null}",
            "{\"name\":\"Ada\",\"email\":\"\"}",
            // PO-4: the email must look like an email
            body("Ada", "ada"),
            body("Ada", "ada@"),
            body("Ada", "@example.com"),
            body("Ada", "ada@example"),
            body("Ada", "ada@example."),
            body("Ada", "a da@example.com"),
            body("Ada", "ada@exa@mple.com"),
            body("Ada", "ada@example.com."),
            body("Ada", "ada@a..com"),
            body("Ada", "ada@.example.com"),
            body("Ada", "ada@exa\u00a0mple.com"),
            body("Ada", "ada@exa\u0007mple.com"),
            body("Ada", " ada@example.com "),
            // not a JSON object
            "[]",
            "\"ada@example.com\"",
            "{\"name\":\"Ada\",",
        };
        for (String json : bad) {
            Api.Res r = api.send("POST", path, json);
            assertEquals(400, r.status, json + " -> " + r);
        }
        assertEquals(400, api.send("POST", path, null).status, "no body");
        assertEquals(0, listSize(workshop), "nothing was registered");
        assertEquals(400, register(999_999, "Ada", "ada").status, "the body is checked before the workshop");
    }

    // ---------------------------------------------------------------- R2, PO-1, PO-2

    @Test
    void onlyAcceptedWorkshopsTakeRegistrations() {
        assertEquals(404, register(999_999, "Ada", uniqueEmail()).status, "unknown talk");
        assertEquals(404, list(999_999).status);

        // PO-1: a talk that isn't a workshop is a 400, whatever its status
        long presentation = api.talk(api.speaker());
        assertEquals(400, register(presentation, "Ada", uniqueEmail()).status);
        assertEquals(400, list(presentation).status);

        // PO-2: a workshop that isn't accepted is a 409
        for (String status : new String[] {"SUBMITTED", "WAITLISTED", "REJECTED"}) {
            long workshop = workshop(status);
            assertEquals(409, register(workshop, "Ada", uniqueEmail()).status, status);
            assertEquals(0, listSize(workshop), status);
        }
    }

    // ---------------------------------------------------------------- R3

    @Test
    void withdrawnWorkshopKeepsItsList() {
        long speaker = api.speaker();
        long workshop = api.talk(speaker, "Hands-on", "WORKSHOP", "BEGINNER");
        api.rate(workshop, 1, 4);
        api.rate(workshop, 2, 5);
        api.decide(workshop, "ACCEPTED");
        Api.Res ada = register(workshop, "Ada", "ada@example.com");
        assertEquals(201, register(workshop, "Grace", "grace@example.com").status);

        assertEquals(200, api.withdraw(workshop, speaker).status);
        assertEquals(2, listSize(workshop), "R3: the people already registered stay");
        assertEquals(409, register(workshop, "Edsger", uniqueEmail()).status, "no new registrations");
        Api.Res again = register(workshop, "Ada", "ADA@example.com");
        assertEquals(200, again.status, "PO-3: already on the list: " + again);
        assertEquals(ada.number("$.id"), again.number("$.id"));
    }

    // ---------------------------------------------------------------- R4

    @Test
    void atMostTwentyAttendees() {
        long workshop = acceptedWorkshop();
        String first = uniqueEmail();
        assertEquals(201, register(workshop, "Attendee 0", first).status);
        for (int i = 1; i < 20; i++) {
            assertEquals(201, register(workshop, "Attendee " + i, uniqueEmail()).status, "attendee " + i);
        }
        assertEquals(409, register(workshop, "Attendee 21", uniqueEmail()).status, "the 21st");
        assertEquals(200, register(workshop, "Attendee 0", first).status, "PO-3: already registered, also when full");
        assertEquals(20, listSize(workshop));
    }

    @Test
    void rushNeverExceedsTwenty() {
        long workshop = acceptedWorkshop();
        List<HttpRequest> rush = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            rush.add(registerRequest(workshop, "Rusher " + i, uniqueEmail()));
        }
        int created = 0;
        int full = 0;
        for (Api.Res r : api.concurrently(rush)) {
            if (r.status == 201) {
                created++;
            } else {
                assertEquals(409, r.status, r.toString());
                full++;
            }
        }
        assertEquals(20, created);
        assertEquals(10, full);
        assertEquals(20, listSize(workshop));
    }

    // ---------------------------------------------------------------- R5, R6, PO-3

    @Test
    void oneRegistrationPerEmailRegardlessOfCase() {
        long workshop = acceptedWorkshop();
        Api.Res first = register(workshop, "Ada", "ada@example.com");
        assertEquals(201, first.status);
        Api.Res again = register(workshop, "Ada L.", "ADA@Example.com");
        assertEquals(200, again.status, "PO-3: the existing registration: " + again);
        assertEquals(first.number("$.id"), again.number("$.id"));
        assertEquals("ada@example.com", again.text("email"), "as first typed");
        assertEquals("Ada", again.text("name"), "nothing changes");
        assertEquals(1, listSize(workshop));
    }

    @Test
    void concurrentDuplicatesRegisterOnce() {
        long workshop = acceptedWorkshop();
        String[] variants = {"grace@example.com", "GRACE@example.com", "Grace@Example.com", "grace@EXAMPLE.COM", "gRaCe@example.com"};
        List<HttpRequest> clicks = new ArrayList<>();
        for (String email : variants) {
            clicks.add(registerRequest(workshop, "Grace", email));
        }
        int created = 0;
        Set<Long> ids = new HashSet<>();
        for (Api.Res r : api.concurrently(clicks)) {
            assertTrue(r.status == 201 || r.status == 200, r.toString());
            if (r.status == 201) {
                created++;
            }
            ids.add(r.number("$.id"));
        }
        assertEquals(1, created);
        assertEquals(1, ids.size(), "every answer is the same registration");
        assertEquals(1, listSize(workshop));
    }

    @Test
    void samePersonCanRegisterForSeveralWorkshops() {
        long a = acceptedWorkshop();
        long b = acceptedWorkshop();
        assertEquals(201, register(a, "Ada", "ada@example.com").status);
        assertEquals(201, register(b, "Ada", "ada@example.com").status);
    }

    // ---------------------------------------------------------------- R7, R8

    @Test
    void organisersSeeRegistrationsInOrder() {
        long workshop = acceptedWorkshop();
        assertEquals(0, listSize(workshop), "[] when none");
        register(workshop, "Grace", "grace@example.com");
        register(workshop, "Ada", "Ada@Example.com");
        register(workshop, "Edsger", "edsger@example.com");
        Api.Res r = list(workshop);
        assertEquals(List.of("Grace", "Ada", "Edsger"), r.at("$[*].name"));
        assertEquals(List.of("grace@example.com", "Ada@Example.com", "edsger@example.com"), r.at("$[*].email"));
        List<Number> workshopIds = r.at("$[*].workshopId");
        workshopIds.forEach(id -> assertEquals(workshop, id.longValue()));
        List<Number> ids = r.at("$[*].id");
        assertTrue(ids.get(0).longValue() < ids.get(1).longValue() && ids.get(1).longValue() < ids.get(2).longValue());
        assertEquals(3, api.get("/workshops/" + workshop + "/registrations/").size("$"), "trailing slash works too");
        Api.Res slash = api.send("POST", "/workshops/" + workshop + "/registrations/", body("Alan", "alan@example.com"));
        assertEquals(201, slash.status, "POST with a trailing slash too: " + slash);
    }
}
