package me.matl114.managers.config;

import lombok.AllArgsConstructor;
import me.matl114.utils.config.AttrKeyValue;

@AllArgsConstructor
public class DoubleRef extends Ref<Double> {
    public static final Class<Double> TYPE = Double.class;

    double value;

    public static DoubleRef of(Object va) {
        return new DoubleRef(((Number) va).doubleValue());
    }

    public DoubleRef(Double doubleValue) {
        this(doubleValue.doubleValue());
    }

    public DoubleRef(Float floatValue) {
        this(floatValue.doubleValue());
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
    public Object getAsPrimitive() {
        return value;
    }

    @Override
    public <W> boolean isSameTypeWith(Ref<W> ref) {
        return ref instanceof DoubleRef;
    }

    @Override
    public <W> boolean copyValueFrom(Ref<W> otherRef) {
        if (otherRef instanceof DoubleRef db) {
            this.set(db.get());
            return true;
        } else return false;
    }

    @Override
    public AttrKeyValue<Double> _createKeyValue0(String key) {
        return AttrKeyValue.doub(key, this.value);
    }

    public void set(double va) {
        if (validateUpdateValue(va)) {
            this.value = va;
            callUpdate();
        }
    }
}
