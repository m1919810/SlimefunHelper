package me.matl114.hooks.impl.xaerowaypoints;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public interface IXWaypointAccess {
    String getName();

    public void addTo(List<IXWaypoint> collector);

    public void add(IXWaypoint IXWaypoint, boolean front);

    public void add(IXWaypoint IXWaypoint);

    public void addAll(Collection<IXWaypoint> IXWaypoints, boolean front);

    public void addAll(Collection<IXWaypoint> IXWaypoints);

    public void remove(IXWaypoint IXWaypoint);

    public IXWaypoint remove(int slot);

    public void removeAll(Collection<IXWaypoint> IXWaypoints);

    public void clear();

    public boolean isEmpty();

    public int size();

    public void update(IXWaypoint waypoint, Consumer<IXWaypoint> updater);

    public IXWaypoint get(int slot);

    public IXWaypoint set(int slot, IXWaypoint IXWaypoint);

    public void requestRefresh();
}
