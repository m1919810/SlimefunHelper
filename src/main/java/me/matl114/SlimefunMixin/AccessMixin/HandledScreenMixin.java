package me.matl114.SlimefunMixin.AccessMixin;

import me.matl114.Access.HandledScreenAccess;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin implements HandledScreenAccess {
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
    public Slot getTouchHoveredSlot() {
        return touchHoveredSlot;
    }
}
