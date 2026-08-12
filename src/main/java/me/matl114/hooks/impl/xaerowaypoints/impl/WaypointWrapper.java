package me.matl114.hooks.impl.xaerowaypoints.impl;

import me.matl114.hooks.impl.xaerowaypoints.IXWaypoint;
import xaero.common.minimap.waypoints.Waypoint;

public record WaypointWrapper(Waypoint waypoint) implements IXWaypoint {

    public static WaypointWrapper asWrapper(IXWaypoint waypoint) {
        if (waypoint instanceof WaypointWrapper wrapper) return wrapper;
        return (WaypointWrapper) XaeroWaypointFactoryImpl.INSTANCE.createWaypoint(
                waypoint.getX(),
                waypoint.getY(),
                waypoint.getZ(),
                waypoint.getName(),
                waypoint.getInitials(),
                waypoint.getColor(),
                waypoint.getPurpose(),
                waypoint.isTemp(),
                waypoint.isYInclude());
    }

    public static Waypoint unwrap(IXWaypoint waypoint) {
        if (waypoint instanceof WaypointWrapper wrapper) return wrapper.waypoint();
        return new Waypoint(
                waypoint.getX(),
                waypoint.getY(),
                waypoint.getZ(),
                waypoint.getName(),
                waypoint.getInitials(),
                waypoint.getColor(),
                waypoint.getPurpose(),
                waypoint.isTemp(),
                waypoint.isYInclude());
    }

    @Override
    public int getX() {
        return waypoint.getX();
    }

    @Override
    public int getY() {
        return waypoint.getY();
    }

    @Override
    public int getZ() {
        return waypoint.getZ();
    }

    @Override
    public void setX(int x) {
        waypoint.setX(x);
    }

    @Override
    public void setY(int y) {
        waypoint.setY(y);
    }

    @Override
    public void setZ(int z) {
        waypoint.setZ(z);
    }

    @Override
    public String getName() {
        return waypoint.getName();
    }

    @Override
    public void setName(String name) {
        waypoint.setName(name);
    }

    @Override
    public String getInitials() {
        return waypoint.getInitials();
    }

    @Override
    public int getColor() {
        return waypoint.getColor();
    }

    public void setColor(int color) {
        waypoint.setColor(color);
    }

    @Override
    public int getPurpose() {
        return waypoint.getWaypointType();
    }

    @Override
    public void setPurpose(int purpose) {
        waypoint.setType(purpose);
    }

    @Override
    public boolean isTemp() {
        return waypoint.isTemporary();
    }

    @Override
    public boolean isYInclude() {
        return waypoint.isYIncluded();
    }

    @Override
    public long getCreatedAt() {
        return waypoint.getCreatedAt();
    }
}
