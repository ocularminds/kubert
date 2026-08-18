package io.github.ocularminds.blazra.service;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import io.github.ocularminds.blazra.model.DeploymentSnapshot;
import io.github.ocularminds.blazra.model.DeploymentTarget;
import io.github.ocularminds.blazra.registry.RegistryException;
import io.github.ocularminds.blazra.repository.DeploymentRepository;
import io.github.ocularminds.blazra.repository.DeploymentRepositoryException;

public final class DeploymentMonitor implements Monitor {
    private static final Logger LOGGER = Logger.getLogger(DeploymentMonitor.class.getName());

    private final DeploymentRepository deploymentRepository;
    private final ImageResolver imageResolver;
    private final DeploymentTarget target;
    private final boolean dryRun;

    public DeploymentMonitor(
            DeploymentRepository deploymentRepository,
            ImageResolver imageResolver,
            DeploymentTarget target,
            boolean dryRun) {
        this.deploymentRepository = Objects.requireNonNull(
                deploymentRepository,
                "deployment repository is required");
        this.imageResolver = Objects.requireNonNull(imageResolver, "image resolver is required");
        this.target = Objects.requireNonNull(target, "target is required");
        this.dryRun = dryRun;
    }

    @Override
    public UpdateResult check() throws MonitoringException {
        try {
            Optional<DeploymentSnapshot> found = deploymentRepository.find(target);
            if (found.isEmpty()) {
                LOGGER.warning(() -> "Deployment " + target.deployment() + " was not found");
                return UpdateResult.DEPLOYMENT_NOT_FOUND;
            }
            DeploymentSnapshot deployment = found.get();
            Optional<String> replacement = imageResolver.latestImage(deployment.image());
            if (replacement.isEmpty()) {
                LOGGER.info(() -> "No newer compatible image tag for " + target.deployment());
                return UpdateResult.NO_UPDATE;
            }
            if (dryRun) {
                LOGGER.info(() -> "Dry run: would update " + target.deployment()
                        + " container " + target.container() + " to " + replacement.get());
                return UpdateResult.DRY_RUN;
            }
            deploymentRepository.updateImage(deployment, replacement.get());
            LOGGER.info(() -> "Updated " + target.deployment()
                    + " container " + target.container() + " to " + replacement.get());
            return UpdateResult.UPDATED;
        } catch (DeploymentRepositoryException | RegistryException exception) {
            throw new MonitoringException("image update check failed", exception);
        }
    }
}
