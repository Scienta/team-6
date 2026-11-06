package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Ratings, rule by rule from specs/ratings.md (base feature). The formats (ratedAtDisplay, the rounding) are pinned
 * in {@link FormatsTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RatingsTest {

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
    void ratingAnswersWithWhatWasRated() {
        long talk = api.talk(api.speaker());
        Api.Res r = api.rate(talk, 7, 3);
        assertEquals(talk, r.json.get("talkId").asLong());
        assertEquals(7, r.json.get("reviewerId").asLong());
        assertEquals(3, r.json.get("score").asInt());
    }

    @Test
    void ratingAgainReplacesTheOldScore() {
        long talk = api.talk(api.speaker());
        api.rate(talk, 1, 2);
        api.rate(talk, 1, 5);
        assertRatings(talk, 1, "5.0");
    }

    @Test
    void anUnratedTalkHasNoAverage() {
        long talk = api.talk(api.speaker());
        Api.Res r = api.talkById(talk);
        assertEquals(0, r.json.get("ratingCount").asInt());
        assertTrue(r.json.get("averageRating").isNull(), r.toString());
    }

    @Test
    void onlyWholeScoresFromOneToFive() {
        long talk = api.talk(api.speaker());
        for (String body : Arrays.asList("{\"score\":0}", "{\"score\":6}", "{\"score\":-1}", "{\"score\":4.5}",
                "{\"score\":\"4\"}", "{\"score\":null}", "{}", "{\"score\":true}")) {
            Api.Res r = api.rating(talk, 1, body);
            assertEquals(400, r.status, body + " -> " + r);
        }
        assertRatings(talk, 0, null);
    }

    @Test
    void scoresAtTheBoundsAreAccepted() {
        long talk = api.talk(api.speaker());
        api.rate(talk, 1, 1);
        api.rate(talk, 2, 5);
        assertRatings(talk, 2, "3.0");
    }

    @Test
    void reviewerIsAPositiveWholeNumber() {
        long talk = api.talk(api.speaker());
        for (String reviewer : Arrays.asList("0", "-1", "abc", "1.5")) {
            Api.Res r = api.rating(talk, reviewer, "{\"score\":4}");
            assertEquals(400, r.status, "reviewer " + reviewer + " -> " + r);
        }
        assertRatings(talk, 0, null);
    }

    @Test
    void ratingAnUnknownTalkIs404() {
        assertEquals(404, api.rating(999999, 1, "{\"score\":4}").status);
    }

    @Test
    void ratingsShowWhereverATalkIsShown() {
        long speaker = api.speaker();
        long talk = api.talk(speaker);
        api.rate(talk, 1, 4);
        api.rate(talk, 2, 5);

        assertRatings(talk, 2, "4.5");
        assertEquals("4.5", find(api.get("/speakers/" + speaker + "/talks"), talk).get("averageRating").asText());
        assertEquals("4.5", find(api.get("/talks"), talk).get("averageRating").asText());
        assertEquals("4.5", find(api.get("/talks?speakerId=" + speaker), talk).get("averageRating").asText());
        assertEquals("4.5", find(api.get("/talks?sort=rating"), talk).get("averageRating").asText());
    }

    /**
     * Best rounded average first; on the same rounded average, more ratings first; still tied, oldest first; unrated
     * last (oldest first). b and c tie at 4.3 only after rounding (4.25 vs 4.33), so ranking on the raw average
     * would put c first.
     */
    @Test
    void rankingUsesTheRoundedAverageThenTheCountThenAge() {
        long s1 = api.speaker();
        long s2 = api.speaker();
        long unratedOld = api.talk(s1);
        long b = api.talk(s1);   // 4,4,4,5 = 4.25 -> 4.3, 4 ratings
        long c = api.talk(s1);   // 4,5,4   = 4.33 -> 4.3, 3 ratings
        long top = api.talk(s2); // 5 -> 5.0, 1 rating
        long d = api.talk(s2);   // same as c, but newer
        long unratedNew = api.talk(s2);
        rateAll(b, 4, 4, 4, 5);
        rateAll(c, 4, 5, 4);
        rateAll(top, 5);
        rateAll(d, 4, 5, 4);

        List<Long> mine = Arrays.asList(unratedOld, b, c, top, d, unratedNew);
        List<Long> ranked = Api.ids(api.get("/talks?sort=rating")).stream().filter(mine::contains).collect(Collectors.toList());
        assertEquals(Arrays.asList(top, b, c, d, unratedOld, unratedNew), ranked);

        assertEquals(Arrays.asList(b, c, unratedOld), Api.ids(api.get("/talks?sort=rating&speakerId=" + s1)));
    }

    @Test
    void theCommitteeListIsOldestFirst() {
        long s = api.speaker();
        long first = api.talk(s);
        long second = api.talk(s);
        rateAll(second, 5);
        assertEquals(Arrays.asList(first, second), Api.ids(api.get("/talks?speakerId=" + s)));
        List<Long> all = Api.ids(api.get("/talks"));
        assertTrue(all.indexOf(first) < all.indexOf(second), all.toString());
    }

    @Test
    void anUnknownRankingIs400() {
        assertEquals(400, api.get("/talks?sort=newest").status);
    }

    @Test
    void manyReviewersAtOnceAreAllCounted() {
        long talk = api.talk(api.speaker());
        List<HttpRequest> requests = new ArrayList<>();
        for (int reviewer = 1; reviewer <= 20; reviewer++) {
            requests.add(api.ratingRequest(talk, reviewer, reviewer % 5 + 1));
        }
        for (Api.Res r : api.concurrently(requests)) {
            assertEquals(200, r.status, r.toString());
        }
        // every score 1..5 four times
        assertRatings(talk, 20, "3.0");
    }

    @Test
    void aDoubleClickCountsOnce() {
        long talk = api.talk(api.speaker());
        List<HttpRequest> requests = new ArrayList<>();
        for (int reviewer = 1; reviewer <= 10; reviewer++) {
            for (int click = 0; click < 3; click++) {
                requests.add(api.ratingRequest(talk, reviewer, 4));
            }
        }
        for (Api.Res r : api.concurrently(requests)) {
            assertEquals(200, r.status, r.toString());
        }
        assertRatings(talk, 10, "4.0");
    }

    private void rateAll(long talk, int... scores) {
        for (int i = 0; i < scores.length; i++) {
            api.rate(talk, i + 1, scores[i]);
        }
    }

    private void assertRatings(long talk, int count, String average) {
        Api.Res r = api.talkById(talk);
        assertEquals(count, r.json.get("ratingCount").asInt(), r.toString());
        assertEquals(average == null ? "null" : average, r.json.get("averageRating").asText(), r.toString());
    }

    private static JsonNode find(Api.Res list, long talk) {
        assertEquals(200, list.status, list.toString());
        for (JsonNode n : list.json) {
            if (n.get("id").asLong() == talk) {
                return n;
            }
        }
        throw new AssertionError("talk " + talk + " not in " + list);
    }
}
