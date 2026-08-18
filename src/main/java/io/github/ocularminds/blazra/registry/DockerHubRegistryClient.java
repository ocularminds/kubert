package io.github.ocularminds.blazra.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import io.github.ocularminds.blazra.model.RegistryCredentials;
import io.github.ocularminds.blazra.model.RegistryRepository;

public final class DockerHubRegistryClient implements RegistryClient {
    private static final URI DEFAULT_BASE_URI = URI.create("https://hub.docker.com/");
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PAGES = 20;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Optional<RegistryCredentials> credentials;
    private final URI baseUri;
    private final Duration requestTimeout;

    public DockerHubRegistryClient(
            Optional<RegistryCredentials> credentials,
            Duration connectTimeout,
            Duration requestTimeout) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                new ObjectMapper(),
                credentials,
                DEFAULT_BASE_URI,
                requestTimeout);
    }

    DockerHubRegistryClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Optional<RegistryCredentials> credentials,
            URI baseUri,
            Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "http client is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper is required");
        this.credentials = Objects.requireNonNull(credentials, "credentials are required");
        this.baseUri = requireHttpBase(baseUri);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "request timeout is required");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("request timeout must be positive");
        }
    }

    @Override
    public List<String> listTags(RegistryRepository repository) throws RegistryException {
        if (repository == null || !repository.isDockerHub()) {
            throw new IllegalArgumentException("a Docker Hub repository is required");
        }
        String[] components = repository.path().split("/", 2);
        URI page = baseUri.resolve(
                "v2/namespaces/" + components[0] + "/repositories/" + components[1]
                        + "/tags?page_size=100");
        Optional<String> bearerToken = credentials.isPresent()
                ? Optional.of(authenticate(credentials.get()))
                : Optional.empty();
        List<String> tags = new ArrayList<>();

        for (int pageNumber = 0; page != null && pageNumber < MAX_PAGES; pageNumber++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(page)
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .GET();
            bearerToken.ifPresent(token -> builder.header("Authorization", "Bearer " + token));
            JsonNode document = sendJson(builder.build(), "list Docker Hub tags");
            JsonNode results = document.path("results");
            if (!results.isArray()) {
                throw new RegistryException("Docker Hub returned an invalid tag response");
            }
            for (JsonNode result : results) {
                JsonNode name = result.get("name");
                if (name != null && name.isTextual()) {
                    tags.add(name.textValue());
                }
            }
            page = nextPage(document.get("next"));
        }
        if (page != null) {
            throw new RegistryException("Docker Hub tag response exceeded the page limit");
        }
        return List.copyOf(tags);
    }

    private String authenticate(RegistryCredentials registryCredentials) throws RegistryException {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "identifier", registryCredentials.identifier(),
                    "secret", registryCredentials.secret()));
        } catch (JsonProcessingException exception) {
            throw new RegistryException("could not create Docker Hub authentication request");
        }
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("v2/auth/token"))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        JsonNode document = sendJson(request, "authenticate with Docker Hub");
        JsonNode token = document.get("access_token");
        if (token == null || !token.isTextual() || token.textValue().isBlank()) {
            throw new RegistryException("Docker Hub returned an invalid authentication response");
        }
        return token.textValue();
    }

    private JsonNode sendJson(HttpRequest request, String operation) throws RegistryException {
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RegistryException(operation + " failed with HTTP " + response.statusCode());
                }
                byte[] body = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (body.length > MAX_RESPONSE_BYTES) {
                    throw new RegistryException(operation + " returned too much data");
                }
                try {
                    return objectMapper.readTree(body);
                } catch (IOException exception) {
                    throw new RegistryException(operation + " returned invalid JSON");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RegistryException(operation + " was interrupted", exception);
        } catch (IOException exception) {
            throw new RegistryException(operation + " response could not be read", exception);
        }
    }

    private URI nextPage(JsonNode next) throws RegistryException {
        if (next == null || next.isNull()) {
            return null;
        }
        if (!next.isTextual() || next.textValue().isBlank()) {
            throw new RegistryException("Docker Hub returned an invalid pagination link");
        }
        URI candidate = baseUri.resolve(next.textValue());
        if (!candidate.getScheme().equalsIgnoreCase(baseUri.getScheme())
                || !candidate.getAuthority().equalsIgnoreCase(baseUri.getAuthority())) {
            throw new RegistryException("Docker Hub returned an unsafe pagination link");
        }
        return candidate;
    }

    private static URI requireHttpBase(URI value) {
        Objects.requireNonNull(value, "base URI is required");
        if (value.getHost() == null
                || !(value.getScheme().equalsIgnoreCase("https")
                || value.getScheme().equalsIgnoreCase("http"))) {
            throw new IllegalArgumentException("base URI must be an absolute HTTP URL");
        }
        return value;
    }
}
