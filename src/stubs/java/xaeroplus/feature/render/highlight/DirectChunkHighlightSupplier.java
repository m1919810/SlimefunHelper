package xaeroplus.feature.render.highlight;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

@FunctionalInterface
public interface DirectChunkHighlightSupplier {
    /**
     * @return a map of long-packed chunk positions to timestamp
     *         timestamp is unused in rendering but included to reduce memory copies
     *         as this is the map type xp stores its own highlight data in
     */
    Long2LongMap getHighlights(final RegistryKey<World> dimension);
}
