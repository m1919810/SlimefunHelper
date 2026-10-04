package me.matl114.gui.elements;

import me.matl114.gui.basic.ColorSampler;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public class OutputSlotElement extends SlotElement {

    /**
     * 需要在判定槽外面渲染一层边框
     * @param inventory
     * @param index
     */
    public OutputSlotElement(Inventory inventory, int index) {
        this(inventory, index, ColorSampler.WHITE);
    }

    public OutputSlotElement(Inventory inventory, int index, ColorSampler color) {
        super(inventory, index, color);
    }

    public OutputSlotElement(ItemStack stack) {
        this(stack, ColorSampler.WHITE);
    }

    public OutputSlotElement(ItemStack stack, ColorSampler color) {
        super(stack, color);
    }

    private static final float new_u0 = (float) u0 / 256f;
    private static final float new_u1 = (float) (u0 + uheight) / 256f;
    private static final float new_v0 = (float) v0 / 256f;
    private static final float new_v1 = (float) (v0 + vheight) / 256f;
    private static final int new_i1 = -4;
    private static final int new_i2 = 18 + 8 + new_i1;

    protected void renderSlotFrame(VDrawContext context) {
        context.setShaderColor(this.color.getColorInt());
        try {
            context.drawTexturedQuad(SLOT_RESOURCE, new_i1, new_i2, new_i1, new_i2, 0, new_u0, new_u1, new_v0, new_v1);
        } finally {
            context.setShaderColor(-1);
        }
    }
}
