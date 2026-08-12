package me.matl114.gui;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import me.matl114.accessors.gui.TextFieldAccess;
import me.matl114.gui.basic.ColorProvider;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.PropertyTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

public class McWidgetHelpers {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static ContentDelegateWidget<EditBoxWidget> createMultiLineEditBox(
            int x, int y, int dx, int dy, PropertyTracker<EditBoxWidget, String> valueTracker, String origin) {
        return createEnhancedMultiLine(x, y, dx, dy, valueTracker, origin, null);
        //        EditBoxWidget widget = new EditBoxWidget(mc.textRenderer, x,y, dx, dy, Text.empty(), Text.empty());
        //        widget.setText(origin);
        //        widget.setChangeListener((str)-> valueTracker.valueChange(widget, str));
        //        return new ContentDelegateWidget<>(0,0, 0,0)
        //            .setContentDelegate(widget);
    }

    public static ContentDelegateWidget<EditBoxWidget> createMultiLineEditBox(
            int x,
            int y,
            int dx,
            int dy,
            PropertyTracker<EditBoxWidget, String> valueTracker,
            String origin,
            ColorProvider boxColorProvider) {
        return createEnhancedMultiLine(x, y, dx, dy, valueTracker, origin, boxColorProvider);
        //        EditBoxWidget widget = new EditBoxWidget(mc.textRenderer, x,y, dx, dy, Text.empty(), Text.empty());
        //        widget.setText(origin);
        //        widget.setChangeListener((str)-> valueTracker.valueChange(widget, str));
        //        return new ContentDelegateWidget<>(0,0, 0,0)
        //            .setContentDelegate(widget);
    }

    public static <T> ContentDelegateWidget<TextFieldWidget> createTextFieldEditBox(
            int x, int y, int dx, int dy, PropertyTracker<T, String> valueTracker, String origin) {
        if (true) return createEnhancedTextBox(x, y, dx, dy, valueTracker, origin, null);
        TextFieldWidget textFieldWidget = new TextFieldWidget(mc.textRenderer, 0, 0, dx, dy, Text.empty());
        textFieldWidget.setMaxLength(32768);
        textFieldWidget.setText(origin);
        textFieldWidget.setChangedListener((str) -> valueTracker.valueChange((T) textFieldWidget, str));
        return new ContentDelegateWidget<TextFieldWidget>(x, y, 0, 0).setContentDelegate(textFieldWidget);
    }

    public static <T> ContentDelegateWidget<TextFieldWidget> createTextFieldEditBox(
            int x,
            int y,
            int dx,
            int dy,
            PropertyTracker<T, String> valueTracker,
            String origin,
            ColorProvider boxColorProvider) {
        if (true) return createEnhancedTextBox(x, y, dx, dy, valueTracker, origin, boxColorProvider);
        TextFieldWidget textFieldWidget = new TextFieldWidget(mc.textRenderer, 0, 0, dx, dy, Text.empty());
        textFieldWidget.setMaxLength(32768);
        textFieldWidget.setText(origin);
        textFieldWidget.setChangedListener((str) -> valueTracker.valueChange((T) textFieldWidget, str));
        TextFieldAccess.of(textFieldWidget).setBorderColorProvider(boxColorProvider);
        return new ContentDelegateWidget<TextFieldWidget>(x, y, 0, 0).setContentDelegate(textFieldWidget);
    }

    public static <T> ContentDelegateWidget<TextFieldWidget> createAttrValueEditBox(
            AttrKeyValue<T> attrKeyValue, int x, int y, int dx, int dy) {
        return new TextContentDelegateWidget<>(
                x, y, new AttrKeyValueTextFieldWidget<>(attrKeyValue, mc.textRenderer, 0, 0, dx, dy));
    }

    private static final ColorProvider TEXT_DEFAULT = (el, fo) -> fo ? -1 : -6250336;

    public static ColorProvider getDefaultTextBoxColorProvider() {
        return TEXT_DEFAULT;
    }

    public static ColorProvider getWrongRedTextBoxColorProvider(BooleanSupplier supplier) {
        return (el, fo) -> {
            return supplier.getAsBoolean() ? (fo ? -1 : -6250336) : Colors.RED;
        };
    }

