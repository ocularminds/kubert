package osfx.kubert.model;

import java.util.Objects;

public record DeploymentSnapshot(
        DeploymentTarget target,
        String image,
        String resourceVersion) {

    public DeploymentSnapshot {
        Objects.requireNonNull(target, "target is required");
        image = requireText(image, "image");
        resourceVersion = requireText(resourceVersion, "resourceVersion");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
