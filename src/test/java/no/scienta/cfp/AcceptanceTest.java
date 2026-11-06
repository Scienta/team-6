package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Acceptance state machine (workflow/specs/acceptance.md). Test names follow the rules R1..R9 and the
 * product owner's answers PO-1..3.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcceptanceTest {

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

    /** A talk rated by two different reviewers, so it can be accepted. */
    private long ratedTalk(long speaker) {
        long talk = api.talk(speaker);
        api.rate(talk, 1, 4);
        api.rate(talk, 2, 5);
        return talk;
    }

    private void assertRefused(int expected, Api.Res res, long talk, String unchangedStatus) {
        assertEquals(expected, res.status, res.toString());
        assertEquals(unchangedStatus, api.status(talk), "R8: a refused request changes nothing");
    }

    private void assertTalk(String status, Api.Res res) {
        assertEquals(200, res.status, res.toString());
        assertEquals(status, res.text("status"));
    }

    @Test
    void committeeCanRejectOrWaitlistASubmittedTalk() {
        long speaker = api.speaker();
        long a = api.talk(speaker);
        long b = api.talk(speaker);
        assertTalk("REJECTED", api.decide(a, "REJECTED"));
        assertTalk("WAITLISTED", api.decide(b, "WAITLISTED"));
        assertEquals("REJECTED", api.status(a));
        assertEquals("WAITLISTED", api.status(b));
    }

    @Test
    void waitlistedCanStillBeDecided() {
        long speaker = api.speaker();
        long a = ratedTalk(speaker);
        long b = api.talk(speaker);
        assertTalk("WAITLISTED", api.decide(a, "WAITLISTED"));
        assertTalk("ACCEPTED", api.decide(a, "ACCEPTED"));
        assertTalk("WAITLISTED", api.decide(b, "WAITLISTED"));
        assertTalk("REJECTED", api.decide(b, "REJECTED"));
    }

    @Test
    void acceptedAndRejectedAreFinal() {
        long speaker = api.speaker();
        long accepted = ratedTalk(speaker);
        long rejected = ratedTalk(speaker);
        assertTalk("ACCEPTED", api.decide(accepted, "ACCEPTED"));
        assertTalk("REJECTED", api.decide(rejected, "REJECTED"));
        assertRefused(409, api.decide(accepted, "REJECTED"), accepted, "ACCEPTED");
        assertRefused(409, api.decide(accepted, "WAITLISTED"), accepted, "ACCEPTED");
        assertRefused(409, api.decide(rejected, "ACCEPTED"), rejected, "REJECTED");
        assertRefused(409, api.decide(rejected, "WAITLISTED"), rejected, "REJECTED");
    }

    @Test
    void acceptNeedsTwoDifferentReviewers() {
        long talk = api.talk(api.speaker());
        assertRefused(409, api.decide(talk, "ACCEPTED"), talk, "SUBMITTED");
        api.rate(talk, 7, 5);
        api.rate(talk, 7, 4); // the same reviewer again is still one reviewer
        assertRefused(409, api.decide(talk, "ACCEPTED"), talk, "SUBMITTED");
        api.rate(talk, 8, 3);
        assertTalk("ACCEPTED", api.decide(talk, "ACCEPTED"));
    }

    @Test
    void speakerCanWithdrawUnlessRejected() {
        long speaker = api.speaker();
        long submitted = api.talk(speaker);
        long waitlisted = api.talk(speaker);
        long accepted = ratedTalk(speaker);
        api.decide(waitlisted, "WAITLISTED");
        api.decide(accepted, "ACCEPTED");
        assertTalk("WITHDRAWN", api.withdraw(submitted, speaker));
        assertTalk("WITHDRAWN", api.withdraw(waitlisted, speaker));
        assertTalk("WITHDRAWN", api.withdraw(accepted, speaker));

        long other = api.speaker();
        long rejected = api.talk(other);
        api.decide(rejected, "REJECTED");
        assertRefused(409, api.withdraw(rejected, other), rejected, "REJECTED");
        // ownership is checked before the state: someone else gets 403 even on a rejected talk
        assertRefused(403, api.withdraw(rejected, speaker), rejected, "REJECTED");
    }

    @Test
    void onlyTheSpeakerCanWithdraw() {
        long speaker = api.speaker();
        long talk = api.talk(speaker);
        long someoneElse = api.speaker();
        assertRefused(403, api.withdraw(talk, someoneElse), talk, "SUBMITTED");
        // PO-3: an id that isn't a registered speaker is "someone else" too: 403, not 404
        assertRefused(403, api.withdraw(talk, 999_999), talk, "SUBMITTED");
    }

    @Test
    void withdrawnIsOutForGood() {
        long speaker = api.speaker();
        long talk = ratedTalk(speaker);
        assertTalk("WITHDRAWN", api.withdraw(talk, speaker));
        assertRefused(409, api.withdraw(talk, speaker), talk, "WITHDRAWN");
        assertRefused(409, api.decide(talk, "ACCEPTED"), talk, "WITHDRAWN");
        assertRefused(409, api.decide(talk, "REJECTED"), talk, "WITHDRAWN");
        assertRefused(409, api.decide(talk, "WAITLISTED"), talk, "WITHDRAWN");
    }

    @Test
    void statusShowsEverywhere() {
        long speaker = api.speaker();
        long talk = api.talk(speaker);
        api.decide(talk, "WAITLISTED");
        assertEquals("WAITLISTED", api.get("/talks/" + talk).text("status"));
        assertEquals("WAITLISTED", api.get("/speakers/" + speaker + "/talks").json.get(0).get("status").asText());
        assertEquals("WAITLISTED", api.get("/talks?speakerId=" + speaker).json.get(0).get("status").asText());
        boolean inList = false;
        for (com.fasterxml.jackson.databind.JsonNode t : api.get("/talks").json) {
            if (t.get("id").asLong() == talk) {
                assertEquals("WAITLISTED", t.get("status").asText());
                inList = true;
            }
        }
        assertTrue(inList);
    }

    @Test
    void invalidRequests() {
        long speaker = api.speaker();
        long talk = api.talk(speaker);
        for (String bad : new String[] {"SUBMITTED", "WITHDRAWN", "accepted", "MAYBE", ""}) {
            assertRefused(400, api.decide(talk, bad), talk, "SUBMITTED");
        }
        assertRefused(400, api.send("PUT", "/talks/" + talk + "/status", "{}"), talk, "SUBMITTED");
        assertRefused(400, api.send("PUT", "/talks/" + talk + "/status", "{\"status\":1}"), talk, "SUBMITTED");
        assertRefused(400, api.send("PUT", "/talks/" + talk + "/status", null), talk, "SUBMITTED");
        assertRefused(400, api.send("POST", "/talks/" + talk + "/withdraw", "{}"), talk, "SUBMITTED");
        assertRefused(400, api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":\"x\"}"), talk, "SUBMITTED");
        assertRefused(400, api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":1.5}"), talk, "SUBMITTED");
        assertRefused(400, api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":0}"), talk, "SUBMITTED");
        assertRefused(400, api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":-3}"), talk, "SUBMITTED");
        assertRefused(400, api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":null}"), talk, "SUBMITTED");
        assertRefused(400, api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":true}"), talk, "SUBMITTED");
        // 2.0 isn't a whole number in JSON terms here, as for rating scores
        assertRefused(400, api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + speaker + ".0}"), talk, "SUBMITTED");
        assertRefused(400, api.send("POST", "/talks/" + talk + "/withdraw", null), talk, "SUBMITTED");
        // PO-3: a whole number too big to be anyone's id is someone else
        assertRefused(403, api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":99999999999999999999}"), talk, "SUBMITTED");
        assertRefused(400, api.send("PUT", "/talks/" + talk + "/status", "{\"status\":null}"), talk, "SUBMITTED");
        assertEquals(404, api.decide(999_999, "REJECTED").status);
        assertEquals(404, api.withdraw(999_999, speaker).status);
        assertEquals(400, api.decide(999_999, "NOPE").status, "the body is checked before the talk");
        assertEquals(400, api.send("POST", "/talks/999999/withdraw", "{}").status, "the body is checked before the talk");
        assertEquals(400, api.send("PUT", "/talks/abc/status", "{\"status\":\"REJECTED\"}").status);
    }

    // PO-1: repeating the decision a talk already has is a 200, and changes nothing
    @Test
    void repeatingTheSameDecisionIsHarmless() {
        long speaker = api.speaker();
        long accepted = ratedTalk(speaker);
        long rejected = api.talk(speaker);
        long waitlisted = api.talk(speaker);
        api.decide(accepted, "ACCEPTED");
        api.decide(rejected, "REJECTED");
        api.decide(waitlisted, "WAITLISTED");
        assertTalk("ACCEPTED", api.decide(accepted, "ACCEPTED"));
        assertTalk("REJECTED", api.decide(rejected, "REJECTED"));
        assertTalk("WAITLISTED", api.decide(waitlisted, "WAITLISTED"));
        assertEquals("WAITLISTED", api.status(waitlisted));
    }

    // PO-2: withdrawn and rejected talks don't count towards the 3-talk limit
    @Test
    void withdrawnAndRejectedTalksDoNotCountTowardsTheLimit() {
        long speaker = api.speaker();
        long first = api.talk(speaker);
        long second = api.talk(speaker);
        api.talk(speaker);
        assertEquals(409, api.submit(speaker).status, "3 counting talks: a 4th is refused");

        assertTalk("WITHDRAWN", api.withdraw(first, speaker));
        long fourth = api.talk(speaker); // the withdrawn one no longer counts
        assertEquals(409, api.submit(speaker).status, "3 counting talks again");

        assertTalk("REJECTED", api.decide(second, "REJECTED"));
        api.talk(speaker); // the rejected one no longer counts
        assertEquals(409, api.submit(speaker).status);

        assertTalk("WAITLISTED", api.decide(fourth, "WAITLISTED"));
        assertEquals(409, api.submit(speaker).status, "waitlisted still counts");
        assertEquals(5, api.get("/speakers/" + speaker + "/talks").json.size(), "the speaker still sees all their talks");
    }

    @Test
    void concurrentDecisionsHaveOneWinner() {
        String[] decisions = {"ACCEPTED", "REJECTED", "WAITLISTED"};
        for (int round = 0; round < 3; round++) {
            long talk = ratedTalk(api.speaker());
            List<HttpRequest> requests = new ArrayList<>();
            List<String> asked = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                String d = decisions[(i + round) % 3];
                asked.add(d);
                requests.add(api.request("PUT", "/talks/" + talk + "/status", "{\"status\":\"" + d + "\"}"));
            }
            List<Api.Res> answers = api.concurrently(requests);
            String finalStatus = api.status(talk);
            for (int i = 0; i < answers.size(); i++) {
                Api.Res r = answers.get(i);
                assertTrue(r.status == 200 || r.status == 409, r.toString());
                if (r.status == 200) {
                    // every winner saw a state the rules allow, and the talk ends in a state some winner asked for
                    assertTrue(asked.get(i).equals(r.text("status")), r.toString());
                }
            }
            // The final state is reachable by the rules: accepted or rejected (final), or waitlisted only if no
            // ACCEPTED/REJECTED request won after it. Every ACCEPTED/REJECTED 200 must equal the final status.
            for (int i = 0; i < answers.size(); i++) {
                if (answers.get(i).status == 200 && !asked.get(i).equals("WAITLISTED")) {
                    assertEquals(asked.get(i), finalStatus, "a final decision was overwritten");
                }
            }
            assertTrue(answers.stream().anyMatch(r -> r.status == 200));
            boolean acceptedWon = false;
            boolean rejectedWon = false;
            for (int i = 0; i < answers.size(); i++) {
                acceptedWon |= answers.get(i).status == 200 && asked.get(i).equals("ACCEPTED");
                rejectedWon |= answers.get(i).status == 200 && asked.get(i).equals("REJECTED");
            }
            assertTrue(!(acceptedWon && rejectedWon), "ACCEPTED and REJECTED can't both win");
            if (!acceptedWon && !rejectedWon) {
                assertEquals("WAITLISTED", finalStatus);
            }
        }
    }

    @Test
    void withdrawRacingAcceptEndsConsistent() {
        for (int round = 0; round < 5; round++) {
            long speaker = api.speaker();
            long talk = ratedTalk(speaker);
            List<HttpRequest> requests = new ArrayList<>();
            requests.add(api.request("PUT", "/talks/" + talk + "/status", "{\"status\":\"ACCEPTED\"}"));
            requests.add(api.request("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + speaker + "}"));
            requests.add(api.request("PUT", "/talks/" + talk + "/status", "{\"status\":\"REJECTED\"}"));
            List<Api.Res> answers = api.concurrently(requests);
            String finalStatus = api.status(talk);
            for (Api.Res r : answers) {
                assertTrue(r.status == 200 || r.status == 409, r.toString());
            }
            boolean withdrew = answers.get(1).status == 200;
            boolean rejected = answers.get(2).status == 200;
            assertTrue(!(answers.get(0).status == 200 && rejected), "accepted and rejected can't both win");
            assertTrue(!(withdrew && rejected), "a rejected talk can't be withdrawn, nor a withdrawn one rejected");
            if (withdrew) {
                assertEquals("WITHDRAWN", finalStatus, "a withdrawal is never overwritten");
            } else if (rejected) {
                assertEquals("REJECTED", finalStatus);
            } else {
                assertEquals("ACCEPTED", finalStatus);
            }
        }
    }
}
