package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.PairLikeFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.text.Text;

public record TracingOption(boolean box, boolean line) implements NBTParsable<TracingOption> {
    public static final PairLikeFactory<Boolean, Boolean, TracingOption> PAIR_FACTORY =
            PairLikeFactory.of(TracingOption::new, TracingOption::box, TracingOption::line);

    public static final NBTType<TracingOption> TYPE = new NBTType<>(
            TracingOption.class,
            RecordCodecBuilder.<TracingOption>create(s -> s.group(
                            Codec.BOOL.fieldOf("box").forGetter(TracingOption::box),
                            Codec.BOOL.fieldOf("line").forGetter(TracingOption::line))
                    .apply(s, TracingOption::new)),
            (s, x, y, dx, dy) -> {
                SubScreenWidget subScreenWidget = SubScreenWidget.instance(x, y, dx, dy);
                return subScreenWidget
                        .addDrawableChild(DisplayWidget.instance(0, 0, 2 * dy, dy)
                                .setRenderHandler(
                                        new ButtonElement(TextProvider.of(Text.literal("Box:")), ButtonAction.empty())))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(
                                        s, PAIR_FACTORY.asFirstWrapper(s::getOriginValue), NBTTypes.BOOLEAN_TYPE)
                                .generateValueWidget(2 * dy, 0, dy, dy))
                        .addDrawableChild(DisplayWidget.instance(3 * dy, 0, 2 * dy, dy)
                                .setRenderHandler(new ButtonElement(
                                        TextProvider.of(Text.literal("Line:")), ButtonAction.empty())))
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(
                                        s, PAIR_FACTORY.asSecondWrapper(s::getOriginValue), NBTTypes.BOOLEAN_TYPE)
                                .generateValueWidget(5 * dy, 0, dy, dy));
            },
            new TracingOption(false, false));

    @Override
    public NBTType<TracingOption> type() {
        return TYPE;
    }

    public boolean isEmpty() {
        return !box && !line;
    }
}
