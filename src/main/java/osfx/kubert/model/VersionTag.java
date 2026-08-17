package osfx.kubert.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record VersionTag(String value, List<Integer> components) implements Comparable<VersionTag> {
    public VersionTag {
        components = List.copyOf(components);
    }

    public static Optional<VersionTag> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String numeric = value.startsWith("v") ? value.substring(1) : value;
        String[] parts = numeric.split("\\.", -1);
        if (parts.length < 2 || parts.length > 4) {
            return Optional.empty();
        }
        List<Integer> components = new ArrayList<>(parts.length);
        try {
            for (String part : parts) {
                if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                    return Optional.empty();
                }
                components.add(Integer.parseInt(part));
            }
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        return Optional.of(new VersionTag(value, components));
    }

    @Override
    public int compareTo(VersionTag other) {
        int length = Math.max(components.size(), other.components.size());
        for (int index = 0; index < length; index++) {
            int left = index < components.size() ? components.get(index) : 0;
            int right = index < other.components.size() ? other.components.get(index) : 0;
            int comparison = Integer.compare(left, right);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }
}
