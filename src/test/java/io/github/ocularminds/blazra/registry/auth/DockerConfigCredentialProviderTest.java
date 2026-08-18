package io.github.ocularminds.blazra.registry.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.ocularminds.blazra.model.RegistryCredentials;
import io.github.ocularminds.blazra.model.RegistryRepository;
import io.github.ocularminds.blazra.registry.RegistryException;

class DockerConfigCredentialProviderTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void readsOnlyCredentialsForTheExactRegistryHost() throws Exception {
        Path config = writeConfig("""
                {"auths":{
                  "ghcr.io":{"auth":"%s"},
                  "attacker-ghcr.io":{"auth":"%s"}
                }}
                """.formatted(
                encoded("robot:secret:with:colons"),
                encoded("attacker:wrong")));
        DockerConfigCredentialProvider provider = new DockerConfigCredentialProvider(config);

        RegistryCredentials credentials = provider.credentialsFor(repository("ghcr.io"))
                .orElseThrow();

        assertEquals("robot", credentials.identifier());
        assertEquals("secret:with:colons", credentials.secret());
        assertTrue(provider.credentialsFor(repository("registry.example")).isEmpty());
    }

    @Test
    void acceptsStandardDockerConfigHostKeyForms() throws Exception {
        for (String key : new String[]{
                "registry.example",
                "https://registry.example",
                "https://registry.example/",
                "https://registry.example/v1/"
        }) {
            Path config = writeConfig("""
                    {"auths":{"%s":{"auth":"%s"}}}
                    """.formatted(key, encoded("robot:token")));

            assertEquals(
                    "robot",
                    new DockerConfigCredentialProvider(config)
                            .credentialsFor(repository("registry.example"))
                            .orElseThrow()
                            .identifier(),
                    key);
        }
    }

    @Test
    void rereadsProjectedSecretsToSupportCredentialRotation() throws Exception {
        Path config = writeConfig(configWithAuth("registry.example", "robot:first"));
        DockerConfigCredentialProvider provider = new DockerConfigCredentialProvider(config);
        assertEquals(
                "first",
                provider.credentialsFor(repository("registry.example")).orElseThrow().secret());

        Files.writeString(
                config,
                configWithAuth("registry.example", "robot:second"),
                StandardCharsets.UTF_8);

        assertEquals(
                "second",
                provider.credentialsFor(repository("registry.example")).orElseThrow().secret());
    }

    @Test
    void rejectsMalformedOrAmbiguousCredentialDocumentsWithoutLeakingThem() throws Exception {
        for (String invalid : new String[]{
                "",
                "[]",
                "{\"secret\":\"visible-value\"}",
                "{\"auths\":[]}",
                "{\"auths\":{\"registry.example\":[]}}",
                "{\"auths\":{\"registry.example\":{\"auth\":1}}}",
                configWithEncodedAuth("registry.example", ""),
                configWithEncodedAuth("registry.example", "not-base64"),
                configWithEncodedAuth("registry.example", encoded("missing-separator")),
                configWithEncodedAuth("registry.example", encoded("robot:")),
                configWithEncodedAuth(
                        "registry.example",
                        Base64.getEncoder().encodeToString(new byte[]{(byte) 0xc3, (byte) 0x28})),
                "{\"auths\":{"
                        + "\"registry.example\":{\"auth\":\"" + encoded("one:first") + "\"},"
                        + "\"https://registry.example\":{\"auth\":\""
                        + encoded("two:second") + "\"}}}"
        }) {
            Path config = writeConfig(invalid);
            RegistryException exception = assertThrows(
                    RegistryException.class,
                    () -> new DockerConfigCredentialProvider(config)
                            .credentialsFor(repository("registry.example")));

            assertFalse(exception.getMessage().contains("visible-value"));
            assertNull(exception.getCause());
        }
    }

    @Test
    void boundsFilesAndCredentialEntries() throws Exception {
        Path oversizedFile = writeConfig(" ".repeat(1024 * 1024 + 1));
        assertThrows(
                RegistryException.class,
                () -> new DockerConfigCredentialProvider(oversizedFile)
                        .credentialsFor(repository("registry.example")));

        Path oversizedAuth = writeConfig(configWithEncodedAuth(
                "registry.example",
                "a".repeat(24 * 1024 + 1)));
        assertThrows(
                RegistryException.class,
                () -> new DockerConfigCredentialProvider(oversizedAuth)
                        .credentialsFor(repository("registry.example")));
    }

    @Test
    void validatesPathsAndReportsUnreadableFilesSafely() {
        assertThrows(
                NullPointerException.class,
                () -> new DockerConfigCredentialProvider(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DockerConfigCredentialProvider(Path.of("relative.json")));

        Path missing = temporaryDirectory.resolve("missing.json");
        RegistryException exception = assertThrows(
                RegistryException.class,
                () -> new DockerConfigCredentialProvider(missing)
                        .credentialsFor(repository("registry.example")));
        assertTrue(exception.getMessage().contains("could not read"));
        assertThrows(
                NullPointerException.class,
                () -> new DockerConfigCredentialProvider(missing).credentialsFor(null));
    }

    @Test
    void anonymousProviderNeverReturnsCredentials() throws Exception {
        RegistryCredentialProvider provider = RegistryCredentialProvider.anonymous();
        assertTrue(provider.credentialsFor(repository("registry.example")).isEmpty());
        assertThrows(NullPointerException.class, () -> provider.credentialsFor(null));
    }

    private Path writeConfig(String content) throws IOException {
        Path config = temporaryDirectory.resolve("config.json");
        Files.writeString(config, content, StandardCharsets.UTF_8);
        return config;
    }

    private static String configWithAuth(String host, String credentials) {
        return configWithEncodedAuth(host, encoded(credentials));
    }

    private static String configWithEncodedAuth(String host, String auth) {
        return "{\"auths\":{\"" + host + "\":{\"auth\":\"" + auth + "\"}}}";
    }

    private static String encoded(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static RegistryRepository repository(String host) {
        return new RegistryRepository(host, "team/app");
    }
}
