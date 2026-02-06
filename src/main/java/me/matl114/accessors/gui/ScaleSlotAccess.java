package me.matl114.accessors.gui;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.slot.Slot;

public interface ScaleSlotAccess {
    public ScaleSlotAccess setXYScale(float scale);
    public float getXYScale();
    public boolean isDefault();
    public void apply(MatrixStack stack);
    static ScaleSlotAccess of(Slot slot){
        return (ScaleSlotAccess) (Slot)(slot);
    }
}
