package osfx.kubert.config;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import osfx.kubert.model.DeploymentTarget;
import osfx.kubert.model.RegistryCredentials;
import osfx.kubert.service.UpdatePolicy;

public final class KubertConfig {
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration MINIMUM_POLL_INTERVAL = Duration.ofSeconds(10);
    private static final Duration MAXIMUM_POLL_INTERVAL = Duration.ofHours(24);

    private final DeploymentTarget target;
    private final Duration pollInterval;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final boolean dryRun;
    private final UpdatePolicy updatePolicy;
    private final Optional<RegistryCredentials> registryCredentials;

    public KubertConfig(
            DeploymentTarget target,
            Duration pollInterval,
            Duration connectTimeout,
            Duration requestTimeout,
            boolean dryRun,
            UpdatePolicy updatePolicy,
            Optional<RegistryCredentials> registryCredentials) {
        this.target = Objects.requireNonNull(target, "target is required");
        this.pollInterval = requireDuration(
                pollInterval,
                "poll interval",
                MINIMUM_POLL_INTERVAL,
                MAXIMUM_POLL_INTERVAL);
        this.connectTimeout = requirePositive(connectTimeout, "connect timeout");
        this.requestTimeout = requirePositive(requestTimeout, "request timeout");
        this.dryRun = dryRun;
        this.updatePolicy = Objects.requireNonNull(updatePolicy, "update policy is required");
        this.registryCredentials = Objects.requireNonNull(
                registryCredentials,
                "registry credentials are required");
    }

    public static KubertConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static KubertConfig fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment is required");
        EnvironmentVariables variables = new EnvironmentVariables(environment);
        DeploymentTarget target = new DeploymentTarget(
                variables.value("NAMESPACE").orElse("default"),
                required(variables, "DEPLOYMENT"),
                required(variables, "CONTAINER"));
        Duration pollInterval = duration(
                variables,
                "POLL_INTERVAL",
                DEFAULT_POLL_INTERVAL);
        Duration connectTimeout = duration(
                variables,
                "CONNECT_TIMEOUT",
                DEFAULT_CONNECT_TIMEOUT);
        Duration requestTimeout = duration(
                variables,
                "REQUEST_TIMEOUT",
                DEFAULT_REQUEST_TIMEOUT);
        boolean dryRun = booleanValue(variables, "DRY_RUN", false);
        UpdatePolicy updatePolicy = updatePolicy(variables.value("UPDATE_POLICY").orElse(null));
        Optional<RegistryCredentials> credentials = credentials(environment);
        return new KubertConfig(
                target,
                pollInterval,
                connectTimeout,
                requestTimeout,
                dryRun,
                updatePolicy,
                credentials);
    }

    public DeploymentTarget target() {
        return target;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public UpdatePolicy updatePolicy() {
        return updatePolicy;
    }

    public Optional<RegistryCredentials> registryCredentials() {
        return registryCredentials;
    }

    private static Optional<RegistryCredentials> credentials(Map<String, String> environment) {
        String identifier = environment.get("DOCKER_HUB_USERNAME");
        String secret = environment.get("DOCKER_HUB_TOKEN");
        if (isBlank(identifier) && isBlank(secret)) {
            return Optional.empty();
        }
        if (isBlank(identifier) || isBlank(secret)) {
            throw new IllegalArgumentException(
                    "DOCKER_HUB_USERNAME and DOCKER_HUB_TOKEN must be configured together");
        }
        return Optional.of(new RegistryCredentials(identifier, secret));
    }

    private static String required(EnvironmentVariables variables, String suffix) {
        return variables.value(suffix).orElseThrow(
                () -> new IllegalArgumentException(variables.currentName(suffix) + " is required"));
    }

    private static Duration duration(
            EnvironmentVariables variables,
            String suffix,
            Duration defaultValue) {
        Optional<String> value = variables.value(suffix);
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Duration.parse(value.orElseThrow());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    variables.currentName(suffix) + " must be an ISO-8601 duration",
                    exception);
        }
    }

    private static boolean booleanValue(
            EnvironmentVariables variables,
            String suffix,
            boolean defaultValue) {
        Optional<String> configuredValue = variables.value(suffix);
        if (configuredValue.isEmpty()) {
            return defaultValue;
        }
        String value = configuredValue.orElseThrow();
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(
                    variables.currentName(suffix) + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static UpdatePolicy updatePolicy(String value) {
        if (isBlank(value)) {
            return UpdatePolicy.PATCH;
        }
        try {
            return UpdatePolicy.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "BLAZRA_UPDATE_POLICY must be PATCH, MINOR, or MAJOR",
                    exception);
        }
    }

    private static Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static Duration requireDuration(
            Duration value,
            String field,
            Duration minimum,
            Duration maximum) {
        requirePositive(value, field);
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    field + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
