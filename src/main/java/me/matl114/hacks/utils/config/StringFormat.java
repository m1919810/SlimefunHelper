package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.elements.IconElement;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

@Accessors(fluent = true)
public class StringFormat implements NBTParsable<StringFormat> {
    public StringFormat(List<String> f1, String f2) {
        this(f1, f2, false);
    }

    public StringFormat(List<String> f1, String f2, boolean colorString) {
        this.formattingArgument = f1;
        this.formatString = f2;
        this.colorString = colorString;
    }

    @Getter
    final List<String> formattingArgument;

    @Getter
    final String formatString;

    @Getter
    final boolean colorString;

    public StringFormat withFormatString(String formatString) {
        return new StringFormat(formattingArgument, formatString, colorString);
    }

    BiConsumer<Map<String, String>, Consumer<Object>> cachedFormatter;
    public static NBTType<StringFormat> TYPE = new NBTType<>(
            StringFormat.class,
            RecordCodecBuilder.create(oInstance -> oInstance
                    .group(
                            Codec.list(Codec.STRING).fieldOf("arguments").forGetter(StringFormat::formattingArgument),
                            Codec.STRING.fieldOf("format").forGetter(StringFormat::formatString),
                            Codec.BOOL.optionalFieldOf("color_str", false).forGetter(StringFormat::colorString))
                    .apply(oInstance, StringFormat::new)),
            (s, x, y, dx, dy) -> {
                StringFormat original = s.getOriginValue();
                AttrKeyValue<String> wrapper = new TypeConvertAttrKeyValue<>(
                        s,
                        WrapperFactory.of(original::withFormatString, StringFormat::formatString),
                        NBTTypes.STRING_TYPE);
                SubScreenWidget subScreenWidget = SubScreenWidget.instance(x, y, dx, dy);
                boolean hasFormatArgument = !original.formattingArgument().isEmpty();
                int width = dx;
                if (hasFormatArgument) {
                    width -= dy;
                }
                if (original.colorString()) {
                    width -= dy;
                }
                subScreenWidget.addDrawableChild(wrapper.generateValueWidget(0, 0, width, dy));
                if (hasFormatArgument) {
                    subScreenWidget.addDrawableChild(new ExecutableWidget(width, 0, dy, dy)
                            .setElementHandler(
                                    IconElement.fixedGui(Constants.FORMATTING_TEXTURE_SPRITE, ButtonAction.empty())
                                            .withTooltips(TooltipHandler.of(
                                                    generateTooltipsForArgument(original.formattingArgument())))));
                }
                if (original.colorString()) {
                    subScreenWidget.addDrawableChild(new ExecutableWidget(width + dy, 0, dy, dy)
                            .setElementHandler(IconElement.fixedGui(
                                            Constants.EDITOR_SPRITE,
                                            ButtonAction.run(StringFormat::openWikiColorString))
                                    .withTooltips(TooltipHandler.of(
                                            (el) -> s.getOriginValue().generateColorStringPreview()))));
                }
                return subScreenWidget;
            },
            new StringFormat(List.of(), ""));

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

    public static final String URL1 = "https://zh.minecraft.wiki/w/%E6%A0%BC%E5%BC%8F%E5%8C%96%E4%BB%A3%E7%A0%81";
    public static final String URL2 = "https://mcg.tuanzi.ink/";

    public static void openWikiColorString() {
        Util.getOperatingSystem().open(URL1);
        Util.getOperatingSystem().open(URL2);
    }

    public List<Text> generateColorStringPreview() {
        List<Text> tooltips = new ArrayList<>();
        tooltips.add(Text.literal("该参数使用\"格式化代码\"来编码颜色字符"));
        tooltips.add(Text.literal("详细规则点击该按钮打开MCWiki查询"));
        tooltips.add(Text.literal("其中,&字符可以替代分节符,同时支持使用&x"));
        tooltips.add(Text.literal("当前内容预览:"));
        tooltips.add(this.formatText());
        return tooltips;
    }

    @Override
    public NBTType<StringFormat> type() {
        return TYPE.cast();
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return type instanceof StringFormat
                && ((StringFormat) type).formattingArgument().equals(formattingArgument())
                && (((StringFormat) type).colorString() == (this.colorString));
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