    public static void drawTextWidgetBox(
            Drawable drawable,
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            boolean focus,
            ColorProvider borderColor) {
        Integer i = borderColor.provideTextColor(drawable, focus);
        if (i != null) {
            context.fill(x, y, x + width, y + height, i);
        }
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, -16777216);
    }

    public static <T> ContentDelegateWidget<TextFieldWidget> createEnhancedTextBox(
            int x,
            int y,
            int dx,
            int dy,
            PropertyTracker<T, String> valueTracker,
            String origin,
            ColorProvider boxColorProvider) {
        TextFieldWidget textFieldWidget = new TextFieldWidget(mc.textRenderer, 0, 0, dx, dy, Text.empty());
        textFieldWidget.setMaxLength(32768);
        textFieldWidget.setText(origin);
        textFieldWidget.setChangedListener((str) -> valueTracker.valueChange((T) textFieldWidget, str));
        if (boxColorProvider != null) TextFieldAccess.of(textFieldWidget).setBorderColorProvider(boxColorProvider);
        return new TextContentDelegateWidget<>(x, y, textFieldWidget);
    }

    public static <T> ContentDelegateWidget<EditBoxWidget> createEnhancedMultiLine(
            int x,
            int y,
            int dx,
            int dy,
            PropertyTracker<EditBoxWidget, String> valueTracker,
            String origin,
            ColorProvider boxColorProvider) {
        EditBoxWidget widget = EditBoxWidget.builder()
                .x(x)
                .y(y)
                .placeholder(Text.empty())
                .build(mc.textRenderer, dx, dy, Text.empty());
        widget.setText(origin);
        widget.setChangeListener((str) -> valueTracker.valueChange(widget, str));
        if (boxColorProvider != null) {
            TextFieldAccess.of(widget).setBorderColorProvider(boxColorProvider);
        }
        return new TextContentDelegateWidget<>(0, 0, widget);
    }

    public static class TextContentDelegateWidget<T extends ClickableWidget> extends ContentDelegateWidget<T> {

        public TextContentDelegateWidget(int x, int y, T widget) {
            super(x, y, 0, 0);
            this.setContentDelegate(widget);
        }

        boolean startDrag = false;

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (this.startDrag && this.getDelegate() != null) {
                var delegate = this.getDelegate();
                // 设置cursor位置
                float textureScale = getTextureScale();
                TextFieldAccess.of(delegate)
                        .dragSelect(
                                (int) ((mouseX - this.getX() - delegate.getX()) / textureScale),
                                (int) ((mouseY - this.getY() - delegate.getY()) / textureScale),
                                true);
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public boolean startDrag(Screen screen, double mouseX, double mouseY) {
            if (getDelegate() != null
                    && TextFieldAccess.of(getDelegate()).canStartDrag(mouseX - this.getX(), mouseY - this.getY())) {
                this.startDrag = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean isDragging() {
            return this.getDelegate() != null && this.startDrag;
        }

        @Override
        public void releaseDrag(Screen screen, double mouseX, double mouseY) {
            this.startDrag = false;
            //
        }
    }

    public static class AttrKeyValueTextFieldWidget<T> extends TextFieldWidget {
        AttrKeyValue<T> attrKeyValue;
        String lastStoredAttrKeyValue;

        public AttrKeyValueTextFieldWidget(
                AttrKeyValue<T> attrKeyValue, TextRenderer textRenderer, int x, int y, int width, int height) {
            super(textRenderer, x, y, width, height, Text.empty());
            setMaxLength(32768);
            setText(attrKeyValue.getValue());
            this.attrKeyValue = attrKeyValue;
            setChangedListener(this::syncChanges);
            TextFieldAccess.of(this)
                    .setBorderColorProvider(getWrongRedTextBoxColorProvider(this.attrKeyValue::isValidate));
        }

        public void syncChanges(String valueUpdate) {
            if (Objects.equals(lastStoredAttrKeyValue, attrKeyValue.getValue())) {
                this.attrKeyValue.valueChange(this, valueUpdate);
                String updateValue = attrKeyValue.getValue();
                if (Objects.equals(getText(), updateValue)) {
                    lastStoredAttrKeyValue = updateValue;
                } else {
                    setText(updateValue);
                }
            } else {
                // internal change, update from internal
                lastStoredAttrKeyValue = attrKeyValue.getValue();
                setText(lastStoredAttrKeyValue);
            }
        }

        private void checkAttrKeyValueUpdate() {
            if (!Objects.equals(lastStoredAttrKeyValue, attrKeyValue.getValue())) {
                lastStoredAttrKeyValue = attrKeyValue.getValue();
                setText(lastStoredAttrKeyValue);
            }
        }

        public String getText() {
            checkAttrKeyValueUpdate();
            return super.getText();
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            checkAttrKeyValueUpdate();
            super.renderWidget(context, mouseX, mouseY, deltaTicks);
        }
    }
}
