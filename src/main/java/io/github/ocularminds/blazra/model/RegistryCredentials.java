package io.github.ocularminds.blazra.model;

public record RegistryCredentials(String identifier, String secret) {
    public RegistryCredentials {
        if (identifier == null || identifier.isBlank() || secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("registry identifier and secret are required");
        }
    }

    @Override
    public String toString() {
        return "RegistryCredentials[identifier=" + identifier + ", secret=<redacted>]";
    }
}
