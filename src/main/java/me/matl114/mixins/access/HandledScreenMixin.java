package me.matl114.mixins.access;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.accessors.gui.ScaleSlotAccess;
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
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    public boolean isSlotPointed(Slot slot){
        var mouseCoord = ScreenUtils.getMouseCoord(MinecraftClient.getInstance());
        return this.isPointOverSlot(slot, mouseCoord.x, mouseCoord.y);
    }
    @Unique
    public boolean isSlotPointed(Slot slot, int var1, int var3){
        return this.isPointOverSlot(slot,var1, var3);
    }


    @Shadow
    protected int x;
    @Shadow
    protected int y;
    @Accessor("x")
    public abstract int getScreenX();
    @Accessor("y")
    public abstract int getScreenY();
    @Accessor("backgroundWidth")
    public abstract int getScreenBackgroundX();
    @Accessor("backgroundHeight")
    public abstract int getScreenBackgroundY();

    @Shadow
    protected abstract boolean isPointOverSlot(Slot slot, double pointX, double pointY);

    @Shadow
    @Nullable
    protected Slot focusedSlot;


    @Shadow
    public static void drawSlotHighlight(DrawContext context, int x, int y, int z) {
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

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlotHighlight(Lnet/minecraft/client/gui/DrawContext;III)V"))
    private void redirectDrawSlotHighlightScaled(DrawContext context, int x, int y, int z, Operation<Void> original){
        ScaleSlotAccess acc = ScaleSlotAccess.of(this.focusedSlot);
        if(acc != null && !acc.isDefault()){
            drawSlotHightlightScaled(context, x, y, z, acc.getXYScale());
        }else {
            original.call(context, x, y, z);
        }
    }
    private static void drawSlotHightlightScaled(DrawContext context, int x, int y, int z, float scaled){
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + (int)(16* scaled), y + (int)(16* scaled), -2130706433, -2130706433, z);
    }
}
