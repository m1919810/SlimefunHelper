package me.matl114.hacks.modules.extra;

import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import net.minecraft.client.network.ClientPlayerEntity;

public class Misc extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public Misc() {
        super("Misc");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final ModulePath root = makePath(Configs.EXTRA_CONFIG, "misc");

    public final ModulePath friendsWine = root.add("friends_wine");

    public final FlagRef friendsWineEnable =
            flagBuilder(friendsWine.addEnable()).build();

    public final FlagRef sneak = flagBuilder(friendsWine.add("sneak")).build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    public void onPostTick(Event<ClientPlayerEntity> event) {
        if (checkNull()) return;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        LegalMovementManager.MovementModifier.super.applyPreTickModify(movementManagerEvent);
        boolean tickRotate = false;
        int tick = Tasks.getTick() % 10;
        if (friendsWineEnable.get() && !mc.player.isFallFlying()) {
            if (sneak.get()) {
                PlayerInputManager.INSTANCE.addSneakModifier(0, tick <= 4, 1);
            }
            tickRotate = true;
        }
        if (tickRotate) {
            if (!movementManagerEvent.context.hasImportantRotation()) {
                PlayerStateManager.setPlayerYawSafe(mc.player, mc.player.getYaw() + 36 * tick);
                movementManagerEvent.context.markForResetRot();
                movementManagerEvent.context.markForMoveFix();
            }
        }
    }
}
