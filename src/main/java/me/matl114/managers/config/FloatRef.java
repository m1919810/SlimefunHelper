package me.matl114.managers.config;

import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;

public class FloatRef extends NumberRef<Float> {
    public static final Class<Float> TYPE = Float.class;
    float value;

    public static FloatRef of(Object va) {
        return new FloatRef(((Number) va).floatValue());
    }

    public FloatRef(float fa) {
        this.value = fa;
    }

    @Override
    public void setFromNumber(Number number) {
        set(number.floatValue());
    }

    @Override
    public Float getValue() {
        return value;
    }

    @Override
    public void setValue(Float value) {
        set(value);
    }

    public float get() {
        return this.value;
    }

    public void set(float va) {
        if (validateUpdateValue(va)) {
            this.value = va;
            callUpdate();
        }
    }

    @Override
    protected BaseAttrKeyValue<Float> _createKeyValue0(String key) {
        return AttrKeyValue.floatVal(key, this.value);
    }
}
