package me.matl114.accessors.gui;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.slot.Slot;

public interface ScaleSlotAccess extends DepthableContent {
    public ScaleSlotAccess setXYScale(float scale);
    public float getXYScale();
    public ScaleSlotAccess setExtraDepth(int depth);
    public int getExtraDepth();
    public boolean isDefault();
    public void apply(MatrixStack stack);
    static ScaleSlotAccess of(Slot slot){
        return (ScaleSlotAccess) (Slot)(slot);
    }
}
