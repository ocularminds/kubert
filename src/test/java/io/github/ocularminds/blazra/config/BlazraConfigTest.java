package io.github.ocularminds.blazra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.github.ocularminds.blazra.service.UpdatePolicy;

class BlazraConfigTest {
    @Test
    void loadsSafeDefaults() {
        BlazraConfig config = BlazraConfig.fromEnvironment(Map.of(
                "BLAZRA_DEPLOYMENT", "api",
                "BLAZRA_CONTAINER", "web"));

        assertEquals("default", config.target().namespace());
        assertEquals(Duration.ofMinutes(5), config.pollInterval());
        assertEquals(Duration.ofSeconds(5), config.connectTimeout());
        assertEquals(Duration.ofSeconds(15), config.requestTimeout());
        assertFalse(config.dryRun());
        assertEquals(UpdatePolicy.PATCH, config.updatePolicy());
        assertTrue(config.dockerHubCredentials().isEmpty());
        assertTrue(config.ociRegistryConfigPath().isEmpty());
    }

    @Test
    void loadsEveryOverride() {
        BlazraConfig config = BlazraConfig.fromEnvironment(Map.ofEntries(
                Map.entry("BLAZRA_NAMESPACE", "payments"),
                Map.entry("BLAZRA_DEPLOYMENT", "api"),
                Map.entry("BLAZRA_CONTAINER", "web"),
                Map.entry("BLAZRA_POLL_INTERVAL", "PT30S"),
                Map.entry("BLAZRA_CONNECT_TIMEOUT", "PT2S"),
                Map.entry("BLAZRA_REQUEST_TIMEOUT", "PT8S"),
                Map.entry("BLAZRA_DRY_RUN", "TRUE"),
                Map.entry("BLAZRA_UPDATE_POLICY", "minor"),
                Map.entry(
                        "BLAZRA_OCI_REGISTRY_CONFIG_PATH",
                        "/var/run/secrets/registry/config.json"),
                Map.entry("DOCKER_HUB_USERNAME", "robot"),
                Map.entry("DOCKER_HUB_TOKEN", "secret")));

        assertEquals("payments", config.target().namespace());
        assertEquals(Duration.ofSeconds(30), config.pollInterval());
        assertEquals(Duration.ofSeconds(2), config.connectTimeout());
        assertEquals(Duration.ofSeconds(8), config.requestTimeout());
        assertTrue(config.dryRun());
        assertEquals(UpdatePolicy.MINOR, config.updatePolicy());
        assertEquals("robot", config.dockerHubCredentials().orElseThrow().identifier());
        assertEquals(
                Path.of("/var/run/secrets/registry/config.json"),
                config.ociRegistryConfigPath().orElseThrow());
    }

    @Test
    void loadsDeprecatedKubertAliases() {
        BlazraConfig config = BlazraConfig.fromEnvironment(Map.of(
                "KUBERT_NAMESPACE", "legacy",
                "KUBERT_DEPLOYMENT", "api",
                "KUBERT_CONTAINER", "web",
                "KUBERT_POLL_INTERVAL", "PT1M",
                "KUBERT_CONNECT_TIMEOUT", "PT3S",
                "KUBERT_REQUEST_TIMEOUT", "PT9S",
                "KUBERT_DRY_RUN", "true",
                "KUBERT_UPDATE_POLICY", "major",
                "KUBERT_OCI_REGISTRY_CONFIG_PATH", "/legacy/registry.json"));

        assertEquals("legacy", config.target().namespace());
        assertEquals(Duration.ofMinutes(1), config.pollInterval());
        assertEquals(Duration.ofSeconds(3), config.connectTimeout());
        assertEquals(Duration.ofSeconds(9), config.requestTimeout());
        assertTrue(config.dryRun());
        assertEquals(UpdatePolicy.MAJOR, config.updatePolicy());
        assertEquals(
                Path.of("/legacy/registry.json"),
                config.ociRegistryConfigPath().orElseThrow());
    }

    @Test
    void rejectsConflictingCurrentAndDeprecatedVariables() {
        Map<String, String> environment = Map.of(
                "BLAZRA_DEPLOYMENT", "current-api",
                "KUBERT_DEPLOYMENT", "legacy-api",
                "BLAZRA_CONTAINER", "web");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> BlazraConfig.fromEnvironment(environment));

        assertEquals(
                "BLAZRA_DEPLOYMENT conflicts with deprecated KUBERT_DEPLOYMENT",
                exception.getMessage());
    }

    @Test
    void rejectsMissingAndPartialConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> BlazraConfig.fromEnvironment(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> BlazraConfig.fromEnvironment(Map.of(
                "BLAZRA_DEPLOYMENT", "api")));
        assertThrows(IllegalArgumentException.class, () -> BlazraConfig.fromEnvironment(Map.of(
                "BLAZRA_DEPLOYMENT", "api",
                "BLAZRA_CONTAINER", "web",
                "DOCKER_HUB_USERNAME", "robot")));
        assertThrows(IllegalArgumentException.class, () -> BlazraConfig.fromEnvironment(Map.of(
                "BLAZRA_DEPLOYMENT", "api",
                "BLAZRA_CONTAINER", "web",
                "BLAZRA_OCI_REGISTRY_CONFIG_PATH", "relative/config.json")));
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
                    () -> BlazraConfig.fromEnvironment(environment),
                    invalid.getKey());
        }
        Map<String, String> malformed = Map.of(
                "BLAZRA_DEPLOYMENT", "api",
                "BLAZRA_CONTAINER", "web",
                "BLAZRA_POLL_INTERVAL", "five minutes");
        assertThrows(IllegalArgumentException.class, () -> BlazraConfig.fromEnvironment(malformed));
        Map<String, String> oversizedPath = Map.of(
                "BLAZRA_DEPLOYMENT", "api",
                "BLAZRA_CONTAINER", "web",
                "BLAZRA_OCI_REGISTRY_CONFIG_PATH", "/" + "a".repeat(4096));
        assertThrows(
                IllegalArgumentException.class,
                () -> BlazraConfig.fromEnvironment(oversizedPath));
        Map<String, String> invalidPath = Map.of(
                "BLAZRA_DEPLOYMENT", "api",
                "BLAZRA_CONTAINER", "web",
                "BLAZRA_OCI_REGISTRY_CONFIG_PATH", "/bad\u0000path");
        assertThrows(
                IllegalArgumentException.class,
                () -> BlazraConfig.fromEnvironment(invalidPath));
    }
}
