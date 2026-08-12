package me.matl114.managers.config;

import me.matl114.gui.presets.single.KeyBindConfigurateWidget;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.WrapperFactory;

public class KeyBindRef extends ObjectRef<MultiKeyBind> {
    public static final Class<MultiKeyBind> TYPE = MultiKeyBind.class;

    public KeyBindRef(MultiKeyBind object) {
        super(object);
    }

    @Override
    public Object getAsPrimitive() {
        return get().asString();
    }

    @Override
    public <W> boolean isSameTypeWith(Ref<W> ref) {
        return ref instanceof KeyBindRef;
    }

    @Override
    public <W> boolean copyValueFrom(Ref<W> otherRef) {
        if (otherRef instanceof KeyBindRef stringRef) {
            set(stringRef.get());
            return true;
        }
        return false;
    }

    @Override
    public BaseAttrKeyValue<MultiKeyBind> _createKeyValue0(String key) {
        return new BaseAttrKeyValue<>(key, this.get(), WIDGET_FACTORY, FACTORY);
    }

    @Override
    protected MultiKeyBind validateAndCast(Object val) {
        return (MultiKeyBind) val;
    }

    public static KeyBindRef fromString(String val) {
        if (val.startsWith("hotkey:")) {
            try {
                return new KeyBindRef(new MultiKeyBind(val));
            } catch (Throwable e) {
            }
        }
        return null;
    }

    public static final AttrKeyValue.CustomWidgetFactory<MultiKeyBind> WIDGET_FACTORY = (s, x, y, dx, dy) -> {
        return new KeyBindConfigurateWidget(x, y, dx, dy, s);
    };

    public static final WrapperFactory<String, MultiKeyBind> FACTORY = WrapperFactory.of(
            (s) -> {
                if (s.startsWith("hotkey:")) {
                    return new MultiKeyBind(s);
                } else {
                    throw WrapperFactory.PARSE_FAILURE;
                }
            },
            MultiKeyBind::asString);
}
