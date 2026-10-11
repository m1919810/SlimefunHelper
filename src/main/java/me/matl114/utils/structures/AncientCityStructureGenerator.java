package me.matl114.utils.structures;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.gen.structure.JigsawStructure;
import net.minecraft.world.gen.structure.Structure;

/** Runs Minecraft's 1.21.11 jigsaw expansion for a seed-derived Ancient City candidate. */
public final class AncientCityStructureGenerator {
    private static final Identifier ANCIENT_CITY = Identifier.ofVanilla("ancient_city");

    private AncientCityStructureGenerator() {}

    public static Optional<Result> generate(
            long seed,
            StructurePosition candidate,
            CubiomesOverworldBiomeSampler sampler) {
        Objects.requireNonNull(candidate);
        Objects.requireNonNull(sampler);
        if (!candidate.config().containsStructure(ANCIENT_CITY)) {
            return Optional.empty();
        }
        if (!sampler.isAncientCityBiomeViable(seed, candidate)) {
            return Optional.empty();
        }

        DynamicRegistryManager registries = DefaultWorldgenRegistryManager.registry();
        Registry<Structure> structures = registries.getWrapperOrThrow(RegistryKeys.STRUCTURE);
        Structure structure = structures.get(ANCIENT_CITY);
        if (!(structure instanceof JigsawStructure jigsaw)) {
            return Optional.empty();
        }

        StructureTemplateManager templates = DefaultStructureTemplateResources.templates();
        HeightLimitView heightLimit = sampler.heightLimitView();
        ChunkPos chunk = new ChunkPos(
                Math.toIntExact(candidate.chunkX()), Math.toIntExact(candidate.chunkZ()));
        Structure.Context context = new Structure.Context(
                registries,
                sampler.terrainGenerator(),
                sampler.biomeSource(),
                sampler.noiseConfig(),
                templates,
                seed,
                chunk,
                heightLimit,
                biome -> BiomeValidity.contains(jigsaw.getValidBiomes(), biome));
        Optional<Structure.StructurePosition> generated = jigsaw.getValidStructurePosition(context);
        if (generated.isEmpty()) {
            return Optional.empty();
        }

        StructurePiecesCollector collector = generated.get().generate();
        List<net.minecraft.structure.StructurePiece> generatedPieces = collector.toList().pieces();
        if (generatedPieces.isEmpty()) {
            return Optional.empty();
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (net.minecraft.structure.StructurePiece piece : generatedPieces) {
            minX = Math.min(minX, piece.getBoundingBox().getMinX());
            minZ = Math.min(minZ, piece.getBoundingBox().getMinZ());
            maxX = Math.max(maxX, piece.getBoundingBox().getMaxX());
            maxZ = Math.max(maxZ, piece.getBoundingBox().getMaxZ());
        }
        List<Piece> pieces = generatedPieces.stream()
                .filter(PoolStructurePiece.class::isInstance)
                .map(PoolStructurePiece.class::cast)
                .filter(piece -> piece.getPoolElement() instanceof SinglePoolElement)
                .map(piece -> {
                    SinglePoolElement element = (SinglePoolElement) piece.getPoolElement();
                    Identifier template = element.getIdOrThrow();
                    BlockPos origin = piece.getPos().toImmutable();
                    BlockRotation rotation = piece.getRotation();
                    return new Piece(
                            template,
                            origin,
                            rotation,
                            AncientCityChestTemplateData.worldChests(template, origin, rotation));
                })
                .toList();
        return Optional.of(new Result(
                candidate,
                generated.get().position(),
                minX,
                minZ,
                maxX,
                maxZ,
                pieces));
    }

    public record Result(
            StructurePosition candidate,
            BlockPos origin,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            List<Piece> pieces) {
        public Result {
            origin = origin.toImmutable();
            pieces = List.copyOf(pieces);
        }

        public List<AncientCityChestTemplateData.WorldChest> chests() {
            return pieces.stream().flatMap(piece -> piece.chests().stream()).toList();
        }

        StructureReferenceOrder.Bounds referenceBounds() {
            return new StructureReferenceOrder.Bounds(minX, minZ, maxX, maxZ);
        }
    }

    public record Piece(
            Identifier template,
            BlockPos origin,
            BlockRotation rotation,
            List<AncientCityChestTemplateData.WorldChest> chests) {
        public Piece {
            origin = origin.toImmutable();
            chests = List.copyOf(chests);
        }
    }
}
