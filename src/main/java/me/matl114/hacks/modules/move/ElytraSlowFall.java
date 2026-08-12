package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;

public class ElytraSlowFall extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;
    public final ModulePath velocityManagement = makePath(Configs.MOV_CONFIG, "velocity-management");
    public final ModulePath floatingUtils = velocityManagement.add("floating-utils");
    public final ModulePath elytraSlowFalling = floatingUtils.add("elytra-slow-falling");

    public ElytraSlowFall() {
        super("ElytraSlowFall");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enableElytraSlowFall);
    }

    public final FlagRef enableElytraSlowFall =
            flagBuilder(elytraSlowFalling.addEnable()).build();

    public final KeyBindRef hotkeySlowFall = moduleEntry(
                    elytraSlowFalling.addHotkey(), new MultiKeyBind(), elytraSlowFalling.addEnable())
            .build();

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (enableElytraSlowFall.get()) {
            if (mc.player.isFallFlying() && !mc.player.isOnGround()) {
                boolean rotateYaw = Tasks.getTick() % 2 == 0;
                movementManagerEvent.context.pushImportantRotation(true, rotateYaw);
                EntityUtils.setEntityPitchSafe(mc.player, 0);
                if (rotateYaw) {
                    PlayerStateManager.setPlayerYawSafe(mc.player, mc.player.getYaw() + 180);
                }
                movementManagerEvent.context.markForResetRot();
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return true;
    }
}
