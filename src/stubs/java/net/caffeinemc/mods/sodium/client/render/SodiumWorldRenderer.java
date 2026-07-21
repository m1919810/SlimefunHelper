package net.caffeinemc.mods.sodium.client.render;

import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.render.Camera;

public class SodiumWorldRenderer {
    public void setupTerrain(
            Camera camera,
            Viewport viewport,
            FogParameters fogParameters,
            boolean spectator,
            boolean updateChunksImmediately,
            ChunkRenderMatrices matrices) {}
}
