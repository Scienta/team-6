package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * The deploy contract: /health answers 200 only if the database answers within 5 s. HealthTest runs with a 1 s
 * timeout to save time, so this checks that the shipped configuration still says 5.
 */
class HealthContractTest {

    @Test
    void healthTimeoutIsFiveSecondsInProduction() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            props.load(in);
        }
        assertEquals("5", props.getProperty("health.db-timeout-seconds"));
    }
}
