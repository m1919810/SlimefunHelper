package me.matl114.hacks.modules.mine;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Tasks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MiningProgressManager extends BaseModule {
    public static MiningProgressManager INSTANCE;

    public MiningProgressManager() {
        super("MiningProgressManager");
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getEntityRemoveListener().getChannel(EntityType.PLAYER), this::onEntityRemove);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldChange);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(BlockBreakingProgressS2CPacket.class),
                this::onBlockProgressUpdate);
        registerListener(Listener.getPreGameTick(), this::onUpdate);
    }

    final Map<Integer, BlockBreakTracker> trackedMap = new HashMap<>();

    public Map<Integer, BlockBreakTracker> getBreakingMap() {
        return trackedMap;
    }

    public void onEntityRemove(Event<Entity> event) {
        trackedMap.remove(event.context.getId());
    }

    public void onWorldChange(Event<World> world) {
        trackedMap.clear();
    }

    public void onBlockProgressUpdate(Event<BlockBreakingProgressS2CPacket> eventProgress) {
        int eid = eventProgress.context.getEntityId();
        BlockPos ps = eventProgress.context.getPos();
        if (mc.world.getEntityById(eid) instanceof PlayerEntity pl) {
            int progress = eventProgress.context.getProgress();
            if (progress == 255) {
                // 傻逼吧。
                progress = -1;
            }
            BlockBreakTracker tracker = trackedMap.computeIfAbsent(eid, (v) -> new BlockBreakTracker(pl));
            tracker.pushBreakingProgress(ps, progress);
        }
    }

    public void onUpdate(Event<ClientPlayerEntity> eventUpdate) {
        if (checkNull()) return;
        for (var re : trackedMap.values()) {
            re.tickWorld(mc.world);
        }
    }

    public static class BlockBreakTracker {
        public BlockBreakTracker(PlayerEntity player) {
            this.player = player;
        }

        public PlayerEntity player;
        public BlockPos blockPos;
        public int breakingStartTick = 0;
        public int breakingProgress = -1;
        public BlockPos potentialDoubleBreak;
        public int doubleBreakProgress = -1;
        public int potentialDoubleBreakStartTick = 0;

        public void pushBreakingProgress(BlockPos pos, int breakingProgress) {
            if (Objects.equals(blockPos, pos)) {
                this.breakingProgress = breakingProgress;
                updateB(pos);
            } else if (Objects.equals(potentialDoubleBreak, pos)) {
                this.doubleBreakProgress = breakingProgress;
                updateD(pos);
            } else {
                if (blockPos == null) {
                    this.breakingProgress = breakingProgress;
                    updateB(pos);
                } else {
                    if (this.breakingProgress != 0 && this.doubleBreakProgress <= 0) {
                        this.doubleBreakProgress = this.breakingProgress;
                        updateD(this.blockPos);
                    }
                    this.breakingProgress = breakingProgress;
                    updateB(pos);
                }
            }
        }

        private void updateB(BlockPos pos) {
            if (!Objects.equals(blockPos, pos)) {
                blockPos = pos;
                breakingStartTick = Tasks.getTick();
            }
            if (breakingProgress >= 10) {
                breakingProgress = -1;
            }
        }

        private void updateD(BlockPos pos) {
            if (!Objects.equals(potentialDoubleBreak, pos)) {
                potentialDoubleBreak = pos;
                potentialDoubleBreakStartTick = Tasks.getTick();
            }
            if (doubleBreakProgress >= 10) {
                potentialDoubleBreak = null;
                doubleBreakProgress = -1;
            }
        }

        public void tickWorld(World world) {
            if (potentialDoubleBreak != null
                    && world.getBlockState(potentialDoubleBreak).isAir()) {
                potentialDoubleBreak = null;
                doubleBreakProgress = -1;
            }
        }
    }
}
