package me.matl114.SlimefunMixin;

import me.matl114.Access.HandledScreenAccess;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin implements HandledScreenAccess {
    @Shadow
    protected abstract Slot getSlotAt(double x, double y) ;
    @Shadow
    private Slot touchHoveredSlot;
//    @Inject(method = "mouseClicked",at = @At("HEAD"))
//    public void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
//    }
    @Override
    public Slot reallyGetSlotAt(double var1, double var3) {
        return getSlotAt(var1, var3);
    }
    public Slot getTouchHoveredSlot() {
        return touchHoveredSlot;
    }
}
