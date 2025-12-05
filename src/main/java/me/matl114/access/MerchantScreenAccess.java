package me.matl114.access;

import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.village.TradeOfferList;

public interface MerchantScreenAccess {
    public int getSelectedIndex();

    public TradeOfferList getTradingRecipes();

    public void setSelectedIndex(int k);

    public static MerchantScreenAccess of(MerchantScreen screen){
        return (MerchantScreenAccess) screen;
    }
}
