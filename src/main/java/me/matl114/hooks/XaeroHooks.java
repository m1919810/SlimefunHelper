package me.matl114.hooks;

import java.util.ArrayList;
import lombok.Getter;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.annotations.ExtraArgs;
import me.matl114.events.channels.EventChannel;
import me.matl114.hooks.impl.xaeroplus.IMapDrawFactory;
import me.matl114.hooks.impl.xaeroplus.impl.MapDrawFactoryImpl;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypointFactory;
import me.matl114.hooks.impl.xaerowaypoints.impl.XaeroWaypointFactoryImpl;
import me.matl114.hooks.impl.xaeroworldmap.MapClickContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.map.WorldMap;
import xaero.map.gui.GuiMap;
import xaero.minimap.XaeroMinimap;
import xaeroplus.Globals;

public class XaeroHooks implements IHooks {
    public static final XaeroHooks INSTANCE = new XaeroHooks();

    public static XaeroHooks getInstance() {
        return INSTANCE;
    }

    boolean enable;
    XaeroWorldMapHooks worldMapHooks;
    XaeroPlusHooks plusHooks;
    XaeroMiniMapHooks minimap;

    @Getter
    @Broadcast
    @ExtraArgs({RegistryKey.class, BlockPos.class})
    private static final EventChannel<ArrayList<MapClickContext>> worldMapRightClickOption = new EventChannel<>();

    public XaeroHooks() {
        try {
            worldMapHooks = new XaeroWorldMapImpl();
        } catch (Throwable e) {
            worldMapHooks = new XaeroWorldMapHooks();
        }
        try {
            plusHooks = new XaeroPlusImpl();
        } catch (Throwable e) {
            plusHooks = new XaeroPlusHooks();
        }
        try {
            minimap = new XaeroMiniMapImpl();
        } catch (Throwable e) {
            minimap = new XaeroMiniMapHooks();
        }
        enable = isXaeroPlusEnable() || isXaeroMiniMapEnable() || isXaeroWorldMapEnable();
    }

    public boolean isXaeroWorldMapEnable() {
        return worldMapHooks.isEnabled();
    }

    public boolean isXaeroMiniMapEnable() {
        return minimap.isEnabled();
    }

    public boolean isXaeroPlusEnable() {
        return plusHooks.isEnabled();
    }

    @Override
    public boolean isEnabled() {
        return enable;
    }

    public boolean isGuiMap(Screen screen) {
        return worldMapHooks.isGuiMap(screen);
    }

    public IMapDrawFactory getMapDrawFactory() {
        return plusHooks.getMapDrawFactory();
    }

    public IXWaypointFactory getWaypointFactory() {
        return minimap.getWaypointFactory();
    }

    public static class XaeroWorldMapHooks {
        public boolean isEnabled() {
            return false;
        }

        public boolean isGuiMap(Screen screen) {
            return false;
        }
    }

    public static class XaeroWorldMapImpl extends XaeroWorldMapHooks {
        public XaeroWorldMapImpl() {
            Class<?> main = WorldMap.class;
        }

        public boolean isEnabled() {
            return true;
        }

        public boolean isGuiMap(Screen screen) {
            return screen instanceof GuiMap;
        }
    }

    public static class XaeroPlusHooks {
        public XaeroPlusHooks() {}

        public boolean isEnabled() {
            return false;
        }

        public IMapDrawFactory getMapDrawFactory() {
            return null;
        }
    }

    public static class XaeroPlusImpl extends XaeroPlusHooks {
        final IMapDrawFactory mapDrawFactory;

        public XaeroPlusImpl() {
            Class<?> main = Globals.class;
            this.mapDrawFactory = MapDrawFactoryImpl.INSTANCE;
        }

        public boolean isEnabled() {
            return true;
        }

        @Override
        public IMapDrawFactory getMapDrawFactory() {
            return mapDrawFactory;
        }
    }

    public static class XaeroMiniMapHooks {
        public XaeroMiniMapHooks() {}

        public boolean isEnabled() {
            return false;
        }

        public IXWaypointFactory getWaypointFactory() {
            return null;
        }
    }

    public static class XaeroMiniMapImpl extends XaeroMiniMapHooks {
        IXWaypointFactory waypointFactory;

        public XaeroMiniMapImpl() {
            Class<?> miniMap = XaeroMinimap.class;
            Class<?> access = BuiltInHudModules.class;
            waypointFactory = XaeroWaypointFactoryImpl.INSTANCE;
        }

        public boolean isEnabled() {
            return true;
        }

        @Override
        public IXWaypointFactory getWaypointFactory() {
            return waypointFactory;
        }
    }
}
