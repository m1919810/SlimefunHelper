package me.matl114.hacks.modules.interact;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.Getter;
import me.matl114.accessors.access.PendingUpdateManagerAccess;
import me.matl114.accessors.access.PlayerInteractBlockC2SPacketAccess;
import me.matl114.accessors.access.PlayerInteractItemC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.channels.EventChannelDispatcher;
import me.matl114.events.impl.BlockBreak;
import me.matl114.events.impl.SequencedAction;
import me.matl114.events.impl.UseItem;
import me.matl114.events.impl.UseItemOnBlock;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Tasks;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public class SequencedActionManager extends BaseModule {
    public static SequencedActionManager INSTANCE;

    public SequencedActionManager() {
        super("SequencedActionManager");
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerRespawnPoint(), this::onPlayerRespawn);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractBlockC2SPacket.class),
                this::onInteractBlock,
                Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractItemC2SPacket.class),
                this::onInteractItem,
                Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class),
                this::onBlockBreak,
                Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(PlayerActionResponseS2CPacket.class), this::onAck);
    }

    int maxSeq = 0;
    Deque<IndexEntry<UseItemOnBlock>> blockPlaceSequences = new ArrayDeque<>(8);
    Deque<IndexEntry<UseItem>> itemUsageSequences = new ArrayDeque<>(8);
    Deque<IndexEntry<BlockBreak>> blockBreakSequences = new ArrayDeque<>(8);
    public IndexEntry<UseItemOnBlock> lastBlockPlace;
    public int lastBlockPlaceTick;
    public IndexEntry<UseItem> lastItemUse;
    public int lastItemUseTick;

    @Broadcast
    @Getter
    public static EventChannelDispatcher<SequencedAction> sequencedActionResponse =
            new EventChannelDispatcher<>(SequencedAction::getClass);

    private void onPlayerRespawn(Event<ClientPlayerEntity> playerEntityEvent) {
        maxSeq = 0;
        blockPlaceSequences.forEach(s -> sequencedActionResponse.broadcast(s.val()));
        itemUsageSequences.forEach(s -> sequencedActionResponse.broadcast(s.val()));
        blockBreakSequences.forEach(s -> sequencedActionResponse.broadcast(s.val()));
        blockPlaceSequences.clear();
        itemUsageSequences.clear();
        blockBreakSequences.clear();
        lastBlockPlace = null;
        lastItemUse = null;
    }

    private void onInteractBlock(Event<PlayerInteractBlockC2SPacket> event) {
        if (event.isCancelled()) return;
        // ignore fake sequence
        if (event.context.getSequence() > mc.world.getPendingUpdateManager().sequence + 20) {
            return;
        }
        maxSeq = Math.max(maxSeq, event.context.getSequence());
        var context = PlayerInteractBlockC2SPacketAccess.of(event.context).getUseContext();
        var stack = context != null && context.stack() != null
                ? context.stack()
                : mc.player.getStackInHand(event.context.getHand());
        blockPlaceSequences.addLast(
                lastBlockPlace = new IndexEntry<>(
                        event.context.getSequence(),
                        new UseItemOnBlock(
                                event.context.getBlockHitResult(),
                                ActionResult.SUCCESS,
                                context != null ? context.placePos() : Optional.empty(),
                                event.context.getHand(),
                                stack)));
        lastBlockPlaceTick = Tasks.getTick();
    }

    private void onInteractItem(Event<PlayerInteractItemC2SPacket> event) {
        if (event.isCancelled()) return;
        // ignore fake sequence
        if (event.context.getSequence() > mc.world.getPendingUpdateManager().sequence + 20) {
            return;
        }
        maxSeq = Math.max(maxSeq, event.context.getSequence());
        ItemStack stack = PlayerInteractItemC2SPacketAccess.of(event.context).getItemStack();
        itemUsageSequences.add(
                lastItemUse = new IndexEntry<>(
                        event.context.getSequence(),
                        new UseItem(ActionResult.SUCCESS, event.context.getHand(), stack)));
        lastItemUseTick = Tasks.getTick();
    }

    private void onBlockBreak(Event<PlayerActionC2SPacket> event) {
        if (event.isCancelled()) return;
        if (event.context.getSequence() > mc.world.getPendingUpdateManager().sequence + 20) {
            return;
        }
        // not sequenced packet
        if (event.context.getSequence() == 0) return;
        if (event.context.getAction() != PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                && event.context.getAction() != PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
            return;
        }
        maxSeq = Math.max(maxSeq, event.context.getSequence());
        ItemStack stack = mc.player.getStackInHand(Hand.MAIN_HAND);
        blockBreakSequences.add(new IndexEntry<>(
                event.context.getSequence(),
                new BlockBreak(
                        event.context.getPos(),
                        event.context.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                                ? BlockBreak.Stage.PRE
                                : BlockBreak.Stage.POST,
                        stack.copy(),
                        1.0F)));
    }

    private <W extends SequencedAction> void updateACK(Deque<IndexEntry<W>> ackList, int ack) {
        IndexEntry<W> val;
        while ((val = ackList.peek()) != null && val.index() <= ack) {
            var pollOut = ackList.pollFirst();
            if (pollOut != null) {
                sequencedActionResponse.broadcast(pollOut.val());
            }
        }
        if (!ackList.isEmpty()) {
            var iter = ackList.iterator();
            while (iter.hasNext()) {
                var s = iter.next();
                if (s.index() <= ack) {
                    iter.remove();
                    sequencedActionResponse.broadcast(s.val());
                }
            }
        }
    }

    public boolean isWaitingBlockResponse(BlockPos bp) {
        return blockPlaceSequences.stream()
                .anyMatch(s -> Objects.equals(s.val().hitResult().getBlockPos(), bp));
    }

    public boolean isWaitingBlockResponse(BlockPos bp, Predicate<ItemStack> placeBlock) {
        return blockPlaceSequences.stream()
                .anyMatch(s -> Objects.equals(s.val().hitResult().getBlockPos(), bp)
                        && placeBlock.test(s.val().handItem()));
    }

    public boolean isWaitingBlockUseOnResponse(Predicate<UseItemOnBlock> bp) {
        return blockPlaceSequences.stream().anyMatch(s -> bp.test(s.val()));
    }

    public boolean isWaitingItemResponse(Predicate<ItemStack> stack) {
        return itemUsageSequences.stream().anyMatch(s -> stack.test(s.val().handItem()));
    }

    public boolean isWaitingItemUseResponse(Predicate<UseItem> bp) {
        return itemUsageSequences.stream().anyMatch(s -> bp.test(s.val()));
    }

    public boolean isWaitingBreakResponse(Predicate<BlockPos> bp) {
        return blockBreakSequences.stream().anyMatch(s -> bp.test(s.val().blockPos()));
    }

    public boolean isWaitingBlockBreakResponse(Predicate<BlockBreak> bp) {
        return blockBreakSequences.stream().anyMatch(s -> bp.test(s.val()));
    }

    public Stream<UseItemOnBlock> getCurrentPendingBlockPlace() {
        return blockPlaceSequences.stream().map(IndexEntry::val);
    }

    public void onAck(Event<PlayerActionResponseS2CPacket> event) {
        int response = event.context.sequence();
        updateACK(itemUsageSequences, response);
        updateACK(blockPlaceSequences, response);
        updateACK(blockBreakSequences, response);
    }

    public boolean appendBlockBreakPrediction(BlockPos pos, BlockState state) {
        if (mc.world.getPendingUpdateManager().hasPendingSequence()) {
            return mc.world.setBlockState(pos, state, 11, 512);
        } else {
            BlockState blockState = mc.world.getBlockState(pos);
            boolean bl = mc.world.setBlockState(pos, state, 11, 512);
            if (bl) {
                mc.world.getPendingUpdateManager().addPendingUpdate(pos, blockState, mc.player);
            }
            return bl;
        }
    }

    public Optional<BlockState> getBeforeBreakPredictionState(BlockPos pos) {
        var latestAction = blockBreakSequences.stream()
                .filter(s -> Objects.equals(s.val().blockPos(), pos))
                .max(Comparator.comparingInt(IndexEntry::index))
                .orElse(null);
        if (latestAction != null) {
            return PendingUpdateManagerAccess.of(mc.world.getPendingUpdateManager())
                    .getPendingBlockState(latestAction.index(), pos);
        } else {
            return Optional.empty();
        }
    }
}
