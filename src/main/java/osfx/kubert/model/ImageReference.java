package osfx.kubert.model;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record ImageReference(String sourceRepository, String dockerHubRepository, String tag) {
    private static final Pattern COMPONENT = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");
    private static final Set<String> DOCKER_HUB_HOSTS = Set.of(
            "docker.io",
            "index.docker.io",
            "registry-1.docker.io");

    public static Optional<ImageReference> parse(String value) {
        if (value == null || value.isBlank() || value.indexOf('@') >= 0) {
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
        int offset = 0;
        if (path.length > 1 && isRegistryHost(path[0])) {
            if (!DOCKER_HUB_HOSTS.contains(path[0].toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            offset = 1;
        }

        int componentCount = path.length - offset;
        String namespace;
        String repository;
        if (componentCount == 1) {
            namespace = "library";
            repository = path[offset];
        } else if (componentCount == 2) {
            namespace = path[offset];
            repository = path[offset + 1];
        } else {
            return Optional.empty();
        }
        if (!COMPONENT.matcher(namespace).matches() || !COMPONENT.matcher(repository).matches()) {
            return Optional.empty();
        }
        return Optional.of(new ImageReference(
                sourceRepository,
                namespace + "/" + repository,
                tag));
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
}
