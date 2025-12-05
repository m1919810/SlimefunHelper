package me.matl114.mixins.HackMixin;

import me.matl114.access.MerchantScreenAccess;
import me.matl114.gui.other.TradeInformationSubScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOfferList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
@Environment(EnvType.CLIENT)
public abstract class VillagerTradeScreenMixin extends HandledScreen<MerchantScreenHandler> implements MerchantScreenAccess {
    public VillagerTradeScreenMixin(MerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }
    @Shadow
    private int selectedIndex;

    @Shadow protected abstract void syncRecipeIndex();

    @Unique
    public int getSelectedIndex(){
        return  selectedIndex;
    }
    @Unique
    @Override
    public void setSelectedIndex(int index ){
        this.selectedIndex = index;
        syncRecipeIndex();
    }
    @Unique
    public TradeOfferList getTradingRecipes(){
        return  getScreenHandler().getRecipes();
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci){
        addDrawableChild(new TradeInformationSubScreen(this.x, this.y,  (MerchantScreen) (Screen)this));
    }
}
