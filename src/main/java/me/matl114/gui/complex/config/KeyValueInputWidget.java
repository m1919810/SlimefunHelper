package me.matl114.gui.complex.config;

import java.util.List;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.text.Text;

public class KeyValueInputWidget<T> extends SubScreenWidget {
    AttrKeyValue<T> keyValueHolder;
    int dkey;
    int dblank;
    int dvalue;

    public KeyValueInputWidget(int x, int y, int dx, int dy, int dKey, AttrKeyValue<T> kv) {
        this(x, y, dx, dy, dKey, 0, dx - dKey, kv);
    }

    public KeyValueInputWidget(int x, int y, int dx, int dy, int dKey, int dblank, int dvalue, AttrKeyValue<T> kv) {
        super(x, y, dx, dy);
        this.dkey = dKey;
        this.dblank = dblank;
        this.dvalue = dvalue;
        this.keyValueHolder = kv;
        init();
    }

    List<Text> cachedTooltips;

    public KeyValueInputWidget<T> setTooltips(List<Text> tooltips) {
        this.cachedTooltips = tooltips;
        return this;
    }

    public List<Text> getTooltips() {
        if (cachedTooltips == null) {
            cachedTooltips = ChatUtils.parseTooltipsTranslation(this.keyValueHolder.getKeyName() + ".tooltips", "暂无介绍");
        }
        return cachedTooltips;
    }

    DisplayWidget keyLabel;
    DrawableWidget interactPlace;

    protected void valueChange() {}

    protected void init() {
        String key = this.keyValueHolder.getKeyName();
        ElementHandler button =
                new ButtonElement(TextProvider.of(Text.translatableWithFallback(key, key)), ButtonAction.empty());

        button = button.withTooltips(TooltipHandler.of(this::getTooltips));
        this.keyLabel = DisplayWidget.instance(1, 1, dkey - 1, dy - 1)
                .setRenderHandler(
                        button
                        // LabelElement.instance(Text.literal(this.keyValueHolder.getKeyName()))
                        )
                .addToSub(this);
        this.interactPlace = this.keyValueHolder
                .generateValueWidget(dkey + 1 + dblank, 1, dvalue - 2, dy - 2)
                .addToSub(this);
    }
}
