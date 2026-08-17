package osfx.kubert.registry;

import java.util.List;

public interface RegistryClient {
    List<String> listTags(String repository) throws RegistryException;
}
