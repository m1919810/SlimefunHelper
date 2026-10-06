package me.matl114.utils.config.kv;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.Getter;
import me.matl114.gui.basic.*;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.NumberClamp;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.versioned.api.VNbt;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public interface AttrKeyValues {
    public static final AttrKeyValue.CustomWidgetGenerator<Boolean> BOOLEAN_WIDGET_FACTORY =
            (factory, s, x, y, inputDx, dy) -> {
                return factory.createToggleButton(
                        () -> net.minecraft.text.Text.empty(), List::of, ValueAccessor.of(s), true, x, y, dy, dy);
            };

    public static final AttrKeyValue.CustomWidgetGenerator<Integer> INT_WIDGET_FACTORY =
            new AttrKeyValue.CustomWidgetGenerator<>() {
                @Override
                public DrawableWidget generateWidget(
                        ElementFactory factory, AttrKeyValue<Integer> attrKeyValue, int x, int y, int inputDx, int dy) {
                    return generateWidget(factory, attrKeyValue, x, y, inputDx, dy, null);
                }

                @Override
                public DrawableWidget generateWidget(
                        ElementFactory factory,
                        AttrKeyValue<Integer> attrKeyValue,
                        int x,
                        int y,
                        int inputDx,
                        int dy,
                        TextAlign overrideAlign) {
                    return factory.createSlider(
                            attrKeyValue, createIntSlider(attrKeyValue), overrideAlign, x, y, inputDx, dy);
                }
            };
    public static final AttrKeyValue.CustomWidgetGenerator<Double> DOUBLE_WIDGET_FACTORY =
            new AttrKeyValue.CustomWidgetGenerator<>() {
                @Override
                public DrawableWidget generateWidget(
                        ElementFactory factory, AttrKeyValue<Double> attrKeyValue, int x, int y, int inputDx, int dy) {
                    return generateWidget(factory, attrKeyValue, x, y, inputDx, dy, null);
                }

                @Override
                public DrawableWidget generateWidget(
                        ElementFactory factory,
                        AttrKeyValue<Double> attrKeyValue,
                        int x,
                        int y,
                        int inputDx,
                        int dy,
                        TextAlign overrideAlign) {
                    return factory.createSlider(
                            attrKeyValue, createDoubleSlider(attrKeyValue), overrideAlign, x, y, inputDx, dy);
                }
            };

    private static WrapperFactory<Double, Integer> createIntSlider(AttrKeyValue<?> attrKeyValue) {
        Integer min = null;
        Integer max = null;
        for (var validator : attrKeyValue.getValidators()) {
            if (validator instanceof NumberClamp<?> clamp) {
                Integer clampMin = clamp.min() != null ? clamp.min().intValue() : null;
                Integer clampMax = clamp.max() != null ? clamp.max().intValue() : null;
                if (clampMin != null && (min == null || clampMin > min)) {
                    min = clampMin;
                }
                if (clampMax != null && (max == null || clampMax < max)) {
                    max = clampMax;
                }
            }
        }

        if (max == null) {
            if (min == null) {
                max = 10;
                min = 0;
            } else {
                max = min + 10;
            }
        } else if (min == null) {
            if (max <= 10) {
                min = max - 10;
            } else {
                min = 0;
            }
        }
        int finalMin = min;
        int finalMax = max;
        return WrapperFactory.of(
                percentage -> (int) ((1 - percentage) * finalMin + percentage * finalMax),
                current -> Math.clamp((current - finalMin) / (double) (finalMax - finalMin), 0.0D, 1.0D));
    }

    private static WrapperFactory<Double, Double> createDoubleSlider(AttrKeyValue<?> attrKeyValue) {
        Double min = null;
        Double max = null;
        for (var validator : attrKeyValue.getValidators()) {
            if (validator instanceof NumberClamp<?> clamp) {
                Double clampMin = clamp.min() != null ? clamp.min().doubleValue() : null;
                Double clampMax = clamp.max() != null ? clamp.max().doubleValue() : null;
                if (clampMin != null && (min == null || clampMin > min)) {
                    min = clampMin;
                }
                if (clampMax != null && (max == null || clampMax < max)) {
                    max = clampMax;
                }
            }
        }

        if (max == null) {
            if (min == null) {
                max = 10.0D;
                min = 0.0D;
            } else {
                max = min + 10;
            }
        } else if (min == null) {
            if (max <= 10) {
                min = max - 10;
            } else {
                min = 0.0D;
            }
        }
        double finalMin = min;
        double finalMax = max;
        return WrapperFactory.of(
                percentage -> ((1 - percentage) * finalMin + percentage * finalMax),
                current -> Math.clamp((current - finalMin) / (finalMax - finalMin), 0.0D, 1.0D));
    }

    private static <T> WrapperFactory<Double, T> createDoubleSlider(Value<T> value, AttrKeyValue<?> attrKeyValue) {
        Number min = null;
        Number max = null;
        boolean foundClamp = false;
        for (var validator : attrKeyValue.getValidators()) {
            if (validator instanceof NumberClamp<?> clamp) {
                foundClamp = true;
                Number clampMin = clamp.min() instanceof Number number ? number : 0;
                Number clampMax = clamp.max() instanceof Number number ? number : 0;
                if (min == null || clampMin.doubleValue() > min.doubleValue()) {
                    min = clampMin;
                }
                if (max == null || clampMax.doubleValue() < max.doubleValue()) {
                    max = clampMax;
                }
            }
        }
        if (!foundClamp) {
            return null;
        }

        Number sample = value.get() instanceof Number number ? number : min;
        if (sample == null) {
            sample = max;
        }
        if (sample == null) {
            return null;
        }
        double minValue = min.doubleValue();
        double range = max.doubleValue() - minValue;
        Number numberSample = sample;
        return WrapperFactory.of(
                percentage -> (T) numberFromDouble(minValue + range * percentage, numberSample),
                current -> range == 0.0 ? 0.0 : (((Number) current).doubleValue() - minValue) / range);
    }

    private static Number numberFromDouble(double value, Number sample) {
        if (sample instanceof Integer) return (int) Math.round(value);
        if (sample instanceof Long) return Math.round(value);
        if (sample instanceof Float) return (float) value;
        if (sample instanceof Short) return (short) Math.round(value);
        if (sample instanceof Byte) return (byte) Math.round(value);
        return value;
    }

    public static WrapperFactory<String, Boolean> BOOL_FACTORY = WrapperFactory.of(
            (s) -> {
                switch (s) {
                    case "true" -> {
                        return Boolean.TRUE;
                    }
                    case "false" -> {
                        return Boolean.FALSE;
                    }
                    default -> {
                        throw WrapperFactory.PARSE_FAILURE;
                    }
                }
            },
            (v) -> v == Boolean.TRUE ? "true" : "false");

    public static WrapperFactory<String, Integer> INT_FACTORY =
            WrapperFactory.of(Integer::parseInt, s -> s != null ? String.valueOf(s) : "0");

    public static WrapperFactory<String, Long> LONG_FACTORY =
            me.matl114.utils.config.WrapperFactory.of(Long::parseLong, s -> s != null ? String.valueOf(s) : "0");

    public static WrapperFactory<String, Identifier> IDENTIFIER_FACTORY =
            WrapperFactory.of(Identifier::tryParse, Identifier::toString);

    public static class ClampedIntAttrKeyValue extends BaseAttrKeyValue<Integer> {
        @Getter
        final int min;

        @Getter
        final int max;

        public ClampedIntAttrKeyValue(String key, int value, int min, int max) {
            super(key, value, INT_WIDGET_FACTORY, INT_FACTORY);
            this.min = min;
            this.max = max;
            getValidators().add(new NumberClamp<>(min, max));
        }

        public int clampInput(int val) {
            return MathHelper.clamp(val, min, max);
        }
    }

    WrapperFactory<String, Float> FLOAT_FACTORY =
            WrapperFactory.of(Float::parseFloat, s -> s != null ? String.valueOf(s) : "0.0");

    WrapperFactory<String, Double> DOUBLE_FACTORY =
            WrapperFactory.of(Double::parseDouble, s -> s != null ? String.valueOf(s) : "0.0");

    WrapperFactory<String, String> STRING_FACTORY = WrapperFactory.of(Function.identity(), Function.identity());

    WrapperFactory<String, TextColor> COLOR_FACTORY =
            WrapperFactory.of((s) -> TextColor.parse(s).getOrThrow(), TextColor::getName);

    public static final WrapperFactory<String, NbtElement> NBT_FACTORY = WrapperFactory.of(
            s -> {
                if (s == null || s.isEmpty()) return null;
                return VNbt.getInstance().readNbtNoRegistry(s);
            },
            val -> val == null ? "" : VNbt.getInstance().writeNbt(val));
    public static final WrapperFactory<String, NbtCompound> NBT_COMPOUND_FACTORY = WrapperFactory.of(
            s -> {
                if (s == null || s.isEmpty()) return null;
                if (VNbt.getInstance().readNbtNoRegistry(s) instanceof NbtCompound cpd) {
                    return cpd;
                }
                throw WrapperFactory.PARSE_FAILURE;
            },
            val -> val == null ? "" : VNbt.getInstance().writeNbt(val));

    static final Gson gson = new Gson();

    public static final WrapperFactory<String, JsonElement> JSON_ELEMENT_FACTORY =
            WrapperFactory.of((str) -> gson.fromJson(str, JsonElement.class), gson::toJson);

    static final Type LIST_TYPE = new TypeToken<List<String>>() {}.getType();
    public static final WrapperFactory<String, List<String>> STR_LIST_FACTORY =
            WrapperFactory.of(s -> gson.fromJson(s, LIST_TYPE), gson::toJson);

    static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    public static final WrapperFactory<String, Map<String, String>> STR_MAP_FACTORY =
            WrapperFactory.of(s -> gson.fromJson(s, MAP_TYPE), gson::toJson);
}
