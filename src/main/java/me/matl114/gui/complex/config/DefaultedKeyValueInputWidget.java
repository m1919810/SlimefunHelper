package me.matl114.gui.complex.config;

import java.util.Objects;
import java.util.Optional;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.elements.ResetButtonElement;
import me.matl114.managers.config.Ref;
import me.matl114.utils.config.AttrKeyValue;

public class DefaultedKeyValueInputWidget<W> extends KeyValueInputWidget<W> {
    Optional<W> reference;

    public DefaultedKeyValueInputWidget(
            int x, int y, int dx, int dy, int dKey, int dblank, int dvalue, Ref<W> kv, String key) {
        super(x, y, dx, dy, dKey, dblank, dvalue - dy, kv.createKeyValue(key));
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

    @Override
    protected void init() {
        super.init();
        ExecutableWidget.instance(this.dkey + this.dblank + this.dvalue + 1, 1, dy - 2, dy - 2)
                .setElementHandler(new ResetButtonElement(
                        () -> this.reference.isPresent()
                                && !Objects.equals(this.keyValueHolder.getOriginValue(), this.reference.get()),
                        () -> {
                            this.reference.ifPresent(w -> this.keyValueHolder.valueChangeInternal(null, w));
                        }))
                .addToSub(this);
    }
}
