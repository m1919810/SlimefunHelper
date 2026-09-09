package xaeroplus.feature.highlights;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public abstract class ChunkHighlightBaseCacheHandler implements ChunkHighlightCache {
    public final Long2LongMap chunks = new Long2LongOpenHashMap();
    public MinecraftClient mc = MinecraftClient.getInstance();

    public ChunkHighlightBaseCacheHandler() {
        this.chunks.defaultReturnValue(-1);
    }

    @Override
    public void addHighlight(final int x, final int z) {
        addHighlight(x, z, System.currentTimeMillis());
    }

    @Override
    public void addHighlight(final int x, final int z, final RegistryKey<World> dimensionId) {
        addHighlight(x, z);
    }

    @Override
    public void addHighlight(final int x, final int z, final long foundTime) {}

    @Override
    public void addHighlight(final int x, final int z, final long foundTime, final RegistryKey<World> dimensionId) {
        addHighlight(x, z, foundTime);
    }

    @Override
    public void removeHighlight(final int x, final int z) {}

    @Override
    public void removeHighlight(final int x, final int z, final RegistryKey<World> dimensionId) {
        removeHighlight(x, z);
    }

    @Override
    public void removeHighlights(final LongCollection toRemove) {}

    @Override
    public void removeHighlights(final LongCollection toRemove, RegistryKey<World> dimensionId) {
        removeHighlights(toRemove);
    }

    @Override
    public boolean isHighlighted(final int x, final int z, RegistryKey<World> dimensionId) {
        return false;
    }

    @Override
    public Long2LongMap getCacheMap(final RegistryKey<World> dimension) {
        return chunks;
    }

    public boolean isHighlighted(final long chunkPos) {
        return chunks.containsKey(chunkPos);
    }

    public void replaceState(final Long2LongOpenHashMap state) {}

    public void reset() {}
}
