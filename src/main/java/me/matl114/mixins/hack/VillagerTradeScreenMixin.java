package me.matl114.mixins.hack;

import me.matl114.accessors.access.MerchantScreenAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantScreen.class)
@Environment(EnvType.CLIENT)
public abstract class VillagerTradeScreenMixin extends HandledScreen<MerchantScreenHandler> implements MerchantScreenAccess {
    public VillagerTradeScreenMixin(MerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }
    @Shadow
    private int selectedIndex;

    @Shadow protected abstract void syncRecipeIndex();

    @Accessor("selectedIndex")
    public abstract int getSelectedIndex();
    @Unique
    @Override
    public void setSelectedIndex(int index ){
        this.selectedIndex = index;
        syncRecipeIndex();
    }

}
