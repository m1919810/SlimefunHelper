package me.matl114.utils.config;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.lang3.function.Consumers;

public interface ValueAccessor<T> {
    T getValue();

    void setValue(T value);

    public static <T> ValueAccessor<T> holder() {
        return new ValueAccessor<T>() {
            @Override
            public T getValue() {
                return val;
            }

            @Override
            public void setValue(T value) {
                val = value;
            }

            T val;
        };
    }

    public static <T> ValueAccessor<T> holder(T val) {
        ValueAccessor<T> holder = holder();
        holder.setValue(val);
        return holder;
    }

    public static <T> ValueAccessor<T> of(Supplier<T> supplier, Consumer<T> consumer) {
        return new ValueAccessor<T>() {

            @Override
            public T getValue() {
                return supplier.get();
            }

            @Override
            public void setValue(T value) {
                consumer.accept(value);
            }
        };
    }

    public static <T> ValueAccessor<T> of(AttrKeyValue<T> keyValue) {
        return of(keyValue::getOriginValue, keyValue::setOriginValue);
    }

    public static <T> void unsupportWrite(T val) {
        throw new UnsupportedOperationException("Write");
    }

    public static <T> ValueAccessor<T> of(Supplier<T> supplier) {
        return of(supplier, ValueAccessor::unsupportWrite);
    }

    public static <T> ValueAccessor<T> ofIgnore(Supplier<T> supplier) {
        return of(supplier, Consumers.nop());
    }

    public static <T> ValueAccessor<T> of(T value) {
        return of(() -> value);
    }

    public static <T> ValueAccessor<T> ofIgnore(T value) {
        return ofIgnore(() -> value);
    }
}
