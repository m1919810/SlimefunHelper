package me.matl114.utils.structures;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.EndCityGenerator;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.TheEndBiomeSource;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;

/** Uses vanilla's default End generator and piece transformer for exact End City anchors. */
final class VanillaEndCityStructureGenerator {
    private static final Identifier END_CITY = Identifier.ofVanilla("end_city");
    private static final String TEMPLATE_PREFIX = "end_city/";

    private VanillaEndCityStructureGenerator() {}

    static Optional<EndCityResult> generate(long seed, StructurePosition candidate, EndCityTemplateData templateData) {
        DynamicRegistryManager.Immutable registries = DefaultWorldgenRegistryManager.registry();
        Registry<Structure> structures = registries.getWrapperOrThrow(RegistryKeys.STRUCTURE);
        Structure structure = structures.get(END_CITY);
        if (structure == null) {
            return Optional.empty();
        }

        var biomeSource = TheEndBiomeSource.createVanilla(registries.getWrapperOrThrow(RegistryKeys.BIOME));
        RegistryEntry<ChunkGeneratorSettings> settings = registries
                .getWrapperOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
                .getOrThrow(ChunkGeneratorSettings.END);
        NoiseConfig noiseConfig =
                NoiseConfig.create(settings.value(), registries.getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
        NoiseChunkGenerator generator = new NoiseChunkGenerator(biomeSource, settings);
        HeightLimitView heightLimit = HeightLimitView.create(-64, 384);
        ChunkPos chunk = new ChunkPos(Math.toIntExact(candidate.chunkX()), Math.toIntExact(candidate.chunkZ()));
        StructureStart start = structure.createStructureStart(
                registries
                        .getWrapperOrThrow(RegistryKeys.STRUCTURE)
                        .getOrThrow(RegistryKey.of(RegistryKeys.STRUCTURE, END_CITY)),
                World.END,
                registries,
                generator,
                biomeSource,
                noiseConfig,
                DefaultStructureTemplateResources.templates(),
                seed,
                chunk,
                0,
                heightLimit,
                biome -> BiomeValidity.contains(structure.getValidBiomes(), biome));
        if (start == null || !start.hasChildren()) {
            return Optional.empty();
        }

        List<EndCityGenerator.Piece> vanillaPieces = start.getChildren().stream()
                .filter(EndCityGenerator.Piece.class::isInstance)
                .map(EndCityGenerator.Piece.class::cast)
                .toList();
        if (vanillaPieces.isEmpty()) {
            return Optional.empty();
        }
        EndCityGenerator.Piece base = vanillaPieces.getFirst();
        int terrainHeight = base.getPos().getY();
        int rotation = base.getRotation().ordinal();
        List<EndCityPiece> pieces = new ArrayList<>(vanillaPieces.size());
        List<EndCityMarkerPosition> markers = new ArrayList<>();
        var structureContext = DefaultStructureTemplateResources.context(registries);
        for (EndCityGenerator.Piece piece : vanillaPieces) {
            NbtCompound pieceNbt = piece.toNbt(structureContext);
            Identifier id = Identifier.ofVanilla("end_city/" + pieceNbt.getString("Template"));
            String template = id.getPath().startsWith(TEMPLATE_PREFIX)
                    ? id.getPath().substring(TEMPLATE_PREFIX.length())
                    : id.getPath();
            BlockPos origin = piece.getPos();
            var bounds = piece.getBoundingBox();
            EndCityPiece converted = new EndCityPiece(
                    template,
                    piece.getRotation().ordinal(),
                    origin.getX(),
                    origin.getY() - terrainHeight,
                    origin.getZ(),
                    bounds.getMinX(),
                    bounds.getMinY() - terrainHeight,
                    bounds.getMinZ(),
                    bounds.getMaxX(),
                    bounds.getMaxY() - terrainHeight,
                    bounds.getMaxZ());
            pieces.add(converted);

            for (EndCityMarker marker : templateData.markers(template)) {
                int[] transformed = transform(marker.x(), marker.z(), converted.rotation());
                int markerX = converted.x() + transformed[0];
                int markerY = terrainHeight + converted.y() + marker.y();
                int markerZ = converted.z() + transformed[1];
                if (marker.type() == EndCityMarker.Type.CHEST) {
                    markerY--;
                }
                BlockPos position = new BlockPos(markerX, markerY, markerZ);
                boolean generated = converted.contains(markerX, markerY - terrainHeight, markerZ);
                markers.add(new EndCityMarkerPosition(
                        marker.type(),
                        template,
                        position,
                        generated,
                        marker.type() == EndCityMarker.Type.CHEST
                                ? Identifier.ofVanilla("chests/end_city_treasure")
                                        .toString()
                                : null));
            }
        }

        long blockX = candidate.blockX();
        long blockZ = candidate.blockZ();
        boolean outsideCentralIsland = blockX * blockX + blockZ * blockZ >= 1008L * 1008L;
        boolean shipGenerated =
                pieces.stream().anyMatch(piece -> piece.template().equals("ship"));
        return Optional.of(new EndCityResult(
                candidate,
                rotation,
                outsideCentralIsland,
                true,
                shipGenerated,
                EndCityResult.TerrainStatus.VALID,
                terrainHeight,
                pieces,
                markers));
    }

    private static int[] transform(int x, int z, int rotation) {
        return switch (rotation) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            case 3 -> new int[] {z, -x};
            default -> throw new IllegalStateException("Invalid End City rotation: " + rotation);
        };
    }
}
