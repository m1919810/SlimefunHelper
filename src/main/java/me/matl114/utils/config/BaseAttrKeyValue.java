package me.matl114.utils.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.val;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.DrawableWidget;
import org.jetbrains.annotations.Nullable;

public class BaseAttrKeyValue<T> implements AttrKeyValue<T>, Cloneable {
    public static CustomWidgetFactory<?> WIDGET_FACTORY = BaseAttrKeyValue::generateTextInputValueWidget;

    public static <T> CustomWidgetFactory<T> getWidgetFactory() {
        return (CustomWidgetFactory<T>) WIDGET_FACTORY;
    }

    protected CustomWidgetFactory<T> widgetFactory;
    protected final WrapperFactory<String, T> stringifyFactory;

    public BaseAttrKeyValue(String key, @Nonnull T value, WrapperFactory<String, T> stringifyFactory) {
        this(key, value, getWidgetFactory(), stringifyFactory);
    }

    public BaseAttrKeyValue(
            String key,
            @Nonnull T value,
            CustomWidgetFactory<T> widgetFactory,
            WrapperFactory<String, T> stringifyFactory) {
        this.keyName = key;
        this.originValue = value;
        this.stringifyFactory = stringifyFactory;
        this.value = updateValue(value);
        this.widgetFactory = widgetFactory;
        this.validate = true;
    }

    public <R extends BaseAttrKeyValue<T>> BaseAttrKeyValue(
            String key,
            @Nonnull T value,
            CustomWidgetFactory<T> widgetFactory,
            Function<R, WrapperFactory<String, T>> lateInitialization) {
        this.keyName = key;
        this.originValue = value;
        this.stringifyFactory = lateInitialization.apply((R) this);
        this.value = updateValue(value);
        this.widgetFactory = widgetFactory;
        this.validate = true;
    }

    public <R extends BaseAttrKeyValue<T>> BaseAttrKeyValue(
            String key,
            Optional<T> value,
            CustomWidgetFactory<T> widgetFactory,
            WrapperFactory<String, T> lateInitialization) {
        this.keyName = key;
        this.originValue = value.orElse(null);
        this.stringifyFactory = lateInitialization;
        this.value = originValue == null ? null : updateValue(originValue);
        this.widgetFactory = widgetFactory;
        this.validate = value.isPresent();
    }

    @Getter
    final String keyName;

    private String value;

    @Override
    public final WrapperFactory<String, T> getStringifyFactory() {
        return stringifyFactory;
    }

    public final String getValue() {
        return value;
    }

    @Getter
    @Nullable
    private T originValue;

    @Getter
    List<Predicate<T>> validators = new ArrayList<>();

    @Getter
    List<Consumer<T>> listeners = new ArrayList<>();

    public final boolean isValueValid(T value) {
        try {
            for (var validator : validators) {
                if (!validator.test(value)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public final boolean setOriginValue(T value) {
        valueChange(null, updateValue(value));
        return isValidate();
    }

    protected final boolean setOriginValue0(T value) {
        if (isValueValid(value)) {
            this.originValue = value;
            callListeners(this.originValue);
            return true;
        }
        return false;
    }

    private final void callListeners(T val) {
        try {
            for (var validator : listeners) {
                validator.accept(val);
            }
        } catch (Throwable e) {
        }
    }

    @Getter
    protected boolean validate = true;

    public final boolean validateAndUpdate() {
        try {
            var re = getStringifyFactory().create(getValue());
            return (validate = setOriginValue0(re));
        } catch (Throwable e) {
            return (validate = false);
        }
    }

    @Override
    public final void valueChange(Object selectable, String string) {
        // do not update if there is not change
        if (Objects.equals(string, value)) {
            return;
        }
        this.value = string;
        this.validate = validateAndUpdate();
    }

    public final String updateValue(T val) {
        return stringifyFactory.get(val);
    }

    public final void valueChangeInternal(Object selectable, T val) {
        valueChange(selectable, updateValue(val));
    }

    public final void addListener(Consumer<T> li) {
        listeners.add(li);
    }

    public final void addValidator(Predicate<T> validator) {
        validators.add(validator);
    }

    @Override
    public final CustomWidgetFactory<T> getCustomWidgetFactory() {
        return widgetFactory;
    }

    @Override
    public <W extends AttrKeyValue<T>> W copy() {
        BaseAttrKeyValue<T> attrKeyValue = (BaseAttrKeyValue<T>) this.clone();
        attrKeyValue.validators = new ArrayList<>(attrKeyValue.validators);
        attrKeyValue.listeners = new ArrayList<>(attrKeyValue.listeners);
        return (W) attrKeyValue;
    }

    @Override
    protected final Object clone() {
        try {
            return super.clone();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> DrawableWidget generateTextInputValueWidget(
            AttrKeyValue<T> attrKeyValue, int x, int y, int inputDx, int dy) {
        return McWidgetHelpers.createAttrValueEditBox(attrKeyValue, x, y, inputDx, dy);
    }
}
