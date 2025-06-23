package me.matl114.mixins.HackMixin;

import me.matl114.access.SlimefunMultiBlockHolder;
import me.matl114.hackUtils.SlimefunTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.Collection;

@Mixin(DispenserBlockEntity.class)
@Environment(EnvType.CLIENT)
public abstract class DispenserTileEntityMixin extends LootableContainerBlockEntity implements SlimefunMultiBlockHolder {
    protected DispenserTileEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Unique
    public Collection<SlimefunTasks.MultiBlockWithLocation> getOptionalMultiBlocks(){
        return SlimefunTasks.getOptionalMultiBlocks(this.world, this.pos);
    }

    //left as not done
}
