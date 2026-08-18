package io.github.ocularminds.blazra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionTagTest {
    @Test
    void comparesNumericVersionComponents() {
        VersionTag oneTen = VersionTag.parse("1.10").orElseThrow();
        VersionTag oneNine = VersionTag.parse("1.9").orElseThrow();
        VersionTag prefixed = VersionTag.parse("v2.0.0").orElseThrow();
        VersionTag unprefixed = VersionTag.parse("2.0").orElseThrow();

        assertTrue(oneTen.compareTo(oneNine) > 0);
        assertEquals(0, prefixed.compareTo(unprefixed));
        assertTrue(VersionTag.parse("03.33").orElseThrow().compareTo(oneTen) > 0);
    }

    @Test
    void rejectsNonNumericAndUnboundedVersions() {
        for (String candidate : new String[]{
                null,
                "",
                "1",
                "1.2.3.4.5",
                "1.2-rc1",
                "v",
                "1..2",
                "999999999999.1"
        }) {
            assertTrue(VersionTag.parse(candidate).isEmpty(), () -> "accepted " + candidate);
        }
    }
}
