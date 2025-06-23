package me.matl114.mixins.RenderMixin;

import me.matl114.access.TextFieldAccess;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.ColorProvider;
import me.matl114.utils.UtilClass.PropertyTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;

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
}
