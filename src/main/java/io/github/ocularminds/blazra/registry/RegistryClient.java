package io.github.ocularminds.blazra.registry;

import java.util.List;

public interface RegistryClient {
    List<String> listTags(String repository) throws RegistryException;
}
