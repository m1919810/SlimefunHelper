package xaeroplus.feature.render.ellipse;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

@FunctionalInterface
public interface MultiColorEllipseSupplier {
    /**
     * @return Map of ellipse to int color
     */
    Object2IntMap<Ellipse> getEllipses(
            final int windowRegionX,
            final int windowRegionZ,
            final int windowRegionSize,
            final RegistryKey<World> dimension);
}
