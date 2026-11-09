package me.matl114.managers.config;

public abstract class NumberRef<T extends Number> extends Ref<T> {
    @Override
    public final Object getAsPrimitive() {
        return this.getValue();
    }

    @Override
    public final <W> boolean isSameTypeWith(Ref<W> ref) {
        return ref.getClass() == this.getClass();
    }

    public abstract void setFromNumber(Number number);

    public abstract T getValue();

    public abstract void setValue(T value);

    @Override
    public final <W> boolean copyValueFrom(Ref<W> otherRef) {
        if (otherRef instanceof NumberRef db) {
            this.setFromNumber(db.getValue());
            return true;
        } else return false;
    }
}
