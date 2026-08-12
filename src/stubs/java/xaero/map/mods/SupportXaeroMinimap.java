package xaero.map.mods;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

public class SupportXaeroMinimap {
    public int compatibilityVersion;
    private boolean deathpoints = true;
    private boolean refreshWaypoints = true;
    private MinimapWorld waypointWorld;
    private MinimapWorld mapWaypointWorld;
    private RegistryKey<World> mapDimId;
    private double dimDiv;
    private WaypointSet waypointSet;
    private boolean allSets;

    public void requestWaypointsRefresh() {
        this.refreshWaypoints = true;
    }

    public KeyBinding getWaypointKeyBinding() {
        return null;
    }

    public KeyBinding getTempWaypointKeyBinding() {
        return null;
    }

    public KeyBinding getTempWaypointsMenuKeyBinding() {
        return null;
    }
}
