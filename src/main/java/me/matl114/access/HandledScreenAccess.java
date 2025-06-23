package me.matl114.access;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Set;

public interface HandledScreenAccess extends ScreenAccess{
    @Nullable
    public Slot reallyGetSlotAt(double var1,double var3);
    default Slot reallyGetSlotWithExtra(double var1, double var3) {
        Slot slot ;
        if((slot =  reallyGetSlotAt(var1, var3)) == null){
            slot = getExtraSlotAt(var1, var3);
        }
        return slot;
    }
    default Slot reallyGetExtraSlotWithOrigin(double var1, double var3){
        Slot slot ;
        if((slot =  getExtraSlotAt(var1, var3)) == null){
            slot = reallyGetSlotAt(var1, var3);
        }
        return slot;
    }
    public boolean isSlotPointed(Slot slot);
    public boolean isSlotPointed(Slot slot, int var1, int var3);
    public Slot getTouchHoveredSlot();
    public void setHandler(ScreenHandler var1);
    static HandledScreenAccess of(HandledScreen var0) {
        return (HandledScreenAccess)var0;
    }
    public void updateSharedArgument(String var1,String var2);
    public TextRenderer getTextRenderer();
    public int getScreenX();
    public int getScreenY();
    public Set<Slot> getExtraSlots();
    public Slot getExtraSlotAt(double var1, double var3);
}
