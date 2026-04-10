package me.matl114.utils.config.kv;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.val;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;

public class TypeConvertAttrKeyValue<W, T> implements AttrKeyValue<T>, Cloneable {
    public AttrKeyValue<W> delegate;

    public final WrapperFactory<T, W> wrapperFactory;
    public final CustomWidgetFactory<T> factoryOverride;
    public final WrapperFactory<String, T> stringifyOverride;

    public TypeConvertAttrKeyValue(AttrKeyValue<W> attrKeyValue, WrapperFactory<T, W> wrapperFactory, NBTType<T> type) {
        this(attrKeyValue, wrapperFactory, type.customWidgetFactory(), type.stringifyFactory());
    }

    public TypeConvertAttrKeyValue(
            AttrKeyValue<W> attrKeyValue,
            WrapperFactory<T, W> wrapperFactory,
            CustomWidgetFactory<T> factoryOverride,
            WrapperFactory<String, T> stringifyFactory) {
        this.wrapperFactory = wrapperFactory;
        this.factoryOverride = factoryOverride;
        this.stringifyOverride = stringifyFactory;
        this.delegate = attrKeyValue;
        checkUpdate();
    }

    protected boolean validate = true;

    private String value = "";

    private W lastUpdate;

    private void checkUpdate() {
        var re = delegate.getOriginValue();
        if (!Objects.equals(re, lastUpdate)) {
            lastUpdate = re;
            value = stringifyOverride.get(wrapperFactory.get(re));
            validate = true;
        }
    }

    @Override
    public String getKeyName() {
        return this.delegate.getKeyName();
    }

    @Override
    public WrapperFactory<String, T> getStringifyFactory() {
        return stringifyOverride;
    }

    @Override
    public String getValue() {
        checkUpdate();
        return value;
    }

    @Override
    public T getOriginValue() {
        checkUpdate();
        return wrapperFactory.get(lastUpdate);
    }

    @Override
    public boolean isValueValid(T val) {
        W value;
        try {
            value = wrapperFactory.create(val);
        } catch (Throwable e) {
            return false;
        }
        return delegate.isValueValid(value);
    }

    @Override
    public boolean setOriginValue(T val) {
        String value0 = stringifyOverride.get(val);
        valueChange(null, value0);
        return isValidate();
    }

    @Override
    public boolean validateAndUpdate() {
        try {
            T val = stringifyOverride.create(value);
            W wval = wrapperFactory.create(val);
            if (delegate.setOriginValue(wval)) {
                validate = true;
                lastUpdate = wval;
                return true;
            }
            validate = false;
            return false;
        } catch (Throwable e) {
            return (validate = false);
        }
    }

    @Override
    public String updateValue(T val) {
        return stringifyOverride.get(val);
    }

    @Override
    public boolean isValidate() {
        return validate && delegate.isValidate();
    }

    @Override
    public void addListener(Consumer<T> li) {
        delegate.addListener(s -> li.accept(wrapperFactory.get(s)));
    }

    @Override
    public void addValidator(Predicate<T> validator) {
        delegate.addValidator(s -> validator.test(wrapperFactory.get(s)));
    }

    @Override
    public CustomWidgetFactory<T> getCustomWidgetFactory() {
        return factoryOverride;
    }

    @Override
    public <S extends AttrKeyValue<T>> S copy() {
        TypeConvertAttrKeyValue<W, T> val = clone();
        val.delegate = val.delegate.copy();
        return (S) val;
    }

    @Override
    public void valueChange(Object object, String string) {
        checkUpdate();
        if (Objects.equals(this.value, string)) {
            return;
        }
        this.value = string;
        this.validate = validateAndUpdate();
    }

    @Override
    public TypeConvertAttrKeyValue<W, T> clone() {
        try {
            TypeConvertAttrKeyValue clone = (TypeConvertAttrKeyValue) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
