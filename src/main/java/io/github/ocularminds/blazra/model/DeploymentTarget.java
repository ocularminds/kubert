package io.github.ocularminds.blazra.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record DeploymentTarget(String namespace, String deployment, String container) {
    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9](?:[-a-z0-9]*[a-z0-9])?");
    private static final Pattern DNS_SUBDOMAIN = Pattern.compile(
            "[a-z0-9](?:[-a-z0-9.]*[a-z0-9])?");

    public DeploymentTarget {
        namespace = requireName(namespace, "namespace", 63, DNS_LABEL);
        deployment = requireName(deployment, "deployment", 253, DNS_SUBDOMAIN);
        container = requireName(container, "container", 63, DNS_LABEL);
    }

    private static String requireName(
            String value,
            String field,
            int maximumLength,
            Pattern format) {
        Objects.requireNonNull(value, field + " is required");
        if (value.length() > maximumLength || !format.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a valid Kubernetes name");
        }
        return value;
    }
}
