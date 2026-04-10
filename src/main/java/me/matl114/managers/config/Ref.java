package me.matl114.managers.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import me.matl114.utils.config.AttrKeyValue;

public abstract class Ref<T> {

    protected Config configReference;

    public void setConfigReference(Config ref) {
        if (ref != configReference) {
            configReference = ref;
            if (configReference != null) {
                configReference.markForSave();
            }
        }
    }

    private final List<Consumer<T>> updated = new ArrayList<>();
    private final List<Predicate<T>> validators = new ArrayList<>();

    public abstract T getValue();

    public abstract void setValue(T value);

    public void addUpdateListener(Consumer<T> updateListener) {
        if (updateListener == null) return;
        this.updated.add(updateListener);
    }

    public void addUpdateListenerWithUpdate(Consumer<T> updateListener) {
        if (updateListener == null) return;
        this.updated.add(updateListener);
        try {
            updateListener.accept(getValue());
        } catch (Throwable e) {

        }
    }

    public void removeUpdateListener(Predicate<Consumer<T>> removeListener) {
        this.updated.removeIf(removeListener);
    }

    public void addValidator(Predicate<T> validator) {
        this.validators.add(validator);
    }

    public void removeValidator(Predicate<Predicate<T>> removeListener) {
        this.validators.removeIf(removeListener);
    }

    public boolean validateUpdateValue(T val) {
        try {
            for (Predicate<T> updateListener : validators) {
                if (!updateListener.test(val)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public void callUpdate() {
        T val = getValue();
        try {
            updated.forEach(i -> i.accept(val));
        } catch (Throwable e) {
        }
        // auto save issues
        if (this.configReference != null) {
            this.configReference.markForSave();
        }
    }
    // this method return object that is acceptable for yaml save and load
    public abstract Object getAsPrimitive();
    // isSameTypeWith should return the same value as copyValueTo, but they don't do copy
    public abstract <W> boolean isSameTypeWith(Ref<W> ref);
    // copy this value to the argument
    public abstract <W> boolean copyValueTo(Ref<W> otherRef);

    public final AttrKeyValue<T> createKeyValue(String key) {
        AttrKeyValue<T> keyValue = _createKeyValue0(key);
        validators.forEach(keyValue::addValidator);
        keyValue.addListener(this::setValue);
        return keyValue;
    }

    protected abstract AttrKeyValue<T> _createKeyValue0(String key);
}
