package me.matl114.mixins.gui;

import java.util.function.Consumer;
import javax.annotation.Nonnull;
import me.matl114.accessors.gui.TextFieldAccess;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.ColorProvider;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.PropertyTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.EditBox;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.ScrollableTextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(EditBoxWidget.class)
public abstract class EditBoxWidgetMixin extends ScrollableTextFieldWidget implements TextFieldAccess {
    @Unique
    private static final ColorProvider ORIGIN_PROVIDER = McWidgetHelpers.getDefaultTextBoxColorProvider();


    @Unique
    public void setBorderColorProvider(ColorProvider provider) {
        this.boxColorProvider = provider == null ? ORIGIN_PROVIDER : provider;
    }


    @Shadow
    public abstract void setChangeListener(Consumer<String> changeListener);

    @Shadow
    @Final
    private EditBox editBox;

    @Shadow
    protected abstract void moveCursor(double mouseX, double mouseY);

    @Shadow
    protected abstract double getDeltaYPerScroll();

    @Unique
    public void setListener(PropertyTracker<TextFieldAccess, String> tracker) {
        setChangeListener((str) -> tracker.valueChange(this, str));
    }

    @Unique
    @Nonnull
    private ColorProvider boxColorProvider = ORIGIN_PROVIDER;

    public EditBoxWidgetMixin(int i, int j, int k, int l, Text text) {
        super(i, j, k, l, text);
    }
    // override ALL EditBox behaviour
    @Override
    protected void draw(DrawContext context, int x, int y, int width, int height) {
        McWidgetHelpers.drawTextWidgetBox(this, context, x, y, width, height, this.isFocused(), this.boxColorProvider);
    }

    @Inject(method = "setFocused", at = @At("HEAD"))
    private void resetSelectOnRelease(boolean focused, CallbackInfo ci) {
        if (!focused) {
            resetSelect();
        }
    }

    @Inject(method = "keyPressed", at = @At(value = "RETURN"), cancellable = true)
    public void fixInventoryKeyPressedWhenFocused(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (this.isFocused()
                && MinecraftClient.getInstance().options.inventoryKey.matchesKey(input)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    public boolean canStartDrag(double mouseX, double mouseY) {
        return this.isWithinBounds(mouseX, mouseY) || super.scrollbarDragged;
    }

    @Unique
    private boolean isWithinBounds(double x, double y) {
        return x >= (double) this.getX()
                && y >= (double) this.getY()
                && x < (double) this.getRight()
                && y < (double) this.getBottom();
    }

    @Unique
    public void dragSelect(int deltaX, int deltaY, boolean shiftDownAction) {
        //        if(deltaX >= 1 && deltaX <= this.getWidth() -1 && deltaY >= 1 && deltaY <= this.getHeight() -1){

        //        }else {
        // if(deltaY < 0 || deltaY > this.getHeight() || deltaX < 0 || deltaX > this.getWidth()){
        if (!super.scrollbarDragged) {
            this.editBox.setSelecting(true);
            this.moveCursor(this.getX() + deltaX, this.getY() + deltaY);
            this.editBox.setSelecting(ScreenUtils.hasShiftDown());
        }

        //            if(deltaY < 0){
        //                this.setScrollY(this.getScrollY() - 2.0f * this.getDeltaYPerScroll());
        //            }else if(deltaY >  this.getHeight()){
        //                this.setScrollY(this.getScrollY() + 2.0f * this.getDeltaYPerScroll());
        //            }else{
        //
        //            }
        //        }
        // }
    }

    @Unique
    public void resetSelect() {
        if (this.editBox.hasSelection()) {
            this.editBox.setSelecting(false);
            this.editBox.selectionEnd = this.editBox.getCursor();
        }
    }
}
