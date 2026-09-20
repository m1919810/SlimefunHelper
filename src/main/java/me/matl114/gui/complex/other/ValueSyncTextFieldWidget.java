package me.matl114.gui.complex.other;

import java.util.Objects;
import me.matl114.accessors.gui.TextFieldAccess;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.utils.config.Value;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ValueSyncTextFieldWidget<T> extends TextFieldWidget {
    Value<T> attrKeyValue;
    String lastStoredAttrKeyValue;

    public ValueSyncTextFieldWidget(
            Value<T> attrKeyValue, TextRenderer textRenderer, int x, int y, int width, int height) {
        super(textRenderer, x, y, width, height, Text.empty());
        setMaxLength(32768);
        setText(attrKeyValue.getInput());
        this.attrKeyValue = attrKeyValue;
        setChangedListener(this::syncChanges);
        TextFieldAccess.of(this)
                .setBorderColorProvider(McWidgetHelpers.getWrongRedTextBoxColorProvider(this.attrKeyValue::isValidate));
    }

    public void syncChanges(String valueUpdate) {
        if (Objects.equals(lastStoredAttrKeyValue, attrKeyValue.getInput())) {
            this.attrKeyValue.setInput(valueUpdate);
            String updateValue = attrKeyValue.getInput();
            lastStoredAttrKeyValue = updateValue;
        } else {
            // internal change, update from internal
            lastStoredAttrKeyValue = attrKeyValue.getInput();
            setText(lastStoredAttrKeyValue);
        }
    }

    private void checkAttrKeyValueUpdate() {
        if (!Objects.equals(lastStoredAttrKeyValue, attrKeyValue.getInput())) {
            lastStoredAttrKeyValue = attrKeyValue.getInput();
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
