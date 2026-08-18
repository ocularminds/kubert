package io.github.ocularminds.blazra.registry;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

record OciBearerChallenge(URI realm, String service, String scope) {
    private static final int MAX_HEADER_LENGTH = 2048;
    private static final Pattern SERVICE = Pattern.compile("[A-Za-z0-9._:-]{1,255}");
    private static final Pattern SCOPE = Pattern.compile("[A-Za-z0-9._~:/,-]{1,512}");

    static OciBearerChallenge parse(String header, URI registryBase) throws RegistryException {
        if (header == null
                || header.length() > MAX_HEADER_LENGTH
                || header.length() < 8
                || !header.regionMatches(true, 0, "Bearer", 0, 6)
                || !Character.isWhitespace(header.charAt(6))) {
            throw new RegistryException("OCI registry returned an invalid authentication challenge");
        }

        Map<String, String> parameters = parseParameters(header, 7);
        URI realm = parseRealm(parameters.get("realm"), registryBase);
        String service = parameters.get("service");
        String scope = parameters.get("scope");
        if (service == null
                || scope == null
                || !SERVICE.matcher(service).matches()
                || !SCOPE.matcher(scope).matches()) {
            throw new RegistryException("OCI registry returned an invalid authentication challenge");
        }
        return new OciBearerChallenge(realm, service, scope);
    }

    URI tokenUri() {
        String separator = realm.getRawQuery() == null ? "?" : "&";
        return URI.create(realm.toASCIIString()
                + separator
                + "service=" + encode(service)
                + "&scope=" + encode(scope));
    }

    private static Map<String, String> parseParameters(String header, int start)
            throws RegistryException {
        Map<String, String> parameters = new HashMap<>();
        int index = start;
        while (index < header.length()) {
            index = skipWhitespace(header, index);
            int keyStart = index;
            while (index < header.length() && validKeyCharacter(header.charAt(index))) {
                index++;
            }
            if (keyStart == index || index >= header.length() || header.charAt(index) != '=') {
                throw invalidChallenge();
            }
            String key = header.substring(keyStart, index).toLowerCase(Locale.ROOT);
            index++;
            if (index >= header.length() || header.charAt(index) != '"') {
                throw invalidChallenge();
            }
            StringBuilder value = new StringBuilder();
            index++;
            boolean closed = false;
            while (index < header.length()) {
                char character = header.charAt(index++);
                if (character == '"') {
                    closed = true;
                    break;
                }
                if (character == '\\') {
                    if (index >= header.length()) {
                        throw invalidChallenge();
                    }
                    character = header.charAt(index++);
                }
                if (Character.isISOControl(character)) {
                    throw invalidChallenge();
                }
                value.append(character);
            }
            if (!closed || parameters.putIfAbsent(key, value.toString()) != null) {
                throw invalidChallenge();
            }
            index = skipWhitespace(header, index);
            if (index < header.length()) {
                if (header.charAt(index) != ',') {
                    throw invalidChallenge();
                }
                index++;
                if (skipWhitespace(header, index) >= header.length()) {
                    throw invalidChallenge();
                }
            }
        }
        return parameters;
    }

    private static URI parseRealm(String value, URI registryBase) throws RegistryException {
        try {
            URI realm = URI.create(value == null ? "" : value);
            if (!realm.isAbsolute()
                    || realm.getHost() == null
                    || realm.getUserInfo() != null
                    || realm.getFragment() != null
                    || !realm.getScheme().equalsIgnoreCase(registryBase.getScheme())
                    || !realm.getAuthority().equalsIgnoreCase(registryBase.getAuthority())) {
                throw invalidChallenge();
            }
            return realm;
        } catch (IllegalArgumentException exception) {
            throw invalidChallenge();
        }
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean validKeyCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '-';
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static RegistryException invalidChallenge() {
        return new RegistryException("OCI registry returned an invalid authentication challenge");
    }
}
