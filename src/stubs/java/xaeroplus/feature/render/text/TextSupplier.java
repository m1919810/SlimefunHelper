package xaeroplus.feature.render.text;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public interface TextSupplier {
    Long2ObjectMap<Text> getText(
            final int windowRegionX,
            final int windowRegionZ,
            final int windowRegionSize,
            final RegistryKey<World> dimension);
}
