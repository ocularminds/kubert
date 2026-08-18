package io.github.ocularminds.blazra.service;

import java.util.Optional;
import io.github.ocularminds.blazra.registry.RegistryException;

public interface ImageResolver {
    Optional<String> latestImage(String currentImage) throws RegistryException;
}
