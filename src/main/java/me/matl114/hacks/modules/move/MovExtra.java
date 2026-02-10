package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import net.minecraft.util.math.Vec3d;

public class MovExtra extends BaseModule {
    public static final String[] MOVE_COMPATE_HIGHER_VERSION = {"move-safety", "disable-stepheight-feature"};
    public static final String[] MOVE_DISABLE_SETBACK_VELOCITY_RESET = {"move-safety", "disable-setback-velocity-reset"
    };

    public MovExtra() {}

    public final FlagRef noStepHeightFeature =
            flagBuilder(Configs.MOV_CONFIG, MOVE_COMPATE_HIGHER_VERSION).build();

    public final FlagRef noVelocitySetback =
            flagBuilder(Configs.MOV_CONFIG, MOVE_DISABLE_SETBACK_VELOCITY_RESET).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getTeleportConfirmVelocityUpdatePoint(), this::onSetbackVelocityUpdate);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    public void onSetbackVelocityUpdate(Event<Vec3d> event) {
        if (noVelocitySetback.get()) {
            event.cancel();
        }
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        ModulePreset preset = event.context().getValue();
        // todo: test features
        switch (preset) {
            case AC_GRIM, AC_MATRIX -> {
                noVelocitySetback.set(false);
            }
        }
    }
}
