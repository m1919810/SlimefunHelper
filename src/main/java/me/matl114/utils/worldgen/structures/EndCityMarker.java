package me.matl114.utils.worldgen.structures;

public record EndCityMarker(String template, Type type, int x, int y, int z) {
    public enum Type {
        CHEST,
        ELYTRA
    }
}
