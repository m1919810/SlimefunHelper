package me.matl114.hacks.modules.extra;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;

public class GuiFix extends BaseModule {
    public static final String[] WORLD_LOAD_GUI_FIX = new String[] {"other", "gui-fix", "disable-terrain-load-screen"};

    public GuiFix() {}

    public final FlagRef noTerrain =
            flagBuilder(Configs.TEST_CONFIG, WORLD_LOAD_GUI_FIX).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreSetScreen(), this::onTerrainScreenLoad);
    }

    public void onTerrainScreenLoad(Event<Screen> event) {
        if (noTerrain.get() && event.context() instanceof LevelLoadingScreen levelLoadingScreen) {
            event.context(null);
            // event.cancel();
        }
    }
}
