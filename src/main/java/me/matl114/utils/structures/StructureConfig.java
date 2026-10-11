package me.matl114.utils.structures;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

import net.minecraft.util.Identifier;
import net.minecraft.world.gen.chunk.placement.SpreadType;

public final class StructureConfig {
    private static final Map<Identifier, StructureConfig> VANILLA = createVanillaConfigs();

    private final Identifier structureSetId;
    private final Set<Identifier> structureIds;
    private final int salt;
    private final int spacing;
    private final int separation;
    private final SpreadType spreadType;

    private StructureConfig(
            Identifier structureSetId,
            Set<Identifier> structureIds,
            int salt,
            int spacing,
            int separation,
            SpreadType spreadType) {
        if (spacing <= separation || separation < 0) {
            throw new IllegalArgumentException("Structure spacing must be greater than separation");
        }
        this.structureSetId = Objects.requireNonNull(structureSetId);
        this.structureIds = Set.copyOf(structureIds);
        this.salt = salt;
        this.spacing = spacing;
        this.separation = separation;
        this.spreadType = Objects.requireNonNull(spreadType);
    }

    public static StructureConfig of(
            Identifier structureSetId,
            Set<Identifier> structureIds,
            int salt,
            int spacing,
            int separation,
            SpreadType spreadType) {
        return new StructureConfig(
                structureSetId,
                structureIds,
                salt,
                spacing,
                separation,
                spreadType);
    }

    public static Map<Identifier, StructureConfig> vanillaConfigs() {
        return VANILLA;
    }

    private static Map<Identifier, StructureConfig> createVanillaConfigs() {
        Map<Identifier, StructureConfig> configs = new LinkedHashMap<>();
        configs.put(Identifier.ofVanilla("end_cities"), of(
                Identifier.ofVanilla("end_cities"),
                Set.of(Identifier.ofVanilla("end_city")),
                10387313,
                20,
                11,
                SpreadType.TRIANGULAR));
        configs.put(Identifier.ofVanilla("ancient_cities"), of(
                Identifier.ofVanilla("ancient_cities"),
                Set.of(Identifier.ofVanilla("ancient_city")),
                20083232,
                24,
                8,
                SpreadType.LINEAR));
        configs.put(Identifier.ofVanilla("desert_pyramids"), of(
                Identifier.ofVanilla("desert_pyramids"),
                Set.of(Identifier.ofVanilla("desert_pyramid")),
                14357617,
                32,
                8,
                SpreadType.LINEAR));
        configs.put(Identifier.ofVanilla("ruined_portals"), of(
                Identifier.ofVanilla("ruined_portals"),
                Set.of(
                        Identifier.ofVanilla("ruined_portal"),
                        Identifier.ofVanilla("ruined_portal_desert"),
                        Identifier.ofVanilla("ruined_portal_jungle"),
                        Identifier.ofVanilla("ruined_portal_swamp"),
                        Identifier.ofVanilla("ruined_portal_mountain"),
                        Identifier.ofVanilla("ruined_portal_ocean"),
                        Identifier.ofVanilla("ruined_portal_nether")),
                34222645,
                40,
                15,
                SpreadType.LINEAR));
        return Map.copyOf(configs);
    }

    public Identifier structureSetId() {
        return structureSetId;
    }

    public Set<Identifier> structureIds() {
        return structureIds;
    }

    public OptionalInt salt() {
        return OptionalInt.of(salt);
    }

    public int spacing() {
        return spacing;
    }

    public int separation() {
        return separation;
    }

    public SpreadType spreadType() {
        return spreadType;
    }

    public boolean containsStructure(Identifier structureId) {
        return structureIds.contains(structureId);
    }

    StructureChunk getStartChunk(long seed, long regionX, long regionZ) {
        // regionX/Z are region indices, not chunk coordinates. Java long overflow
        // intentionally preserves the low 64 bits used by cubiomes' uint64_t math.
        JavaLegacyRandom random = new JavaLegacyRandom(
                seed + regionX * 341873128712L + regionZ * 132897987541L + salt);
        int range = spacing - separation;
        int offsetX = randomOffset(random, range);
        int offsetZ = randomOffset(random, range);
        return new StructureChunk(regionX * spacing + offsetX, regionZ * spacing + offsetZ);
    }

    private int randomOffset(JavaLegacyRandom random, int range) {
        if (spreadType == SpreadType.TRIANGULAR) {
            return (random.nextInt(range) + random.nextInt(range)) / 2;
        }
        return random.nextInt(range);
    }

    record StructureChunk(long chunkX, long chunkZ) {}
}
