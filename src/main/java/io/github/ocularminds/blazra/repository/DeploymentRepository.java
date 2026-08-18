package io.github.ocularminds.blazra.repository;

import java.util.Optional;
import io.github.ocularminds.blazra.model.DeploymentSnapshot;
import io.github.ocularminds.blazra.model.DeploymentTarget;

public interface DeploymentRepository {
    Optional<DeploymentSnapshot> find(DeploymentTarget target) throws DeploymentRepositoryException;

    void updateImage(DeploymentSnapshot expected, String newImage)
            throws DeploymentRepositoryException;
}
