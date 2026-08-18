package io.github.ocularminds.blazra.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import io.github.ocularminds.blazra.model.VersionTag;

class UpdatePolicyTest {
    private final VersionTag current = VersionTag.parse("1.2.3").orElseThrow();

    @Test
    void patchPolicyKeepsTheMajorAndMinorTrack() {
        assertTrue(UpdatePolicy.PATCH.allows(current, tag("1.2.9")));
        assertFalse(UpdatePolicy.PATCH.allows(current, tag("1.3.0")));
        assertFalse(UpdatePolicy.PATCH.allows(current, tag("2.0.0")));
    }

    @Test
    void minorPolicyKeepsTheMajorTrack() {
        assertTrue(UpdatePolicy.MINOR.allows(current, tag("1.9.0")));
        assertFalse(UpdatePolicy.MINOR.allows(current, tag("2.0.0")));
    }

    @Test
    void majorPolicyAllowsEveryNumericTrack() {
        assertTrue(UpdatePolicy.MAJOR.allows(current, tag("2.0.0")));
    }

    @Test
    void policiesRejectMissingTags() {
        assertThrows(NullPointerException.class, () -> UpdatePolicy.PATCH.allows(null, current));
        assertThrows(NullPointerException.class, () -> UpdatePolicy.MINOR.allows(current, null));
        assertThrows(NullPointerException.class, () -> UpdatePolicy.MAJOR.allows(null, current));
    }

    private static VersionTag tag(String value) {
        return VersionTag.parse(value).orElseThrow();
    }
}
