package me.matl114.utils.config;

import static me.matl114.utils.config.kv.AttrKeyValues.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import me.matl114.gui.basic.*;
import me.matl114.gui.config.KeyValueInputWidget;
import me.matl114.utils.ReflectUtils;
import me.matl114.utils.config.kv.EnumAttrKeyValue;
import me.matl114.utils.config.kv.RegistryAttrKeyValue;
import me.matl114.utils.config.kv.StringListAttrKeyValue;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public interface AttrKeyValue<T> extends KeyValue<T>, PropertyTracker<Object, String> {

    /**
     * get the stringify factory,
     * it can create instance from string
     * and can print instance to string,
     *
     * the getValue() will return the string generated from this factory
     *
     * constant value returned
     * @return
     */
    public WrapperFactory<String, T> getStringifyFactory();
    /**
     * get the string value
     * @return
     */
    public String getValue();

    public boolean validateAndUpdate();

    default String updateValue(T val) {
        return getStringifyFactory().get(val);
    }

    default void valueChangeInternal(Object selectable, T val) {
        valueChange(selectable, updateValue(val));
    }

    /**
     * add listener
     * @param li
     */
    public void addListener(Consumer<T> li);

    /**
     * add validator
     * @param validator
     */
    public void addValidator(Predicate<T> validator);

    default SubScreenWidget generateKeyValueInput(int x, int y, int keyDx, int blankDx, int inputDx, int dy) {
        return new KeyValueInputWidget<>(x, y, keyDx + blankDx + inputDx, dy, keyDx, blankDx, inputDx, this);
    }
    // do not override
    default DrawableWidget generateValueWidget(int x, int y, int inputDx, int dy) {
        return getCustomWidgetFactory().generateWidget(this, x, y, inputDx, dy);
    }

    /**
     * create the input widget to modify the Attr
     * @return
     */
    public CustomWidgetFactory<T> getCustomWidgetFactory();

    public <W extends AttrKeyValue<T>> W copy();

    public static AttrKeyValue<Boolean> bool(String key) {
        return bool(key, false);
    }

    public static AttrKeyValue<Boolean> bool(String key, boolean value) {
        return new BaseAttrKeyValue<>(key, value, BOOLEAN_WIDGET_FACTORY, BOOL_FACTORY);
    }

    public static AttrKeyValue<Integer> integer(String key, int val) {
        return new BaseAttrKeyValue<>(key, val, INT_FACTORY);
    }

    public static AttrKeyValue<Integer> clampedInt(String key, int val, int from, int to) {
        return new ClampedIntAttrKeyValue(key, val, from, to);
    }

    public static AttrKeyValue<Double> doub(String keyName, double val) {
        return new BaseAttrKeyValue<>(keyName, val, DOUBLE_FACTORY);
    }

    public static <T> AttrKeyValue<T> registry(String key, Registry<T> registry, T val) {
        return new RegistryAttrKeyValue<>(key, val, registry);
    }

    public static <T> AttrKeyValue<T> openRegistry(String key, Registry<T> registry, String val) {
        Identifier identifier = Identifier.tryParse(val);
        T val0;
        if (identifier != null && (val0 = registry.getOrEmpty(identifier).orElse(null)) != null) {
            return new RegistryAttrKeyValue<>(key, val0, registry);
        } else {
            return new RegistryAttrKeyValue<>(key, val, registry, null);
        }
    }

    public static BaseAttrKeyValue<String> str(String key, String val) {
        return new BaseAttrKeyValue<>(key, val, STRING_FACTORY);
    }

    public static <T> EnumAttrKeyValue<T> enumMap(String key, T val, Map<String, T> finiteValueMap) {
        return new EnumAttrKeyValue<>(key, val, (Class<T>) (val == null ? Enum.class : val.getClass()), finiteValueMap);
    }

    public static <T extends Enum<T>> EnumAttrKeyValue<T> enumMap(String key, T val, Class<T> enumClass) {
        Map<String, T> map = ReflectUtils.getEnumMap(enumClass);
        return new EnumAttrKeyValue<>(key, val, enumClass, map);
    }

    public static AttrKeyValue<List<String>> list(String key, List<String> list) {
        return new StringListAttrKeyValue(key, list);
    }

    public static AttrKeyValue<Identifier> identifier(String key, Identifier id) {
        return new BaseAttrKeyValue<>(key, id, IDENTIFIER_FACTORY);
    }

    public static interface CustomWidgetFactory<T> extends WidgetFactory<AttrKeyValue<T>> {
        // public DrawableWidget generateWidget(AttrKeyValue<T> kv, int x, int y, int dx, int dy);

        public static <T> CustomWidgetFactory<T> cutSizeXLeft(CustomWidgetFactory<T> factory, double portion) {
            return (s1, x, y, dx, dy) -> {
                return factory.generateWidget(s1, x, y, (int) (dx * portion), dy);
            };
        }

        public static <T> UnaryOperator<CustomWidgetFactory<T>> cutSizeXLeft(double portion) {
            return (w) -> cutSizeXLeft(w, portion);
        }

        public static <T> CustomWidgetFactory<T> cutSizeXRight(CustomWidgetFactory<T> factory, double portion) {
            return (s1, x, y, dx, dy) -> {
                int val = (int) (dx * portion) + 1;
                return factory.generateWidget(s1, x + val, y, dx - val, dy);
            };
        }

        public static <T> UnaryOperator<CustomWidgetFactory<T>> cutSizeXRight(double portion) {
            return (w) -> cutSizeXRight(w, portion);
        }
    }
}
