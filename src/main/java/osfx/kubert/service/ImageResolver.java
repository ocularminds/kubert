package osfx.kubert.service;

import java.util.Optional;
import osfx.kubert.registry.RegistryException;

public interface ImageResolver {
    Optional<String> latestImage(String currentImage) throws RegistryException;
}
