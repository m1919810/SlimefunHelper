package me.matl114.gui;

import me.matl114.access.TextFieldAccess;
import me.matl114.gui.basic.ColorProvider;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.itemEdit.ItemEditScreen;
import me.matl114.utils.UtilClass.PropertyTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

import java.util.function.BooleanSupplier;

public class McWidgetHelpers {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static DrawableWidget createMultiLineEditBox(int x, int y, int dx, int dy, PropertyTracker<EditBoxWidget, String> valueTracker, String origin){
        EditBoxWidget widget = new EditBoxWidget(mc.textRenderer, x,y, dx, dy, Text.empty(), Text.empty());
        widget.setText(origin);
        widget.setChangeListener((str)-> valueTracker.valueChange(widget, str));
        return new ContentDelegateWidget<>(0,0, 0,0)
            .setContentDelegate(widget);
    }
    public static <T> ContentDelegateWidget<TextFieldWidget> createTextFieldEditBox(int x, int y, int dx, int dy, PropertyTracker<T, String> valueTracker, String origin){
        TextFieldWidget textFieldWidget = new TextFieldWidget(mc.textRenderer, 0,0,dx, dy, Text.empty());
        textFieldWidget.setText(origin);
        textFieldWidget.setChangedListener((str)->valueTracker.valueChange((T)textFieldWidget, str));
        return new ContentDelegateWidget<TextFieldWidget>(x,y,0,0)
            .setContentDelegate(textFieldWidget);
    }

    public static <T> ContentDelegateWidget<TextFieldWidget> createTextFieldEditBox(int x, int y, int dx, int dy, PropertyTracker<T, String> valueTracker, String origin, ColorProvider boxColorProvider){
        TextFieldWidget textFieldWidget = new TextFieldWidget(mc.textRenderer, 0,0,dx, dy, Text.empty());
        textFieldWidget.setText(origin);
        textFieldWidget.setChangedListener((str)->valueTracker.valueChange((T)textFieldWidget, str));
        TextFieldAccess.of(textFieldWidget).setBorderColorProvider(boxColorProvider);
        return new ContentDelegateWidget<TextFieldWidget>(x,y,0,0)
            .setContentDelegate(textFieldWidget);
    }
    private static final ColorProvider TEXT_DEFAULT = (el, fo)->fo? -1:-6250336;
    public static ColorProvider getDefaultTextBoxColorProvider(){
        return TEXT_DEFAULT;
    }

    public static ColorProvider getWrongRedTextBoxColorProvider(BooleanSupplier supplier){
        return (el, fo)->{
            return  supplier.getAsBoolean()? (fo? -1:-6250336) : Colors.RED;
        };
    }
    public static void drawTextWidgetBox(Drawable drawable, DrawContext context, int x, int y, int width, int height, boolean focus, ColorProvider borderColor){
        int i = borderColor.provideTextColor(drawable, focus);
        context.fill(x, y, x + width, y + height, i);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, -16777216);
    }
    public static void drawHighLightBox(DrawContext context, int x, int y, int width, int height, int color){
        context.fill(x, y, x+ 1, y+height, color);
        context.fill(x, y, x +width, y+1, color);
        context.fill(x + width -1, y, x + width, y + height, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
    }
}
