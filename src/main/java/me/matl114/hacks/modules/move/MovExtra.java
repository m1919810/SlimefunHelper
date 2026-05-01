package me.matl114.hacks.modules.move;

import me.matl114.accessors.access.ClientPlayerAccess;
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
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.versioned.SupportVersion;
import me.matl114.versioned.api.VDataFlag;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;

public class MovExtra extends BaseModule {
    public static final String[] MOVE_COMPATE_HIGHER_VERSION = {"move-safety", "disable-stepheight-feature"};

    public static final String[] ELYTRA_CONSIDER_FUCKING_GRIMAC_1_21_2_INPUT = {
        "move-safety", "grimac-1-21-2-input-features"
    };

    public static final String[] FLIGTH_HOTKEY = {"move-safety", "flight", "toggle-flying"};

    public MovExtra() {}

    public final FlagRef fuckGrimAC = flagBuilder(Configs.MOV_CONFIG, ELYTRA_CONSIDER_FUCKING_GRIMAC_1_21_2_INPUT)
            .build();

    public final FlagRef noStepHeightFeature =
            flagBuilder(Configs.MOV_CONFIG, MOVE_COMPATE_HIGHER_VERSION).build();

    public final KeyBindRef toggleFlyStateKeyBind = hotkey(Configs.MOV_CONFIG, FLIGTH_HOTKEY)
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::onFlightToggle))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
    }

    public void onFlightToggle() {
        if (mc.player == null) return;
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

    public void sendPacketsForInventoryAction() {
        if (fuckGrimAC.get() && SupportVersion.CURRENT.isHigherOrEqualTo(21, 2)) {
            ClientPlayerEntity player = mc.player;
            if (player.isSprinting()) {
                mc.getNetworkHandler()
                        .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                ClientPlayerAccess.of(player).resyncSprint();
            }
            PlayerInputUtils.Input input = PlayerInputUtils.of(player.input);
            input.right(false)
                    .left(false)
                    .forward(false)
                    .backward(false)
                    .jump(false)
                    .sprint(false)
                    .sendPlayerInputPacket();
            ClientPlayerAccess.of(player).resyncInput();
        }
    }
    // mostly same as InventoryAction packets
    public void sendPacketsForPreStartFallFlying() {
        if (fuckGrimAC.get() && SupportVersion.CURRENT.isHigherOrEqualTo(21, 2)) {
            var input = PlayerInputUtils.of(mc.player.input).jump(false);
            input.sendPlayerInputPacket();
            input.applyInput(mc.player.input);
        }
    }

    public void sendPacketsForPostStartFallFlying() {
        if (fuckGrimAC.get() && SupportVersion.CURRENT.isHigherOrEqualTo(21, 2)) {
            var input = PlayerInputUtils.of(mc.player.input).jump(true);
            input.sendPlayerInputPacket();
            input.applyInput(mc.player.input);
        }
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        switch (presetEvent.context.getValue()) {
                // check 1.21.2+
            case AC_GRIM -> fuckGrimAC.set(SupportVersion.CURRENT.isHigherOrEqualTo(21, 2));
            default -> fuckGrimAC.set(false);
        }
    }
}
