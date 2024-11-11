package me.matl114.Access;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

public interface HandledScreenAccess {
    public Slot reallyGetSlotAt(double var1,double var3);
    public Slot getTouchHoveredSlot();
    static HandledScreenAccess of(HandledScreen var0) {
        return (HandledScreenAccess)var0;
    }
}
