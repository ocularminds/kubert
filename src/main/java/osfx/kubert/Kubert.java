package osfx.kubert;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.util.Config;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class Kubert {

    private static final String NAMESPACE = "default";  // Set namespace as per your cluster
    private static final List<String> DEPLOYMENT_NAMES = Arrays.asList("osfx-data"); // List of your deployment names
    private final ApiClient client;
    private final AppsV1Api appsV1Api;
    private final ImageChecker checker;
    private static final Logger logger = Logger.getLogger(Kubert.class.getName());

    public Kubert() throws Exception {
        this.client = Config.defaultClient();
        this.appsV1Api = new AppsV1Api(client);
        this.checker = new ImageChecker();
    }

    public void checkAndUpdateImages() {
        DEPLOYMENT_NAMES.forEach(deploymentName -> {
            try {
                V1Deployment deployment = appsV1Api.readNamespacedDeployment(deploymentName, NAMESPACE, null);
                if (deployment != null) {
                    String currentImage = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
                    String latestImage = checker.getLatestImage(currentImage);

                    if (!currentImage.equals(latestImage)) {
                        System.out.printf("Updating deployment %s from %s to %s\n", deploymentName, currentImage, latestImage);
                        updateDeploymentImage(deployment, latestImage);
                    } else {
                        System.out.printf("No new image for deployment %s. Current image is up-to-date: %s\n", deploymentName, currentImage);
                    }
                }
            } catch (ApiException e) {
                System.err.println("Failed to fetch deployment: " + e.getResponseBody());
            }
        });
    }

    private void updateDeploymentImage(V1Deployment deployment, String newImage) throws ApiException {
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(newImage);

        V1Deployment updatedDeployment = appsV1Api.replaceNamespacedDeployment(
                deployment.getMetadata().getName(),
                NAMESPACE,
                deployment,
                null,
                null,
                null
        );

        System.out.printf("Deployment %s updated to image %s\n", updatedDeployment.getMetadata().getName(), newImage);
    }

    public static void main(String[] args) throws Exception {
        Kubert controller = new Kubert();
        while (true) {
            controller.checkAndUpdateImages();
            Thread.sleep(5 * 60 * 1000);
        }
    }
}
