package io.github.ocularminds.blazra.model;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record ImageReference(
        String sourceRepository,
        RegistryRepository registryRepository,
        String tag) {
    private static final Pattern COMPONENT = Pattern.compile(
            "[a-z0-9]+(?:(?:[._]|__|[-]+)[a-z0-9]+)*");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");
    private static final Set<String> DOCKER_HUB_HOSTS = Set.of(
            "docker.io",
            "index.docker.io",
            "registry-1.docker.io");

    public ImageReference {
        if (sourceRepository == null
                || sourceRepository.isBlank()
                || sourceRepository.length() > 512
                || !sourceRepository.equals(sourceRepository.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("source repository is invalid");
        }
        if (registryRepository == null) {
            throw new IllegalArgumentException("registry repository is required");
        }
        if (tag == null || !TAG.matcher(tag).matches()) {
            throw new IllegalArgumentException("image tag is invalid");
        }
    }

    public static Optional<ImageReference> parse(String value) {
        if (value == null || value.isBlank() || value.length() > 512 || value.indexOf('@') >= 0) {
            return Optional.empty();
        }
        int lastSlash = value.lastIndexOf('/');
        int tagSeparator = value.lastIndexOf(':');
        if (tagSeparator <= lastSlash || tagSeparator == value.length() - 1) {
            return Optional.empty();
        }

        String sourceRepository = value.substring(0, tagSeparator);
        String tag = value.substring(tagSeparator + 1);
        if (!sourceRepository.equals(sourceRepository.toLowerCase(Locale.ROOT))
                || !TAG.matcher(tag).matches()) {
            return Optional.empty();
        }

        String[] path = sourceRepository.split("/", -1);
        RegistryRepository registryRepository;
        if (path.length > 1 && isRegistryHost(path[0])) {
            String host = path[0];
            String repositoryPath = joinPath(path, 1);
            if (DOCKER_HUB_HOSTS.contains(host)) {
                repositoryPath = dockerHubPath(path, 1);
                if (repositoryPath == null) {
                    return Optional.empty();
                }
                registryRepository = RegistryRepository.dockerHub(repositoryPath);
            } else {
                try {
                    registryRepository = new RegistryRepository(host, repositoryPath);
                } catch (IllegalArgumentException exception) {
                    return Optional.empty();
                }
            }
        } else {
            String repositoryPath = dockerHubPath(path, 0);
            if (repositoryPath == null) {
                return Optional.empty();
            }
            registryRepository = RegistryRepository.dockerHub(repositoryPath);
        }
        return Optional.of(new ImageReference(sourceRepository, registryRepository, tag));
    }

    public String withTag(String replacement) {
        if (replacement == null || !TAG.matcher(replacement).matches()) {
            throw new IllegalArgumentException("replacement tag is invalid");
        }
        return sourceRepository + ":" + replacement;
    }

    private static boolean isRegistryHost(String value) {
        return value.contains(".") || value.contains(":") || value.equals("localhost");
    }

    private static String dockerHubPath(String[] path, int offset) {
        int componentCount = path.length - offset;
        if (componentCount == 1 && COMPONENT.matcher(path[offset]).matches()) {
            return "library/" + path[offset];
        }
        if (componentCount == 2
                && COMPONENT.matcher(path[offset]).matches()
                && COMPONENT.matcher(path[offset + 1]).matches()) {
            return path[offset] + "/" + path[offset + 1];
        }
        return null;
    }

    private static String joinPath(String[] components, int offset) {
        StringBuilder joined = new StringBuilder();
        for (int index = offset; index < components.length; index++) {
            if (index > offset) {
                joined.append('/');
            }
            joined.append(components[index]);
        }
        return joined.toString();
    }
}
