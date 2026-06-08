package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.With;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.text.Text;

@With
public record Vec3(double x, double y, double z) implements NBTParsable<Vec3> {
    public static final NBTType<Vec3> TYPE = new NBTType<>(
            Vec3.class,
            RecordCodecBuilder.<Vec3>create(s -> s.group(
                            Codec.DOUBLE.fieldOf("x").forGetter(Vec3::x),
                            Codec.DOUBLE.fieldOf("y").forGetter(Vec3::y),
                            Codec.DOUBLE.fieldOf("z").forGetter(Vec3::z))
                    .apply(s, Vec3::new)),
            (s, x, y, dx, dy) -> {
                SubScreenWidget subScreenWidget = SubScreenWidget.instance(x, y, dx, dy);
                int half = dx / 3;
                WrapperFactory<Double, Vec3> firstWrapper =
                        WrapperFactory.of((d) -> s.getOriginValue().withX(d), Vec3::x);
                WrapperFactory<Double, Vec3> secondWrapper =
                        WrapperFactory.of((d) -> s.getOriginValue().withY(d), Vec3::y);
                WrapperFactory<Double, Vec3> thirdWrapper =
                        WrapperFactory.of((d) -> s.getOriginValue().withZ(d), Vec3::z);

                return subScreenWidget
                        .addDrawableChild(DisplayWidget.instance(0, 0, dy, dy)
                                .setRenderHandler(
                                        new ButtonElement(TextProvider.of(Text.literal("X:")), ButtonAction.empty())))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(s, firstWrapper, NBTTypes.DOUBLE_TYPE)
                                .generateValueWidget(dy, 0, half - dy, dy))
                        .addDrawableChild(DisplayWidget.instance(half, 0, dy, dy)
                                .setRenderHandler(
                                        new ButtonElement(TextProvider.of(Text.literal("Y:")), ButtonAction.empty())))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(s, secondWrapper, NBTTypes.DOUBLE_TYPE)
                                .generateValueWidget(half + dy, 0, half - dy, dy))
                        .addDrawableChild(DisplayWidget.instance(2 * half, 0, dy, dy)
                                .setRenderHandler(
                                        new ButtonElement(TextProvider.of(Text.literal("Z:")), ButtonAction.empty())))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(s, thirdWrapper, NBTTypes.DOUBLE_TYPE)
                                .generateValueWidget(2 * half + dy, 0, half - dy, dy));
            },
            new Vec3(0, 0, 0));

    @Override
    public NBTType<Vec3> type() {
        return TYPE;
    }
}
