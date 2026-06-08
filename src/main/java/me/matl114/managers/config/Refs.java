package me.matl114.managers.config;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import me.matl114.managers.input.MultiKeyBind;

public class Refs {
    private static final List<TypedReferenceBuilder<?>> referenceBuilders;

    @AllArgsConstructor
    public static class TypedReferenceBuilder<T> {
        public Class<T> baseClass;
        public List<Function<T, Ref<?>>> builders;

        public Ref<?> tryBuild(Object value) {
            if (baseClass.isAssignableFrom(value.getClass())) {
                // is instance
                T val = (T) value;
                Ref<?> ref = null;
                for (var builder : builders) {
                    if ((ref = builder.apply(val)) != null) {
                        break;
                    }
                }
                return ref;
            }
            return null;
        }
    }

    public static List<TypedReferenceBuilder<?>> getReferenceBuilders() {
        return Collections.unmodifiableList(referenceBuilders);
    }

    public static Ref<?> wrapInstance(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Ref<?> ref) {
            return ref;
        } else if (value instanceof Map map) {
            return transferConfig(map);
        } else {

            // Enum should be written
            for (var typedBuilder : Refs.getReferenceBuilders()) {
                Ref<?> ref = typedBuilder.tryBuild(value);
                if (ref != null) {
                    return ref;
                }
            }
            return null;
        }
    }

    public static MapRef transferConfig(Map<String, Object> config) {
        MapRef newConfig = new MapRef();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            if (entry.getValue() != null) {
                Ref<?> wrapped = Refs.wrapInstance(entry.getValue());
                if (wrapped != null) {
                    newConfig.putRaw(entry.getKey(), wrapped);
                }
            }
        }
        return newConfig;
    }

    static {
        referenceBuilders = ImmutableList.<TypedReferenceBuilder<?>>builder()
                .add(new TypedReferenceBuilder<>(Boolean.class, List.of(FlagRef::new)))
                .add(new TypedReferenceBuilder<>(Integer.class, List.of(IntRef::new)))
                .add(new TypedReferenceBuilder<>(Long.class, List.of(LongRef::new)))
                .add(new TypedReferenceBuilder<>(Float.class, List.of(DoubleRef::of)))
                .add(new TypedReferenceBuilder<>(Double.class, List.of(DoubleRef::of)))
                .add(new TypedReferenceBuilder<>(ConfigEnum.class, List.of(EnumRef::new)))
                .add(new TypedReferenceBuilder<>(MultiKeyBind.class, List.of(KeyBindRef::new)))
                .add(new TypedReferenceBuilder<>(List.class, List.of(ListRef::new)))
                .add(new TypedReferenceBuilder<>(NBTParsable.class, List.of(NBTRef::new)))
                .add(new TypedReferenceBuilder<>(
                        String.class,
                        ImmutableList.<Function<String, Ref<?>>>builder()
                                .add(EnumRef::fromString)
                                .add(KeyBindRef::fromString)
                                .add(NBTRef::fromString)
                                .add(FlagRef::fromString)
                                .add(IntRef::fromString)
                                .add(LongRef::fromString)
                                .add(StringRef::new)
                                .build()))
                .add(new TypedReferenceBuilder<Object>(Object.class, List.of(ObjectRef.JustOnlyObjectRef::new)))
                .build();
    }
}
