package io.github.ocularminds.blazra.config;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class EnvironmentVariables {
    private static final String CURRENT_PREFIX = "BLAZRA_";
    private static final String LEGACY_PREFIX = "KUBERT_";

    private final Map<String, String> environment;

    EnvironmentVariables(Map<String, String> environment) {
        this.environment = Map.copyOf(
                Objects.requireNonNull(environment, "environment is required"));
    }

    Optional<String> value(String suffix) {
        String currentName = currentName(suffix);
        String legacyName = legacyName(suffix);
        String currentValue = nonBlank(environment.get(currentName));
        String legacyValue = nonBlank(environment.get(legacyName));

        if (currentValue != null
                && legacyValue != null
                && !currentValue.equals(legacyValue)) {
            throw new IllegalArgumentException(
                    currentName + " conflicts with deprecated " + legacyName);
        }
        return Optional.ofNullable(currentValue != null ? currentValue : legacyValue);
    }

    String currentName(String suffix) {
        return CURRENT_PREFIX + requireSuffix(suffix);
    }

    private static String legacyName(String suffix) {
        return LEGACY_PREFIX + requireSuffix(suffix);
    }

    private static String requireSuffix(String suffix) {
        Objects.requireNonNull(suffix, "environment variable suffix is required");
        if (suffix.isBlank()) {
            throw new IllegalArgumentException("environment variable suffix is required");
        }
        return suffix;
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
