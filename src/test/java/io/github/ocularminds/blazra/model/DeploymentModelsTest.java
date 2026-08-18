package io.github.ocularminds.blazra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DeploymentModelsTest {
    @Test
    void validatesKubernetesTargetNames() {
        DeploymentTarget target = new DeploymentTarget("apps", "api.v2", "web-api");
        assertEquals("apps", target.namespace());

        for (String invalid : new String[]{"", "Uppercase", "-leading", "trailing-", "contains_underscore"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new DeploymentTarget(invalid, "app", "container"));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeploymentTarget("default", "app", "container.name"));
    }

    @Test
    void validatesSnapshotsAndRedactsSecrets() {
        DeploymentTarget target = new DeploymentTarget("default", "app", "web");
        DeploymentSnapshot snapshot = new DeploymentSnapshot(target, "team/app:1.0", "42");
        assertEquals("team/app:1.0", snapshot.image());
        assertThrows(IllegalArgumentException.class, () -> new DeploymentSnapshot(target, "", "42"));
        assertThrows(IllegalArgumentException.class, () -> new DeploymentSnapshot(target, "app:1.0", ""));

        RegistryCredentials credentials = new RegistryCredentials("robot", "very-secret");
        assertFalse(credentials.toString().contains("very-secret"));
        assertThrows(IllegalArgumentException.class, () -> new RegistryCredentials("robot", ""));
    }
}
