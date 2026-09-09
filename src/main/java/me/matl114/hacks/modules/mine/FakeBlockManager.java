package me.matl114.hacks.modules.mine;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.*;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.utils.NetworkUtils;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class FakeBlockManager extends BaseModule {
    public static FakeBlockManager INSTANCE;

    public FakeBlockManager() {
        super("FakeBlockManager");
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(PlayerActionResponseS2CPacket.class), this::onBlockACK);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(BlockUpdateS2CPacket.class), this::onBlockUpdate);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(ChunkDeltaUpdateS2CPacket.class),
                this::onChunkDeltaUpdate);
        registerListener(Listener.getChunkUpdateListener(), this::onChunkUpdate);
    }

    final Int2ObjectOpenHashMap<BlockPos> fakeMiningBlocks = new Int2ObjectOpenHashMap<>(4);
    final Set<BlockPos> permanentFakeMiningBlocks = new HashSet<>();

    public void onWorldSwitch(Event<World> event) {
        fakeMiningBlocks.clear();
    }

    public void addFakeCompensateState(BlockPos pos) {
        addFakeCompensateState(pos, false);
    }

    public void addFakeCompensateState(BlockPos pos, boolean force) {
        if (!force && fakeMiningBlocks.containsValue(pos)) {
            return;
        }
        if (Objects.equals(
                PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos(), new BlockPos(-1, -1, -1))) {
            // start to avoid wrong break
            PlayerInteractionAccess.of(mc.interactionManager).sendStartBreakPacket(pos);
        }
        Direction direction = Direction.getFacing(mc.player.getEyePos().subtract(pos.toCenterPos()));
        int seq = NetworkUtils.generateNextSequence();
        mc.getNetworkHandler()
                .sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, seq));
        BlockPos pos2 = fakeMiningBlocks.put(seq, pos);
        if (pos2 != null) {
            permanentFakeMiningBlocks.add(pos2);
        }
    }

    public boolean isCurrentlyFakeState(BlockPos pos) {
        return fakeMiningBlocks.containsValue(pos);
    }

    public void addPermanentFakeCompensateState(BlockPos pos) {
        // todo
        // howto: send two packet with same sequence, the first one will be permanent fake state
    }
    // 交互不能产生假方块， see ACK

    public void onBlockACK(Event<PlayerActionResponseS2CPacket> event) {
        if (fakeMiningBlocks.isEmpty()) {
            return;
        }
        int prediction = event.context.sequence();
        for (Iterator<Int2ObjectMap.Entry<BlockPos>> it =
                        fakeMiningBlocks.int2ObjectEntrySet().iterator();
                it.hasNext(); ) {
            var iter = it.next();
            if (iter.getIntKey() <= prediction) {
                it.remove();
            }
        }
    }

    public void onBlockUpdate(Event<BlockUpdateS2CPacket> event) {}

    public void onChunkDeltaUpdate(Event<ChunkDeltaUpdateS2CPacket> event) {}

    public void onChunkUpdate(Event<ChunkPos> chunkUpdate) {}
}
