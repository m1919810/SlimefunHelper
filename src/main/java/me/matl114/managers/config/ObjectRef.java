package me.matl114.managers.config;

import lombok.AllArgsConstructor;
import me.matl114.utils.config.BaseAttrKeyValue;

@AllArgsConstructor
public abstract class ObjectRef<T> extends Ref<T> {
    private T object;

    @Override
    public final T getValue() {
        return get();
    }

    public T get() {
        return object;
    }

    @Override
    public final void setValue(T value) {
        set(value);
    }

    @Override
    public abstract Object getAsPrimitive();

    protected abstract T validateAndCast(Object val);

    public void set(T val) {
        T cas = validateAndCast(val);
        if (validateUpdateValue(cas)) {
            this.object = cas;
            callUpdate();
        }
    }

    public static class JustOnlyObjectRef extends ObjectRef<Object> {

        public JustOnlyObjectRef(Object object) {
            super(object);
        }

        public <W> boolean copyValueTo(Ref<W> otherRef) {
            if (otherRef.getClass() == JustOnlyObjectRef.class) {
                ((JustOnlyObjectRef) otherRef).set(this.get());
                return true;
            } else {
                return false;
            }
        }

        @Override
        public BaseAttrKeyValue<Object> _createKeyValue0(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Object validateAndCast(Object val) {
            return val;
        }

        public Object getAsPrimitive() {
            return this.get().toString();
        }

        @Override
        public <W> boolean isSameTypeWith(Ref<W> ref) {
            return ref.getClass() == JustOnlyObjectRef.class;
        }

        @Override
        public <W> boolean copyValueFrom(Ref<W> otherRef) {
            if (otherRef.getClass() == JustOnlyObjectRef.class) {
                set(((JustOnlyObjectRef) otherRef).get());
                return true;
            } else {
                return false;
            }
        }
    }
}
