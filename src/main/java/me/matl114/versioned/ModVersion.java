package me.matl114.versioned;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/** A dot-separated mod version; missing trailing components compare as zero. */
public final class ModVersion implements Comparable<ModVersion> {
    private final List<Integer> components;

    public ModVersion(int... components) {
        Objects.requireNonNull(components, "components");
        if (components.length == 0) {
            throw new IllegalArgumentException("A mod version must have at least one component.");
        }
        List<Integer> values = new ArrayList<>(components.length);
        for (int component : components) {
            if (component < 0) {
                throw new IllegalArgumentException("Mod version components cannot be negative.");
            }
            values.add(component);
        }
        this.components = List.copyOf(values);
    }

    public static ModVersion parse(String version) {
        Objects.requireNonNull(version, "version");
        String[] parts = version.split("\\.", -1);
        int[] components = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isEmpty() || !parts[i].matches("[0-9]+")) {
                    throw new IllegalArgumentException("Invalid mod version: " + version);
                }
                components[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid mod version: " + version, exception);
        }
        return new ModVersion(components);
    }

    /** Parses the trailing dot-separated numeric version from a string. */
    public static ModVersion parseTrailing(String version) {
        Objects.requireNonNull(version, "version");
        int start = version.length();
        while (start > 0) {
            char character = version.charAt(start - 1);
            if ((character >= '0' && character <= '9') || character == '.') {
                start--;
            } else {
                break;
            }
        }
        if (start == version.length()) {
            throw new IllegalArgumentException("No trailing mod version found: " + version);
        }
        return parse(version.substring(start));
    }

    public List<Integer> components() {
        return components;
    }

    @Override
    public int compareTo(ModVersion other) {
        Objects.requireNonNull(other, "other");
        int length = Math.max(components.size(), other.components.size());
        for (int i = 0; i < length; i++) {
            int left = i < components.size() ? components.get(i) : 0;
            int right = i < other.components.size() ? other.components.get(i) : 0;
            int comparison = Integer.compare(left, right);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    /** Returns whether this version is equal to or newer than {@code other}. */
    public boolean higherOrEqual(ModVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModVersion version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        int length = components.size();
        while (length > 1 && components.get(length - 1) == 0) {
            length--;
        }
        return components.subList(0, length).hashCode();
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(".");
        components.forEach(component -> joiner.add(Integer.toString(component)));
        return joiner.toString();
    }
}
