package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.With;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

@With
public record Pos3(int x, int y, int z) implements NBTParsable<Pos3> {
    public static Pos3 from(BlockPos pos) {
        return new Pos3(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockPos to() {
        return new BlockPos(x, y, z);
    }

    public static final NBTType<Pos3> TYPE = new NBTType<>(
            "pos3",
            RecordCodecBuilder.<Pos3>create(s -> s.group(
                            Codec.INT.fieldOf("x").forGetter(Pos3::x),
                            Codec.INT.fieldOf("y").forGetter(Pos3::y),
                            Codec.INT.fieldOf("z").forGetter(Pos3::z))
                    .apply(s, Pos3::new)),
            (AttrKeyValue.CustomWidgetFactory<Pos3>) (s, x, y, dx, dy) -> {
                SubScreenWidget subScreenWidget = SubScreenWidget.instance(x, y, dx, dy);
                int half = dx / 4;
                WrapperFactory<Integer, Pos3> firstWrapper =
                        WrapperFactory.of((d) -> s.getOriginValue().withX(d), Pos3::x);
                WrapperFactory<Integer, Pos3> secondWrapper =
                        WrapperFactory.of((d) -> s.getOriginValue().withY(d), Pos3::y);
                WrapperFactory<Integer, Pos3> thirdWrapper =
                        WrapperFactory.of((d) -> s.getOriginValue().withZ(d), Pos3::z);

                return subScreenWidget
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(s, firstWrapper, NBTTypes.INT_TYPE)
                                .generateValueWidget(0, 0, half, dy))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(s, secondWrapper, NBTTypes.INT_TYPE)
                                .generateValueWidget(half, 0, half, dy))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(s, thirdWrapper, NBTTypes.INT_TYPE)
                                .generateValueWidget(2 * half, 0, half, dy))
                        .addDrawableChild(ExecutableWidget.instance(3 * half, 0, half / 2, dy)
                                .setElementHandler(new ButtonElement(
                                                TextProvider.of(Text.translatableWithFallback(
                                                        "widget.nbt-parsable.pos3.here", "Here")),
                                                ButtonAction.run(() -> {
                                                    var pl = MinecraftClient.getInstance().player;
                                                    if (pl != null) {
                                                        s.valueChangeInternal(null, from(pl.getBlockPos()));
                                                    }
                                                }))
                                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                                "widget.nbt-parsable.pos3.here.tooltips", "")))))
                        .addDrawableChild(ExecutableWidget.instance(4 * half - half / 2, 0, half / 2, dy)
                                .setElementHandler(new ButtonElement(
                                                TextProvider.of(Text.translatableWithFallback(
                                                        "widget.nbt-parsable.pos3.zero", "Zero")),
                                                ButtonAction.run(() -> {
                                                    s.valueChangeInternal(null, new Pos3(0, 0, 0));
                                                }))
                                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                                "widget.nbt-parsable.pos3.zero.tooltips", "")))));
            },
            new Pos3(0, 0, 0));

    @Override
    public NBTType<Pos3> type() {
        return TYPE;
    }
}
