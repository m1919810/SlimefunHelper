package me.matl114.managers.config;

import lombok.AllArgsConstructor;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;

@AllArgsConstructor
public class DoubleRef extends NumberRef<Double> {
    public static final Class<Double> TYPE = Double.class;

    double value;

    public static DoubleRef of(Object va) {
        return new DoubleRef(((Number) va).doubleValue());
    }

    public DoubleRef(Double doubleValue) {
        this(doubleValue.doubleValue());
    }

    @Override
    public void setFromNumber(Number number) {
        set(number.doubleValue());
    }

    @Override
    public Double getValue() {
        return get();
    }

    public double get() {
        return value;
    }

    @Override
    public void setValue(Double value) {
        set(value);
    }

    @Override
    public BaseAttrKeyValue<Double> _createKeyValue0(String key) {
        return AttrKeyValue.doubleVal(key, this.value);
    }

    public void set(double va) {
        if (validateUpdateValue(va)) {
            this.value = va;
            callUpdate();
        }
    }
}
