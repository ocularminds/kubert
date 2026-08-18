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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.ocularminds.blazra.model.RegistryRepository;

class OciRegistryClientTest {
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
    void listsAllAnonymousTagPages() throws Exception {
        AtomicReference<String> accept = new AtomicReference<>();
        server.createContext("/v2/team/app/tags/list", exchange -> {
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            if (exchange.getRequestURI().getQuery().contains("last=1.0")) {
                respond(exchange, 200, "{\"name\":\"team/app\",\"tags\":[\"1.2\"]}");
            } else {
                exchange.getResponseHeaders().set(
                        "Link",
                        "</v2/team/app/tags/list?n=100&last=1.0>; rel=\"next\"");
                respond(exchange, 200, "{\"name\":\"team/app\",\"tags\":[\"1.0\"]}");
            }
        });

        assertEquals(List.of("1.0", "1.2"), client().listTags(repository("team/app")));
        assertEquals("application/json", accept.get());
    }

    @Test
    void obtainsAndUsesAnAnonymousBearerToken() throws Exception {
        AtomicInteger tagRequests = new AtomicInteger();
        AtomicReference<String> tokenQuery = new AtomicReference<>();
        server.createContext("/token", exchange -> {
            tokenQuery.set(exchange.getRequestURI().getQuery());
            respond(exchange, 200, "{\"access_token\":\"short-lived-token\"}");
        });
        server.createContext("/v2/team/app/tags/list", exchange -> {
            tagRequests.incrementAndGet();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization == null) {
                exchange.getResponseHeaders().set(
                        "WWW-Authenticate",
                        "Bearer realm=\"" + baseUri + "token\","
                                + "service=\"registry.example\","
                                + "scope=\"repository:team/app:pull\"");
                respond(exchange, 401, "{\"errors\":[]}");
            } else {
                assertEquals("Bearer short-lived-token", authorization);
                respond(exchange, 200, "{\"name\":\"team/app\",\"tags\":null}");
            }
        });

