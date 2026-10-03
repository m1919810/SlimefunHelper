package me.matl114.gui.basic;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public interface TextProvider {
    Text getText(DrawableWidget element);

    // DO NO CALL
    default OrderedText getLabel(DrawableWidget element) {
        Text text = getText(element);
        return text == null ? null : text.asOrderedText();
    }


    default TextAlign getAlign(DrawableWidget element) {
        return TextAlign.MIDDLE;
    }

    static TextProvider of(Text text) {
        return of(element -> text);
    }

    static TextProvider of(Supplier<Text> text) {
        return of(element -> text.get());
    }

    static TextProvider of(Function<DrawableWidget, Text> text) {
        return text::apply;
    }

    static TextProvider of(Text text, TextAlign align) {
        return of(element -> text, align);
    }

    static TextProvider of(Supplier<Text> text, TextAlign align) {
        return of(element -> text.get(), align);
    }

    static TextProvider of(Function<DrawableWidget, Text> text, TextAlign align) {
        return withAlign(of(text), align);
    }

    static TextProvider of(Function<DrawableWidget, Text> text, Function<DrawableWidget, TextAlign> align) {
        TextProvider provider = of(text);
        return new TextProvider() {
            @Override
            public Text getText(DrawableWidget element) {
                return provider.getText(element);
            }

            @Override
            public TextAlign getAlign(DrawableWidget element) {
                return align.apply(element);
            }
        };
    }

    static TextProvider withAlign(TextProvider provider, TextAlign align) {
        if (align == null) {
            return provider;
        }
        return new TextProvider() {
            @Override
            public Text getText(DrawableWidget element) {
                return provider.getText(element);
            }

            @Override
            public TextAlign getAlign(DrawableWidget element) {
                return align;
            }
        };
    }

}
