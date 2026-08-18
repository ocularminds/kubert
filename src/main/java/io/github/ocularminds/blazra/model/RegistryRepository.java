package io.github.ocularminds.blazra.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record RegistryRepository(String host, String path) {
    public static final String DOCKER_HUB_HOST = "registry-1.docker.io";

    private static final int MAX_HOST_LENGTH = 259;
    private static final int MAX_PATH_LENGTH = 255;
    private static final Pattern HOST_NAME = Pattern.compile(
            "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
                    + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*");
    private static final Pattern PATH_COMPONENT = Pattern.compile(
            "[a-z0-9]+(?:(?:[._]|__|[-]+)[a-z0-9]+)*");
    private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");
    private static final Pattern NUMERIC_ADDRESS = Pattern.compile("[0-9]+(?:\\.[0-9]+)+");

    public RegistryRepository {
        Objects.requireNonNull(host, "registry host is required");
        Objects.requireNonNull(path, "registry repository path is required");
        if (host.length() > MAX_HOST_LENGTH
                || !host.equals(host.toLowerCase(Locale.ROOT))
                || !validHost(host)) {
            throw new IllegalArgumentException("registry host is invalid");
        }
        if (path.length() > MAX_PATH_LENGTH || !validPath(path)) {
            throw new IllegalArgumentException("registry repository path is invalid");
        }
        if (host.equals(DOCKER_HUB_HOST) && path.split("/", -1).length != 2) {
            throw new IllegalArgumentException(
                    "Docker Hub repositories require a namespace and name");
        }
    }

    public static RegistryRepository dockerHub(String path) {
        return new RegistryRepository(DOCKER_HUB_HOST, path);
    }

    public boolean isDockerHub() {
        return host.equals(DOCKER_HUB_HOST);
    }

    private static boolean validHost(String value) {
        int separator = value.lastIndexOf(':');
        String hostname = value;
        if (separator >= 0) {
            if (separator != value.indexOf(':') || separator == value.length() - 1) {
                return false;
            }
            hostname = value.substring(0, separator);
            String configuredPort = value.substring(separator + 1);
            if (!PORT.matcher(configuredPort).matches()) {
                return false;
            }
            try {
                int port = Integer.parseInt(configuredPort);
                if (port < 1 || port > 65535) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return HOST_NAME.matcher(hostname).matches()
                && hostname.indexOf('.') >= 0
                && !NUMERIC_ADDRESS.matcher(hostname).matches()
                && !hostname.endsWith(".local")
                && !hostname.endsWith(".localhost")
                && !hostname.endsWith(".internal");
    }

    private static boolean validPath(String value) {
        String[] components = value.split("/", -1);
        if (components.length == 0) {
            return false;
        }
        for (String component : components) {
            if (!PATH_COMPONENT.matcher(component).matches()) {
                return false;
            }
        }
        return true;
    }
}
