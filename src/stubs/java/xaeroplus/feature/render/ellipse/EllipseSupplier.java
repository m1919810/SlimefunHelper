package xaeroplus.feature.render.ellipse;

import java.util.List;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

@FunctionalInterface
public interface EllipseSupplier {
    /**
     * Window = region xz center +- size. meaning the area is: (size*2)^2
     */
    List<Ellipse> getEllipses(
            final int windowRegionX,
            final int windowRegionZ,
            final int windowRegionSize,
            final RegistryKey<World> dimension);
}
