package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.matl114.gui.basic.*;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.PairLikeFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.util.math.Vec2f;

public record Vec2(double x, double y) implements NBTParsable<Vec2> {
    public static final PairLikeFactory<Double, Double, Vec2> PAIR_FACTORY =
            PairLikeFactory.of(Vec2::new, Vec2::x, Vec2::y);

    public Vec2f toVec2f() {
        return new Vec2f((float) x, (float) y);
    }

    public static final NBTType<Vec2> TYPE = new NBTType<>(
            Vec2.class,
            RecordCodecBuilder.<Vec2>create(s -> s.group(
                            Codec.DOUBLE.fieldOf("x").forGetter(Vec2::x),
                            Codec.DOUBLE.fieldOf("y").forGetter(Vec2::y))
                    .apply(s, Vec2::new)),
            (s, x, y, dx, dy) -> {
                SubScreenWidget subScreenWidget = SubScreenWidget.instance(x, y, dx, dy);
                int half = dx / 2;
                return subScreenWidget
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(
                                        s, PAIR_FACTORY.asFirstWrapper(s::getOriginValue), NBTTypes.DOUBLE_TYPE)
                                .generateValueWidget(0, 0, half, dy))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(
                                        s, PAIR_FACTORY.asSecondWrapper(s::getOriginValue), NBTTypes.DOUBLE_TYPE)
                                .generateValueWidget(half, 0, half, dy));
            },
            new Vec2(0, 0));

    @Override
    public NBTType<Vec2> type() {
        return TYPE;
    }
}
