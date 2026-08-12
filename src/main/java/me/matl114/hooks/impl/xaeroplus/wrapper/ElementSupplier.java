package me.matl114.hooks.impl.xaeroplus.wrapper;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

@FunctionalInterface
public interface ElementSupplier<T> {
    public T supplyElement(
            final int windowRegionX,
            final int windowRegionZ,
            final int windowRegionSize,
            final RegistryKey<World> dimension);
}
