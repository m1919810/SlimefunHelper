package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Iterator;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.RenderSlot;
import me.matl114.utils.collections.Point;
import me.matl114.versioned.api.VDrawContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(HandledScreen.class)
@Environment(EnvType.CLIENT)
public abstract class HandledScreenEvents extends Screen {

    protected HandledScreenEvents(Text title) {
        super(title);
    }

    @Inject(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V",
                            shift = At.Shift.AFTER))
    public void onRenderBegin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // RenderListener.renderHandledScreen(context, (HandledScreen<?>) (Object) this, mouseX, mouseY, delta);
    }
    @Unique
    Point cachedMouseXY;
    @Inject(method = "render", at = @At("HEAD"))
    private void onCacheMouseXY(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        cachedMouseXY = new Point(mouseX, mouseY);
    }

    @WrapOperation(
            method = "drawSlots",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;)V"))
    public void onRenderSlot(
        HandledScreen instance, DrawContext context, Slot slot, Operation<Void> original) {
        if (cachedMouseXY != null && !RenderListener.getRenderSlot()
            .fireEvent(new RenderSlot(VDrawContext.of(context), instance, slot, cachedMouseXY.x, cachedMouseXY.y))) {
            return;
        }
        original.call(instance, context, slot);
    }

    // fix mouse scroll dispatch
    //    @Inject(method = "mouseScrolled", at = @At("RETURN"), cancellable = true)
    //    public void onMouseScrolled(
    //            double mouseX,
    //            double mouseY,
    //            double horizontalAmount,
    //            double verticalAmount,
    //            CallbackInfoReturnable<Boolean> cir) {
    //        if (cir.getReturnValueZ()) {
    //            return;
    //        }
    //        cir.setReturnValue(super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount));
    //    }
}
