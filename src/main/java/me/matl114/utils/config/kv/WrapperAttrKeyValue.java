package me.matl114.utils.config.kv;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;

public class WrapperAttrKeyValue<W, T> implements AttrKeyValue<T>, Cloneable {
    public final WrapperFactory<T, W> wrapperFactory;
    public AttrKeyValue<W> delegate;
    public final CustomWidgetGenerator<T> factoryOverride;
    public final WrapperFactory<String, T> stringifyFactory;
    private List<Predicate<T>> validators = new ArrayList<>();

    public WrapperAttrKeyValue(AttrKeyValue<W> attrKeyValue, WrapperFactory<T, W> wrapperFactory) {
        this(attrKeyValue, wrapperFactory, (CustomWidgetGenerator<T>) null);
    }
    // create may never throw
    public WrapperAttrKeyValue(AttrKeyValue<W> attrKeyValue, WrapperFactory<T, W> wrapperFactory, NBTType<T> nbtType) {
        this(attrKeyValue, wrapperFactory, nbtType.customWidgetFactory());
    }

    public WrapperAttrKeyValue(
            AttrKeyValue<W> attrKeyValue,
            WrapperFactory<T, W> wrapperFactory,
            CustomWidgetGenerator<T> factoryOverride) {
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
    public String getInput() {
        return delegate.getInput();
    }

    @Override
    public T get() {
        return wrapperFactory.get(delegate.get());
    }

    @Override
    public boolean isValueValid(T val) {
        W wp;
        try {
            wp = create0(val);
        } catch (Throwable e) {
            return false;
        }
        return delegate.isValueValid(wp);
    }

    @Override
    public boolean setOriginValue(T val) {
        W wp;
        try {
            wp = create0(val);
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
    public String toInput(T val) {
        return delegate.toInput(create0(val));
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
        validators.add(validator);
    }


    private W create0(T val){
        if(validators.stream().anyMatch(s -> !s.test(val))){
            throw WrapperFactory.PARSE_FAILURE;
        }
        return wrapperFactory.create(val);
    }

    @Override
    public List<Predicate<T>> getValidators() {
        return validators;
    }

    @Override
    public CustomWidgetGenerator<T> getCustomWidgetFactory() {
        if (factoryOverride != null) {
            return factoryOverride;
        }
        return (CustomWidgetGenerator<T>) delegate.getCustomWidgetFactory();
    }

    @Override
    public <W extends AttrKeyValue<T>> W copy() {
        return (W) clone();
    }

    @Override
    public void setInput(String string) {
        delegate.setInput(string);
    }

    @Override
    public WrapperAttrKeyValue<W, T> clone() {
        try {
            WrapperAttrKeyValue clone = (WrapperAttrKeyValue) super.clone();
            clone.delegate = delegate.copy();
            clone.validators = new ArrayList<>(validators);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
