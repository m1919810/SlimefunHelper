package xaero.hud.minimap.waypoint;

public enum WaypointPurpose {
    NORMAL(false, false),
    DEATH(true, true),
    OLD_DEATH(true, true),
    DESTINATION(false, true);

    private final boolean death;
    private final boolean destination;

    private WaypointPurpose(boolean death, boolean destination) {
        this.death = death;
        this.destination = destination;
    }

    public boolean isDeath() {
        return this.death;
    }

    public boolean isDestination() {
        return this.destination;
    }
}
