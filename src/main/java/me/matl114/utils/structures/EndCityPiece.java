package me.matl114.utils.structures;

public record EndCityPiece(
        String template,
        int rotation,
        int x,
        int y,
        int z,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ) {
    public boolean contains(int blockX, int blockY, int blockZ) {
        return blockX >= minX && blockX <= maxX
                && blockY >= minY && blockY <= maxY
                && blockZ >= minZ && blockZ <= maxZ;
    }

    public EndCityPiece offsetY(int offset) {
        return new EndCityPiece(
                template, rotation, x, y + offset, z, minX, minY + offset, minZ, maxX, maxY + offset, maxZ);
    }
}
