package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class AntiChunkLag extends BaseModule implements LegalMovementManager.MovementModifier {
    public static AntiChunkLag INSTANCE;
    public static LegalMovementManager.DelegateMovementModifier instance;

    public AntiChunkLag() {
        INSTANCE = this;
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.MOV_CONFIG, "move-safety.anti-chunk-lag");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final IntRef velocity = intBuilder(root.add("predict-velocity"))
            .defaultValue(10)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef freeze = flagBuilder(root.add("freeze-when-lag")).build();

    public final FlagRef log = flagBuilder(root.add("log-to-player")).build();

    public boolean currentMayFaceLagChunk;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (currentMayFaceLagChunk && enable.get() && freeze.get()) {
            FloatingUtils.INSTANCE.setGrimFloatingTick(true);
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        Vec3d currentVelocity = mc.player.getRotationVector();
        Vec3d predictingPosition = currentVelocity.multiply(velocity.get()).add(mc.player.getPos());
        BlockPos predictingPos = BlockPos.ofFloored(predictingPosition);
        boolean val = !WorldUtils.isChunkLoaded(predictingPos);
        if (val && !currentMayFaceLagChunk) {
            currentMayFaceLagChunk = true;
            if (enable.get() && log.get()) {
                Debug.chat(ChatUtils.stringToText("&c[ChunkLag] &f Facing chunk lag"));
            }
        } else if (!val && currentMayFaceLagChunk) {
            currentMayFaceLagChunk = false;
        }

        return true;
    }
}
