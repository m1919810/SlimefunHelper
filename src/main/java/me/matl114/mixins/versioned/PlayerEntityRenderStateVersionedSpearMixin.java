package me.matl114.mixins.versioned;

import me.matl114.versioned.accessors.PlayerEntityRendererStateAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
@Environment(EnvType.CLIENT)
public abstract class PlayerEntityRenderStateVersionedSpearMixin implements PlayerEntityRendererStateAccess {
    @Unique
    private Arm spearHand;

    @Unique
    private ItemStack spearItemStack;

    @Unique
    @Override
    public Arm getSpearingHand() {
        return spearHand;
    }

    @Override
    @Unique
    public void setSpearingHand(Arm hand) {
        this.spearHand = hand;
    }

    @Override
    @Unique
    public ItemStack getSpearingItem() {
        return spearItemStack;
    }

    @Override
    @Unique
    public void setSpearingItem(ItemStack stack) {
        spearItemStack = stack;
    }
}
