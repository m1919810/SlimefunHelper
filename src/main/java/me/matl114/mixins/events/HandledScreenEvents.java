package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.events.RenderListener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;

@Mixin(HandledScreen.class)
@Environment(EnvType.CLIENT)
public abstract class HandledScreenEvents{

    @Inject(method = "renderMain", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;translate(FF)Lorg/joml/Matrix3x2f;",shift = At.Shift.AFTER))
    public void onRenderBegin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci){
        RenderListener.renderHandledScreen(context, (HandledScreen<?>)(Object) this, mouseX, mouseY, delta);
    }


    @Inject(method = "drawSlots", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V"))
    public void onRenderSlot(DrawContext context, int mouseX, int mouseY, CallbackInfo ci, @Local Slot slot){
        RenderListener.renderSlotInScreen(context, (HandledScreen<?>) (Object)this, slot, mouseX, mouseY);
    }
}
