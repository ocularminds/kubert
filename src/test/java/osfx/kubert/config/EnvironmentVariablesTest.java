package osfx.kubert.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentVariablesTest {
    @Test
    void resolvesCurrentVariables() {
        EnvironmentVariables variables = new EnvironmentVariables(Map.of(
                "BLAZRA_DEPLOYMENT", "api"));

        assertEquals("api", variables.value("DEPLOYMENT").orElseThrow());
        assertEquals("BLAZRA_DEPLOYMENT", variables.currentName("DEPLOYMENT"));
    }

    @Test
    void fallsBackToDeprecatedVariables() {
        EnvironmentVariables variables = new EnvironmentVariables(Map.of(
                "BLAZRA_DEPLOYMENT", " ",
                "KUBERT_DEPLOYMENT", "api"));

        assertEquals("api", variables.value("DEPLOYMENT").orElseThrow());
    }

    @Test
    void acceptsMatchingCurrentAndDeprecatedValues() {
        EnvironmentVariables variables = new EnvironmentVariables(Map.of(
                "BLAZRA_DEPLOYMENT", "api",
                "KUBERT_DEPLOYMENT", "api"));

        assertEquals("api", variables.value("DEPLOYMENT").orElseThrow());
    }

    @Test
    void treatsBlankValuesAsAbsent() {
        EnvironmentVariables variables = new EnvironmentVariables(Map.of(
                "BLAZRA_DEPLOYMENT", " ",
                "KUBERT_DEPLOYMENT", ""));

        assertTrue(variables.value("DEPLOYMENT").isEmpty());
    }

    @Test
    void rejectsConflictsWithoutExposingValues() {
        EnvironmentVariables variables = new EnvironmentVariables(Map.of(
                "BLAZRA_DEPLOYMENT", "current-secret-value",
                "KUBERT_DEPLOYMENT", "legacy-secret-value"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> variables.value("DEPLOYMENT"));

        assertEquals(
                "BLAZRA_DEPLOYMENT conflicts with deprecated KUBERT_DEPLOYMENT",
                exception.getMessage());
    }

    @Test
    void rejectsInvalidConstructionAndSuffixes() {
        assertThrows(NullPointerException.class, () -> new EnvironmentVariables(null));
        EnvironmentVariables variables = new EnvironmentVariables(Map.of());
        assertThrows(NullPointerException.class, () -> variables.value(null));
        assertThrows(IllegalArgumentException.class, () -> variables.value(" "));
    }
}
