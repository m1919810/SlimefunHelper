package me.matl114.mixins.interfaces;

import me.matl114.accessors.interfaces.MetadataHolder;
import me.matl114.utils.containers.MetaData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntity.class)
@Environment(EnvType.CLIENT)
public abstract class BlockEntityMetadataHolderMixin implements MetadataHolder {
    @Unique
    public MetaData metaData;

    @Unique
    public MetaData getMetadata() {
        if (metaData == null) {
            metaData = new MetaData();
        }
        return metaData;
    }

    @Unique
    @Override
    public boolean isMetaEmpty() {
        return metaData == null;
    }
}
