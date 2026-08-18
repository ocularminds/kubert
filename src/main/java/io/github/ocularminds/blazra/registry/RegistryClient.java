package io.github.ocularminds.blazra.registry;

import java.util.List;
import io.github.ocularminds.blazra.model.RegistryRepository;

public interface RegistryClient {
    List<String> listTags(RegistryRepository repository) throws RegistryException;
}
