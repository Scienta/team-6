package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The legacy string formats that other tools read (specs/talks.md, specs/ratings.md, the CSV export). They depend on
 * the JDK's locale data and on Jackson's defaults, so a Java or Spring Boot upgrade can change them without any other
 * test noticing. Don't loosen these to make an upgrade pass: fix the formatting.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FormatsTest {

    /** specs/talks.md: always milliseconds, always "+00:00", never "Z". */
    private static final String SUBMITTED_AT = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00";

    /** specs/ratings.md: M/d/yy, h:mm AM, no leading zeros, one ordinary space (U+0020) before AM/PM. */
    private static final String RATED_AT_DISPLAY = "([1-9]|1[0-2])/([1-9]|[12]\\d|3[01])/\\d{2}, ([1-9]|1[0-2]):[0-5]\\d [AP]M";

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

    // ---------------------------------------------------------------- ratedAtDisplay (Utils.usShort)

    @Test
    void ratedAtDisplayIsUsShortWithoutLeadingZeros() {
        assertEquals("11/4/26, 1:05 PM", Utils.usShort(utc("2026-11-04T13:05:00Z")));
        assertEquals("1/9/27, 9:07 AM", Utils.usShort(utc("2027-01-09T09:07:59Z")));
        assertEquals("12/31/26, 11:59 PM", Utils.usShort(utc("2026-12-31T23:59:00Z")));
    }

    @Test
    void ratedAtDisplayAtMidnightAndNoonIsTwelve() {
        assertEquals("11/4/26, 12:00 AM", Utils.usShort(utc("2026-11-04T00:00:00Z")));
        assertEquals("11/4/26, 12:00 PM", Utils.usShort(utc("2026-11-04T12:00:00Z")));
    }

    /** JDK 20+ (CLDR 42) puts a narrow no-break space (U+202F) before AM/PM. The spec wants an ordinary space. */
    @Test
    void ratedAtDisplayHasAnOrdinarySpaceBeforeAmPm() {
        String shown = Utils.usShort(utc("2026-11-04T13:05:00Z"));
        char beforeAmPm = shown.charAt(shown.length() - 3);
        assertEquals(' ', beforeAmPm, String.format("U+%04X before AM/PM in '%s' on Java %s", (int) beforeAmPm, shown,
                System.getProperty("java.version")));
    }

    @Test
    void ratingAnswersWithTheTimeOfRatingInUtc() {
        long talk = api.talk(api.speaker());
        Instant before = Instant.now();
        Api.Res r = api.rate(talk, 1, 4);
        String shown = r.text("ratedAtDisplay");
        assertTrue(shown.matches(RATED_AT_DISPLAY), "'" + shown + "'");
        // the display has minute precision: it is one of the minutes around the request, in UTC
        String atStart = Utils.usShort(Date.from(before));
        String atEnd = Utils.usShort(Date.from(Instant.now()));
        assertTrue(shown.equals(atStart) || shown.equals(atEnd), shown + " not in [" + atStart + ", " + atEnd + "]");
    }

    // ---------------------------------------------------------------- submittedAt / submittedOn

    @Test
    void submittedAtHasMillisecondsAndAPlusZeroOffset() {
        Instant before = Instant.now();
        Api.Res r = api.submit(api.speaker());
        assertEquals(201, r.status, r.toString());
        String at = r.text("submittedAt");
        assertTrue(at.matches(SUBMITTED_AT), "'" + at + "'");
        Instant parsed = OffsetDateTime.parse(at).toInstant();
        assertTrue(Duration.between(before, parsed).abs().getSeconds() < 60, at + " is not now (" + before + ") in UTC");
        assertEquals(at.substring(0, 10), r.text("submittedOn"), "submittedOn is the UTC date of submittedAt");
    }

    @Test
    void submittedAtIsTheSameEverywhereTheTalkIsShown() {
        long speaker = api.speaker();
        Api.Res created = api.submit(speaker);
        assertEquals(201, created.status, created.toString());
        long id = created.json.get("id").asLong();
        String at = created.text("submittedAt");
        String on = created.text("submittedOn");

        assertEquals(at, api.talkById(id).text("submittedAt"));
        assertEquals(on, api.talkById(id).text("submittedOn"));
        Api.Res own = api.get("/speakers/" + speaker + "/talks");
        assertEquals(at, own.json.get(0).get("submittedAt").asText());
        assertEquals(on, own.json.get(0).get("submittedOn").asText());
    }

    /** 23:30 UTC is already the next day in Oslo; submittedOn must still be the UTC date. */
    @Test
    void submittedOnIsTheUtcDateEvenWhenItIsTomorrowLocally() {
        assertEquals("2026-11-04", Utils.utcDay(utc("2026-11-04T23:30:00Z").getTime()));
        assertEquals("2026-11-05", Utils.utcDay(utc("2026-11-05T00:30:00Z").getTime()));
    }

    // ---------------------------------------------------------------- averageRating

    @Test
    void averageRatingHasTwoDecimals() {
        long talk = api.talk(api.speaker());
        api.rate(talk, 1, 4);
        api.rate(talk, 2, 4);
        api.rate(talk, 3, 4);
        api.rate(talk, 4, 5);
        // ratings-cr: two decimals, half to even, so 4.25 stays 4.25 (was 4.3 under specs/ratings.md)
        assertTrue(api.talkById(talk).body.contains("\"averageRating\":4.25"), api.talkById(talk).body);
    }

    @Test
    void averageRatingOfAWholeNumberIsWrittenWithTwoDecimals() {
        long talk = api.talk(api.speaker());
        api.rate(talk, 1, 4);
        // ratings-cr PO-2: always two decimals. Checked on the raw body: the tests' JSON tree would read 4.00 as 4.0.
        assertTrue(api.talkById(talk).body.contains("\"averageRating\":4.00"), api.talkById(talk).body);
    }

    // ---------------------------------------------------------------- CSV export (ReportController)

    @Test
    void csvDateIsNorwegianMediumInOsloTime() {
        assertEquals("4. nov. 2026", Utils.norwegianDate(utc("2026-11-04T12:00:00Z")));
        // 23:30 UTC is past midnight in Oslo
        assertEquals("5. nov. 2026", Utils.norwegianDate(utc("2026-11-04T23:30:00Z")));
        assertEquals("1. mai 2027", Utils.norwegianDate(utc("2027-05-01T10:00:00Z")));
    }

    @Test
    void csvValuesWithSemicolonsOrQuotesAreQuoted() {
        assertEquals("", Utils.csv(null));
        assertEquals("plain", Utils.csv("plain"));
        assertEquals("\"a;b\"", Utils.csv("a;b"));
        assertEquals("\"say 'hi'\"", Utils.csv("say \"hi\""));
    }

    @Test
    void csvFormatAndLevelAreCapitalised() {
        assertEquals("Lightning 10", Utils.capitalize("LIGHTNING_10".replace("_", " ")));
        assertEquals("Advanced", Utils.capitalize("ADVANCED"));
    }

    private static Date utc(String instant) {
        return Date.from(Instant.parse(instant));
    }
}
