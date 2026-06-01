package com.viaversion.viaversion.api.protocol.version;

import java.util.Comparator;
import java.util.Set;

public class ProtocolVersion {
    public int getVersion() {
        return 0;
    }

    public int getSnapshotVersion() {
        return 0;
    }

    public int getFullSnapshotVersion() {
        return 0;
    }

    public int getOriginalVersion() {
        return 0;
    }

    public boolean isKnown() {
        return false;
    }

    public boolean isRange() {
        return false;
    }

    public Set<String> getIncludedVersions() {
        return Set.of();
    }

    public boolean isVersionWildcard() {
        return false;
    }

    public String getName() {
        return null;
    }

    public boolean isSnapshot() {
        return false;
    }

    public boolean equalTo(ProtocolVersion other) {
        return this.compareTo(other) == 0;
    }

    public boolean newerThan(ProtocolVersion other) {
        return this.compareTo(other) > 0;
    }

    public boolean newerThanOrEqualTo(ProtocolVersion other) {
        return this.compareTo(other) >= 0;
    }

    public boolean olderThan(ProtocolVersion other) {
        return this.compareTo(other) < 0;
    }

    public boolean olderThanOrEqualTo(ProtocolVersion other) {
        return this.compareTo(other) <= 0;
    }

    public boolean betweenInclusive(ProtocolVersion min, ProtocolVersion max) {
        return this.newerThanOrEqualTo(min) && this.olderThanOrEqualTo(max);
    }

    public boolean betweenExclusive(ProtocolVersion min, ProtocolVersion max) {
        return this.newerThan(min) && this.olderThan(max);
    }

    protected Comparator<ProtocolVersion> customComparator() {
        return null;
    }

    public boolean equals(Object o) {
        return o == this;
    }

    public int compareTo(ProtocolVersion other) {
        return 0;
    }
}
