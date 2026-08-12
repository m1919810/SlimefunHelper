package me.matl114.hooks.impl.xaerowaypoints.impl;

import javax.annotation.Nullable;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypoint;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypointAccess;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypointFactory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;

public class XaeroWaypointFactoryImpl implements IXWaypointFactory {
    public static final XaeroWaypointFactoryImpl INSTANCE = new XaeroWaypointFactoryImpl();

    @Override
    public IXWaypoint createWaypoint(
            int x, int y, int z, String name, String initials, int color, int type, boolean temp, boolean yIncluded) {
        return new WaypointWrapper(new Waypoint(x, y, z, name, initials, color, type, temp, yIncluded));
    }

    @Nullable
    @Override
    public IXWaypointAccess getCurrentWaypointSet() {
        var world = BuiltInHudModules.MINIMAP.getCurrentSession().getWorldManager();
        if (world == null) return null;
        var set = world.getCurrentWorld();
        if (set == null) return null;
        var acc = set.getCurrentWaypointSet();
        if (acc == null) return null;
        return new XaeroWaypointSetImpl(acc);
    }

    @Nullable
    @Override
    public RegistryKey<World> getCurrentWorld() {
        var world = BuiltInHudModules.MINIMAP.getCurrentSession().getWorldManager();
        if (world == null) return null;
        var acc = world.getCurrentWorld();
        if (acc == null) return null;
        return acc.getDimId();
    }
}
