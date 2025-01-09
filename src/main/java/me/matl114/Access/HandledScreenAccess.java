package me.matl114.Access;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public interface HandledScreenAccess {
    public Slot reallyGetSlotAt(double var1,double var3);
    public Slot getTouchHoveredSlot();
    public void setHandler(ScreenHandler var1);
    static HandledScreenAccess of(HandledScreen var0) {
        return (HandledScreenAccess)var0;
    }
    public void updateSharedArgument(String var1,String var2);
}
