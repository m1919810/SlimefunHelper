package me.matl114.managers.config;

import lombok.AllArgsConstructor;
import me.matl114.utils.config.AttrKeyValue;

@AllArgsConstructor
public class IntRef extends Ref<Integer> {
    public static final Class<Integer> TYPE = Integer.class;

    int value;

    public IntRef(Object ref) {
        this((int) ref);
    }

    public static IntRef fromString(String value) {
        try {
            int val = Integer.parseInt(value);
            return new IntRef(val);
        } catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    @Override
    public Integer getValue() {
        return get();
    }

    public int get() {
        return value;
    }

    @Override
    public void setValue(Integer value) {
        set(value);
    }

    @Override
    public Object getAsPrimitive() {
        return this.value;
    }

    @Override
    public <W> boolean isSameTypeWith(Ref<W> ref) {
        return ref instanceof IntRef;
    }

    @Override
    public <W> boolean copyValueTo(Ref<W> otherRef) {
        if (otherRef instanceof IntRef integer) {
            integer.set(this.value);
            return true;
        } else if (otherRef instanceof FlagRef flag) {
            if (this.value == 0 || this.value == 1) {
                flag.set(this.value == 1);
                return true;
            }
            return false;
        } else return false;
    }

    @Override
    public AttrKeyValue<Integer> _createKeyValue0(String key) {
        return AttrKeyValue.integer(key, this.value);
    }

    public void set(int value) {
        if (validateUpdateValue(value)) {
            this.value = value;
            callUpdate();
        }
    }
}
