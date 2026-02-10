package me.matl114.accessors.access;

import net.minecraft.client.gui.screen.ingame.MerchantScreen;

public interface MerchantScreenAccess {
    public int getSelectedIndex();

    public void setSelectedIndex(int k);

    public static MerchantScreenAccess of(MerchantScreen screen) {
        return (MerchantScreenAccess) screen;
    }
}
