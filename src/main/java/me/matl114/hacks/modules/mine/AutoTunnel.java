package me.matl114.hacks.modules.mine;

import java.util.*;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.RaycastUtils;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoTunnel extends BaseModule {
    public AutoTunnel() {
        super("AutoTunnel");
        bindFlag(enable);
    }

    private final ModulePath tunnel = makePath(Configs.MINE_CONFIG, "mine-tools.tunnel");
    private final Set<BlockPos> queuedTargets = new HashSet<>();

    public final FlagRef enable = flagBuilder(tunnel.addEnable()).build();

    public final KeyBindRef hotkey = toggleHotkey(tunnel.addHotkey(), new MultiKeyBind(), tunnel.addEnable())
            .build();
    public final DoubleRef expandWidth = doubleBuilder(tunnel.add("expand-width"))
            .defaultValue(1.0D)
            .validator(Configs.doubleRange(0.0D, 32.0D))
            .build();
    public final DoubleRef expandHeight = doubleBuilder(tunnel.add("expand-height"))
            .defaultValue(1.0D)
            .validator(Configs.doubleRange(0.0D, 32.0D))
            .build();
    public final DoubleRef distance = doubleBuilder(tunnel.add("distance"))
            .defaultValue(3.0D)
            .validator(Configs.doubleRange(0.0D, 64.0D))
            .build();
    public final FlagRef axisLock = flagBuilder(tunnel.add("axis-lock")).build();
    public final FlagRef lockGround = flagBuilder(tunnel.add("lock-ground")).build();
    public final IntRef groundLockDistance = intBuilder(tunnel.add("ground-lock-distance"))
            .defaultValue(3)
            .validator(Configs.intRange(1, 64))
            .show(lockGround::get)
            .build();
    public final RenderCollector<List<Vec3d>> lineCollector = RenderCollectors.createLinesCollector();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onAutoTunnelTick);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        queuedTargets.clear();
    }

    private void onAutoTunnelTick() {
        if (!isActive() || mc.player == null || mc.world == null || QueueMine.INSTANCE == null) {
            return;
        }

        Set<BlockPos> targets = getTunnelTargets();
        removeTargetsOutsideTunnel(targets);

        int availableQueueSlots =
                Math.max(0, QueueMine.INSTANCE.queueSize.get() - QueueMine.INSTANCE.breakRequest.size());
        for (BlockPos target : targets) {
            if (availableQueueSlots <= 0) {
                break;
            }
            if (QueueMine.INSTANCE.sumitMine(target)) {
                queuedTargets.add(target);
                availableQueueSlots--;
            }
        }
    }

    private Set<BlockPos> getTunnelTargets() {
        Box playerBox = mc.player.getBoundingBox();
        double width = expandWidth.get();
        Box expandedBox = new Box(
                playerBox.minX - width,
                playerBox.minY,
                playerBox.minZ - width,
                playerBox.maxX + width,
                playerBox.maxY + expandHeight.get(),
                playerBox.maxZ + width);
        if (lockGround.get()) {
            var groundY = findGroundY(playerBox);
            var down = Math.max(0, expandedBox.minY - groundY);
            expandedBox = expandedBox.offset(0, -down, 0);
        }

        float yaw = axisLock.get() ? alignToAxis(mc.player.getYaw()) : mc.player.getYaw();
        Vec3d movement = EntityUtils.pitchYawToRotation(0.0F, yaw)
                .withAxis(Direction.Axis.Y, 0.0D)
                .normalize()
                .multiply(distance.get());
        Set<BlockPos> targets = new LinkedHashSet<>();
        Iterator<BlockPos> blockIterator = RaycastUtils.createRaycastBlockPosIterator(expandedBox, movement, false);
        while (blockIterator.hasNext()) {
            BlockPos target = blockIterator.next();
            if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), target)) {
                continue;
            }
            if (isMineable(mc.world.getBlockState(target))) {
                targets.add(target.toImmutable());
            }
        }
        return targets;
    }

    private double findGroundY(Box playerBox) {
        double searchMinY = playerBox.minY - groundLockDistance.get();
        double searchMaxY = playerBox.minY + 1.0E-7D;
        Box searchBox = new Box(playerBox.minX, searchMinY, playerBox.minZ, playerBox.maxX, searchMaxY, playerBox.maxZ);
        double groundY = Double.NEGATIVE_INFINITY;
        for (var collisionShape : mc.world.getBlockCollisions(mc.player, searchBox)) {
            for (Box collisionBox : collisionShape.getBoundingBoxes()) {
                double topY = collisionBox.maxY;
                if (topY > searchMinY && topY <= searchMaxY) {
                    groundY = Math.max(groundY, topY);
                }
            }
        }
        return Double.isFinite(groundY) ? groundY : playerBox.minY;
    }

    private void removeTargetsOutsideTunnel(Set<BlockPos> targets) {
        Iterator<BlockPos> iterator = queuedTargets.iterator();
        while (iterator.hasNext()) {
            BlockPos target = iterator.next();
            if (!QueueMine.INSTANCE.breakRequest.contains(target)) {
                iterator.remove();
            } else if (!targets.contains(target)) {
                QueueMine.INSTANCE.breakRequest.remove(target);
                iterator.remove();
            }
        }
    }

    private float alignToAxis(float currentYaw) {
        float yaw = EntityUtils.normalizeYaw(currentYaw);
        return EntityUtils.normalizeYaw(Math.round(yaw / 45.0F) * 45.0F);
    }

    private boolean isMineable(BlockState state) {
        if (state.isAir() || state.isLiquid() || state.getBlock().getHardness() < 0.0F) {
            return false;
        }
        return PacketMine.INSTANCE == null || PacketMine.INSTANCE.isMineable(state);
    }
}
