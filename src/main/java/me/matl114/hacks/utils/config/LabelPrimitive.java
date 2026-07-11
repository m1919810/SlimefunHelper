package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import lombok.With;
import me.matl114.gui.basic.DisplayWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.complex.RawTextElement;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.text.Text;

@With
public record LabelPrimitive<T>(String label, Primitive<T> primitive) implements NBTParsable<LabelPrimitive<T>> {
    public static final NBTType<LabelPrimitive<?>> TYPE = new NBTType<>(
                    LabelPrimitive.class,
                    RecordCodecBuilder.create(oinstance -> oinstance
                            .group(
                                    Codec.STRING.fieldOf("label").forGetter(LabelPrimitive::label),
                                    Primitive.TYPE.typeCodec().fieldOf("data").forGetter(LabelPrimitive::primitive))
                            .apply(oinstance, LabelPrimitive::new)),
                    (instance, x, y, dx, dy) -> {
                        SubScreenWidget subScreen = new SubScreenWidget(x, y, dx, dy);
                        String label = instance.getOriginValue().label();
                        subScreen.addDrawableChild(DisplayWidget.instance(0, 0, 2 * dy, dy)
                                .setRenderHandler(new RawTextElement(Text.translatableWithFallback(label, label), -1)));
                        WrapperFactory<Primitive<?>, LabelPrimitive> wrapper =
                                WrapperFactory.of((d) -> new LabelPrimitive<>(label, d), LabelPrimitive::primitive);
                        subScreen.addDrawableChild(
                                new TypeConvertAttrKeyValue<>(instance, wrapper, Primitive.TYPE.cast())
                                        .generateValueWidget(2 * dy, 0, dx - 2 * dy, dy));
                        return subScreen;
                    },
                    new LabelPrimitive<>("", Primitive.TYPE.empty()))
            .cast();

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
