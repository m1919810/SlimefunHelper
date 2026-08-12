package xaero.hud.minimap.module;

import xaero.common.minimap.MinimapProcessor;
import xaero.hud.minimap.radar.RadarSession;
import xaero.hud.minimap.waypoint.WaypointSession;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.module.ModuleSession;

public final class MinimapSession extends ModuleSession<MinimapSession> {

    public void prePotentialRender() {}

    public void close() {}

    public int getWidth(double screenScale) {
        return 0;
    }

    public int getHeight(double screenScale) {
        return 0;
    }

    public int getConfiguredWidth() {
        return 0;
    }

    public MinimapProcessor getProcessor() {
        return null;
    }

    public boolean getHideMinimapUnderScreen() {
        return false;
    }

    public boolean getHideMinimapUnderF3() {
        return false;
    }

    //    public MultiTextureRenderTypeRendererProvider getMultiTextureRenderTypeRenderers() {
    //        return this.multiTextureRenderTypeRenderers;
    //    }

    public WaypointSession getWaypointSession() {
        return null;
    }

    public RadarSession getRadarSession() {
        return null;
    }

    public MinimapWorldManager getWorldManager() {
        return null;
    }

    //    public MinimapWorldState getWorldState() {
    //        return this.worldState;
    //    }

    //    public MinimapWorldStateUpdater getWorldStateUpdater() {
    //        return this.worldStateUpdater;
    //    }

    //    public MinimapDimensionHelper getDimensionHelper() {
    //        return this.dimensionHelper;
    //    }
    //
    //    public MinimapWorldManagerIO getWorldManagerIO() {
    //        return this.worldManagerIO;
    //    }
}
