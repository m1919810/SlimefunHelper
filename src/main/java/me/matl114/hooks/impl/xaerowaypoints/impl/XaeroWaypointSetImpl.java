package me.matl114.hooks.impl.xaerowaypoints.impl;

import static me.matl114.hooks.impl.xaerowaypoints.impl.WaypointWrapper.*;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypoint;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypointAccess;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.map.mods.SupportMods;

public record XaeroWaypointSetImpl(WaypointSet waypointSet) implements IXWaypointAccess {
    @Override
    public String getName() {
        return waypointSet.getName();
    }

    @Override
    public void addTo(List<IXWaypoint> collector) {
        waypointSet.addTo(collector.stream().map(WaypointWrapper::unwrap).toList());
    }

    @Override
    public void add(IXWaypoint IXWaypoint, boolean front) {
        waypointSet.add(unwrap(IXWaypoint), front);
    }

    @Override
    public void add(IXWaypoint IXWaypoint) {
        waypointSet.add(unwrap(IXWaypoint));
    }

    @Override
    public void addAll(Collection<IXWaypoint> IXWaypoints, boolean front) {
        waypointSet.addAll(IXWaypoints.stream().map(WaypointWrapper::unwrap).toList(), front);
    }

    @Override
    public void addAll(Collection<IXWaypoint> IXWaypoints) {
        waypointSet.addAll(IXWaypoints.stream().map(WaypointWrapper::unwrap).toList());
    }

    @Override
    public void remove(IXWaypoint IXWaypoint) {
        waypointSet.remove(unwrap(IXWaypoint));
    }

    @Override
    public IXWaypoint remove(int slot) {
        return new WaypointWrapper(waypointSet.remove(slot));
    }

    @Override
    public void removeAll(Collection<IXWaypoint> IXWaypoints) {
        waypointSet.removeAll(IXWaypoints.stream().map(WaypointWrapper::unwrap).toList());
    }

    @Override
    public void clear() {
        waypointSet.clear();
    }

    @Override
    public boolean isEmpty() {
        return waypointSet.isEmpty();
    }

    @Override
    public int size() {
        return waypointSet.size();
    }

    @Override
    public IXWaypoint get(int slot) {
        return new WaypointWrapper(waypointSet.get(slot));
    }

    @Override
    public IXWaypoint set(int slot, IXWaypoint IXWaypoint) {
        return new WaypointWrapper(waypointSet.set(slot, unwrap(IXWaypoint)));
    }

    @Override
    public void requestRefresh() {
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
    }

    public void update(IXWaypoint waypoint, Consumer<IXWaypoint> updater) {
        int index = size();
        Waypoint waypoint1 = WaypointWrapper.unwrap(waypoint);
        for (int i = 0; i < index; i++) {
            var re = waypointSet.get(i);
            if (Objects.equals(re, waypoint1)) {
                WaypointWrapper wrapper = new WaypointWrapper(re);
                updater.accept(wrapper);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        return o == this || (o instanceof XaeroWaypointSetImpl impl && impl.waypointSet() == waypointSet);
    }
}
