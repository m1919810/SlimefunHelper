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

    public static <T extends Number> ValueAccessor<T> numberHolder(T value) {
        if (value instanceof Integer intValue) {
            return (ValueAccessor<T>) new Int(intValue);
        }
        if (value instanceof Long longValue) {
            return (ValueAccessor<T>) new LongValue(longValue);
        }
        if (value instanceof Double doubleValue) {
            return (ValueAccessor<T>) new DoubleValue(doubleValue);
        }
        if (value instanceof Float floatValue) {
            return (ValueAccessor<T>) new FloatValue(floatValue);
        }
        if (value instanceof Short shortValue) {
            return (ValueAccessor<T>) new ShortValue(shortValue);
        }
        if (value instanceof Byte byteValue) {
            return (ValueAccessor<T>) new ByteValue(byteValue);
        }
        return holder(value);
    }

    public static class Int implements ValueAccessor<Number> {
        int value;

        public Int() {
            this(0);
        }

        public Int(int value) {
            this.value = value;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public void setValue(Number value) {
            this.value = value.intValue();
        }
    }

    public static class LongValue implements ValueAccessor<Number> {
        long value;

        public LongValue() {
            this(0L);
        }

        public LongValue(long value) {
            this.value = value;
        }

        @Override
        public Long getValue() {
            return value;
        }

        @Override
        public void setValue(Number value) {
            this.value = value.longValue();
        }
    }

    public static class DoubleValue implements ValueAccessor<Number> {
        double value;

        public DoubleValue() {
            this(0D);
        }

        public DoubleValue(double value) {
            this.value = value;
        }

        @Override
        public Double getValue() {
            return value;
        }

        @Override
        public void setValue(Number value) {
            this.value = value.doubleValue();
        }
    }

    public static class FloatValue implements ValueAccessor<Number> {
        float value;

        public FloatValue() {
            this(0F);
        }

        public FloatValue(float value) {
            this.value = value;
        }

        @Override
        public Float getValue() {
            return value;
        }

        @Override
        public void setValue(Number value) {
            this.value = value.floatValue();
        }
    }

    public static class ShortValue implements ValueAccessor<Number> {
        short value;

        public ShortValue() {
            this((short) 0);
        }

        public ShortValue(short value) {
            this.value = value;
        }

        @Override
        public Short getValue() {
            return value;
        }

        @Override
        public void setValue(Number value) {
            this.value = value.shortValue();
        }
    }

    public static class ByteValue implements ValueAccessor<Number> {
        byte value;

        public ByteValue() {
            this((byte) 0);
        }

        public ByteValue(byte value) {
            this.value = value;
        }

        @Override
        public Byte getValue() {
            return value;
        }

        @Override
        public void setValue(Number value) {
            this.value = value.byteValue();
        }
    }
}
