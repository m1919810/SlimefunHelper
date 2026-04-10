package me.matl114.utils.config.kv;

import java.util.function.Consumer;
import java.util.function.Predicate;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;

public class WrapperAttrKeyValue<W, T> implements AttrKeyValue<T>, Cloneable {
    public final WrapperFactory<T, W> wrapperFactory;
    public final AttrKeyValue<W> delegate;
    public final CustomWidgetFactory<T> factoryOverride;
    public final WrapperFactory<String, T> stringifyFactory;

    public WrapperAttrKeyValue(AttrKeyValue<W> attrKeyValue, WrapperFactory<T, W> wrapperFactory) {
        this(attrKeyValue, wrapperFactory, (CustomWidgetFactory<T>) null);
    }
    // create may never throw
    public WrapperAttrKeyValue(AttrKeyValue<W> attrKeyValue, WrapperFactory<T, W> wrapperFactory, NBTType<T> nbtType) {
        this(attrKeyValue, wrapperFactory, nbtType.customWidgetFactory());
    }

    public WrapperAttrKeyValue(
            AttrKeyValue<W> attrKeyValue, WrapperFactory<T, W> wrapperFactory, CustomWidgetFactory<T> factoryOverride) {
        this.wrapperFactory = wrapperFactory;
        this.delegate = attrKeyValue;
        this.factoryOverride = factoryOverride;
        WrapperFactory<String, W> factory = attrKeyValue.getStringifyFactory();
        this.stringifyFactory = factory.concat(wrapperFactory.inverse());
    }

    @Override
    public String getKeyName() {
        return delegate.getKeyName();
    }

    @Override
    public WrapperFactory<String, T> getStringifyFactory() {
        return stringifyFactory;
    }

    @Override
    public String getValue() {
        return delegate.getValue();
    }

    @Override
    public T getOriginValue() {
        return wrapperFactory.get(delegate.getOriginValue());
    }

    @Override
    public boolean isValueValid(T val) {
        W wp;
        try {
            wp = wrapperFactory.create(val);
        } catch (Throwable e) {
            return false;
        }
        return delegate.isValueValid(wp);
    }

    @Override
    public boolean setOriginValue(T val) {
        W wp;
        try {
            wp = wrapperFactory.create(val);
        } catch (Throwable e) {
            return false;
        }
        return delegate.setOriginValue(wp);
    }

    @Override
    public boolean validateAndUpdate() {
        return delegate.validateAndUpdate();
    }

    @Override
    public String updateValue(T val) {
        return delegate.updateValue(wrapperFactory.create(val));
    }

    @Override
    public boolean isValidate() {
        return delegate.isValidate();
    }

    @Override
    public void addListener(Consumer<T> li) {
        delegate.addListener((va) -> li.accept(wrapperFactory.get(va)));
    }

    @Override
    public void addValidator(Predicate<T> validator) {
        delegate.addValidator((va) -> validator.test(wrapperFactory.get(va)));
    }

    @Override
    public CustomWidgetFactory<T> getCustomWidgetFactory() {
        if (factoryOverride != null) {
            return factoryOverride;
        }
        return (CustomWidgetFactory<T>) delegate.getCustomWidgetFactory();
    }

    @Override
    public <W extends AttrKeyValue<T>> W copy() {
        return (W) clone();
    }

    @Override
    public void valueChange(Object object, String string) {
        delegate.valueChange(object, string);
    }

    @Override
    public WrapperAttrKeyValue<W, T> clone() {
        try {
            WrapperAttrKeyValue clone = (WrapperAttrKeyValue) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
