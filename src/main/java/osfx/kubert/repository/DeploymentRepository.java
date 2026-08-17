package osfx.kubert.repository;

import java.util.Optional;
import osfx.kubert.model.DeploymentSnapshot;
import osfx.kubert.model.DeploymentTarget;

public interface DeploymentRepository {
    Optional<DeploymentSnapshot> find(DeploymentTarget target) throws DeploymentRepositoryException;

    void updateImage(DeploymentSnapshot expected, String newImage)
            throws DeploymentRepositoryException;
}
