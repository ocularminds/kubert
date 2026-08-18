package io.github.ocularminds.blazra.registry;

import java.util.List;
import java.util.Objects;
import io.github.ocularminds.blazra.model.RegistryRepository;

public final class RegistryClientRouter implements RegistryClient {
    private final RegistryClient dockerHubClient;
    private final RegistryClient ociClient;

    public RegistryClientRouter(RegistryClient dockerHubClient, RegistryClient ociClient) {
        this.dockerHubClient = Objects.requireNonNull(
                dockerHubClient,
                "Docker Hub client is required");
        this.ociClient = Objects.requireNonNull(ociClient, "OCI client is required");
    }

    @Override
    public List<String> listTags(RegistryRepository repository) throws RegistryException {
        Objects.requireNonNull(repository, "registry repository is required");
        return repository.isDockerHub()
                ? dockerHubClient.listTags(repository)
                : ociClient.listTags(repository);
    }
}
