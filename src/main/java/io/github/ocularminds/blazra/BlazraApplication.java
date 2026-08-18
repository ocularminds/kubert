package io.github.ocularminds.blazra;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.github.ocularminds.blazra.config.BlazraConfig;
import io.github.ocularminds.blazra.registry.DockerHubRegistryClient;
import io.github.ocularminds.blazra.repository.kubernetes.Fabric8DeploymentRepository;
import io.github.ocularminds.blazra.runtime.PollingRunner;
import io.github.ocularminds.blazra.service.DeploymentMonitor;
import io.github.ocularminds.blazra.service.DockerHubImageResolver;

public final class BlazraApplication {
    private static final Logger LOGGER = Logger.getLogger(BlazraApplication.class.getName());

    private BlazraApplication() {
    }

    public static void main(String[] arguments) {
        try {
            run(BlazraConfig.fromEnvironment());
        } catch (IllegalArgumentException exception) {
            LOGGER.log(Level.SEVERE, "Invalid configuration: " + exception.getMessage());
            System.exit(2);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.info("Blazra stopped");
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Blazra failed to start", exception);
            System.exit(1);
        }
    }

    static void run(BlazraConfig config) throws InterruptedException {
        try (KubernetesClient kubernetesClient = new KubernetesClientBuilder().build()) {
            DockerHubRegistryClient registryClient = new DockerHubRegistryClient(
                    config.registryCredentials(),
                    config.connectTimeout(),
                    config.requestTimeout());
            DeploymentMonitor monitor = new DeploymentMonitor(
                    new Fabric8DeploymentRepository(kubernetesClient),
                    new DockerHubImageResolver(registryClient, config.updatePolicy()),
                    config.target(),
                    config.dryRun());
            try (PollingRunner runner = new PollingRunner(monitor, config.pollInterval())) {
                Thread shutdownHook = new Thread(runner::close, "blazra-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdownHook);
                try {
                    runner.start();
                    LOGGER.info(() -> "Monitoring deployment "
                            + config.target().namespace() + "/" + config.target().deployment()
                            + " container " + config.target().container());
                    runner.awaitTermination();
                } finally {
                    removeShutdownHook(shutdownHook);
                }
            }
        }
    }

    private static void removeShutdownHook(Thread shutdownHook) {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // The JVM is already shutting down and executing the hook.
        }
    }
}
