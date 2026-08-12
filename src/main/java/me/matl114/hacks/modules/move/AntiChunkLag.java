package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.MathUtils;
import me.matl114.utils.WorldUtils;
import net.minecraft.util.math.*;

public class AntiChunkLag extends BaseModule implements LegalMovementManager.MovementModifier {
    public static AntiChunkLag INSTANCE;
    public static LegalMovementManager.DelegateMovementModifier instance;

    public AntiChunkLag() {
        super("AntiChunkLag");
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
            .defaultValue(48)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef freeze = flagBuilder(root.add("freeze-when-lag")).build();

    public final FlagRef log = flagBuilder(root.add("log-to-player")).build();

    public boolean currentMayFaceLagChunk;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        this.applyBeforeMovementPacketModify(movementManagerEvent);
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        int chunkSize = 1 + (velocity.get() / 16);
        boolean hasUnloadedChunk = false;
        ChunkPos playerChunkPos = mc.player.getChunkPos();
        Vec3d playerHorizontalPos = mc.player.getPos().withAxis(Direction.Axis.Y, 0);
        double distanceS2 = MathUtils.s2(velocity.get());
        search:
        for (var x = -chunkSize; x <= chunkSize; x++) {
            for (var z = -chunkSize; z <= chunkSize; z++) {
                ChunkPos chunkPos = new ChunkPos(x + playerChunkPos.x, z + playerChunkPos.z);
                if (WorldUtils.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                    continue;
                }
                Vec3d startPos = new Vec3d(chunkPos.getStartX(), 0, chunkPos.getStartZ());
                Box chunkBox = new Box(startPos, startPos.add(16, 0, 16));
                double distance = Math.max(
                        MathUtils.getBoxDistance(playerHorizontalPos.x, chunkBox.minX, chunkBox.maxX),
                        MathUtils.getBoxDistance(playerHorizontalPos.z, chunkBox.minZ, chunkBox.maxZ));
                if (distance < distanceS2) {
                    hasUnloadedChunk = true;
                    break search;
                }
            }
        }
        boolean val = hasUnloadedChunk;
        if (val && !currentMayFaceLagChunk) {
            currentMayFaceLagChunk = true;
            if (enable.get() && log.get()) {
                logI18N("message.module.anti-chunk-lag.facing-lag");
            }
        } else if (!val && currentMayFaceLagChunk) {
            currentMayFaceLagChunk = false;
        }
        if (currentMayFaceLagChunk && enable.get() && freeze.get()) {
            Vec3d currentPos = mc.player.getPos();
            Vec3d oldPos = movementManagerEvent.context.playerStatus.pos;
            Vec3d movement = currentPos.subtract(oldPos);
            int movementSgnX = (int) MathUtils.sgn(movement.x);
            int movementSgnZ = (int) MathUtils.sgn(movement.z);
            if (movementSgnZ != 0 || movementSgnX != 0) {
                boolean hasUnloaded = false;
                search:
                for (var x = 0; x <= chunkSize; x++) {
                    for (var z = 0; z <= chunkSize; z++) {
                        ChunkPos chunkPos =
                                new ChunkPos(x * movementSgnX + playerChunkPos.x, z * movementSgnZ + playerChunkPos.z);
                        if (WorldUtils.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                            continue;
                        }
                        Vec3d startPos = new Vec3d(chunkPos.getStartX(), 0, chunkPos.getStartZ());
                        Box chunkBox = new Box(startPos, startPos.add(16, 0, 16));
                        double distance = Math.max(
                                MathUtils.getBoxDistance(playerHorizontalPos.x, chunkBox.minX, chunkBox.maxX),
                                MathUtils.getBoxDistance(playerHorizontalPos.z, chunkBox.minZ, chunkBox.maxZ));
                        if (distance < distanceS2) {
                            hasUnloaded = true;
                            break search;
                        }
                    }
                }
                if (hasUnloaded) {
                    movementManagerEvent.context.playerStatus.restorePos();
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                    // fix armorGlide
                    if (mc.player.isFallFlying()) {
                        if (ElytraExtra.INSTANCE.isCurrentArmorGliding()) {
                            if (ElytraExtra.INSTANCE.isThisTickArmoGlideMovementServerSideGlide()) {
                                FloatingUtils.INSTANCE.setForceSilent(false);
                            } else {
                                FloatingUtils.INSTANCE.setForceSilent(true);
                            }
                        } else {
                            FloatingUtils.INSTANCE.setForceSilent(false);
                        }
                    } else {
                        FloatingUtils.INSTANCE.setForceSilent(true);
                    }
                }
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {

        return true;
    }
}
