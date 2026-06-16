package me.matl114.managers.config;

import java.util.Objects;

public abstract class LazilyRegisterTypeRef<T, W> extends ObjectRef<T> {
    public LazilyRegisterTypeRef(String type, T object) {
        super(object);
        tryRegisterType(object);
        this.enumType = type;
        this.enumValue = toLazy(object);
        this.resolved = true;
    }

    public LazilyRegisterTypeRef(String value) {
        super(null);
        String val = prefix() + ":";
        if (value.startsWith(val)) {
            value = value.substring(val.length());
        }
        int idx = value.indexOf(":");
        String first = value.substring(0, idx);
        String second = value.substring(idx + 1);
        this.enumType = first;
        this.enumValue = fromStringToLazy(second);
        tryResolve();
    }

    @Override
    public void setDefaultValue(T defaultValue) {
        tryRegisterType(defaultValue);
        tryResolve();
        super.setDefaultValue(defaultValue);
    }

    protected abstract void tryRegisterType(T value);

    protected abstract W toLazy(T val);

    protected abstract W fromStringToLazy(String string);

    protected abstract String fromLazyToString(W val);

    protected abstract String prefix();

    protected abstract void tryResolve() throws RuntimeException;

    public final String enumType;
    public W enumValue;
    public boolean resolved;

    @Override
    public final void set(T val) {
        super.set(val);
        enumValue = toLazy(val);
    }

    public final Object getAsPrimitive() {
        return prefix() + ":" + enumType + ":" + fromLazyToString(enumValue);
    }

    @Override
    public <W> boolean isSameTypeWith(Ref<W> ref) {
        if (ref instanceof LazilyRegisterTypeRef what
                && what.getClass() == this.getClass()
                && Objects.equals(what.enumType, enumType)) {
            try {
                if (!resolved && what.resolved) {
                    tryResolve();
                    return resolved;
                }
                if (!what.resolved && resolved) {
                    what.tryResolve();
                    return what.resolved;
                }
            } catch (Throwable e) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public final <W> boolean copyValueTo(Ref<W> otherRef) {
        if (otherRef instanceof LazilyRegisterTypeRef what
                && what.getClass() == this.getClass()
                && Objects.equals(what.enumType, this.enumType)) {
            if (!this.resolved) {
                tryResolve();
            }
            if (this.resolved) {
                what.set(this.get());
            } else {
                what.enumValue = this.enumValue;
            }

            return true;
        }
        return false;
    }

    @Override
    public final T get() {
        if (resolved) {
            return super.get();
        } else {
            tryResolve();
            T val = super.get();
            if (val != null) {
                return val;
            } else {
                throw new IllegalStateException("Access to a lazily registered type instance before it is registered");
            }
        }
    }
}
