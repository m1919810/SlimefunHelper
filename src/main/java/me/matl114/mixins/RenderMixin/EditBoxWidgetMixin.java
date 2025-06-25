package me.matl114.mixins.RenderMixin;

import me.matl114.access.TextFieldAccess;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.ColorProvider;
import me.matl114.utils.UtilClass.PropertyTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.EditBox;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
@Mixin(EditBoxWidget.class)
public abstract class EditBoxWidgetMixin extends ScrollableWidget implements TextFieldAccess {
    @Unique
    private static final ColorProvider ORIGIN_PROVIDER = McWidgetHelpers.getDefaultTextBoxColorProvider();
    @Unique
    public boolean isMultiLine(){
        return false;
    }
    @Unique
    public void setBorderColorProvider(ColorProvider provider){
        this.boxColorProvider = provider == null? ORIGIN_PROVIDER: provider;
    }
    @Shadow
    public abstract String getText();
    @Unique
    @Override
    public String getTextContent(){
        return getText();
    }

    @Shadow
    public abstract void setText(String text);

    @Unique
    public void setTextContent(String value){
        setText(value);
    }
    @Shadow
    public abstract void setChangeListener(Consumer<String> changeListener);

    @Shadow @Final private EditBox editBox;

    @Shadow protected abstract void moveCursor(double mouseX, double mouseY);

    @Shadow protected abstract double getDeltaYPerScroll();

    @Unique
    public void setListener(PropertyTracker<TextFieldAccess, String> tracker){
        setChangeListener((str)->tracker.valueChange(this, str));
    }
    @Unique
    @Nonnull
    private ColorProvider boxColorProvider = ORIGIN_PROVIDER;
    public EditBoxWidgetMixin(int i, int j, int k, int l, Text text) {
        super(i, j, k, l, text);
    }
    @Override
    protected void drawBox(DrawContext context, int x, int y, int width, int height){
        McWidgetHelpers.drawTextWidgetBox(this, context, x, y, width, height, this.isFocused(), this.boxColorProvider);
    }

    @Inject(method = "setFocused", at = @At("HEAD"))
    private void resetSelectOnRelease(boolean focused, CallbackInfo ci){
        if(!focused){
            resetSelect();
        }
    }

    @Unique
    public void dragSelect(int deltaX, int deltaY, boolean shiftDownAction){
        if(this.isWithinBounds(deltaX, deltaY)){
            this.editBox.setSelecting(true);
            this.moveCursor(deltaX, deltaY);
            this.editBox.setSelecting(Screen.hasShiftDown());
        }else {
            if(deltaY < this.getY()){
                this.setScrollY(this.getScrollY() - 2.0f * this.getDeltaYPerScroll());
            }else if(deltaY > this.getY() + this.getHeight()){
                this.setScrollY(this.getScrollY() + 2.0f * this.getDeltaYPerScroll());
            }
        }
    }
    @Unique
    public void resetSelect(){
        if(this.editBox.hasSelection()){
            this.editBox.setSelecting(false);
            this.editBox.selectionEnd = this.editBox.getCursor();
        }
    }
}
