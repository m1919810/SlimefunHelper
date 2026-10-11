package me.matl114.utils.structures;

import java.util.List;
import net.minecraft.util.math.BlockPos;

public record EndCityResult(
        StructurePosition structure,
        int rotation,
        boolean outsideCentralIsland,
        boolean piecesGenerated,
        boolean shipGenerated,
        TerrainStatus terrainStatus,
        int terrainHeight,
        List<EndCityPiece> pieces,
        List<EndCityMarkerPosition> markers) {
    public EndCityResult {
        pieces = List.copyOf(pieces);
        markers = List.copyOf(markers);
    }

    public List<EndCityMarkerPosition> chests() {
        return markers.stream().filter(EndCityMarkerPosition::isChest).toList();
    }

    public List<BlockPos> elytraPositions() {
        return markers.stream()
                .filter(EndCityMarkerPosition::isElytra)
                .map(EndCityMarkerPosition::position)
                .toList();
    }

    public boolean terrainIsKnownToBeValid() {
        return terrainStatus == TerrainStatus.VALID;
    }

    /** Returns piece bounds translated from the generator's relative Y origin into world Y. */
    public List<EndCityPiece> worldPieces() {
        if (!terrainIsKnownToBeValid()) {
            return List.of();
        }
        return pieces.stream().map(piece -> piece.offsetY(terrainHeight)).toList();
    }

    public enum TerrainStatus {
        UNKNOWN,
        VALID,
        INVALID
    }
}
