package me.matl114.mixins.events;

import java.util.Iterator;
import me.matl114.events.RenderListener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(HandledScreen.class)
@Environment(EnvType.CLIENT)
public abstract class HandledScreenEvents {

    @Inject(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V",
                            shift = At.Shift.AFTER))
    public void onRenderBegin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        RenderListener.renderHandledScreen(context, (HandledScreen<?>) (Object) this, mouseX, mouseY, delta);
    }

    @Inject(
            method = "drawSlots",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;)V"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    public void onRenderSlot(DrawContext context, CallbackInfo ci, Iterator var2, Slot slot) {
        RenderListener.renderSlotInScreen(context, (HandledScreen<?>) (Object) this, slot);
    }
}
