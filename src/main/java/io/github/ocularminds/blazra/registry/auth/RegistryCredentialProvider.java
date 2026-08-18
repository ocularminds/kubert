package io.github.ocularminds.blazra.registry.auth;

import java.util.Objects;
import java.util.Optional;
import io.github.ocularminds.blazra.model.RegistryCredentials;
import io.github.ocularminds.blazra.model.RegistryRepository;
import io.github.ocularminds.blazra.registry.RegistryException;

@FunctionalInterface
public interface RegistryCredentialProvider {
    Optional<RegistryCredentials> credentialsFor(RegistryRepository repository)
            throws RegistryException;

    static RegistryCredentialProvider anonymous() {
        return repository -> {
            Objects.requireNonNull(repository, "registry repository is required");
            return Optional.empty();
        };
    }
}
