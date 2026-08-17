package osfx.kubert.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KubertConfigTest {
    @Test
    void loadsSafeDefaults() {
        KubertConfig config = KubertConfig.fromEnvironment(Map.of(
                "KUBERT_DEPLOYMENT", "api",
                "KUBERT_CONTAINER", "web"));

        assertEquals("default", config.target().namespace());
        assertEquals(Duration.ofMinutes(5), config.pollInterval());
        assertEquals(Duration.ofSeconds(5), config.connectTimeout());
        assertEquals(Duration.ofSeconds(15), config.requestTimeout());
        assertFalse(config.dryRun());
        assertTrue(config.registryCredentials().isEmpty());
    }

    @Test
    void loadsEveryOverride() {
        KubertConfig config = KubertConfig.fromEnvironment(Map.of(
                "KUBERT_NAMESPACE", "payments",
                "KUBERT_DEPLOYMENT", "api",
                "KUBERT_CONTAINER", "web",
                "KUBERT_POLL_INTERVAL", "PT30S",
                "KUBERT_CONNECT_TIMEOUT", "PT2S",
                "KUBERT_REQUEST_TIMEOUT", "PT8S",
                "KUBERT_DRY_RUN", "TRUE",
                "DOCKER_HUB_USERNAME", "robot",
                "DOCKER_HUB_TOKEN", "secret"));

        assertEquals("payments", config.target().namespace());
        assertEquals(Duration.ofSeconds(30), config.pollInterval());
        assertEquals(Duration.ofSeconds(2), config.connectTimeout());
        assertEquals(Duration.ofSeconds(8), config.requestTimeout());
        assertTrue(config.dryRun());
        assertEquals("robot", config.registryCredentials().orElseThrow().identifier());
    }

    @Test
    void rejectsMissingAndPartialConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> KubertConfig.fromEnvironment(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> KubertConfig.fromEnvironment(Map.of(
                "KUBERT_DEPLOYMENT", "api")));
        assertThrows(IllegalArgumentException.class, () -> KubertConfig.fromEnvironment(Map.of(
                "KUBERT_DEPLOYMENT", "api",
                "KUBERT_CONTAINER", "web",
                "DOCKER_HUB_USERNAME", "robot")));
    }

    @Test
    void rejectsUnsafeIntervalsAndMalformedValues() {
        for (Map.Entry<String, String> invalid : Map.of(
                "KUBERT_POLL_INTERVAL", "PT9S",
                "KUBERT_CONNECT_TIMEOUT", "PT0S",
                "KUBERT_REQUEST_TIMEOUT", "-PT1S",
                "KUBERT_DRY_RUN", "yes").entrySet()) {
            Map<String, String> environment = new HashMap<>();
            environment.put("KUBERT_DEPLOYMENT", "api");
            environment.put("KUBERT_CONTAINER", "web");
            environment.put(invalid.getKey(), invalid.getValue());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> KubertConfig.fromEnvironment(environment),
                    invalid.getKey());
        }
        Map<String, String> malformed = Map.of(
                "KUBERT_DEPLOYMENT", "api",
                "KUBERT_CONTAINER", "web",
                "KUBERT_POLL_INTERVAL", "five minutes");
        assertThrows(IllegalArgumentException.class, () -> KubertConfig.fromEnvironment(malformed));
    }
}
