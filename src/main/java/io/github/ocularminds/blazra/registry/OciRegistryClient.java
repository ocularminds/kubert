package io.github.ocularminds.blazra.registry;

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
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.ocularminds.blazra.model.RegistryCredentials;
import io.github.ocularminds.blazra.model.RegistryRepository;
import io.github.ocularminds.blazra.registry.auth.RegistryCredentialProvider;

public final class OciRegistryClient implements RegistryClient {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PAGES = 20;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_TAGS_PER_PAGE = 1000;
    private static final int MAX_LINK_HEADER_LENGTH = 2048;
    private static final Pattern NEXT_LINK = Pattern.compile(
            "<([^<>]+)>\\s*;[^,]*?\\brel\\s*=\\s*\"?next\"?",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_TOKEN_LENGTH = 16 * 1024;
    private static final int MAX_AUTH_CHALLENGE_LENGTH = 2048;
    private static final Pattern BEARER_TOKEN = Pattern.compile("[A-Za-z0-9\\-._~+/]+=*");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Function<RegistryRepository, URI> endpointResolver;
    private final Duration requestTimeout;
    private final RegistryCredentialProvider credentialProvider;

    public OciRegistryClient(Duration connectTimeout, Duration requestTimeout) {
        this(connectTimeout, requestTimeout, RegistryCredentialProvider.anonymous());
    }

    public OciRegistryClient(
            Duration connectTimeout,
            Duration requestTimeout,
            RegistryCredentialProvider credentialProvider) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                new ObjectMapper(),
                repository -> URI.create("https://" + repository.host() + "/"),
                requestTimeout,
                credentialProvider);
    }

    OciRegistryClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Function<RegistryRepository, URI> endpointResolver,
            Duration requestTimeout) {
        this(
                httpClient,
                objectMapper,
                endpointResolver,
                requestTimeout,
                RegistryCredentialProvider.anonymous());
    }

    OciRegistryClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Function<RegistryRepository, URI> endpointResolver,
            Duration requestTimeout,
            RegistryCredentialProvider credentialProvider) {
        this.httpClient = Objects.requireNonNull(httpClient, "http client is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper is required");
        this.endpointResolver = Objects.requireNonNull(
                endpointResolver,
                "endpoint resolver is required");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "request timeout is required");
        this.credentialProvider = Objects.requireNonNull(
                credentialProvider,
                "credential provider is required");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("request timeout must be positive");
        }
    }

    @Override
    public List<String> listTags(RegistryRepository repository) throws RegistryException {
        Objects.requireNonNull(repository, "registry repository is required");
        URI baseUri = requireHttpBase(endpointResolver.apply(repository));
        URI page = baseUri.resolve(
                "v2/" + repository.path() + "/tags/list?n=" + PAGE_SIZE);
        String authorization = null;
        List<String> tags = new ArrayList<>();

        for (int pageNumber = 0; page != null && pageNumber < MAX_PAGES; pageNumber++) {
            HttpResponse<InputStream> response = sendTagRequest(page, authorization);
            if (response.statusCode() == 401 && authorization == null) {
                String challengeHeader = response.headers()
                        .firstValue("WWW-Authenticate")
                        .orElse(null);
                close(response.body());
                authorization = authorize(challengeHeader, baseUri, repository);
                response = sendTagRequest(page, authorization);
            }

            String linkHeader = response.headers().firstValue("Link").orElse(null);
            JsonNode document = readJson(response, "list OCI registry tags");
            appendTags(document, repository, tags);
            page = nextPage(linkHeader, baseUri, repository);
        }
        if (page != null) {
            throw new RegistryException("OCI registry tag response exceeded the page limit");
        }
        return List.copyOf(tags);
    }

    private String authorize(
            String challengeHeader,
            URI baseUri,
            RegistryRepository repository) throws RegistryException {
        if (hasScheme(challengeHeader, "Bearer")) {
            OciBearerChallenge challenge = OciBearerChallenge.parse(challengeHeader, baseUri);
            return "Bearer " + requestToken(challenge, repository);
        }
        if (hasScheme(challengeHeader, "Basic")) {
            RegistryCredentials credentials = credentialProvider.credentialsFor(repository)
                    .orElseThrow(() -> new RegistryException(
                            "OCI registry requires credentials for the configured repository"));
            return basicAuthorization(credentials);
        }
        throw new RegistryException("OCI registry returned an invalid authentication challenge");
    }

    private String requestToken(
            OciBearerChallenge challenge,
            RegistryRepository repository) throws RegistryException {
        HttpRequest.Builder request = HttpRequest.newBuilder(challenge.tokenUri())
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET();
        Optional<RegistryCredentials> credentials = credentialProvider.credentialsFor(repository);
        credentials.ifPresent(value -> request.header("Authorization", basicAuthorization(value)));
        JsonNode document = readJson(send(request.build(), "request OCI registry token"),
                "request OCI registry token");
        if (document == null || !document.isObject()) {
            throw new RegistryException("OCI registry returned an invalid authentication token");
        }
        String token = text(document.get("token"));
        String accessToken = text(document.get("access_token"));
        if (token != null && accessToken != null && !token.equals(accessToken)) {
            throw new RegistryException("OCI registry returned conflicting authentication tokens");
        }
        String selected = token == null ? accessToken : token;
        if (selected == null
                || selected.length() > MAX_TOKEN_LENGTH
                || !BEARER_TOKEN.matcher(selected).matches()) {
            throw new RegistryException("OCI registry returned an invalid authentication token");
        }
        return selected;
    }

    private HttpResponse<InputStream> sendTagRequest(URI page, String authorization)
            throws RegistryException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(page)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return send(builder.build(), "list OCI registry tags");
    }

    private HttpResponse<InputStream> send(HttpRequest request, String operation)
            throws RegistryException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RegistryException(operation + " was interrupted", exception);
        } catch (IOException exception) {
            throw new RegistryException(operation + " failed", exception);
        }
    }

    private JsonNode readJson(HttpResponse<InputStream> response, String operation)
            throws RegistryException {
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
        } catch (IOException exception) {
            throw new RegistryException(operation + " response could not be read", exception);
        }
    }

    private static void appendTags(
            JsonNode document,
            RegistryRepository repository,
            List<String> tags) throws RegistryException {
        if (document == null || !document.isObject()) {
            throw new RegistryException("OCI registry returned an invalid tag response");
        }
        JsonNode name = document.get("name");
        JsonNode foundTags = document.get("tags");
        if (name == null || !name.isTextual() || !name.textValue().equals(repository.path())) {
            throw new RegistryException("OCI registry returned an invalid repository name");
        }
        if (foundTags == null || foundTags.isNull()) {
            return;
        }
        if (!foundTags.isArray()) {
            throw new RegistryException("OCI registry returned an invalid tag response");
        }
        if (foundTags.size() > MAX_TAGS_PER_PAGE) {
            throw new RegistryException("OCI registry returned too many tags in one page");
        }
        for (JsonNode tag : foundTags) {
            if (!tag.isTextual() || !TAG.matcher(tag.textValue()).matches()) {
                throw new RegistryException("OCI registry returned an invalid tag response");
            }
            tags.add(tag.textValue());
        }
    }

    private static URI nextPage(
            String linkHeader,
            URI baseUri,
            RegistryRepository repository) throws RegistryException {
        if (linkHeader == null) {
            return null;
        }
        if (linkHeader.length() > MAX_LINK_HEADER_LENGTH) {
            throw new RegistryException("OCI registry returned an invalid pagination link");
        }
        Matcher matcher = NEXT_LINK.matcher(linkHeader);
        if (!matcher.find()) {
            throw new RegistryException("OCI registry returned an invalid pagination link");
        }
        URI candidate = baseUri.resolve(matcher.group(1));
        String expectedPath = "/v2/" + repository.path() + "/tags/list";
        if (!candidate.getScheme().equalsIgnoreCase(baseUri.getScheme())
                || !candidate.getAuthority().equalsIgnoreCase(baseUri.getAuthority())
                || !expectedPath.equals(candidate.getPath())
                || candidate.getRawQuery() == null
                || candidate.getFragment() != null) {
            throw new RegistryException("OCI registry returned an unsafe pagination link");
        }
        return candidate;
    }

    private static URI requireHttpBase(URI value) {
        Objects.requireNonNull(value, "registry base URI is required");
        if (value.getHost() == null
                || value.getUserInfo() != null
                || value.getFragment() != null
                || !(value.getScheme().equalsIgnoreCase("https")
                || value.getScheme().equalsIgnoreCase("http"))) {
            throw new IllegalArgumentException("registry base URI must be an absolute HTTP URL");
        }
        return value;
    }

    private static String text(JsonNode value) {
        return value != null && value.isTextual() && !value.textValue().isBlank()
                ? value.textValue()
                : null;
    }

    private static boolean hasScheme(String challenge, String scheme) {
        return challenge != null
                && challenge.length() <= MAX_AUTH_CHALLENGE_LENGTH
                && challenge.length() > scheme.length()
                && challenge.regionMatches(true, 0, scheme, 0, scheme.length())
                && Character.isWhitespace(challenge.charAt(scheme.length()));
    }

    private static String basicAuthorization(RegistryCredentials credentials) {
        String value = credentials.identifier() + ":" + credentials.secret();
        return "Basic " + Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static void close(InputStream input) throws RegistryException {
        try {
            input.close();
        } catch (IOException exception) {
            throw new RegistryException("could not close OCI registry response", exception);
        }
    }
}
