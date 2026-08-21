package me.matl114.hacks.modules.move;

import java.util.Objects;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.versioned.api.VDataFlag;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;

public class MovExtra extends BaseModule {
    public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");
    public final ModulePath flight = moveSafety.add("flight");
    public static MovExtra INSTANCE;

    public MovExtra() {
        super("MovExtra");
        INSTANCE = this;
    }

    public final FlagRef fuckGrimAC =
            flagBuilder(moveSafety.add("grimac-1-21-2-input-features")).build();

    public final FlagRef fuckGrimACSprint = builder(moveSafety.add("grimac-sprint-features"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef noStepHeightFeature =
            flagBuilder(moveSafety.add("disable-stepheight-feature")).build();

    public final KeyBindRef toggleFlyStateKeyBind = hotkey(flight.add("toggle-flying"))
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
        sendSprintPacketsForInventoryAction();
        if (fuckGrimAC.get()) {
            if (ViaFabricPlusHooks.isSupportEndTick()) {
                sendNoMultiActionInputPacket(mc.player);
            }
        }
    }

    public void sendSprintPacketsForInventoryAction() {
        if (fuckGrimACSprint.get()) {
            ClientPlayerEntity player = mc.player;
            // only sprint need to be toggled
            if (PlayerStateManager.INSTANCE.lastSprint) {
                mc.getNetworkHandler()
                        .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                ClientPlayerAccess.of(player).setLastSprintFlag(false);
            }
        }
    }

    public void sendInputPacketsForInventoryAction() {
        if (fuckGrimAC.get() && ViaFabricPlusHooks.isSupportEndTick()) {
            ClientPlayerEntity player = mc.player;
            sendNoMultiActionInputPacket(player);
        }
    }

    private void sendNoMultiActionInputPacket(ClientPlayerEntity player) {
        if (PlayerStateManager.INSTANCE.lastInput.hasMovement() || PlayerStateManager.INSTANCE.lastInput.sprint()) {
            PlayerInputUtils.Input input = PlayerInputUtils.of(player);
            input.right(false)
                    .left(false)
                    .forward(false)
                    .backward(false)
                    .jump(false)
                    .sprint(false);
            if (!Objects.equals(PlayerStateManager.INSTANCE.lastInput, input)) {
                input.sendPlayerInputPacket();
                ClientPlayerAccess.of(player).resyncInput();
            }
        }
    }
    // mostly same as InventoryAction packets
    public void sendPacketsForPreStartFallFlying() {
        if (fuckGrimAC.get() && ViaFabricPlusHooks.isSupportEndTick()) {
            if (PlayerStateManager.INSTANCE.lastInput.jump()) {
                var input = PlayerInputUtils.of(mc.player).jump(false);
                input.sendPlayerInputPacket();
                input.applyInput(mc.player);
            }
        }
    }

    public void sendPacketsForPostStartFallFlying() {
        if (fuckGrimAC.get() && ViaFabricPlusHooks.isSupportEndTick()) {
            if (!PlayerStateManager.INSTANCE.lastInput.jump()) {
                var input = PlayerInputUtils.of(mc.player).jump(true);
                input.sendPlayerInputPacket();
                input.applyInput(mc.player);
            }
        }
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        switch (presetEvent.context.getValue()) {
                // check 1.21.2+
            case AC_GRIM, AC_GRIM_LEGACY -> fuckGrimAC.set(true);
            default -> fuckGrimAC.set(false);
        }
    }
}
