package io.github.ocularminds.blazra.registry.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import io.github.ocularminds.blazra.model.RegistryCredentials;
import io.github.ocularminds.blazra.model.RegistryRepository;
import io.github.ocularminds.blazra.registry.RegistryException;

public final class DockerConfigCredentialProvider implements RegistryCredentialProvider {
    private static final int MAX_CONFIG_BYTES = 1024 * 1024;
    private static final int MAX_ENCODED_AUTH_LENGTH = 24 * 1024;

    private final Path configPath;
    private final ObjectMapper objectMapper;

    public DockerConfigCredentialProvider(Path configPath) {
        this(configPath, new ObjectMapper());
    }

    DockerConfigCredentialProvider(Path configPath, ObjectMapper objectMapper) {
        this.configPath = requireAbsolutePath(configPath);
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper is required");
    }

    @Override
    public Optional<RegistryCredentials> credentialsFor(RegistryRepository repository)
            throws RegistryException {
        Objects.requireNonNull(repository, "registry repository is required");
        JsonNode document = readConfig();
        JsonNode auths = document.get("auths");
        if (auths == null || !auths.isObject()) {
            throw invalidConfig();
        }

        JsonNode entry = findExactHostEntry(auths, repository.host());
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.isObject()) {
            throw invalidConfig();
        }
        JsonNode encoded = entry.get("auth");
        if (encoded == null
                || !encoded.isTextual()
                || encoded.textValue().isBlank()
                || encoded.textValue().length() > MAX_ENCODED_AUTH_LENGTH) {
            throw invalidConfig();
        }
        return Optional.of(decodeCredentials(encoded.textValue()));
    }

    private JsonNode readConfig() throws RegistryException {
        byte[] content;
        try (InputStream input = Files.newInputStream(configPath)) {
            content = input.readNBytes(MAX_CONFIG_BYTES + 1);
        } catch (IOException exception) {
            throw new RegistryException("could not read OCI registry credential file", exception);
        }
        if (content.length > MAX_CONFIG_BYTES) {
            throw new RegistryException("OCI registry credential file is too large");
        }
        try {
            JsonNode document = objectMapper.readTree(content);
            if (document == null || !document.isObject()) {
                throw invalidConfig();
            }
            return document;
        } catch (IOException exception) {
            throw invalidConfig();
        }
    }

    private static JsonNode findExactHostEntry(JsonNode auths, String host)
            throws RegistryException {
        JsonNode selected = null;
        for (String candidate : List.of(
                host,
                "https://" + host,
                "https://" + host + "/",
                "https://" + host + "/v1/")) {
            JsonNode found = auths.get(candidate);
            if (found != null) {
                if (selected != null) {
                    throw new RegistryException(
                            "OCI registry credential file has ambiguous host entries");
                }
                selected = found;
            }
        }
        return selected;
    }

    private static RegistryCredentials decodeCredentials(String encoded)
            throws RegistryException {
        try {
            String decoded = decodeUtf8(Base64.getDecoder().decode(encoded));
            int separator = decoded.indexOf(':');
            if (separator <= 0 || separator == decoded.length() - 1) {
                throw invalidConfig();
            }
            return new RegistryCredentials(
                    decoded.substring(0, separator),
                    decoded.substring(separator + 1));
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            throw invalidConfig();
        }
    }

    private static String decodeUtf8(byte[] value) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value))
                .toString();
    }

    private static Path requireAbsolutePath(Path value) {
        Objects.requireNonNull(value, "credential file path is required");
        if (!value.isAbsolute()) {
            throw new IllegalArgumentException("credential file path must be absolute");
        }
        return value.normalize();
    }

    private static RegistryException invalidConfig() {
        return new RegistryException("OCI registry credential file is invalid");
    }
}
