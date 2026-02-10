package me.matl114.managers.config;

import me.matl114.utils.config.AttrKeyValue;

public class StringRef extends ObjectRef<String> {
    public static final Class<String> TYPE = String.class;

    public StringRef(String value) {
        super(value);
    }

    public Object getAsPrimitive() {
        return get();
    }

    @Override
    public <W> boolean isSameTypeWith(Ref<W> ref) {
        return ref instanceof StringRef;
    }

    @Override
    public <W> boolean copyValueTo(Ref<W> otherRef) {
        if (otherRef instanceof StringRef stringRef) {
            stringRef.set(this.get());
            return true;
        }
        return false;
    }

    @Override
    public AttrKeyValue<String> _createKeyValue0(String key) {
        return AttrKeyValue.str(key, this.get());
    }

    @Override
    protected String validateAndCast(Object val) {
        return (String) val;
    }
}
