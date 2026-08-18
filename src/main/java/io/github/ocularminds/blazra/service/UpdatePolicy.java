package io.github.ocularminds.blazra.service;

import java.util.Objects;
import io.github.ocularminds.blazra.model.VersionTag;

public enum UpdatePolicy {
    PATCH {
        @Override
        public boolean allows(VersionTag current, VersionTag candidate) {
            requireTags(current, candidate);
            return sameComponent(current, candidate, 0)
                    && sameComponent(current, candidate, 1);
        }
    },
    MINOR {
        @Override
        public boolean allows(VersionTag current, VersionTag candidate) {
            requireTags(current, candidate);
            return sameComponent(current, candidate, 0);
        }
    },
    MAJOR {
        @Override
        public boolean allows(VersionTag current, VersionTag candidate) {
            requireTags(current, candidate);
            return true;
        }
    };

    public abstract boolean allows(VersionTag current, VersionTag candidate);

    private static void requireTags(VersionTag current, VersionTag candidate) {
        Objects.requireNonNull(current, "current tag is required");
        Objects.requireNonNull(candidate, "candidate tag is required");
    }

    private static boolean sameComponent(VersionTag left, VersionTag right, int index) {
        return left.components().get(index).equals(right.components().get(index));
    }
}
