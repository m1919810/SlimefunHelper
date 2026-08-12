package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.Optional;
import lombok.With;
import me.matl114.gui.basic.DisplayWidget;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.complex.RawTextElement;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.PairLikeFactory;
import net.minecraft.text.Text;

@With
public record LabelPrimitive<T>(String label, Primitive<T> primitive) implements NBTParsable<LabelPrimitive<T>> {
    public static final NBTType<LabelPrimitive<?>> TYPE = NBTTypes.createPairWithKey(
            "labelprimitive",
            Codec.STRING,
            "",
            "label",
            Primitive.TYPE.cast(),
            "data",
            PairLikeFactory.<String, Primitive<?>, LabelPrimitive<?>>of(
                    LabelPrimitive::new, LabelPrimitive::label, LabelPrimitive::primitive),
            (string, x, y, dx, dy) -> {
                return DisplayWidget.instance(0, 0, 2 * dy, dy)
                        .setRenderHandler(new RawTextElement(Text.translatableWithFallback(string, string), -1)
                                .withTooltips(TooltipHandler.of(
                                        ChatUtils.parseTooltipsTranslation(string + ".tooltips", ""))));
            },
            (factory) -> {
                return (s, x, y, dx, dy) -> {
                    return factory.generateWidget(s, x + 2 * dy, y, dx - 2 * dy, dy);
                };
            });

    @Override
    public NBTType<LabelPrimitive<T>> type() {
        return TYPE.cast();
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return type instanceof LabelPrimitive<?> primitive && Objects.equals(primitive.label, this.label);
    }

    @Override
    public <W> Optional<LabelPrimitive<T>> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof NBTRef nbtRef) {
            if (nbtRef.get() instanceof Primitive<?> primitive && primitive.valueType() == this.primitive.valueType()) {
                return Optional.of(this.withPrimitive((Primitive) primitive));
            }
            if (nbtRef.get() instanceof LabelPrimitive<?> primitive
                    && primitive.primitive.valueType() == this.primitive.valueType()) {
                return Optional.of(this.withPrimitive((Primitive<T>) primitive.primitive));
            }
        } else {
            var optional = Primitive.convertPrimitives(ref);
            if (optional.isPresent()) {
                var re = optional.get();
                if (re.valueType() == this.primitive.valueType()) {
                    return Optional.of(this.withPrimitive((Primitive<T>) re));
                }
            }
        }
        return Optional.empty();
    }
}
