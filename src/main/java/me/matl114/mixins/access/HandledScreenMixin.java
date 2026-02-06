package me.matl114.mixins.access;

import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

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


}
