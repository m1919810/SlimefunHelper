package me.matl114.mixins.access;

import me.matl114.accessors.access.HandledScreenAccess;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
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
    protected abstract Slot getSlotAt(double x, double y);

    @Override
    @Unique
    public Slot reallyGetSlotAt(double var1, double var3) {
        return getSlotAt(var1, var3);
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
}
