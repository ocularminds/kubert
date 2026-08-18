package osfx.kubert.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import osfx.kubert.service.UpdatePolicy;

class KubertConfigTest {
    @Test
    void loadsSafeDefaults() {
        KubertConfig config = KubertConfig.fromEnvironment(Map.of(
                "BLAZRA_DEPLOYMENT", "api",
                "BLAZRA_CONTAINER", "web"));

        assertEquals("default", config.target().namespace());
        assertEquals(Duration.ofMinutes(5), config.pollInterval());
        assertEquals(Duration.ofSeconds(5), config.connectTimeout());
        assertEquals(Duration.ofSeconds(15), config.requestTimeout());
        assertFalse(config.dryRun());
        assertEquals(UpdatePolicy.PATCH, config.updatePolicy());
        assertTrue(config.registryCredentials().isEmpty());
    }

    @Test
    void loadsEveryOverride() {
        KubertConfig config = KubertConfig.fromEnvironment(Map.of(
                "BLAZRA_NAMESPACE", "payments",
                "BLAZRA_DEPLOYMENT", "api",
                "BLAZRA_CONTAINER", "web",
                "BLAZRA_POLL_INTERVAL", "PT30S",
                "BLAZRA_CONNECT_TIMEOUT", "PT2S",
                "BLAZRA_REQUEST_TIMEOUT", "PT8S",
                "BLAZRA_DRY_RUN", "TRUE",
                "BLAZRA_UPDATE_POLICY", "minor",
                "DOCKER_HUB_USERNAME", "robot",
                "DOCKER_HUB_TOKEN", "secret"));

        assertEquals("payments", config.target().namespace());
        assertEquals(Duration.ofSeconds(30), config.pollInterval());
        assertEquals(Duration.ofSeconds(2), config.connectTimeout());
        assertEquals(Duration.ofSeconds(8), config.requestTimeout());
        assertTrue(config.dryRun());
        assertEquals(UpdatePolicy.MINOR, config.updatePolicy());
        assertEquals("robot", config.registryCredentials().orElseThrow().identifier());
    }

    @Test
    void loadsDeprecatedKubertAliases() {
        KubertConfig config = KubertConfig.fromEnvironment(Map.of(
                "KUBERT_NAMESPACE", "legacy",
                "KUBERT_DEPLOYMENT", "api",
                "KUBERT_CONTAINER", "web",
                "KUBERT_POLL_INTERVAL", "PT1M",
                "KUBERT_CONNECT_TIMEOUT", "PT3S",
                "KUBERT_REQUEST_TIMEOUT", "PT9S",
                "KUBERT_DRY_RUN", "true",
                "KUBERT_UPDATE_POLICY", "major"));

        assertEquals("legacy", config.target().namespace());
        assertEquals(Duration.ofMinutes(1), config.pollInterval());
        assertEquals(Duration.ofSeconds(3), config.connectTimeout());
        assertEquals(Duration.ofSeconds(9), config.requestTimeout());
        assertTrue(config.dryRun());
        assertEquals(UpdatePolicy.MAJOR, config.updatePolicy());
    }

    @Test
    void rejectsConflictingCurrentAndDeprecatedVariables() {
        Map<String, String> environment = Map.of(
                "BLAZRA_DEPLOYMENT", "current-api",
                "KUBERT_DEPLOYMENT", "legacy-api",
                "BLAZRA_CONTAINER", "web");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> KubertConfig.fromEnvironment(environment));

        assertEquals(
                "BLAZRA_DEPLOYMENT conflicts with deprecated KUBERT_DEPLOYMENT",
                exception.getMessage());
    }

    @Test
    void rejectsMissingAndPartialConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> KubertConfig.fromEnvironment(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> KubertConfig.fromEnvironment(Map.of(
                "BLAZRA_DEPLOYMENT", "api")));
        assertThrows(IllegalArgumentException.class, () -> KubertConfig.fromEnvironment(Map.of(
                "BLAZRA_DEPLOYMENT", "api",
                "BLAZRA_CONTAINER", "web",
                "DOCKER_HUB_USERNAME", "robot")));
    }

    @Test
    void rejectsUnsafeIntervalsAndMalformedValues() {
        for (Map.Entry<String, String> invalid : Map.of(
                "BLAZRA_POLL_INTERVAL", "PT9S",
                "BLAZRA_CONNECT_TIMEOUT", "PT0S",
                "BLAZRA_REQUEST_TIMEOUT", "-PT1S",
                "BLAZRA_DRY_RUN", "yes",
                "BLAZRA_UPDATE_POLICY", "anything").entrySet()) {
            Map<String, String> environment = new HashMap<>();
            environment.put("BLAZRA_DEPLOYMENT", "api");
            environment.put("BLAZRA_CONTAINER", "web");
            environment.put(invalid.getKey(), invalid.getValue());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> KubertConfig.fromEnvironment(environment),
                    invalid.getKey());
        }
        Map<String, String> malformed = Map.of(
                "BLAZRA_DEPLOYMENT", "api",
                "BLAZRA_CONTAINER", "web",
                "BLAZRA_POLL_INTERVAL", "five minutes");
        assertThrows(IllegalArgumentException.class, () -> KubertConfig.fromEnvironment(malformed));
    }
}
