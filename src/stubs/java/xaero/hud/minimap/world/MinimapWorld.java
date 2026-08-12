package xaero.hud.minimap.world;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.path.XaeroPath;

public final class MinimapWorld {
    public WaypointSet getCurrentWaypointSet() {
        return null;
    }

    public void addWaypointSet(String s) {}

    public void cleanupOnSave(Path worldFile) throws IOException {}

    public XaeroPath getLocalWorldKey() {
        return null;
    }

    public WaypointSet addWaypointSet(WaypointSet set) {
        return null;
    }

    public WaypointSet getWaypointSet(String key) {
        return null;
    }

    public WaypointSet removeWaypointSet(String key) {
        return null;
    }

    public Iterable<WaypointSet> getIterableWaypointSets() {
        return null;
    }

    public String getCurrentWaypointSetId() {
        return null;
    }

    public void setCurrentWaypointSetId(String currentWaypointSetId) {}

    public String getNode() {
        return null;
    }

    public XaeroPath getFullPath() {
        return null;
    }

    public void setNode(String node) {}

    public MinimapWorldContainer getContainer() {
        return null;
    }

    public void setContainer(MinimapWorldContainer container) {}

    public void requestRemovalOnSave(String name) {}

    public boolean hasSomethingToRemoveOnSave() {
        return false;
    }

    public RegistryKey<World> getDimId() {
        return null;
    }

    public void setDimId(RegistryKey<World> dimId) {}

    public int getSetCount() {
        return 0;
    }

    public Long getSlimeChunkSeed() {
        return 0l;
    }

    public void setSlimeChunkSeed(Long slimeChunkSeed) {}
}
