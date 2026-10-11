package me.matl114.utils.structures;

import java.util.Set;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Cubiomes 1.21.x ruined portal template and placement flags. */
public record RuinedPortalVariant(
        Identifier template,
        String biomeCategory,
        int rotation,
        boolean mirrored,
        boolean underground,
        boolean airPocket,
        boolean giant,
        VerticalPlacement verticalPlacement,
        RuinedPortalTemplateData templateData) {
    private static final Set<String> OCEANS = Set.of(
            "cold_ocean",
            "deep_cold_ocean",
            "deep_frozen_ocean",
            "deep_lukewarm_ocean",
            "deep_ocean",
            "frozen_ocean",
            "lukewarm_ocean",
            "ocean",
            "warm_ocean");
    private static final Set<String> MOUNTAINS = Set.of(
            "badlands",
            "cherry_grove",
            "eroded_badlands",
            "frozen_peaks",
            "jagged_peaks",
            "meadow",
            "savanna_plateau",
            "snowy_slopes",
            "stony_peaks",
            "stony_shore",
            "windswept_gravelly_hills",
            "windswept_forest",
            "windswept_hills",
            "windswept_savanna",
            "wooded_badlands");
    private static final Set<String> NETHER_BIOMES = Set.of(
            "basalt_deltas", "crimson_forest", "nether_wastes", "soul_sand_valley", "warped_forest");

    public static RuinedPortalVariant generate(
            long seed, StructurePosition structure, Identifier biomeId) {
        JavaLegacyRandom random = StructureStartRandom.forCandidate(seed, structure);
        String family = biomeVariant(biomeId.getPath());
        boolean underground = false;
        boolean airPocket = false;
        if (family.equals("plains") || family.equals("mountains")) {
            underground = random.nextFloat() < 0.5F;
            airPocket = underground;
            if (!underground) {
                airPocket = random.nextFloat() < 0.5F;
            }
        } else if (family.equals("jungle")) {
            airPocket = random.nextFloat() < 0.5F;
        }
        return finishGeneration(random, family, underground, airPocket);
    }

    /** Generates the actual 1.21.11 vanilla variant, including its Nether air-pocket draw. */
    public static RuinedPortalVariant generateVanilla(
            long seed, StructurePosition structure, Identifier biomeId) {
        return generateVanillaForStructure(seed, structure, structureIdForBiome(biomeId));
    }

    /** Generates one vanilla portal type before its final-height biome-tag check. */
    public static RuinedPortalVariant generateVanillaForStructure(
            long seed, StructurePosition structure, Identifier structureId) {
        String family = switch (structureId.getPath()) {
            case "ruined_portal" -> "plains";
            case "ruined_portal_desert" -> "desert";
            case "ruined_portal_jungle" -> "jungle";
            case "ruined_portal_swamp" -> "swamp";
            case "ruined_portal_ocean" -> "ocean";
            case "ruined_portal_mountain" -> "mountains";
            case "ruined_portal_nether" -> "nether_wastes";
            default -> throw new IllegalArgumentException("Not a ruined portal structure: " + structureId);
        };
        JavaLegacyRandom random = StructureStartRandom.forCandidate(seed, structure);
        boolean underground = false;
        boolean airPocket = false;
        if (family.equals("plains") || family.equals("mountains")) {
            underground = random.nextFloat() < 0.5F;
            airPocket = underground;
            if (!underground) {
                airPocket = random.nextFloat() < 0.5F;
            }
        } else if (family.equals("jungle") || family.equals("nether_wastes")) {
            airPocket = random.nextFloat() < 0.5F;
        }
        return finishGeneration(random, family, underground, airPocket);
    }

    private static RuinedPortalVariant finishGeneration(
            JavaLegacyRandom random, String family, boolean underground, boolean airPocket) {
        boolean giant = random.nextFloat() < 0.05F;
        int start = 1 + random.nextInt(giant ? 3 : 10);
        int rotation = random.nextInt(4);
        boolean mirrored = random.nextFloat() >= 0.5F;
        String templateName = (giant ? "giant_portal_" : "portal_") + start;
        VerticalPlacement placement = switch (family) {
            case "desert" -> VerticalPlacement.PARTLY_BURIED;
            case "jungle" -> VerticalPlacement.ON_LAND_SURFACE;
            case "swamp", "ocean" -> VerticalPlacement.ON_OCEAN_FLOOR;
            case "nether_wastes" -> VerticalPlacement.IN_NETHER;
            case "mountains" -> underground
                    ? VerticalPlacement.IN_MOUNTAIN
                    : VerticalPlacement.ON_LAND_SURFACE;
            default -> underground
                    ? VerticalPlacement.UNDERGROUND
                    : VerticalPlacement.ON_LAND_SURFACE;
        };
        return new RuinedPortalVariant(
                Identifier.ofVanilla("ruined_portal/" + templateName),
                family,
                rotation,
                mirrored,
                underground,
                airPocket,
                giant,
                placement,
                RuinedPortalTemplateData.get(giant, start));
    }

    /** Returns the template-relative chest offset after vanilla mirror/rotation. */
    public BlockPos chestPosition(BlockPos structureOrigin) {
        int[] transformed = transformTemplatePoint(
                templateData.chestX(),
                templateData.chestZ(),
                templateData.sizeX() / 2,
                templateData.sizeZ() / 2);
        return structureOrigin.add(transformed[0], templateData.chestY(), transformed[1]);
    }

    public HorizontalBounds horizontalBounds(StructurePosition structure) {
        int pivotX = templateData.sizeX() / 2;
        int pivotZ = templateData.sizeZ() / 2;
        int[] first = transformTemplatePoint(0, 0, pivotX, pivotZ);
        int[] opposite = transformTemplatePoint(
                templateData.sizeX() - 1, templateData.sizeZ() - 1, pivotX, pivotZ);
        int originX = Math.toIntExact(structure.blockX());
        int originZ = Math.toIntExact(structure.blockZ());
        return new HorizontalBounds(
                originX + Math.min(first[0], opposite[0]),
                originZ + Math.min(first[1], opposite[1]),
                originX + Math.max(first[0], opposite[0]),
                originZ + Math.max(first[1], opposite[1]));
    }

    /** The chunk whose decoration pass places the template, matching RuinedPortalStructurePiece.generate. */
    public ChunkPos templatePlacementChunk(StructurePosition structure) {
        HorizontalBounds bounds = horizontalBounds(structure);
        return new ChunkPos(Math.floorDiv(bounds.centerX(), 16), Math.floorDiv(bounds.centerZ(), 16));
    }

    /** Registry member whose vanilla biome tag matches this portal biome family. */
    public Identifier structureId() {
        String suffix = switch (biomeCategory) {
            case "desert" -> "desert";
            case "jungle" -> "jungle";
            case "swamp" -> "swamp";
            case "ocean" -> "ocean";
            case "mountains" -> "mountain";
            case "nether_wastes" -> "nether";
            default -> null;
        };
        return Identifier.ofVanilla(suffix == null ? "ruined_portal" : "ruined_portal_" + suffix);
    }

    int originY(long seed, StructurePosition structure, CubiomesOverworldBiomeSampler terrain) {
        return terrain.getRuinedPortalOriginY(this, structure, seed);
    }

    public BlockPos chestPosition(
            long seed, StructurePosition structure, CubiomesOverworldBiomeSampler terrain) {
        BlockPos origin = new BlockPos(
                Math.toIntExact(structure.blockX()),
                originY(seed, structure, terrain),
                Math.toIntExact(structure.blockZ()));
        return chestPosition(origin);
    }

    public BlockPos chestPosition(
            long seed, StructurePosition structure, CubiomesNetherBiomeSampler terrain) {
        BlockPos origin = new BlockPos(
                Math.toIntExact(structure.blockX()),
                terrain.getRuinedPortalOriginY(this, structure, seed),
                Math.toIntExact(structure.blockZ()));
        return chestPosition(origin);
    }

    JavaLegacyRandom randomAtOriginHeight(long seed, StructurePosition structure) {
        JavaLegacyRandom random = StructureStartRandom.forCandidate(seed, structure);
        if (biomeCategory.equals("plains") || biomeCategory.equals("mountains")) {
            random.nextFloat(); // Select the underground or surface setup.
            if (!underground) {
                random.nextFloat(); // Surface setup has a 0.5 air-pocket chance.
            }
        } else if (biomeCategory.equals("jungle")) {
            random.nextFloat(); // Jungle setup has a 0.5 air-pocket chance.
        } else if (biomeCategory.equals("nether_wastes")) {
            random.nextFloat(); // Vanilla Nether setup has a 0.5 air-pocket chance.
        }
        random.nextFloat(); // Rare giant template chance.
        random.nextInt(giant ? 3 : 10);
        random.nextInt(4); // Template rotation.
        random.nextFloat(); // Template mirror.
        return random;
    }

    private int[] transformTemplatePoint(int x, int z, int pivotX, int pivotZ) {
        if (mirrored) {
            x = -x;
        }
        return switch (rotation) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {pivotX + pivotZ - z, pivotZ - pivotX + x};
            case 2 -> new int[] {2 * pivotX - x, 2 * pivotZ - z};
            case 3 -> new int[] {pivotX - pivotZ + z, pivotX + pivotZ - x};
            default -> throw new AssertionError("nextInt(4) returned outside its range");
        };
    }

    private static String biomeVariant(String biome) {
        if (biome.equals("desert")) {
            return "desert";
        }
        if (biome.equals("jungle")
                || biome.equals("sparse_jungle")
                || biome.equals("bamboo_jungle")) {
            return "jungle";
        }
        if (biome.equals("swamp") || biome.equals("mangrove_swamp")) {
            return "swamp";
        }
        if (OCEANS.contains(biome)) {
            return "ocean";
        }
        if (NETHER_BIOMES.contains(biome)) {
            return "nether_wastes";
        }
        if (MOUNTAINS.contains(biome)) {
            return "mountains";
        }
        return "plains";
    }

    public static Identifier structureIdForBiome(Identifier biomeId) {
        String suffix = switch (biomeVariant(biomeId.getPath())) {
            case "desert" -> "desert";
            case "jungle" -> "jungle";
            case "swamp" -> "swamp";
            case "ocean" -> "ocean";
            case "mountains" -> "mountain";
            case "nether_wastes" -> "nether";
            default -> null;
        };
        return Identifier.ofVanilla(suffix == null ? "ruined_portal" : "ruined_portal_" + suffix);
    }

    public enum VerticalPlacement {
        ON_LAND_SURFACE,
        ON_OCEAN_FLOOR,
        IN_MOUNTAIN,
        UNDERGROUND,
        PARTLY_BURIED,
        IN_NETHER
    }

    public record HorizontalBounds(int minX, int minZ, int maxX, int maxZ) {
        public int centerX() {
            return minX + (maxX - minX + 1) / 2;
        }

        public int centerZ() {
            return minZ + (maxZ - minZ + 1) / 2;
        }
    }
}
