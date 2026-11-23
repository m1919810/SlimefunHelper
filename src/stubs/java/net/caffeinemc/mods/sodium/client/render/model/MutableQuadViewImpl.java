package net.caffeinemc.mods.sodium.client.render.model;

public abstract class MutableQuadViewImpl {
    public abstract int baseColor(int vertex);

    public abstract MutableQuadViewImpl setColor(int vertex, int color);
}
