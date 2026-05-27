package me.matl114.mixins.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.function.Consumer;
import me.matl114.accessors.gui.TextFieldAccess;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.ColorProvider;
import me.matl114.utils.config.PropertyTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextFieldWidget.class)
@Environment(EnvType.CLIENT)
public abstract class TextFieldWidgetMixin extends ClickableWidget implements TextFieldAccess {
    @Unique
    private static final ColorProvider ORIGIN_PROVIDER = McWidgetHelpers.getDefaultTextBoxColorProvider();

    @Final
    @Shadow
    private TextRenderer textRenderer;

    @Shadow
    private String text;

    @Shadow
    private int firstCharacterIndex;

    @Unique
    TextFieldWidget cast() {
        return (TextFieldWidget) (Object) this;
    }

    @Unique
    public boolean isMultiLine() {
        return false;
    }

    @Unique
    public void setBorderColorProvider(ColorProvider provider) {
        this.boxColorProvider = provider;
    }

    @Shadow
    public abstract String getText();

    @Unique
    @Override
    public String getTextContent() {
        return getText();
    }

    @Shadow
    public abstract void setText(String text);

    @Unique
    public void setTextContent(String value) {
        setText(value);
    }

    @Shadow
    public abstract void setChangedListener(Consumer<String> changedListener);

    @Shadow
    private int selectionStart;

    @Shadow
    protected abstract void onChanged(String newText);

    @Unique
    public void setListener(PropertyTracker<TextFieldAccess, String> tracker) {
        setChangedListener((str) -> tracker.valueChange(this, str));
    }

    @Unique
    private ColorProvider boxColorProvider = null;

    public TextFieldWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @WrapOperation(
            method = "renderWidget",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"))
    public void redirectBorderBoxRender(DrawContext instance, Identifier texture, int x, int y, int width, int height) {
        // only override specific widget behaviour
        if (boxColorProvider != null) {
            // use custom color provided
            McWidgetHelpers.drawTextWidgetBox(
                    this, instance, x, y, width, height, this.isFocused(), this.boxColorProvider);
        } else {
            original.call(instance, pipeline, sprite, x, y, width, height);
        }
    }

    @Inject(method = "keyPressed", at = @At(value = "RETURN"), cancellable = true)
    public void fixInventoryKeyPressedWhenFocused(
            int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this.isFocused()
                && MinecraftClient.getInstance().options.inventoryKey.matchesKey(keyCode, scanCode)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    public void dragSelect(int deltaX, int deltaY, boolean shiftDownAction) {
        int i = deltaX;
        if (cast().drawsBackground()) {
            i -= 4;
        }

        String string = this.textRenderer.trimToWidth(
                this.text.substring(this.firstCharacterIndex), this.cast().getInnerWidth());
        this.cast()
                .setCursor(
                        this.textRenderer.trimToWidth(string, i).length() + this.firstCharacterIndex, shiftDownAction);
    }

    @Unique
    public boolean canStartDrag(double mouseX, double mouseY) {
        return this.isMouseOver(mouseX, mouseY);
    }

    @Inject(method = "setFocused", at = @At("HEAD"))
    public void resetSelectOnRelease(boolean focused, CallbackInfo ci) {
        if (!focused) {
            resetSelect();
        }
    }

    @Unique
    public void resetSelect() {
        this.cast().setSelectionEnd(this.selectionStart);
        this.onChanged(this.text);
    }
}
