package me.matl114.gui.basic;

import net.minecraft.text.OrderedText;

public interface TextProvider {
    public net.minecraft.text.Text getText(DrawableWidget el);

    // DO NO CALL
    default OrderedText getLabel(DrawableWidget element) {
        net.minecraft.text.Text text = getText(element);
        return text == null ? null : text.asOrderedText();
    }

    static TextProvider of(net.minecraft.text.Text text) {
        return ((b) -> text);
    }
}
