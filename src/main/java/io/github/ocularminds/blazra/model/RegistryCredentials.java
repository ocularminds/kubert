package io.github.ocularminds.blazra.model;

public record RegistryCredentials(String identifier, String secret) {
    private static final int MAX_IDENTIFIER_LENGTH = 256;
    private static final int MAX_SECRET_LENGTH = 16 * 1024;

    public RegistryCredentials {
        if (identifier == null
                || identifier.isBlank()
                || identifier.length() > MAX_IDENTIFIER_LENGTH
                || identifier.indexOf(':') >= 0
                || identifier.chars().anyMatch(Character::isISOControl)
                || secret == null
                || secret.isBlank()
                || secret.length() > MAX_SECRET_LENGTH) {
            throw new IllegalArgumentException("registry identifier and secret are required");
        }
    }

    @Override
    public String toString() {
        return "RegistryCredentials[identifier=" + identifier + ", secret=<redacted>]";
    }
}
