package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CollisionUtil;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public class ElytraJump extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public ElytraJump() {
        super("ElytraJump");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
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

    public final DoubleRef groundHeight =
            doubleBuilder(root.add("ground-height")).defaultValue(3.0D).build();
    boolean workThisTick = false;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        workThisTick = false;

        if (enable.get()) {
            // set the pitch first to avoid conflict with other mode
            workThisTick = !CollisionUtil.getIntersectingBlockPositions(
                            mc.world, mc.player.getBoundingBox().stretch(0, -groundHeight.get(), 0), false)
                    .isEmpty();
        }
        if (workThisTick) {
            if (mc.player.isFallFlying() || lastFallFly) {
                mc.player.setPitch((float) pitch.get());
                movementManagerEvent.context.markForResetRot();
            }
        }
    }

    boolean lastOnGround = false;
    int counter = 0;
    boolean lastFallFly = false;

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        if (workThisTick) {
            var re = PlayerInputUtils.of(mc.player);
            if (mc.player.isOnGround()) {
                re.jump(true).sprint(true).forward(true).sneak(sneak.get()).applyInput(mc.player);
                mc.player.setSprinting(true);
                counter = 0;
            } else {
                if (!mc.player.isFallFlying()) {
                    if (mc.player.checkFallFlying()) {
                        MovTasks.getMovExtra().sendPacketsForPreStartFallFlying();
                        mc.getNetworkHandler()
                                .sendPacket(new ClientCommandC2SPacket(
                                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                    }
                }
                re.sprint(true).jump(false).forward(true).sneak(sneak.get()).applyInput(mc.player);
            }
            counter++;
        }
        lastOnGround = mc.player.isOnGround();
        lastFallFly = mc.player.isFallFlying();
    }
}
