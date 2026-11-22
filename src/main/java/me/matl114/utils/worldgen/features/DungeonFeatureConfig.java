package me.matl114.utils.worldgen.features;

import java.util.List;
import java.util.Objects;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;

/**
 * Hard-coded vanilla overworld {@code minecraft:monster_room} placed features.
 *
 * <p>Source data: {@code data/minecraft/worldgen/placed_feature/monster_room.json} and
 * {@code monster_room_deep.json}, identical in 1.21.1, 1.21.4, 1.21.11 and 26.1. Both are
 * {@code count -> in_square -> height_range(uniform) -> biome}.
 *
 * <p>{@code featureIndex} is the position in {@code PlacedFeatureIndexer}'s
 * {@code UNDERGROUND_STRUCTURES} list for the overworld biome source. Desert, swamp and mangrove
 * swamp list {@code fossil_upper, fossil_lower, monster_room, monster_room_deep} in that step and
 * every other overworld biome lists {@code monster_room, monster_room_deep}, so the topological
 * order fixes the indices at 2 and 3 in all four versions. Because every overworld biome contains
 * both features, the {@code biome} placement filter always passes in the overworld.
 */
public record DungeonFeatureConfig(
        Identifier placedFeatureId,
        int featureIndex,
        GenerationStep.Feature generationStep,
        int attemptsPerChunk,
        int minY,
        int maxYInclusive) {
    /** Overworld {@code min_y} / {@code height} from {@code dimension_type/overworld.json}. */
    public static final int OVERWORLD_BOTTOM_Y = -64;

    public static final int OVERWORLD_TOP_Y_INCLUSIVE = 319;

    /** {@code count: 10}, Y uniform from absolute 0 to {@code below_top: 0}. */
    public static final DungeonFeatureConfig MONSTER_ROOM = new DungeonFeatureConfig(
            Identifier.ofVanilla("monster_room"),
            2,
            GenerationStep.Feature.UNDERGROUND_STRUCTURES,
            10,
            0,
            OVERWORLD_TOP_Y_INCLUSIVE);

    /** {@code count: 4}, Y uniform from {@code above_bottom: 6} to absolute -1. */
    public static final DungeonFeatureConfig MONSTER_ROOM_DEEP = new DungeonFeatureConfig(
            Identifier.ofVanilla("monster_room_deep"),
            3,
            GenerationStep.Feature.UNDERGROUND_STRUCTURES,
            4,
            OVERWORLD_BOTTOM_Y + 6,
            -1);

    /** In generation order: the chunk generator runs a step's features by ascending index. */
    public static final List<DungeonFeatureConfig> OVERWORLD = List.of(MONSTER_ROOM, MONSTER_ROOM_DEEP);

    public DungeonFeatureConfig {
        Objects.requireNonNull(placedFeatureId);
        Objects.requireNonNull(generationStep);
        if (featureIndex < 0 || attemptsPerChunk <= 0 || minY > maxYInclusive) {
            throw new IllegalArgumentException("Invalid dungeon placement for " + placedFeatureId);
        }
    }
}
