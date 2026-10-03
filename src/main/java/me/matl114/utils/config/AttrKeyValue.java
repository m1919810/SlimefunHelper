package me.matl114.utils.config;

import static me.matl114.utils.config.kv.AttrKeyValues.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.config.KeyValueInputWidget;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.utils.ReflectUtils;
import me.matl114.utils.config.kv.EnumAttrKeyValue;
import me.matl114.utils.config.kv.RegistryAttrKeyValue;
import me.matl114.utils.config.kv.StringListAttrKeyValue;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public interface AttrKeyValue<T> extends KeyValue<T> {

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

    public boolean validateAndUpdate();

    default String toInput(T val) {
        return getStringifyFactory().get(val);
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

    default SubScreenWidget generateKeyValueInput(
            ElementFactory factory, int x, int y, int keyDx, int blankDx, int inputDx, int dy) {
        return new KeyValueInputWidget<>(
                x, y, keyDx + blankDx + inputDx, dy, keyDx, blankDx, inputDx, this, factory);
    }
    // do not override
    default DrawableWidget generateValueWidget(int x, int y, int inputDx, int dy) {
        return getCustomWidgetFactory().generateWidget(this, x, y, inputDx, dy);
    }

    default DrawableWidget generateValueWidget(ElementFactory factory, int x, int y, int inputDx, int dy) {
        return getCustomWidgetFactory().generateWidget(factory, this, x, y, inputDx, dy);
    }

    /**
     * create the input widget to modify the Attr
     * @return
     */
    public CustomWidgetGenerator<T> getCustomWidgetFactory();

    public <W extends AttrKeyValue<T>> W copy();

    public static BaseAttrKeyValue<Boolean> bool(String key) {
        return bool(key, false);
    }

    public static BaseAttrKeyValue<Boolean> bool(String key, boolean value) {
        return new BaseAttrKeyValue<>(key, value, BOOLEAN_WIDGET_FACTORY, BOOL_FACTORY);
    }

    public static BaseAttrKeyValue<Integer> integer(String key, int val) {
        return new BaseAttrKeyValue<>(key, val, INT_WIDGET_FACTORY, INT_FACTORY);
    }

    public static BaseAttrKeyValue<Integer> clampedInt(String key, int val, int from, int to) {
        return new ClampedIntAttrKeyValue(key, val, from, to);
    }

    public static BaseAttrKeyValue<Float> floatVal(String keyName, float val) {
        return new BaseAttrKeyValue<>(keyName, val, FLOAT_FACTORY);
    }

    public static BaseAttrKeyValue<Double> doubleVal(String keyName, double val) {
        return new BaseAttrKeyValue<>(keyName, val, DOUBLE_WIDGET_FACTORY, DOUBLE_FACTORY);
    }

    public static <T> BaseAttrKeyValue<T> registry(String key, Registry<T> registry, T val) {
        return new RegistryAttrKeyValue<>(key, val, registry);
    }

    public static <T> BaseAttrKeyValue<T> openRegistry(String key, Registry<T> registry, String val) {
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

    public static BaseAttrKeyValue<List<String>> list(String key, List<String> list) {
        return new StringListAttrKeyValue(key, list);
    }

    public static BaseAttrKeyValue<Identifier> identifier(String key, Identifier id) {
        return new BaseAttrKeyValue<>(key, id, IDENTIFIER_FACTORY);
    }

    public static interface CustomWidgetGenerator<T> extends WidgetGenerator<AttrKeyValue<T>> {
        // public DrawableWidget generateWidget(ElementFactory factory, AttrKeyValue<T> kv, int x, int y, int dx, int dy);

        public static <T> CustomWidgetGenerator<T> cutSizeXLeft(CustomWidgetGenerator<T> factory, double portion) {
            return (elementFactory, s1, x, y, dx, dy) -> {
                return factory.generateWidget(elementFactory, s1, x, y, (int) (dx * portion), dy);
            };
        }

        public static <T> UnaryOperator<CustomWidgetGenerator<T>> cutSizeXLeft(double portion) {
            return (w) -> cutSizeXLeft(w, portion);
        }

        public static <T> CustomWidgetGenerator<T> cutSizeXRight(CustomWidgetGenerator<T> factory, double portion) {
            return (elementFactory, s1, x, y, dx, dy) -> {
                int val = (int) (dx * portion) + 1;
                return factory.generateWidget(elementFactory, s1, x + val, y, dx - val, dy);
            };
        }

        public static <T> UnaryOperator<CustomWidgetGenerator<T>> cutSizeXRight(double portion) {
            return (w) -> cutSizeXRight(w, portion);
        }

        public static <T> UnaryOperator<CustomWidgetGenerator<T>> withLabel(Text label) {
            return (w) -> {
                return (elementFactory, s111, x, y, dx, dy) -> {
                    SubScreenWidget subScreenWidget = new SubScreenWidget(x, y, dx, dy);
                    subScreenWidget.addDrawableChild(
                            elementFactory.createLabel(() -> label, List::of, 0, 0, dy * 2, dy));

                    subScreenWidget.addDrawableChild(
                            w.generateWidget(elementFactory, s111, dy * 2, 0, dx - dy * 2, dy));
                    return subScreenWidget;
                };
            };
        }
    }
}
