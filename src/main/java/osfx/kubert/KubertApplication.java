package osfx.kubert;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.logging.Level;
import java.util.logging.Logger;
import osfx.kubert.config.KubertConfig;
import osfx.kubert.registry.DockerHubRegistryClient;
import osfx.kubert.repository.kubernetes.Fabric8DeploymentRepository;
import osfx.kubert.runtime.PollingRunner;
import osfx.kubert.service.DeploymentMonitor;
import osfx.kubert.service.DockerHubImageResolver;

public final class KubertApplication {
    private static final Logger LOGGER = Logger.getLogger(KubertApplication.class.getName());

    private KubertApplication() {
    }

    public static void main(String[] arguments) {
        try {
            run(KubertConfig.fromEnvironment());
        } catch (IllegalArgumentException exception) {
            LOGGER.log(Level.SEVERE, "Invalid configuration: " + exception.getMessage());
            System.exit(2);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.info("Kubert stopped");
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Kubert failed to start", exception);
            System.exit(1);
        }
    }

    static void run(KubertConfig config) throws InterruptedException {
        try (KubernetesClient kubernetesClient = new KubernetesClientBuilder().build()) {
            DockerHubRegistryClient registryClient = new DockerHubRegistryClient(
                    config.registryCredentials(),
                    config.connectTimeout(),
                    config.requestTimeout());
            DeploymentMonitor monitor = new DeploymentMonitor(
                    new Fabric8DeploymentRepository(kubernetesClient),
                    new DockerHubImageResolver(registryClient),
                    config.target(),
                    config.dryRun());
            try (PollingRunner runner = new PollingRunner(monitor, config.pollInterval())) {
                Thread shutdownHook = new Thread(runner::close, "kubert-shutdown");
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
