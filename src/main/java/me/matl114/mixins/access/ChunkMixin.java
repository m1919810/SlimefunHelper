package me.matl114.mixins.access;

import java.util.Map;
import me.matl114.accessors.access.ChunkAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Environment(EnvType.CLIENT)
@Mixin(Chunk.class)
public abstract class ChunkMixin implements ChunkAccess {
    @Shadow
    @Final
    protected Map<BlockPos, BlockEntity> blockEntities;

    @Override
    public Iterable<Map.Entry<BlockPos, BlockEntity>> blockEntityEntries() {
        return blockEntities.entrySet();
    }
}
