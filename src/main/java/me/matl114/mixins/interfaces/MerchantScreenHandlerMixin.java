package me.matl114.mixins.interfaces;

import javax.annotation.Nullable;
import me.matl114.accessors.interfaces.EntityInventory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.screen.MerchantScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(MerchantScreenHandler.class)
public abstract class MerchantScreenHandlerMixin implements EntityInventory.Handler<VillagerEntity> {
    @Unique
    VillagerEntity owner;

    @Nullable
    @Override
    @Unique
    public VillagerEntity getOwner() {
        return owner;
    }

    @Override
    public void sync(EntityInventory<VillagerEntity> inventory) {
        this.owner = inventory.getOwner();
    }
}
