package me.matl114.gui.complex.config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.*;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.managers.config.Ref;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.text.Text;

public class DefaultedKeyValueInputWidget<W> extends KeyValueInputWidget<W> {
    Optional<W> reference;

    public DefaultedKeyValueInputWidget(
            int x, int y, int dx, int dy, int dKey, int dblank, int dvalue, Ref<W> kv, String key) {
        super(x, y, dx, dy, dKey, dblank, dvalue - dy, kv.createKeyValue(key));
        this.reference = kv.hasDefaultValue() ? Optional.of(kv.getDefaultValue()) : Optional.empty();
    }

    public DefaultedKeyValueInputWidget(
            int x,
            int y,
            int dx,
            int dy,
            int dKey,
            int dblank,
            int dvalue,
            Ref<W> kv,
            String key,
            ElementFactory elementFactory) {
        super(x, y, dx, dy, dKey, dblank, dvalue - dy, kv.createKeyValue(key), elementFactory);
        this.reference = kv.hasDefaultValue() ? Optional.of(kv.getDefaultValue()) : Optional.empty();
    }

    public DefaultedKeyValueInputWidget(
            int x, int y, int dx, int dy, int dKey, int dblank, int dvalue, Ref<W> kv, AttrKeyValue<W> attr) {
        this(
                x,
                y,
                dx,
                dy,
                dKey,
                dblank,
                dvalue,
                kv.hasDefaultValue() ? Optional.of(kv.getDefaultValue()) : Optional.empty(),
                attr);
    }

    public DefaultedKeyValueInputWidget(
            int x, int y, int dx, int dy, int dKey, int dblank, int dvalue, Optional<W> kv, AttrKeyValue<W> attr) {
        super(x, y, dx, dy, dKey, dblank, dvalue - dy, attr);
        this.reference = kv;
    }

    public DefaultedKeyValueInputWidget(
            int x,
            int y,
            int dx,
            int dy,
            int dKey,
            int dblank,
            int dvalue,
            Optional<W> kv,
            AttrKeyValue<W> attr,
            ElementFactory elementFactory) {
        super(x, y, dx, dy, dKey, dblank, dvalue - dy, attr, elementFactory);
        this.reference = kv;
    }

    @Override
    protected void init() {
        super.init();
        BooleanSupplier canReset =
                () -> this.reference.isPresent() && !Objects.equals(this.keyValueHolder.get(), this.reference.get());
        ExecutableWidget.instance(this.dkey + this.dblank + this.dvalue + 1, 1, dy - 2, dy - 2)
                .setElementHandler((ElementHandler) elementFactory
                        .createButton(
                                TextProvider.of(Text.empty()),
                                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                        "widget.gui.reset-button-element.reset.tooltips", "")),
                                ButtonAction.run(() -> {
                                    if (canReset.getAsBoolean()) {
                                        this.reference.ifPresent(this.keyValueHolder::accept);
                                    }
                                }),
                                canReset,
                                canReset)
                        .combineRender((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                            int width = element.getTextureWidth();
                            int height = element.getTextureHeight();
                            int hhw = width / 4;
                            int hhh = height / 4;
                            context.drawGuiTexture(
                                    Constants.RESET_BUTTON_SPRITE, hhw, hhh, width - 2 * hhw, height - 2 * hhh);
                        }))
                .addToSub(this);
    }
}
