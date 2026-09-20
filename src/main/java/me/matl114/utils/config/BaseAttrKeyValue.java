package me.matl114.utils.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.DrawableWidget;
import org.jetbrains.annotations.Nullable;

public class BaseAttrKeyValue<T> implements AttrKeyValue<T>, Cloneable {
    public static CustomWidgetGenerator<?> WIDGET_FACTORY = BaseAttrKeyValue::generateTextInputValueWidget;

    public static <T> CustomWidgetGenerator<T> getWidgetFactory() {
        return (CustomWidgetGenerator<T>) WIDGET_FACTORY;
    }

    @Setter
    protected CustomWidgetGenerator<T> widgetFactory;

    protected final WrapperFactory<String, T> stringifyFactory;

    public BaseAttrKeyValue(String key, @Nonnull T value, WrapperFactory<String, T> stringifyFactory) {
        this(key, value, getWidgetFactory(), stringifyFactory);
    }

    public BaseAttrKeyValue(
            String key,
            @Nonnull T value,
            CustomWidgetGenerator<T> widgetFactory,
            WrapperFactory<String, T> stringifyFactory) {
        this.keyName = key;
        this.originValue = value;
        this.stringifyFactory = stringifyFactory;
        this.value = toInput(value);
        this.widgetFactory = widgetFactory;
        this.validate = true;
    }

    public <R extends BaseAttrKeyValue<T>> BaseAttrKeyValue(
            String key,
            @Nonnull T value,
            CustomWidgetGenerator<T> widgetFactory,
            Function<R, WrapperFactory<String, T>> lateInitialization) {
        this.keyName = key;
        this.originValue = value;
        this.stringifyFactory = lateInitialization.apply((R) this);
        this.value = toInput(value);
        this.widgetFactory = widgetFactory;
        this.validate = true;
    }

    public <R extends BaseAttrKeyValue<T>> BaseAttrKeyValue(
            String key,
            Optional<T> value,
            CustomWidgetGenerator<T> widgetFactory,
            WrapperFactory<String, T> lateInitialization) {
        this.keyName = key;
        this.originValue = value.orElse(null);
        this.stringifyFactory = lateInitialization;
        this.value = originValue == null ? null : toInput(originValue);
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

    public final String getInput() {
        checkUpdate();
        return value;
    }

    @Override
    @Nullable
    public final T get() {
        checkUpdate();
        return originValue;
    }

    @Nullable
    private T originValue;

    @Nullable
    private Supplier<T> updater;

    private void checkUpdate() {
        if (updater == null) {
            return;
        }
        T updated = updater.get();
        if (!Objects.equals(updated, originValue)) {
            originValue = updated;
            value = updated == null ? null : toInput(updated);
            validate = true;
        }
    }

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
        setInput(toInput(value));
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
            var re = getStringifyFactory().create(getInput());
            return (validate = setOriginValue0(re));
        } catch (Throwable e) {
            return (validate = false);
        }
    }

    @Override
    public final void setInput(String string) {
        // do not update if there is not change
        if (Objects.equals(string, value)) {
            return;
        }
        this.value = string;
        this.validate = validateAndUpdate();
    }

    public final String toInput(T val) {
        return stringifyFactory.get(val);
    }

    public final void addListener(Consumer<T> li) {
        listeners.add(li);
    }

    public final void addValidator(Predicate<T> validator) {
        validators.add(validator);
    }

    public final void setUpdater(Supplier<T> updater) {
        this.updater = updater;
        checkUpdate();
    }

    @Override
    public final CustomWidgetGenerator<T> getCustomWidgetFactory() {
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
