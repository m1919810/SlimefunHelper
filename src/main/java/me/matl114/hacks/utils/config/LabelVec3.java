package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import lombok.With;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DisplayWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.text.Text;

@With
public record LabelVec3(String xLabel, String yLabel, String zLabel, Vec3 data) implements NBTParsable<LabelVec3> {
    @Override
    public NBTType<LabelVec3> type() {
        return TYPE;
    }

    public static final NBTType<LabelVec3> TYPE = new NBTType<>(
            LabelVec3.class,
            RecordCodecBuilder.create(s -> s.group(
                            Codec.STRING.fieldOf("x_label").forGetter(LabelVec3::xLabel),
                            Codec.STRING.fieldOf("y_label").forGetter(LabelVec3::yLabel),
                            Codec.STRING.fieldOf("z_label").forGetter(LabelVec3::zLabel),
                            Vec3.TYPE.typeCodec().fieldOf("data").forGetter(LabelVec3::data))
                    .apply(s, LabelVec3::new)),
            (s, x, y, dx, dy) -> {
                SubScreenWidget subScreenWidget = SubScreenWidget.instance(x, y, dx, dy);
                int half = dx / 3;
                int label = Math.min(dy, (int) half / 2);
                var original = s.getOriginValue();
                WrapperFactory<Double, LabelVec3> firstWrapper =
                        WrapperFactory.of((d) -> s.getOriginValue().withX(d), LabelVec3::x);
                WrapperFactory<Double, LabelVec3> secondWrapper =
                        WrapperFactory.of((d) -> s.getOriginValue().withY(d), LabelVec3::y);
                WrapperFactory<Double, LabelVec3> thirdWrapper =
                        WrapperFactory.of((d) -> s.getOriginValue().withZ(d), LabelVec3::z);

                return subScreenWidget
                        .addDrawableChild(DisplayWidget.instance(0, 0, label, dy)
                                .setRenderHandler(new ButtonElement(
                                        TextProvider.of(Text.literal(original.xLabel())), ButtonAction.empty())))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(s, firstWrapper, NBTTypes.DOUBLE_TYPE)
                                .generateValueWidget(label, 0, half - label, dy))
                        .addDrawableChild(DisplayWidget.instance(half, 0, label, dy)
                                .setRenderHandler(new ButtonElement(
                                        TextProvider.of(Text.literal(original.yLabel())), ButtonAction.empty())))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(s, secondWrapper, NBTTypes.DOUBLE_TYPE)
                                .generateValueWidget(half + label, 0, half - label, dy))
                        .addDrawableChild(DisplayWidget.instance(2 * half, 0, label, dy)
                                .setRenderHandler(new ButtonElement(
                                        TextProvider.of(Text.literal(original.zLabel())), ButtonAction.empty())))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(s, thirdWrapper, NBTTypes.DOUBLE_TYPE)
                                .generateValueWidget(2 * half + label, 0, half - label, dy));
            },
            new LabelVec3("", "", "", new Vec3(0, 0, 0)));

    public LabelVec3 withX(double x) {
        return withData(data.withX(x));
    }

    public LabelVec3 withY(double y) {
        return withData(data.withY(y));
    }

    public LabelVec3 withZ(double z) {
        return withData(data.withZ(z));
    }

    public double x() {
        return data.x();
    }

    public double y() {
        return data.y();
    }

    public double z() {
        return data.z();
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return type instanceof LabelVec3 vec3
                && Objects.equals(vec3.xLabel, xLabel)
                && Objects.equals(vec3.yLabel, yLabel)
                && Objects.equals(vec3.zLabel, zLabel);
    }

    @Override
    public <W> Optional<LabelVec3> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof NBTRef nbt) {
            var re = nbt.get();
            if (re instanceof LabelVec3 lbb) {
                return Optional.of((LabelVec3) this.withData(lbb.data()));
            } else if (re instanceof Vec3 ddd) {
                return Optional.of(this.withData(ddd));
            }
        }
        return Optional.empty();
    }
}
