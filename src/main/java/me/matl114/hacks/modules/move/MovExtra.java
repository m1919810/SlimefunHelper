package me.matl114.hacks.modules.move;

import me.matl114.accessors.events.EntityAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.versioned.api.VDataFlag;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;

public class MovExtra extends BaseModule {
    public static final String[] MOVE_COMPATE_HIGHER_VERSION = {"move-safety", "disable-stepheight-feature"};
    public static final String[] MOVE_DISABLE_SETBACK_VELOCITY_RESET = {"move-safety", "disable-setback-velocity-reset"
    };

    public static final String[] FLIGTH_HOTKEY = {"hotkeys", "toggle-flying"};

    public MovExtra() {}

    public final FlagRef noStepHeightFeature =
            flagBuilder(Configs.MOV_CONFIG, MOVE_COMPATE_HIGHER_VERSION).build();

    public final FlagRef noVelocitySetback =
            flagBuilder(Configs.MOV_CONFIG, MOVE_DISABLE_SETBACK_VELOCITY_RESET).build();

    public final KeyBindRef toggleFlyStateKeyBind = hotkey(FLIGTH_HOTKEY)
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::onFlightToggle))
            .build();

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

    public void onFlightToggle() {
        if (mc.player.isFallFlying()) {
            // stop fallflying
            mc.getNetworkHandler()
                    .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            EntityAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
        } else {
            if (mc.player.getAbilities().flying) {
                mc.player.getAbilities().flying = false;
            } else if (mc.player.getAbilities().allowFlying) {
                mc.player.getAbilities().flying = true;
                // mc.player.setPos(mc.player.getX(), mc.player.getY() + 0.001, mc.player.getZ());
                Vec3d vec3d = mc.player.getVelocity();
                mc.player.setVelocity(vec3d.x, 0, vec3d.z);
                mc.player.setOnGround(false);
            } else {
                Debug.chat("You are not allowed to fly");
            }
        }
    }
}
