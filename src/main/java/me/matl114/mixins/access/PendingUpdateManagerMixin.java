package me.matl114.mixins.access;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import me.matl114.accessors.access.PendingUpdateManagerAccess;
import me.matl114.utils.collections.IndexEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(PendingUpdateManager.class)
public abstract class PendingUpdateManagerMixin implements PendingUpdateManagerAccess {

    @Shadow
    @Final
    private Long2ObjectOpenHashMap<PendingUpdateManager.PendingUpdate> blockPosToPendingUpdate;

    @Shadow
    private int sequence;

    @Shadow
    private boolean pendingSequence;

    @Unique
    public Optional<BlockState> getPendingBlockState(int sequenceNumber, BlockPos pos) {
        var pending = blockPosToPendingUpdate.get(pos.asLong());
        return (pending == null || pending.sequence < sequenceNumber)
                ? Optional.empty()
                : Optional.of(pending.blockState);
    }

    @Unique
    public Map<BlockPos, IndexEntry<BlockState>> getPendingBlockStates(int sequenceNumber) {
        Map<BlockPos, IndexEntry<BlockState>> states = new HashMap<>();
        for (var entry : blockPosToPendingUpdate.long2ObjectEntrySet()) {
            var pending = entry.getValue();
            if (pending.sequence >= sequenceNumber) {
                states.put(
                        BlockPos.fromLong(entry.getLongKey()), new IndexEntry<>(pending.sequence, pending.blockState));
            }
        }
        return states;
    }

    @Unique
    public void restorePendingUpdate(int seq, Map<BlockPos, IndexEntry<BlockState>> states) {
        var iterator = blockPosToPendingUpdate.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var pending = entry.getValue();
            IndexEntry<BlockState> original = states.get(BlockPos.fromLong(entry.getLongKey()));
            if (original != null) {
                pending.sequence = original.index();
                pending.blockState = original.val();
            } else if (pending.sequence >= seq) {
                iterator.remove();
            }
        }
    }

    @Unique
    public void restorePredictionSequence(int sequence, boolean pendingSequence) {
        this.sequence = sequence;
        this.pendingSequence = pendingSequence;
    }
}
