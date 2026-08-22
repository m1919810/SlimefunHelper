package xaero.hud.minimap.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.util.Identifier;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.path.XaeroPath;

public class MinimapWorldManager {

    public MinimapWorldContainer getWorldContainer(XaeroPath path) {
        return this.addWorldContainer(path);
    }

    public MinimapWorldContainer getWorldContainerNullable(XaeroPath path) {
        return this.containerExists(path) ? this.addWorldContainer(path) : null;
    }

    //    public MinimapWorldRootContainer getRootWorldContainer(String rootContainerId) {
    //        return this.getRootWorldContainer(XaeroPath.root(rootContainerId));
    //    }
    //
    //    public MinimapWorldRootContainer getRootWorldContainer(XaeroPath rootContainerPath) {
    //        return this.getWorldContainer(rootContainerPath).getRoot();
    //    }

    public MinimapWorldContainer addWorldContainer(XaeroPath path) {
        return null;
    }
    //
    //    public void addRootWorldContainer(MinimapWorldRootContainer container) {
    //
    //    }

    public boolean removeContainer(XaeroPath path) {
        return false;
    }

    public boolean containerExists(XaeroPath path) {
        return false;
    }

    public MinimapWorld getWorld(XaeroPath worldPath) {
        return this.addWorld(worldPath);
    }

    public MinimapWorld addWorld(XaeroPath worldPath) {
        return null;
    }

    public MinimapWorld getCurrentWorld() {
        return null;
    }

    public MinimapWorld getCurrentWorld(XaeroPath autoWorldPath) {
        return null;
    }

    public MinimapWorld getAutoWorld() {
        return null;
    }

    //    public Iterable<MinimapWorldRootContainer> getRootContainers() {
    //        return this.rootContainers.values();
    //    }
    //
    //    public MinimapWorldRootContainer getAutoRootContainer() {
    //        return this.getRootWorldContainer(this.session.getWorldState().getAutoRootContainerPath());
    //    }
    //
    //    public MinimapWorldRootContainer getCurrentRootContainer() {
    //        MinimapWorld currentWorld = this.getCurrentWorld();
    //        return currentWorld == null ? null : currentWorld.getContainer().getRoot();
    //    }

    public Int2ObjectMap<Waypoint> getCustomWaypoints(Identifier modId) {
        return null;
    }

    public boolean hasCustomWaypoints() {
        return false;
    }

    public Iterable<Waypoint> getCustomWaypoints() {
        return null;
    }
}
