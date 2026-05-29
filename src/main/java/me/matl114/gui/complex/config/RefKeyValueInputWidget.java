package me.matl114.gui.complex.config;

import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.elements.ResetButtonElement;
import me.matl114.managers.config.Ref;
import me.matl114.utils.config.AttrKeyValue;

public class RefKeyValueInputWidget<W> extends KeyValueInputWidget<W> {
    Ref<W> reference;

    public RefKeyValueInputWidget(
            int x, int y, int dx, int dy, int dKey, int dblank, int dvalue, Ref<W> kv, String key) {
        super(x, y, dx, dy, dKey, dblank, dvalue - dy, kv.createKeyValue(key));
        this.reference = kv;
    }

    public RefKeyValueInputWidget(
            int x, int y, int dx, int dy, int dKey, int dblank, int dvalue, Ref<W> kv, AttrKeyValue<W> attr) {
        super(x, y, dx, dy, dKey, dblank, dvalue - dy, attr);
        this.reference = kv;
    }

    @Override
    protected void init() {
        super.init();
        ExecutableWidget.instance(this.dkey + this.dblank + this.dvalue + 1, 1, dy - 2, dy - 2)
                .setElementHandler(new ResetButtonElement(() -> this.reference.isValueDifferent(), () -> {
                    if (this.reference.hasDefaultValue()) {
                        this.keyValueHolder.valueChangeInternal(null, this.reference.getDefaultValue());
                    }
                }))
                .addToSub(this);
    }
}
