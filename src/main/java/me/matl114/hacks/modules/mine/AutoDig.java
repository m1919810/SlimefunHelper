package me.matl114.hacks.modules.mine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.modules.move.RotationManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.MathUtils;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class AutoDig extends BaseModule {
    public AutoDig() {
        super("AutoDig");
        bindFlag(enable);
    }

    public final ModulePath autoTunnel = makePath(Configs.MINE_CONFIG, "mine-tools.auto-tunnel");

    private BlockPos snapCenter;
    private final Set<BlockPos> queuedTargets = new HashSet<>();

    public final FlagRef enable = flagBuilder(autoTunnel.addEnable())
            .updateListener(enabled -> {
                if (!enabled) {
                    snapCenter = null;
                    queuedTargets.clear();
                }
            })
            .build();
    public final KeyBindRef hotkey = moduleEntry(autoTunnel.addHotkey(), new MultiKeyBind(), autoTunnel.addEnable())
            .build();
    public final FlagRef snap = flagBuilder(autoTunnel.add("snap"))
            .updateListener(enabled -> {
                if (!enabled) {
                    snapCenter = null;
                }
            })
            .build();
    public final FlagRef fromBottom = flagBuilder(autoTunnel.add("from-bottom")).build();
    public final IntRef searchRange = intBuilder(autoTunnel.add("search-range"))
            .defaultValue(3)
            .validator(Configs.intRange(1, 100))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onAutoDigTick);
        registerListener(Listener.getPreHandleInputEvents(), this::onAutoDigInput);
    }

    private void onAutoDigTick() {
        if (!isActive() || mc.player == null || mc.world == null || QueueMine.INSTANCE == null) {
            return;
        }

        Box playerBox = mc.player.getBoundingBox();
        if (removeTargetsNoLongerUnderfoot(playerBox) || !QueueMine.INSTANCE.breakRequest.isEmpty()) {
            return;
        }

        BlockPos occupiedPos = mc.player.getBlockPos();
        int topTargetY = occupiedPos.getY() - 1;
        int minTargetY = Math.max(occupiedPos.getY() - searchRange.get(), mc.world.getBottomY());
        List<List<BlockPos>> targetLayers = new ArrayList<>();
        for (int y = topTargetY; y >= minTargetY; y--) {
            List<BlockPos> layerTargets = new ArrayList<>();
            boolean layerInRange = false;
            for (BlockPos target : MathUtils.getOccupiedHorizontalBlockPositions(playerBox, y)) {
                if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), target)) {
                    continue;
                }
                layerInRange = true;
                if (isMineable(mc.world.getBlockState(target))) {
                    layerTargets.add(target);
                }
            }
            if (!layerInRange) {
                break;
            }
            if (!layerTargets.isEmpty()) {
                targetLayers.add(layerTargets);
            }
        }

        if (targetLayers.isEmpty()) {
            return;
        }
        List<BlockPos> layer = fromBottom.get() ? targetLayers.get(targetLayers.size() - 1) : targetLayers.get(0);
        int availableQueueSlots = QueueMine.INSTANCE.queueSize.get();
        for (BlockPos target : layer) {
            if (availableQueueSlots <= 0) break;
            if (QueueMine.INSTANCE.sumitMine(target)) {
                queuedTargets.add(target);
                availableQueueSlots--;
            }
        }
    }

    private boolean removeTargetsNoLongerUnderfoot(Box playerBox) {
        boolean removedTarget = false;
        Iterator<BlockPos> iterator = queuedTargets.iterator();
        while (iterator.hasNext()) {
            BlockPos target = iterator.next();
            if (!QueueMine.INSTANCE.breakRequest.contains(target)) {
                iterator.remove();
            } else if (!isUnderfoot(playerBox, target)) {
                QueueMine.INSTANCE.breakRequest.remove(target);
                iterator.remove();
                removedTarget = true;
            }
        }
        return removedTarget;
    }

    private boolean isUnderfoot(Box playerBox, BlockPos pos) {
        return pos.getY() < mc.player.getBlockPos().getY()
                && MathUtils.getOccupiedHorizontalBlockPositions(playerBox, pos.getY())
                        .contains(pos)
                && InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos);
    }

    private boolean isMineable(BlockState state) {
        if (state.isAir() || state.isLiquid() || state.getBlock().getHardness() < 0.0F) {
            return false;
        }
        return PacketMine.INSTANCE == null || PacketMine.INSTANCE.isMineable(state);
    }

    private void onAutoDigInput() {
        if (!isActive() || !snap.get() || mc.player == null || !mc.player.isOnGround()) {
            return;
        }
        ClientPlayerEntity player = mc.player;
        if (player.getPose() == EntityPose.SWIMMING) {
            return;
        }

        if (snapCenter == null || !MathUtils.isInBox(snapCenter.toCenterPos(), player.getPos(), 1.0)) {
            snapCenter =
                    PlayerStateManager.INSTANCE == null ? null : PlayerStateManager.INSTANCE.lastVelocityAffectingPos;
            if (snapCenter == null) {
                snapCenter = player.getBlockPos();
            }
        }

        BlockPos center = snapCenter;
        boolean centered = Math.abs(player.getX() - center.getX() - 0.5) <= 0.2
                && Math.abs(player.getZ() - center.getZ() - 0.5) <= 0.2;
        if (centered) {
            snapCenter = null;
            return;
        }

        if (!PlayerInputUtils.of(mc.options).hasMovement()) {
            Vec3d lookHorizontal = center.toCenterPos().subtract(player.getPos());
            float yaw = EntityUtils.rotationToYaw(lookHorizontal);
            RotationManager.INSTANCE.request(RotationManager.ofDirection(Optional.empty(), Optional.of(yaw))
                    .preTick(() -> {
                        PlayerInputManager.INSTANCE.addForwardModifier(0, true, 0);
                        PlayerInputManager.INSTANCE.addBackwardModifier(0, false, 0);
                    })
                    .applyMoveFix(false)
                    .priority(0));
        }
    }
}
