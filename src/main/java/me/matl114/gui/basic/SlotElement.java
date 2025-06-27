package me.matl114.gui.basic;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.util.Identifier;

public class SlotElement extends AbstractElement implements ElementHandler {
    final Inventory inventory;
    final int index;
    final SlotClickCallback callback;

    protected static final Identifier SLOT_RESOURCE = new Identifier("slimefunhelper","textures/gui/recipecontainer.png");
    protected static final int u0 = 0;
    protected static final int v0 = 222;
    protected static final int vheight = 18;
    protected static final int uheight = 18;
    public SlotElement(ItemStack itemStack){
        this(new SimpleInventory(itemStack),0);
    }
    public static SlotElement instance(ItemStack itemStack){
        return new SlotElement(itemStack);
    }
    public static SlotElement instance(ItemStack itemStack, SlotClickCallback handle){
        return new SlotElement(itemStack, handle);
    }
    public SlotElement(ItemStack itemStack, SlotClickCallback callback){
        this(new SimpleInventory(itemStack),0, callback);
    }

    public SlotElement(Inventory inventory, int index){
        this(inventory, index, SlotClickCallback.DEFAULT);
    }
    public SlotElement(Inventory inventory, int index,SlotClickCallback callback){
        this.inventory = inventory;
        this.index = index;
        this.callback = callback;
    }
    protected void renderSlotFrame(DrawContext context){
        context.drawTexture(SLOT_RESOURCE, 0,0, u0, v0, uheight, vheight);
    }
    @Override
    public void renderCentered0(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
        //render slot here
        context.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        float scalerX = (float) element.getTextureWidth() / uheight;
        float scalerY = (float) element.getTextureHeight() / vheight;
        context.getMatrices().push();
        context.getMatrices().scale(scalerX, scalerY, 1);
        //use matrices because item will be rendered later
        if(slotFrame){
            renderSlotFrame(context);
        }
        context.setShaderColor( 1.0F, 1.0F, 1.0F, 1.0F);
        ItemStack stack = inventory.getStack(index);
        if(!stack.isEmpty()){
            context.getMatrices().push();
            context.getMatrices().translate(0,0,100);
            context.drawItem(stack, 1, 1, 114514);
            context.drawItemInSlot(mc.textRenderer, stack, 1, 1, null);
            context.getMatrices().pop();
        }
        if(shouldHighlight){
            context.fillGradient(RenderLayer.getGuiOverlay(), 1, 1, 1 + 16, 1 + 16, -2130706433, -2130706433, 0);
        }
        context.getMatrices().pop();

    }
    private boolean slotFrame = true;
    public SlotElement setSlotFrame(boolean slot){
        this.slotFrame = slot;
        return this;
    }
    private boolean tooltips = true;
    public AbstractElement withTooltips(TooltipHandler handler){
        if(handler ==null)return this;
        tooltips = false;
        return super.withTooltips(handler);
    }
    @Override
    public void renderExtra0(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
        //draw tooltips here;
        super.renderExtra0(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
        if(shouldHighlight && tooltips){
            ItemStack stack = inventory.getStack(index);
            if(!stack.isEmpty()){
                context.drawTooltip(mc.textRenderer, stack.getTooltip(Item.TooltipContext.create(mc.world), mc.player, TooltipType.ADVANCED), stack.getTooltipData(), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        return callback.handle(inventory.getStack(index), button);
    }

    public static interface SlotClickCallback{
        SlotClickCallback DEFAULT = (i,b)->false;
        public boolean handle(ItemStack item, int button);
    }

}
