package osfx.kubert.service;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import osfx.kubert.model.ImageReference;
import osfx.kubert.model.VersionTag;
import osfx.kubert.registry.RegistryClient;
import osfx.kubert.registry.RegistryException;

public final class DockerHubImageResolver implements ImageResolver {
    private final RegistryClient registryClient;

    public DockerHubImageResolver(RegistryClient registryClient) {
        this.registryClient = Objects.requireNonNull(registryClient, "registry client is required");
    }

    @Override
    public Optional<String> latestImage(String currentImage) throws RegistryException {
        Optional<ImageReference> reference = ImageReference.parse(currentImage);
        if (reference.isEmpty()) {
            return Optional.empty();
        }
        Optional<VersionTag> currentTag = VersionTag.parse(reference.get().tag());
        if (currentTag.isEmpty()) {
            return Optional.empty();
        }
        return registryClient.listTags(reference.get().dockerHubRepository()).stream()
                .map(VersionTag::parse)
                .flatMap(Optional::stream)
                .filter(candidate -> candidate.compareTo(currentTag.get()) > 0)
                .max(Comparator.naturalOrder())
                .map(VersionTag::value)
                .map(reference.get()::withTag);
    }
}
