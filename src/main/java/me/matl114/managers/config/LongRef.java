package me.matl114.managers.config;

import lombok.AllArgsConstructor;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.kv.AttrKeyValues;

@AllArgsConstructor
public class LongRef extends Ref<Long> {
    public static final Class<Long> TYPE = Long.class;

    long value;

    public LongRef(Object ref) {
        this(((Number) ref).longValue());
    }

    public static LongRef fromString(String value) {
        try {
            long val = Long.parseLong(value);
            if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
                return new LongRef(val);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    @Override
    public Long getValue() {
        return get();
    }

    public long get() {
        return value;
    }

    @Override
    public void setValue(Long value) {
        set(value);
    }

    @Override
    public Object getAsPrimitive() {
        return value;
    }

    @Override
    public <W> boolean isSameTypeWith(Ref<W> ref) {
        return ref instanceof LongRef;
    }

    @Override
    public <W> boolean copyValueFrom(Ref<W> otherRef) {
        if (otherRef instanceof LongRef lg) {
            set(lg.get());
            return true;
        }
        return false;
    }

    @Override
    public BaseAttrKeyValue<Long> _createKeyValue0(String key) {
        return new me.matl114.utils.config.BaseAttrKeyValue<>(key, this.value, AttrKeyValues.LONG_FACTORY);
    }

    public void set(long value) {
        if (validateUpdateValue(value)) {
            this.value = value;
            callUpdate();
        }
    }
}
