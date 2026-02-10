package me.matl114.accessors.access;

import javax.annotation.Nullable;
import me.matl114.accessors.gui.ScreenAccess;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public interface HandledScreenAccess extends ScreenAccess {
    @Nullable
    public Slot reallyGetSlotAt(double var1, double var3);

    public boolean isSlotPointed(Slot slot);

    public boolean isSlotPointed(Slot slot, int var1, int var3);

    public Slot getTouchHoveredSlot();

    public void setHandler(ScreenHandler var1);

    static HandledScreenAccess of(HandledScreen var0) {
        return (HandledScreenAccess) var0;
    }

    public void updateSharedArgument(String var1, String var2);

    public TextRenderer getTextRenderer();

    public int getScreenX();

    public int getScreenY();

    public int getScreenBackgroundX();

    public int getScreenBackgroundY();
}
