package xaero.common.minimap.waypoints;

import net.minecraft.client.render.Camera;
import net.minecraft.client.resource.language.I18n;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

public class Waypoint implements Comparable<Waypoint> {

    /** @deprecated */
    @Deprecated
    public Waypoint(int x, int y, int z, String name, String initials, int color) {
        this(x, y, z, name, initials, color, 0, false);
    }

    /** @deprecated */
    @Deprecated
    public Waypoint(int x, int y, int z, String name, String initials, int color, int type) {
        this(x, y, z, name, initials, color, type, false);
    }

    /** @deprecated */
    @Deprecated
    public Waypoint(int x, int y, int z, String name, String initials, int color, int type, boolean temp) {
        this(x, y, z, name, initials, color, type, temp, true);
    }

    /** @deprecated */
    @Deprecated
    public Waypoint(
            int x, int y, int z, String name, String initials, int color, int type, boolean temp, boolean yIncluded) {}

    public Waypoint(int x, int y, int z, String name, String initials, WaypointColor color) {}

    public Waypoint(int x, int y, int z, String name, String initials, WaypointColor color, WaypointPurpose purpose) {}

    public Waypoint(
            int x,
            int y,
            int z,
            String name,
            String initials,
            WaypointColor color,
            WaypointPurpose purpose,
            boolean temp) {
        this(x, y, z, name, initials, color, purpose, temp, true);
    }

    public Waypoint(
            int x,
            int y,
            int z,
            String name,
            String initials,
            WaypointColor color,
            WaypointPurpose purpose,
            boolean temp,
            boolean yIncluded) {}

    public int getX() {
        return 0;
    }

    public void setX(int x) {}

    public int getX(double dimDiv) {
        return dimDiv == 1.0 ? 0 : (int) Math.floor((double) 0 / dimDiv);
    }

    public int getY() {
        return 0;
    }

    public void setY(int y) {}

    public int getZ() {
        return 0;
    }

    public void setZ(int z) {}

    public int getZ(double dimDiv) {
        return dimDiv == 1.0 ? 0 : (int) Math.floor((double) 0 / dimDiv);
    }

    public String getName() {
        return null;
    }

    public String getLocalizedName() {
        return I18n.translate(null, new Object[0]);
    }

    public String getNameSafe(String replacement) {
        return this.getName().replace(":", replacement);
    }

    public void setName(String name) {}

    public String getInitials() {
        return null;
    }

    public void setInitials(String initials) {}

    /** @deprecated */
    @Deprecated
    public String getSymbol() {
        return this.getInitials();
    }

    /** @deprecated */
    @Deprecated
    public void setSymbol(String symbol) {
        this.setInitials(symbol);
    }

    /** @deprecated */
    @Deprecated
    public String getSymbolSafe(String replacement) {
        return this.getInitialsSafe(replacement);
    }

    public String getInitialsSafe(String replacement) {
        return this.getInitials().replace(":", replacement);
    }

    /** @deprecated */
    @Deprecated
    public int getColor() {
        return this.getWaypointColor().ordinal();
    }

    /** @deprecated */
    @Deprecated
    public int getActualColor() {
        return 0;
    }

    /** @deprecated */
    @Deprecated
    public void setColor(int c) {}

    public WaypointColor getWaypointColor() {
        return null;
    }

    public void setWaypointColor(WaypointColor c) {}

    public boolean isGlobal() {
        return false;
    }

    /** @deprecated */
    @Deprecated
    public int getVisibilityType() {
        return 0;
    }

    /** @deprecated */
    @Deprecated
    public void setVisibilityType(int visibility) {}

    public boolean isDisabled() {
        return false;
    }

    public void setDisabled(boolean b) {}

    /** @deprecated */
    @Deprecated
    public int getWaypointType() {
        return 0;
    }

    /** @deprecated */
    @Deprecated
    public void setType(int type) {}

    public WaypointPurpose getPurpose() {
        return null;
    }

    public void setPurpose(WaypointPurpose purpose) {}

    public boolean isRotation() {
        return false;
    }

    public void setRotation(boolean rotation) {}

    public int getYaw() {
        return 0;
    }

    public void setYaw(int yaw) {}

    public boolean isTemporary() {
        return false;
    }

    public void setTemporary(boolean temporary) {}

    public boolean isYIncluded() {
        return false;
    }

    public void setYIncluded(boolean yIncluded) {}

    public long getCreatedAt() {
        return 0L;
    }

    /** @deprecated */
    @Deprecated
    public boolean isOneoffDestination() {
        return false;
    }

    /** @deprecated */
    @Deprecated
    public void setOneoffDestination(boolean oneoffDestination) {}

    public boolean isDestination() {
        return false;
    }

    public double getDistanceSq(double x, double y, double z) {
        return 0;
    }

    public static String getStringFromStringSafe(String stringSafe, String replacement) {
        return stringSafe.replace(replacement, ":");
    }

    public boolean isServerWaypoint() {
        return false;
    }

    public String getComparisonName() {
        return null;
    }

    public double getComparisonDistance(Camera camera, double dimDiv) {
        return 0.0D;
    }

    public double getComparisonAngleCos(Camera camera, double dimDiv) {
        return 0.0D;
    }

    private double getRenderSortingDistanceSquared() {
        return 0;
    }

    public int compareTo(Waypoint other) {
        return 0;
    }
}
