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
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.BaseAttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.versioned.api.VNbt;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public interface AttrKeyValues {
    public static final AttrKeyValue.CustomWidgetFactory<Boolean> BOOLEAN_WIDGET_FACTORY = (s, x, y, inputDx, dy) -> {
        return ExecutableWidget.instance(x, y, dy, dy)
                .setElementHandler(IconElement.statedGuiPredicate(
                        ButtonElement.BUTTON,
                        ButtonElement.BUTTON_INACTIVE,
                        ButtonAction.run(() -> s.valueChange(s, String.valueOf(!s.getOriginValue()))),
                        (bl) -> s.getOriginValue()));
    };
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

    public static WrapperFactory<String, Identifier> IDENTIFIER_FACTORY =
            WrapperFactory.of(Identifier::tryParse, Identifier::toString);

    public static class ClampedIntAttrKeyValue extends BaseAttrKeyValue<Integer> {
        @Getter
        final int min;

        @Getter
        final int max;

        public ClampedIntAttrKeyValue(String key, int value, int min, int max) {
            super(key, value, INT_FACTORY);
            this.min = min;
            this.max = max;
            getValidators().add(i -> i >= this.min && i <= this.max);
        }

        public int clampInput(int val) {
            return MathHelper.clamp(val, min, max);
        }
    }

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