        assertEquals(List.of(), client().listTags(repository("team/app")));
        assertEquals(2, tagRequests.get());
        assertTrue(tokenQuery.get().contains("service=registry.example"));
        assertTrue(tokenQuery.get().contains("scope=repository:team/app:pull"));
    }

    @Test
    void supportsEcrStyleScopesAndTokenFields() throws Exception {
        server.createContext("/token/", exchange -> {
            assertTrue(exchange.getRequestURI().getQuery().contains("scope=aws"));
            respond(exchange, 200, "{\"token\":\"ecr-token\"}");
        });
        authenticatedTags("public/app", "token/", "aws", "ecr-token");

        assertEquals(
                List.of("1.0"),
                client().listTags(repository("public/app")));
    }

    @Test
    void rejectsTokenAndAuthenticationFailures() {
        server.createContext("/token-empty", exchange -> respond(exchange, 200, "{}"));
        server.createContext("/token-conflict", exchange -> respond(
                exchange,
                200,
                "{\"token\":\"first\",\"access_token\":\"second\"}"));
        server.createContext("/token-malformed", exchange -> respond(
                exchange,
                200,
                "{\"token\":\"bad token\"}"));
        server.createContext("/token-empty-body", exchange -> respond(exchange, 200, ""));
        server.createContext("/token-array", exchange -> respond(exchange, 200, "[]"));
        server.createContext("/token-large", exchange -> respond(
                exchange,
                200,
                "{\"token\":\"" + "a".repeat(16 * 1024 + 1) + "\"}"));
        challengeOnly("empty/app", "token-empty");
        challengeOnly("conflict/app", "token-conflict");
        challengeOnly("malformed/app", "token-malformed");
        challengeOnly("empty-body/app", "token-empty-body");
        challengeOnly("array/app", "token-array");
        challengeOnly("large/app", "token-large");
        server.createContext("/v2/missing/app/tags/list", exchange ->
                respond(exchange, 401, "{}"));

        for (String path : new String[]{
                "empty/app", "conflict/app", "malformed/app", "empty-body/app", "array/app",
                "large/app", "missing/app"
        }) {
            assertThrows(
                    RegistryException.class,
                    () -> client().listTags(repository(path)),
                    () -> "accepted authentication response for " + path);
        }
    }

    @Test
    void rejectsInvalidOrFailedTagResponsesWithoutLeakingBodies() {
        server.createContext("/v2/error/app/tags/list", exchange ->
                respond(exchange, 500, "sensitive upstream details"));
        server.createContext("/v2/json/app/tags/list", exchange ->
                respond(exchange, 200, "not-json"));
        server.createContext("/v2/name/app/tags/list", exchange ->
                respond(exchange, 200, "{\"name\":\"other/app\",\"tags\":[]}"));
        server.createContext("/v2/object/app/tags/list", exchange ->
                respond(exchange, 200, "{\"name\":\"object/app\",\"tags\":{}}"));
        server.createContext("/v2/element/app/tags/list", exchange ->
                respond(exchange, 200, "{\"name\":\"element/app\",\"tags\":[1]}"));
        server.createContext("/v2/tag/app/tags/list", exchange ->
                respond(exchange, 200, "{\"name\":\"tag/app\",\"tags\":[\"bad tag\"]}"));
        server.createContext("/v2/empty/app/tags/list", exchange ->
                respond(exchange, 200, ""));

        RegistryException failure = assertThrows(
                RegistryException.class,
                () -> client().listTags(repository("error/app")));
        assertTrue(failure.getMessage().contains("HTTP 500"));
        assertTrue(!failure.getMessage().contains("sensitive"));
        for (String path : new String[]{
                "json/app", "name/app", "object/app", "element/app", "tag/app", "empty/app"
        }) {
            assertThrows(
                    RegistryException.class,
                    () -> client().listTags(repository(path)),
                    () -> "accepted invalid response for " + path);
        }
    }

    @Test
    void boundsResponseSizeAndPagination() {
        String oversized = "{\"name\":\"large/app\",\"tags\":[]}" + " ".repeat(2 * 1024 * 1024);
        server.createContext("/v2/large/app/tags/list", exchange ->
                respond(exchange, 200, oversized));
        linkedTags("unsafe/app", "<https://attacker.example/tags>; rel=\"next\"");
        linkedTags("wrong/app", "</v2/other/app/tags/list?n=100>; rel=\"next\"");
        linkedTags("malformed-link/app", "not-a-link");
        linkedTags("large-link/app", "<" + "a".repeat(2049) + ">; rel=\"next\"");
        linkedTags(
                "endless/app",
                "</v2/endless/app/tags/list?n=100&last=again>; rel=\"next\"");
        server.createContext("/v2/many/app/tags/list", exchange -> respond(
                exchange,
                200,
                "{\"name\":\"many/app\",\"tags\":[\"1.0\""
                        + ",\"1.0\"".repeat(1000) + "]}"));

        for (String path : new String[]{
                "large/app", "unsafe/app", "wrong/app", "malformed-link/app", "large-link/app",
                "endless/app", "many/app"
        }) {
            assertThrows(
                    RegistryException.class,
                    () -> client().listTags(repository(path)),
                    () -> "accepted unbounded response for " + path);
        }
    }

    @Test
    void validatesConstructionAndPreservesInterruptStatus() {
        new OciRegistryClient(Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OciRegistryClient(Duration.ZERO, Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OciRegistryClient(
                        HttpClient.newHttpClient(),
                        new ObjectMapper(),
                        ignored -> URI.create("file:///tmp/registry"),
                        Duration.ofSeconds(1)).listTags(repository("team/app")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OciRegistryClient(
                        HttpClient.newHttpClient(),
                        new ObjectMapper(),
                        ignored -> baseUri,
                        Duration.ZERO));
        assertThrows(
                NullPointerException.class,
                () -> new OciRegistryClient(
                        null,
                        new ObjectMapper(),
                        ignored -> baseUri,
                        Duration.ofSeconds(1)));
        assertThrows(
                NullPointerException.class,
                () -> new OciRegistryClient(
                        HttpClient.newHttpClient(),
                        null,
                        ignored -> baseUri,
                        Duration.ofSeconds(1)));
        assertThrows(
                NullPointerException.class,
                () -> new OciRegistryClient(
                        HttpClient.newHttpClient(),
                        new ObjectMapper(),
                        null,
                        Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> client().listTags(null));

        server.createContext("/v2/team/app/tags/list", exchange ->
                respond(exchange, 200, "{\"name\":\"team/app\",\"tags\":[]}"));
        Thread.currentThread().interrupt();
        try {
            assertThrows(
                    RegistryException.class,
                    () -> client().listTags(repository("team/app")));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private void authenticatedTags(String path, String tokenPath, String scope, String token) {
        server.createContext("/v2/" + path + "/tags/list", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization == null) {
                exchange.getResponseHeaders().set(
                        "WWW-Authenticate",
                        "Bearer realm=\"" + baseUri + tokenPath + "\","
                                + "service=\"registry.example\",scope=\"" + scope + "\"");
                respond(exchange, 401, "{}");
            } else {
                assertEquals("Bearer " + token, authorization);
                respond(exchange, 200, "{\"name\":\"" + path + "\",\"tags\":[\"1.0\"]}");
            }
        });
    }

    private void challengeOnly(String path, String tokenPath) {
        server.createContext("/v2/" + path + "/tags/list", exchange -> {
            exchange.getResponseHeaders().set(
                    "WWW-Authenticate",
                    "Bearer realm=\"" + baseUri + tokenPath + "\","
                            + "service=\"registry.example\",scope=\"aws\"");
            respond(exchange, 401, "{}");
        });
    }

    private void linkedTags(String path, String link) {
        server.createContext("/v2/" + path + "/tags/list", exchange -> {
            exchange.getResponseHeaders().set("Link", link);
            respond(exchange, 200, "{\"name\":\"" + path + "\",\"tags\":[]}");
        });
    }

    private OciRegistryClient client() {
        return new OciRegistryClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                new ObjectMapper(),
                ignored -> baseUri,
                Duration.ofSeconds(2));
    }

    private static RegistryRepository repository(String path) {
        return new RegistryRepository("registry.example", path);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
