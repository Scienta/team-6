package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ratings change request (specs/ratings-cr.md, workflow/specs/ratings-cr.md, DESIGN.md D1204, D1206, D1208–D1210):
 * trimmed at 5+ ratings, two decimals, half to even, always written with two decimals (PO-2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RatingsCrTest {

    private static final Pattern AVERAGE = Pattern.compile("\"averageRating\":(null|[0-9.]+)");

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

    /** A talk rated with these scores, by reviewers 1..n. */
    private long rated(int... scores) {
        long talk = api.talk(api.speaker());
        for (int i = 0; i < scores.length; i++) {
            api.rate(talk, i + 1, scores[i]);
        }
        return talk;
    }

    /** The average exactly as the JSON writes it (the tests' JSON tree would drop trailing zeros). */
    private static String rawAverage(String body) {
        Matcher m = AVERAGE.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    /** The raw average of one talk in a JSON list of talks (Talk objects hold no nested objects). */
    private static String rawAverage(String body, long talk) {
        Matcher m = Pattern.compile("\"id\":" + talk + ",[^{}]*?\"averageRating\":(null|[0-9.]+)").matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    private String average(long talk) {
        return rawAverage(api.talkById(talk).body);
    }

    private long ratingCount(long talk) {
        return api.talkById(talk).number("$.ratingCount");
    }

    // ------------------------------------------------------------ R2: rounding

    @Test
    void averageIsRoundedToTwoDecimalsHalfToEven() {
        assertEquals(new BigDecimal("3.12"), ConferenceService.roundAverage(25, 8), "3.125 -> 3.12");
        assertEquals(new BigDecimal("3.38"), ConferenceService.roundAverage(27, 8), "3.375 -> 3.38");
        assertEquals(new BigDecimal("4.25"), ConferenceService.roundAverage(17, 4));
        assertEquals(new BigDecimal("3.33"), ConferenceService.roundAverage(10, 3));
        assertEquals(new BigDecimal("4.00"), ConferenceService.roundAverage(4, 1), "always two decimals (PO-2)");
    }

    @Test
    void tiesGoToTheEvenNeighbourOverHttp() {
        // 10 ratings: the 1 and the 5 are left out, 8 count
        assertEquals("3.12", average(rated(1, 5, 3, 3, 3, 3, 3, 3, 3, 4)), "25/8 = 3.125");
        assertEquals("3.38", average(rated(1, 5, 3, 3, 3, 3, 3, 4, 4, 4)), "27/8 = 3.375");
    }

    // ------------------------------------------------------------ R1, R3: trimming

    @Test
    void trimmedFromFiveRatings() {
        long talk = rated(1, 2, 5, 5);
        assertEquals("3.25", average(talk), "4 ratings: every score counts");
        api.rate(talk, 5, 5);
        assertEquals("4.00", average(talk), "5 ratings: 1 and one 5 left out, (2+5+5)/3");
        assertEquals(5, ratingCount(talk), "R3: every reviewer is counted");
    }

    @Test
    void leavesOutExactlyOneHighestAndOneLowest() {
        long talk = rated(1, 1, 5, 5, 5, 5);
        assertEquals("4.00", average(talk), "(1+5+5+5)/4: only one 1 and one 5 left out");
        assertEquals(6, ratingCount(talk));
        assertEquals("4.00", average(rated(4, 4, 4, 4, 4)), "all equal");
    }

    @Test
    void replacingARatingRecalculates() {
        long talk = rated(1, 2, 3, 4, 5);
        assertEquals("3.00", average(talk), "(2+3+4)/3");
        api.rate(talk, 1, 5); // reviewer 1: 1 -> 5
        assertEquals("4.00", average(talk), "5,2,3,4,5 -> (3+4+5)/3");
        assertEquals(5, ratingCount(talk));
    }

    // ------------------------------------------------------------ R4: ranking

    @Test
    void rankingUsesTheNewAverage() {
        long outlier = rated(5, 5, 5, 5, 1); // old 4.2, new (5+5+5)/3 = 5.00
        long steady = rated(5, 5, 5, 4, 4);  // old 4.6, new (5+5+4)/3 = 4.67
        assertEquals("5.00", average(outlier));
        assertEquals("4.67", average(steady));
        List<Number> ids = api.get("/talks?sort=rating").at("$[*].id");
        List<Long> order = ids.stream().map(Number::longValue).filter(id -> id == outlier || id == steady).toList();
        assertEquals(List.of(outlier, steady), order, "the trimmed average decides; the old rule ranked steady first");
    }

    // ------------------------------------------------------------ PO-2: always two decimals

    @Test
    void averageIsAlwaysWrittenWithTwoDecimals() {
        long speaker = api.speaker();
        long talk = api.talk(speaker);
        assertEquals("null", average(talk), "unrated");
        api.rate(talk, 1, 4);
        assertEquals("4.00", average(talk));
        api.rate(talk, 2, 5);
        assertEquals("4.50", average(talk));
        for (String view : List.of("/talks", "/talks?sort=rating", "/talks?speakerId=" + speaker, "/speakers/" + speaker + "/talks")) {
            assertEquals("4.50", rawAverage(api.get(view).body, talk), view);
        }
    }
}
