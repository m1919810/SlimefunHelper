package me.matl114.utils.structures;

final class StructureStartRandom {
    private StructureStartRandom() {}

    static JavaLegacyRandom forCandidate(long seed, StructurePosition structure) {
        int chunkX = Math.toIntExact(structure.chunkX());
        int chunkZ = Math.toIntExact(structure.chunkZ());
        JavaLegacyRandom worldRandom = new JavaLegacyRandom(seed);
        long chunkSeed = worldRandom.nextLong() * chunkX ^ worldRandom.nextLong() * chunkZ ^ seed;
        return new JavaLegacyRandom(chunkSeed);
    }
}
