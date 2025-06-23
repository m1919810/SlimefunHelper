package me.matl114.mixins.AccessMixin;

import me.matl114.access.HandledScreenAccess;
import me.matl114.access.ScaleSlotAccess;
import me.matl114.renders.RenderMain;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import oshi.util.tuples.Pair;

import java.util.*;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen implements HandledScreenAccess {
    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Shadow
    protected abstract Slot getSlotAt(double x, double y) ;
    @Final
    @Mutable
    @Shadow
    protected ScreenHandler handler;
    public void setHandler(ScreenHandler handler) {
        this.handler = handler;
    }
    @Shadow
    private Slot touchHoveredSlot;
//    @Inject(method = "mouseClicked",at = @At("HEAD"))
//    public void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
//    }
    @Override
    public Slot reallyGetSlotAt(double var1, double var3) {
        return getSlotAt(var1, var3);
    }
    @Unique
    public Slot getExtraSlotAt(double var1, double var3){
        for(Slot slot: this.extraSlots) {
            if (this.isPointOverSlot(slot, var1, var3) && slot.isEnabled()) {
                return slot;
            }
        }
        return null;
    }

    @Unique
    public boolean isSlotPointed(Slot slot){
        var mouseCoord = ScreenUtils.getMouseCoord(MinecraftClient.getInstance());
        return this.isPointOverSlot(slot, mouseCoord.x, mouseCoord.y);
    }
    @Unique
    public boolean isSlotPointed(Slot slot, int var1, int var3){
        return this.isPointOverSlot(slot,var1, var3);
    }


    public Slot getTouchHoveredSlot() {
        return touchHoveredSlot;
    }

    private Set<Slot> extraSlots = new LinkedHashSet<>(5);
    public Set<Slot> getExtraSlots(){
        return extraSlots;
    }
    @Shadow
    protected int x;
    @Shadow
    protected int y;
    public int getScreenX(){
        return this.x;
    }
    public int getScreenY(){
        return this.y;
    }
    public TextRenderer getTextRenderer(){
        return this.textRenderer;
    }
   // @Inject(method = "drawMouseoverTooltip",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;II)V", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    public void drawTooptipPre(DrawContext context, int x, int y, CallbackInfo ci, ItemStack itemStack){
        //RenderMain.renderItemTooltipsTasks(context, this.textRenderer, itemStack, x, y);
    }
    @Shadow
    protected abstract void drawSlot(DrawContext context, Slot slot);

    @Shadow
    protected abstract boolean isPointOverSlot(Slot slot, double pointX, double pointY);

    @Shadow @Nullable protected Slot focusedSlot;

    @Shadow
    public static void drawSlotHighlight(DrawContext context, int x, int y, int z) {
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;)V"),locals = LocalCapture.CAPTURE_FAILHARD)
    public void onRenderSlot(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci, int i, int j, int k, Slot slot){
        RenderMain.renderSlotInScreen(context, (HandledScreen<?>) (Object)this, slot, mouseX, mouseY);
    }

    @Redirect(method = "drawSlot", at = @At(value = "FIELD", target = "Lnet/minecraft/screen/slot/Slot;x:I"))
    public int onRedirectSlotX(Slot instance){
        ScaleSlotAccess access = ScaleSlotAccess.of(instance);
        float scale = access.getXYScale();
        if(scale != 1.0f){
            return (int) (instance.x / scale);
        }
        return instance.x;
    }
    @Redirect(method = "drawSlot", at = @At(value = "FIELD", target = "Lnet/minecraft/screen/slot/Slot;y:I"))
    public int onRedirectSlotY(Slot instance){
        ScaleSlotAccess access = ScaleSlotAccess.of(instance);
        float scale = access.getXYScale();
        if(scale != 1.0f){
            return (int) (instance.y / scale);
        }
        return instance.y;
    }
    @Inject(method = "drawSlot",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V", shift = At.Shift.AFTER))
    public void onScaleSlot(DrawContext context, Slot slot, CallbackInfo ci){
        ScaleSlotAccess access = ScaleSlotAccess.of(slot);
        if(!access.isDefault()){
            access.apply(context.getMatrices());
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V",shift = At.Shift.AFTER))
    public void onRenderBegin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci){
        //inject after MatrixStack translate to the screen's edge
        this.focusedSlot = null;
        RenderMain.renderHandledScreen(context, (HandledScreen<?>)(Object) this, mouseX, mouseY, delta);
    }
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawForeground(Lnet/minecraft/client/gui/DrawContext;II)V",shift = At.Shift.BEFORE))
    public void onRenderMySlot(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci){
        Set<Slot> extras = new LinkedHashSet<>(this.extraSlots);
        for (Slot slotToRender: extras){
            if (slotToRender.isEnabled()) {
                this.drawSlot(context, slotToRender);
            }

            if (this.isPointOverSlot(slotToRender, (double)mouseX, (double)mouseY) && slotToRender.isEnabled() && slotToRender.canBeHighlighted()) {
                this.focusedSlot = slotToRender;
                //scaling
                ScaleSlotAccess acc = ScaleSlotAccess.of(this.focusedSlot);
                if(acc != null && !acc.isDefault()){
                    drawSlotHightlightScaled(context, slotToRender.x, slotToRender.y, 0, acc.getXYScale());
                }else {
                    drawSlotHighlight(context, slotToRender.x, slotToRender.y, 0);
                }
            }
        }
    }
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlotHighlight(Lnet/minecraft/client/gui/DrawContext;III)V"))
    private void redirectDrawSlotHighlightScaled(DrawContext context, int x, int y, int z){
        ScaleSlotAccess acc = ScaleSlotAccess.of(this.focusedSlot);
        if(acc != null && !acc.isDefault()){
            drawSlotHightlightScaled(context, x, y, z, acc.getXYScale());
        }else {
            drawSlotHighlight(context, x, y, z);
        }
    }
    private static void drawSlotHightlightScaled(DrawContext context, int x, int y, int z, float scaled){
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + (int)(16* scaled), y + (int)(16* scaled), -2130706433, -2130706433, z);
    }
    @Inject(method = "drawSlotHighlight", at = @At("HEAD"))
    private static void onRenderGlowHere(DrawContext context, int x, int y, int z, CallbackInfo ci){
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + 16, y + 16, -2130706433, -2130706433, z);
    }
}
