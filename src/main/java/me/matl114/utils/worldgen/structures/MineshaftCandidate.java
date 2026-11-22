package me.matl114.utils.worldgen.structures;

import java.util.Objects;
import java.util.Set;
import me.matl114.utils.worldgen.loot.LootVersion;
import net.minecraft.util.Identifier;

/** A frequency-filtered mineshaft start chunk before biome validation and piece generation. */
public record MineshaftCandidate(
        Identifier structureSetId,
        Set<Identifier> possibleStructureIds,
        long chunkX,
        long chunkZ,
        LootVersion version,
        Confidence confidence) {
    public MineshaftCandidate {
        Objects.requireNonNull(structureSetId);
        possibleStructureIds = Set.copyOf(possibleStructureIds);
        Objects.requireNonNull(version);
        Objects.requireNonNull(confidence);
    }

    public enum Confidence {
        FREQUENCY_FILTERED_START_CHUNK_ONLY
    }
}
