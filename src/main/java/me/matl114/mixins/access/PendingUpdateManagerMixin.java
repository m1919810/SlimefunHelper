package me.matl114.mixins.access;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Optional;
import me.matl114.accessors.access.PendingUpdateManagerAccess;
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

    @Unique
    public Optional<BlockState> getPendingBlockState(int sequenceNumber, BlockPos pos) {
        var pending = blockPosToPendingUpdate.get(pos.asLong());
        return (pending == null || pending.sequence < sequenceNumber)
                ? Optional.empty()
                : Optional.of(pending.blockState);
    }
}
