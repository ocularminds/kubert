package io.github.ocularminds.blazra.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.ocularminds.blazra.model.RegistryCredentials;
import io.github.ocularminds.blazra.model.RegistryRepository;
import org.mockito.Mockito;

class DockerHubRegistryClientTest {
    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void listsAllPublicTagPages() throws Exception {
        server.createContext("/v2/namespaces/library/repositories/nginx/tags", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("page=2")) {
                respond(exchange, 200, "{\"next\":null,\"results\":[{\"name\":\"1.2\"}]}");
            } else {
                String next = baseUri + "v2/namespaces/library/repositories/nginx/tags?page=2";
                respond(exchange, 200, "{\"next\":\"" + next
                        + "\",\"results\":[{\"name\":\"1.0\"},{\"ignored\":true}]}");
            }
        });

        assertEquals(
                List.of("1.0", "1.2"),
                client(Optional.empty()).listTags(repository("library/nginx")));
    }

    @Test
    void exchangesCredentialsForBearerToken() throws Exception {
        AtomicReference<String> authenticationBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v2/auth/token", exchange -> {
            authenticationBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"access_token\":\"short-lived-token\"}");
        });
        server.createContext("/v2/namespaces/team/repositories/app/tags", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"next\":null,\"results\":[]}");
        });
        Optional<RegistryCredentials> credentials = Optional.of(
                new RegistryCredentials("robot", "private-token"));

        client(credentials).listTags(repository("team/app"));

        assertTrue(authenticationBody.get().contains("robot"));
        assertTrue(authenticationBody.get().contains("private-token"));
        assertEquals("Bearer short-lived-token", authorization.get());
    }

    @Test
    void rejectsFailuresWithoutLeakingResponseBodies() {
        server.createContext("/v2/namespaces/team/repositories/app/tags", exchange ->
                respond(exchange, 500, "sensitive upstream details"));

        RegistryException exception = assertThrows(
                RegistryException.class,
                () -> client(Optional.empty()).listTags(repository("team/app")));

        assertTrue(exception.getMessage().contains("HTTP 500"));
        assertTrue(!exception.getMessage().contains("sensitive"));
    }

    @Test
    void rejectsMalformedAndUnsafeResponses() {
        server.createContext("/v2/namespaces/team/repositories/bad-json/tags", exchange ->
                respond(exchange, 200, "not-json"));
        server.createContext("/v2/namespaces/team/repositories/bad-results/tags", exchange ->
                respond(exchange, 200, "{\"next\":null,\"results\":{}}"));
        server.createContext("/v2/namespaces/team/repositories/unsafe-next/tags", exchange ->
                respond(exchange, 200, "{\"next\":\"https://attacker.example/tags\",\"results\":[]}"));

        DockerHubRegistryClient client = client(Optional.empty());
        assertThrows(
                RegistryException.class,
                () -> client.listTags(repository("team/bad-json")));
        assertThrows(
                RegistryException.class,
                () -> client.listTags(repository("team/bad-results")));
        assertThrows(
                RegistryException.class,
                () -> client.listTags(repository("team/unsafe-next")));
    }

    @Test
    void validatesAuthenticationAndRepositoryResponses() {
        server.createContext("/v2/auth/token", exchange -> respond(exchange, 200, "{}"));
        DockerHubRegistryClient authenticated = client(Optional.of(
                new RegistryCredentials("robot", "token")));
        assertThrows(
                RegistryException.class,
                () -> authenticated.listTags(repository("team/app")));

        DockerHubRegistryClient anonymous = client(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> anonymous.listTags(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> anonymous.listTags(new RegistryRepository("ghcr.io", "team/app")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DockerHubRegistryClient(
                        HttpClient.newHttpClient(),
                        new ObjectMapper(),
                        Optional.empty(),
                        URI.create("file:///tmp/registry"),
                        Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DockerHubRegistryClient(
                        HttpClient.newHttpClient(),
                        new ObjectMapper(),
                        Optional.empty(),
                        baseUri,
                        Duration.ZERO));

        DockerHubRegistryClient productionClient = new DockerHubRegistryClient(
                Optional.empty(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));
        assertThrows(
                IllegalArgumentException.class,
                () -> productionClient.listTags(new RegistryRepository("ghcr.io", "team/app")));
    }

    @Test
    void rejectsOversizedInvalidAndUnboundedPagination() {
        String oversized = "{\"results\":[]}" + " ".repeat(2 * 1024 * 1024);
        server.createContext("/v2/namespaces/team/repositories/oversized/tags", exchange ->
                respond(exchange, 200, oversized));
        server.createContext("/v2/namespaces/team/repositories/blank-next/tags", exchange ->
                respond(exchange, 200, "{\"next\":\"\",\"results\":[]}"));
        server.createContext("/v2/namespaces/team/repositories/endless/tags", exchange ->
                respond(exchange, 200, "{\"next\":\"" + baseUri
                        + "v2/namespaces/team/repositories/endless/tags\",\"results\":[]}"));

        DockerHubRegistryClient client = client(Optional.empty());
        assertThrows(
                RegistryException.class,
                () -> client.listTags(repository("team/oversized")));
        assertThrows(
                RegistryException.class,
                () -> client.listTags(repository("team/blank-next")));
        assertThrows(
                RegistryException.class,
                () -> client.listTags(repository("team/endless")));
    }

    @Test
    void wrapsAuthenticationSerializationFailures() throws Exception {
        ObjectMapper mapper = Mockito.mock(ObjectMapper.class);
        Mockito.when(mapper.writeValueAsString(Mockito.any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("failed") {
                    private static final long serialVersionUID = 1L;
                });
        DockerHubRegistryClient client = new DockerHubRegistryClient(
                HttpClient.newHttpClient(),
                mapper,
                Optional.of(new RegistryCredentials("robot", "token")),
                baseUri,
                Duration.ofSeconds(1));

        assertThrows(RegistryException.class, () -> client.listTags(repository("team/app")));
    }

    @Test
    void preservesInterruptStatus() {
        server.createContext("/v2/namespaces/team/repositories/app/tags", exchange ->
                respond(exchange, 200, "{\"next\":null,\"results\":[]}"));
        Thread.currentThread().interrupt();
        try {
            assertThrows(
                    RegistryException.class,
                    () -> client(Optional.empty()).listTags(repository("team/app")));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private DockerHubRegistryClient client(Optional<RegistryCredentials> credentials) {
        return new DockerHubRegistryClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                new ObjectMapper(),
                credentials,
                baseUri,
                Duration.ofSeconds(2));
    }

    private static RegistryRepository repository(String path) {
        return RegistryRepository.dockerHub(path);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
