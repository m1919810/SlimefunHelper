package me.matl114.hacks.modules.move;

import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public class ElytraJump extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public ElytraJump() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    ModulePath root = makePath(Configs.MOV_CONFIG, "elytra.elytra-flight-legit.elytra-jump");
    public final FlagRef enable = flagBuilder(root.addEnable()).build();
    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();
    //
    public final FlagRef conditionalSprint =
            flagBuilder(root.add("conditional-sprint")).build();

    public final DoubleRef pitch =
            doubleBuilder(root.add("pitch")).defaultValue(80.0D).build();

    public final FlagRef sneak = flagBuilder(root.add("sneak")).build();

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

    boolean lastOnGround = false;
    int counter = 0;

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        if (enable.get()) {
            var re = PlayerInputUtils.of(mc.player);
            if (mc.player.isOnGround()) {
                ClientPlayerAccess.of(mc.player).setJumpingCooldown(0);
                re.jump(true).sprint(true).forward(true).sneak(sneak.get()).applyInput(mc.player);
                mc.player.setSprinting(true);
                counter = 0;

            } else {
                if (conditionalSprint.get()) {
                    mc.player.setSprinting(false);
                }
                if (!mc.player.isFallFlying()) {
                    if (mc.player.checkGliding()) {
                        MovTasks.getMovExtra().sendPacketsForPreStartFallFlying();
                        mc.getNetworkHandler()
                                .sendPacket(new ClientCommandC2SPacket(
                                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                    }
                }
                re.sprint(false).jump(false).forward(false).sneak(sneak.get()).applyInput(mc.player);
            }
            counter++;

            if (mc.player.isFallFlying()) {
                mc.player.setPitch((float) pitch.get());
                movementManagerEvent.context.markForResetRot();
            }
        }
        lastOnGround = mc.player.isOnGround();
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return true;
    }
}
