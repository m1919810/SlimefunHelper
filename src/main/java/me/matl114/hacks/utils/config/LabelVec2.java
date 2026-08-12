package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import lombok.With;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.PairLikeFactory;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.text.Text;

@With
public record LabelVec2(String xLabel, String yLabel, Vec2 data) implements NBTParsable<LabelVec2> {
    @Override
    public NBTType<LabelVec2> type() {
        return TYPE;
    }

    public static final NBTType<LabelVec2> TYPE = new NBTType<>(
            "labelvec2",
            RecordCodecBuilder.create(s -> s.group(
                            Codec.STRING.fieldOf("x_label").forGetter(LabelVec2::xLabel),
                            Codec.STRING.fieldOf("y_label").forGetter(LabelVec2::yLabel),
                            Vec2.TYPE.typeCodec().fieldOf("data").forGetter(LabelVec2::data))
                    .apply(s, LabelVec2::new)),
            (s, x, y, dx, dy) -> {
                LabelVec2 originalLabel = s.getOriginValue();
                SubScreenWidget subScreenWidget = SubScreenWidget.instance(x, y, dx, dy);
                int half = dx / 2;
                WrapperFactory<Vec2, LabelVec2> wrapper = WrapperFactory.of(originalLabel::withData, LabelVec2::data);
                PairLikeFactory<Double, Double, LabelVec2> pairFactory = Vec2.PAIR_FACTORY.concat(wrapper);
                return subScreenWidget
                        .addDrawableChild(DisplayWidget.instance(0, 0, 2 * dy, dy)
                                .setRenderHandler(new ButtonElement(
                                                TextProvider.of(Text.translatableWithFallback(
                                                        originalLabel.xLabel(), originalLabel.xLabel())),
                                                ButtonAction.empty())
                                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                                originalLabel.xLabel() + ".tooltips", "")))))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(
                                        s, pairFactory.asFirstWrapper(s::getOriginValue), NBTTypes.DOUBLE_TYPE)
                                .generateValueWidget(2 * dy, 0, half - 2 * dy, dy))
                        .addDrawableChild(DisplayWidget.instance(half, 0, 2 * dy, dy)
                                .setRenderHandler(new ButtonElement(
                                                TextProvider.of(Text.translatableWithFallback(
                                                        originalLabel.yLabel(), originalLabel.yLabel())),
                                                ButtonAction.empty())
                                        .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                                originalLabel.yLabel() + ".tooltips", "")))))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(
                                        s, pairFactory.asSecondWrapper(s::getOriginValue), NBTTypes.DOUBLE_TYPE)
                                .generateValueWidget(half + 2 * dy, 0, half - 2 * dy, dy));
            },
            new LabelVec2("", "", new Vec2(0, 0)));

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return type instanceof LabelVec2 label2
                && Objects.equals(xLabel, label2.xLabel())
                && Objects.equals(yLabel, label2.yLabel());
    }

    @Override
    public <W> Optional<LabelVec2> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof NBTRef nby) {
            var bb = nby.get();
            if (bb instanceof LabelVec2 bbb) {
                return Optional.of(this.withData(bbb.data()));
            } else if (bb instanceof Vec2 vvv) {
                return Optional.of(this.withData(vvv));
            }
        }
        return Optional.empty();
    }
}
