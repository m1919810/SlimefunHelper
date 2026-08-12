package me.matl114.hooks.impl.xaerowaypoints;

import javax.annotation.Nullable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public interface IXWaypointFactory {

    public IXWaypoint createWaypoint(
            int x, int y, int z, String name, String initials, int color, int type, boolean temp, boolean yIncluded);

    @Nullable
    public IXWaypointAccess getCurrentWaypointSet();

    @Nullable
    public RegistryKey<World> getCurrentWorld();
}
