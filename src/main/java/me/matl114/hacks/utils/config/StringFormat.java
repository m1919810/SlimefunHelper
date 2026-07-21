package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.elements.IconElement;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.PairLikeFactory;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

@Accessors(fluent = true)
public class StringFormat implements NBTParsable<StringFormat> {
    public StringFormat(List<String> f1, String f2) {
        this.formattingArgument = f1;
        this.formatString = f2;
    }

    @Getter
    final List<String> formattingArgument;

    @Getter
    final String formatString;

    public StringFormat withFormatString(String formatString) {
        return new StringFormat(formattingArgument, formatString);
    }

    BiConsumer<Map<String, String>, Consumer<Object>> cachedFormatter;
    public static NBTType<StringFormat> TYPE = NBTTypes.createPairWithKey(
            StringFormat.class,
            Codec.list(Codec.STRING),
            List.of(),
            "arguments",
            NBTTypes.STRING_TYPE,
            "format",
            PairLikeFactory.of(StringFormat::new, StringFormat::formattingArgument, StringFormat::formatString),
            (s, x, y, dx, dy) -> {
                return new ExecutableWidget(x + dx - dy, y, dy, dy)
                        .setElementHandler(
                                IconElement.fixedGui(Constants.FORMATTING_TEXTURE_SPRITE, ButtonAction.empty())
                                        .withTooltips(TooltipHandler.of(generateTooltipsForArgument(s))));
            },
            (factory) -> {
                return (s, x, y, dx, dy) -> {
                    return factory.generateWidget(s, x, y, dx - dy, dy);
                };
            });

    public static List<Text> generateTooltipsForArgument(List<String> formattingArgument) {
        List<Text> tooltips = new ArrayList<>();
        tooltips.add(Text.literal("在该参数中可以使用{...}来进行变量替换"));
        tooltips.add(Text.literal("在真实应用时,字符串中的{...}会被替换为对应变量"));
        tooltips.add(Text.literal("以下是当前位置可以使用的参数: "));
        for (var re : formattingArgument) {
            tooltips.add(Text.literal("- {%s}".formatted(re)));
        }
        return tooltips;
    }

    @Override
    public NBTType<StringFormat> type() {
        return TYPE.cast();
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return type instanceof StringFormat
                && ((StringFormat) type).formattingArgument().equals(formattingArgument());
    }

    @Override
    public <W> Optional<StringFormat> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof NBTRef nbtRef && nbtRef.get() instanceof StringFormat format) {
            return Optional.of(this.withFormatString(format.formatString()));
        }
        return Optional.empty();
    }

    public static final Pattern pattern = Pattern.compile("\\{[^{}]*\\}");

    private <T> BiConsumer<Map<String, T>, Consumer<T>> construct0() {
        if (cachedFormatter == null) {
            Matcher matcher = pattern.matcher(formatString);
            int lastEnd = 0;
            List<BiConsumer<Consumer<Object>, Map<String, String>>> sequenceBuilders = new ArrayList<>();
            while (matcher.find()) {
                String lastSeq = formatString.substring(lastEnd, matcher.start());
                sequenceBuilders.add((a, b) -> a.accept(lastSeq));
                String placeholder = matcher.group();
                if (placeholder.length() <= 2) {
                    sequenceBuilders.add((a, b) -> a.accept(placeholder));
                } else {
                    String key = placeholder.substring(1, placeholder.length() - 1);
                    sequenceBuilders.add((a, b) -> a.accept(b.getOrDefault(key, placeholder)));
                }
                lastEnd = matcher.end();
            }
            if (lastEnd < formatString.length()) {
                String lastSeq = formatString.substring(lastEnd);
                sequenceBuilders.add((a, b) -> a.accept(lastSeq));
            }
            cachedFormatter = (map, consumer) -> {
                for (var re : sequenceBuilders) {
                    re.accept(consumer, map);
                }
            };
        }
        return (BiConsumer) cachedFormatter;
    }

    public String format(String... arguments) {
        int size = Math.min(arguments.length, formattingArgument().size());
        Map<String, String> availableMap = new HashMap<>();
        for (int i = 0; i < size; i++) {
            availableMap.put(formattingArgument.get(i), arguments[i]);
        }
        BiConsumer<Map<String, String>, Consumer<String>> builder = construct0();
        StringBuilder result = new StringBuilder();
        builder.accept(availableMap, result::append);
        return result.toString();
    }

    public MutableText formatText(Object... arguments) {
        int size = Math.min(arguments.length, formattingArgument().size());
        Map<String, Object> availableMap = new HashMap<>();
        for (int i = 0; i < size; i++) {
            availableMap.put(formattingArgument.get(i), arguments[i]);
        }
        BiConsumer<Map<String, Object>, Consumer<Object>> builder = construct0();
        ChatUtils.TextBuilder result = ChatUtils.builder();
        builder.accept(availableMap, (obj) -> {
            if (obj instanceof Text txt) {
                result.appendText(txt);
            } else {
                result.withColorString(obj == null ? "null" : obj.toString());
            }
        });
        return result.end().build();
    }
}
