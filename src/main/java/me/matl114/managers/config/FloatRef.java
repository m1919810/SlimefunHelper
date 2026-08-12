package me.matl114.managers.config;

import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;

public class FloatRef extends Ref<Float> {
    public static final Class<Float> TYPE = Float.class;
    float value;

    public static FloatRef of(Object va) {
        return new FloatRef(((Number) va).floatValue());
    }

    public FloatRef(float fa) {
        this.value = fa;
    }

    @Override
    public Float getValue() {
        return value;
    }

    @Override
    public void setValue(Float value) {
        this.value = value;
    }

    @Override
    public Object getAsPrimitive() {
        return this.value;
    }

    @Override
    public <W> boolean isSameTypeWith(Ref<W> ref) {
        return ref instanceof FloatRef floatRef;
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
    public <W> boolean copyValueFrom(Ref<W> otherRef) {
        if (otherRef instanceof DoubleRef db) {
            this.set((float) db.get());
            return true;
        } else if (otherRef instanceof FloatRef floatRef) {
            this.set(floatRef.get());
            return true;
        } else return false;
    }

    @Override
    protected BaseAttrKeyValue<Float> _createKeyValue0(String key) {
        return AttrKeyValue.floatVal(key, this.value);
    }
}
