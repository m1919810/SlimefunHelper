package me.matl114.utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import me.matl114.utils.chat.SimpleOrderedTextVisitor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;
import net.minecraft.util.Unit;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChatUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isHighSurrogate(char c) {
        return c >= 0xD800 && c <= 0xDBFF;
    }

    public static boolean isLowSurrogate(char c) {
        return c >= 0xDC00 && c <= 0xDFFF;
    }

    // 判断字符是否是普通字符（BMP字符，且不在代理对范围内）
    public static boolean isNormalCharacter(char c) {
        return (!isHighSurrogate(c) && !isLowSurrogate(c));
    }

    public static String toUnicodedString(String str) {
        StringBuilder unicodeStr = new StringBuilder();

        // 遍历字符串中的每个字符，转换为 Unicode 编码格式
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            // 将每个字符转换为 Unicode 编码形式，格式
            unicodeStr.append(toFullWidth(ch));
        }
        return unicodeStr.toString();
    }

    public static char toFullWidth(char c) {

        if (c >= 'a' && c <= 'z') {
            // 转换为全角字母
            return ((char) (c + 0xFEE0));
        } else if (c >= 'A' && c <= 'Z') {
            // 转换为全角字母
            return ((char) (c + 0xFEE0));
        } else if (c >= '0' && c <= '9') {
            // 转换为全角数字
            return ((char) (c + 0xFEE0));
        } else {
            // 其他字符保持不变
            return c;
        }
    }

    private static final Pattern FORMAT_PATTERN = Pattern.compile("(§[0-9a-fk-orx])|(\\n)", Pattern.CASE_INSENSITIVE);
    private static final Style EMPTY =
            Style.EMPTY.withItalic(false); // Paper - Improve Legacy Component serialization size
    private static final Style RESET = Style.EMPTY
            .withBold(false)
            .withItalic(false)
            .withUnderline(false)
            .withStrikethrough(false)
            .withObfuscated(false);
    private static final Map<Character, Formatting> formatMap;
    private static final Map<TextColor, Formatting> colorToFormat;

    static {
        ImmutableMap.Builder<Character, Formatting> builder = ImmutableMap.builder();
        for (Formatting format : Formatting.values()) {
            builder.put(Character.toLowerCase(format.toString().charAt(1)), format);
        }
        formatMap = builder.build();
        colorToFormat = new HashMap<>();
        TextColor.FORMATTING_TO_COLOR.forEach((f, t) -> colorToFormat.put(t, f));
    }

    @ApiMethod
    public static MutableText textFromLegacyString(String value) {
        if (value == null) {
            return Text.empty();
        }
        //        MutableText base = Text.empty();
        //        // Object currentStyle = ChatEnum.STYLE_EMPTY;
        //        Style currentStyle = EMPTY;
        TextBuilder builder = new TextBuilder();
        Matcher matcher = FORMAT_PATTERN.matcher(value);
        String match = null;
        StringBuilder hexColor = null;
        int currentIndex = 0;
        boolean hasReset = false;
        boolean needsAdd = false;
        find_any:
        while (matcher.find()) {
            int groupId = 0;
            while ((match = matcher.group(++groupId)) == null) {}
            int index = matcher.start(groupId);
            if (index > currentIndex) {
                builder.with(value.substring(currentIndex, index));
                needsAdd = false;
                //                Text addition =
                //                        Text.literal(value.substring(currentIndex, index)).setStyle(currentStyle);
                currentIndex = index;
                // base.append(addition);
            }
            switch (groupId) {
                case 1:
                    char c = match.toLowerCase(java.util.Locale.ENGLISH).charAt(1);
                    if (c == 'x') {
                        hexColor = new StringBuilder("#");
                    } else if (hexColor != null) {
                        hexColor.append(c);
                        if (hexColor.length() == 7) {
                            builder.withStyle(RESET.withColor(TextColor.parse(hexColor.toString())
                                    .result()
                                    .get()));
                            //                            currentStyle =
                            // RESET.withColor(TextColor.parse(hexColor.toString())
                            //                                    .result()
                            //                                    .get());
                            hexColor = null;
                        }
                    } else {
                        Formatting format = formatMap.get(c);
                        if (format.isModifier() && format != Formatting.RESET) {
                            switch (format) {
                                case BOLD:
                                    builder.withBold(true);
                                    // currentStyle = currentStyle.withBold(Boolean.TRUE);
                                    break;
                                case ITALIC:
                                    builder.withItalic(true);
                                    // currentStyle = currentStyle.withItalic(Boolean.TRUE);
                                    break;
                                case STRIKETHROUGH:
                                    builder.withStrikethrough(true);
                                    // currentStyle = currentStyle.withStrikethrough(Boolean.TRUE);
                                    break;
                                case UNDERLINE:
                                    builder.withUnderline(true);
                                    // currentStyle = currentStyle.withUnderline(Boolean.TRUE);
                                    break;
                                case OBFUSCATED:
                                    builder.withObfuscated(true);
                                    // currentStyle = currentStyle.withObfuscated(Boolean.TRUE);
                                    break;
                                default:
                                    throw new AssertionError("Unexpected message format");
                            }
                        } else { // Color resets formatting
                            // Paper start - Improve Legacy Component serialization size
                            builder.withReset(format, hasReset);
                            hasReset = true;
                            // Paper end - Improve Legacy Component serialization size
                        }
                    }
                    needsAdd = true;
                    break;
                case 2:
                    if (needsAdd) {
                        builder.with(value.substring(currentIndex, index));
                        // Text addition = Text.literal(value.substring(currentIndex, index))
                        //         .setStyle(currentStyle);
                        //                        base.append(addition);
                    }
                    // ignore \n
                    // return base;
            }
            currentIndex = matcher.end(groupId);
        }
        int len = value.length();
        if (currentIndex < value.length() || needsAdd) {
            builder.with(value.substring(currentIndex, len));
            //            Text addition = Text.literal(value.substring(currentIndex, len)).setStyle(currentStyle);
            //            base.append(addition);
        }
        return builder.end().build();
    }

    @ApiMethod
    public static List<Text> multiLineTextFromLegacyString(String value, int widthLimit) {
        if (value == null) {
            return List.of();
        }
        List<Text> texts = new ArrayList<>();
        MutableFloat width = new MutableFloat(0.0);
        // MutableText base = Text.empty();
        TextBuilder builder = new TextBuilder();
        // Object currentStyle = ChatEnum.STYLE_EMPTY;
        // Style currentStyle = EMPTY;
        Matcher matcher = FORMAT_PATTERN.matcher(value);
        String match = null;
        StringBuilder hexColor = null;
        int currentIndex = 0;
        boolean hasReset = false;
        boolean needsAdd = false;
        find_any:
        while (matcher.find()) {
            int groupId = 0;
            while ((match = matcher.group(++groupId)) == null) {}
            int index = matcher.start(groupId);
            if (index > currentIndex) {
                String additionString = value.substring(currentIndex, index);
                needsAdd = false;
                while (true) {
                    int idx = cutStringWithWidth(additionString, builder.currentStyle(), widthLimit, width);
                    if (idx == additionString.length()) {
                        builder.with(additionString);
                        //                        Text addition = Text.literal(additionString).setStyle(currentStyle);
                        //                        base.append(addition);
                        break;
                    } else {
                        if (idx != 0) {
                            builder.with(additionString.substring(0, idx));
                            //                            Text addition = Text.literal(additionString.substring(0, idx))
                            //                                    .setStyle(currentStyle);
                            //                            base.append(addition);
                        }
                        texts.add(builder.end().build());
                        // switch line
                        width.setValue(0.0F);
                        additionString = additionString.substring(idx);
                    }
                }
                currentIndex = index;
            }
            switch (groupId) {
                case 1:
                    char c = match.toLowerCase(java.util.Locale.ENGLISH).charAt(1);
                    if (c == 'x') {
                        hexColor = new StringBuilder("#");
                    } else if (hexColor != null) {
                        hexColor.append(c);
                        if (hexColor.length() == 7) {
                            builder.withStyle(RESET.withColor(TextColor.parse(hexColor.toString())
                                    .result()
                                    .get()));
                            hexColor = null;
                        }
                    } else {
                        Formatting format = formatMap.get(c);
                        if (format.isModifier() && format != Formatting.RESET) {
                            switch (format) {
                                case BOLD:
                                    builder.withBold(true);
                                    // currentStyle = currentStyle.withBold(Boolean.TRUE);
                                    break;
                                case ITALIC:
                                    builder.withItalic(true);
                                    // currentStyle = currentStyle.withItalic(Boolean.TRUE);
                                    break;
                                case STRIKETHROUGH:
                                    builder.withStrikethrough(true);
                                    // currentStyle = currentStyle.withStrikethrough(Boolean.TRUE);
                                    break;
                                case UNDERLINE:
                                    builder.withUnderline(true);
                                    // currentStyle = currentStyle.withUnderline(Boolean.TRUE);
                                    break;
                                case OBFUSCATED:
                                    builder.withObfuscated(true);
                                    // currentStyle = currentStyle.withObfuscated(Boolean.TRUE);
                                    break;
                                default:
                                    throw new AssertionError("Unexpected message format");
                            }
                        } else { // Color resets formatting
                            // Paper start - Improve Legacy Component serialization size
                            builder.withReset(format, hasReset);
                            // Paper end - Improve Legacy Component serialization size
                        }
                    }
                    needsAdd = true;
                    break;
                case 2:
                    if (needsAdd) {
                        String additionString = value.substring(currentIndex, index);
                        while (true) {
                            int idx = cutStringWithWidth(additionString, builder.currentStyle(), widthLimit, width);
                            if (idx == additionString.length()) {
                                builder.with(additionString);
                                //                                Text addition =
                                // Text.literal(additionString).setStyle(currentStyle);
                                //                                base.append(addition);
                                break;
                            } else {
                                if (idx != 0) {
                                    builder.with(additionString.substring(0, idx));
                                    //                                    Text addition =
                                    // Text.literal(additionString.substring(0, idx))
                                    //                                            .setStyle(currentStyle);
                                    //                                    base.append(addition);
                                }
                                texts.add(builder.end().build());
                                // switch line
                                width.setValue(0.0F);
                                additionString = additionString.substring(idx);
                            }
                        }
                    }
                    // switch line
                    texts.add(builder.end().build());
                    width.setValue(0.0F);
                    needsAdd = false;
            }
            currentIndex = matcher.end(groupId);
        }
        int len = value.length();
        if (currentIndex < value.length() || needsAdd) {
            String additionString = value.substring(currentIndex, len);
            while (true) {
                int idx = cutStringWithWidth(additionString, builder.currentStyle(), widthLimit, width);
                if (idx == additionString.length()) {
                    builder.with(additionString);
                    //                    Text addition = Text.literal(additionString).setStyle(currentStyle);
                    //                    base.append(addition);
                    break;
                } else {
                    if (idx != 0) {
                        builder.with(additionString.substring(0, idx));
                        //                        Text addition =
                        //                                Text.literal(additionString.substring(0,
                        // idx)).setStyle(currentStyle);
                        //                        base.append(addition);
                    }
                    texts.add(builder.end().build());
                    // switch line
                    width.setValue(0.0F);
                    additionString = additionString.substring(idx);
                }
            }
            texts.add(builder.end().build());
        }
        return texts;
    }

    @ApiMethod
    public static List<Text> splitToMultiLineText(Text text, int widthLimit) {
        List<Text> texts = new ArrayList<>();
        TextBuilder builder = new TextBuilder();
        MutableFloat width = new MutableFloat(0.0);
        text.visit(
                ((style, asbString) -> {
                    builder.withStyle(style);
                    while (true) {
                        int idxRet = asbString.indexOf('\n');
                        String addString;
                        if (idxRet == -1) {
                            addString = asbString;
                        } else {
                            addString = asbString.substring(0, idxRet);
                            asbString = asbString.substring(idxRet + 1);
                        }
                        while (true) {
                            int idx = cutStringWithWidth(addString, style, widthLimit, width);
                            if (idx == addString.length()) {
                                builder.with(addString);
                                break;
                            } else {
                                if (idx != 0) {
                                    builder.with(addString.substring(0, idx));
                                    //                        Text addition =
                                    //                                Text.literal(additionString.substring(0,
                                    // idx)).setStyle(currentStyle);
                                    //                        base.append(addition);
                                }
                                texts.add(builder.end().build());
                                // switch line
                                width.setValue(0.0F);
                                addString = addString.substring(idx);
                            }
                        }
                        if (idxRet == -1) {
                            break;
                        } else {
                            texts.add(builder.end().build());
                            width.setValue(0.0F);
                        }
                    }
                    return Optional.empty();
                }),
                Style.EMPTY);
        if (width.floatValue() > 0.0F) {
            texts.add(builder.end().build());
        }
        return texts;
    }

    public static int cutStringWithWidth(String string, Style style, int limit, MutableFloat widthCounter) {
        int len = string.length();

        for (var i = 0; i < len; ++i) {
            int codepoint = string.codePointAt(i);
            OrderedText text = OrderedText.styled(codepoint, style);
            float wid = mc.textRenderer.getTextHandler().getWidth(text);
            if (widthCounter.getValue() + wid > limit) {
                return i;
            }
            widthCounter.add(wid);
        }
        return len;
    }

    @ApiMethod
    public static Stream<Text> textStream(Text comp) {
        return com.google.common.collect.Streams.concat(
                new Stream[] {Stream.of(comp), comp.getSiblings().stream().flatMap(ChatUtils::textStream)});
    }

    @ApiMethod
    public static String textToLegacyString(Text component) {
        if (component == null) return "";
        StringBuilder out = new StringBuilder();

        boolean hadFormat = false;
        Iterator<Text> textIterator = textStream(component).iterator();
        while (textIterator.hasNext()) {
            Text c = textIterator.next();
            Style modi = c.getStyle();
            TextColor color = modi.getColor();
            if (c.getContent() != PlainTextContent.EMPTY || color != null) {
                if (color != null) {
                    Formatting format = colorToFormat.get(color);
                    if (format != null) {
                        out.append(format);
                    } else {
                        out.append('§').append("x");
                        for (char magic : color.getName().substring(1).toCharArray()) {
                            out.append('§').append(magic);
                        }
                    }
                    hadFormat = true;
                } else if (hadFormat) {
                    out.append("§r");
                    hadFormat = false;
                }
            }
            if (modi.isBold()) {
                out.append(Formatting.BOLD);
                hadFormat = true;
            }
            if (modi.isItalic()) {
                out.append(Formatting.ITALIC);
                hadFormat = true;
            }
            if (modi.isUnderlined()) {
                out.append(Formatting.UNDERLINE);
                hadFormat = true;
            }
            if (modi.isStrikethrough()) {
                out.append(Formatting.STRIKETHROUGH);
                hadFormat = true;
            }
            if (modi.isObfuscated()) {
                out.append(Formatting.OBFUSCATED);
                hadFormat = true;
            }
            c.getContent().visit((x) -> {
                out.append(x);
                return Optional.empty();
            });
        }
        return out.toString();
    }

    @ApiMethod
    public static String textToPlainString(Text component) {
        if (component == null) return "";
        StringBuilder out = new StringBuilder();
        component.visit(
                ((style, asString) -> {
                    out.append(asString);
                    return Optional.empty();
                }),
                Style.EMPTY);
        return out.toString();
    }

    @ApiMethod
    public static String removeColorCode(String str) {
        return str.replaceAll("§.", "");
    }

    @ApiMethod
    public static String translatedTextToLegacyString(Text component) {
        if (component == null) return "";
        StringBuilder out = new StringBuilder();
        final MutableBoolean hadFormat = new MutableBoolean(false);
        component.visit(
                (StringVisitable.StyledVisitor<? extends Object>) (style, str) -> {
                    Style modi = style;
                    TextColor color = modi.getColor();
                    if (
                    // c.getContent() != PlainTextContent.EMPTY ||
                    color != null) {
                        if (color != null) {
                            Formatting format = colorToFormat.get(color);
                            if (format != null) {
                                out.append(format);
                            } else {
                                out.append('§').append("x");
                                for (char magic : color.getName().substring(1).toCharArray()) {
                                    out.append('§').append(magic);
                                }
                            }
                            hadFormat.setValue(true); // = true;
                        } else if (hadFormat.booleanValue()) {
                            out.append("§r");
                            hadFormat.setValue(false); // = false;
                        }
                    }
                    if (modi.isBold()) {
                        out.append(Formatting.BOLD);
                        hadFormat.setValue(true); // = true;
                    }
                    if (modi.isItalic()) {
                        out.append(Formatting.ITALIC);
                        hadFormat.setValue(true);
                    }
                    if (modi.isUnderlined()) {
                        out.append(Formatting.UNDERLINE);
                        hadFormat.setValue(true);
                    }
                    if (modi.isStrikethrough()) {
                        out.append(Formatting.STRIKETHROUGH);
                        hadFormat.setValue(true);
                    }
                    if (modi.isObfuscated()) {
                        out.append(Formatting.OBFUSCATED);
                        hadFormat.setValue(true);
                    }
                    out.append(str);
                    return Optional.empty();
                },
                Style.EMPTY);
        //        for (var txt : text){
        //            txt.accept(((index, style, codePoint) -> {
        //
        //            }));
        //        }
        return out.toString();
    }

    @ApiMethod
    public static String orderedTextToLegacyString(OrderedText... text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        MutableObject<Style> currentStyle = new MutableObject<>(null);
        final MutableBoolean hadFormat = new MutableBoolean(false);
        for (var txt : text) {
            txt.accept(((index, style, codePoint) -> {
                if (!Objects.equals(style, currentStyle.getValue())) {
                    // update only when change style
                    currentStyle.setValue(style);
                    Style modi = style;
                    TextColor color = modi.getColor();

                    if (color != null) {
                        Formatting format = colorToFormat.get(color);
                        if (format != null) {
                            out.append(format);
                        } else {
                            out.append('§').append("x");
                            for (char magic : color.getName().substring(1).toCharArray()) {
                                out.append('§').append(magic);
                            }
                        }
                        hadFormat.setValue(true); // = true;
                    } else if (hadFormat.booleanValue()) {
                        out.append("§r");
                        hadFormat.setValue(false); // = false;
                    }

                    if (modi.isBold()) {
                        out.append(Formatting.BOLD);
                        hadFormat.setValue(true); // = true;
                    }
                    if (modi.isItalic()) {
                        out.append(Formatting.ITALIC);
                        hadFormat.setValue(true);
                    }
                    if (modi.isUnderlined()) {
                        out.append(Formatting.UNDERLINE);
                        hadFormat.setValue(true);
                    }
                    if (modi.isStrikethrough()) {
                        out.append(Formatting.STRIKETHROUGH);
                        hadFormat.setValue(true);
                    }
                    if (modi.isObfuscated()) {
                        out.append(Formatting.OBFUSCATED);
                        hadFormat.setValue(true);
                    }
                }

                out.appendCodePoint(codePoint);
                return true;
            }));
        }
        return out.toString();
    }

    @ApiMethod
    public static String translateAlternateColorCodes(
            char altColorChar, char translateTo, @NotNull String textToTranslate) {
        Preconditions.checkArgument(textToTranslate != null, "Cannot translate null text");

        char[] b = textToTranslate.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == altColorChar && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[i + 1]) > -1) {
                b[i] = translateTo;
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }

    @ApiMethod
    public static String textToString(Text com) {
        try {
            String val = textToLegacyString(com);
            return translateAlternateColorCodes('§', '&', val);
        } catch (Throwable e) {
            return "";
        }
    }

    @ApiMethod
    public static MutableText stringToText(String origin) {
        try {
            String val = translateAlternateColorCodes('&', '§', origin);
            return textFromLegacyString(val);
        } catch (Throwable e) {
            return Text.empty();
        }
    }

    @ApiMethod
    public static Text getDisplayedLocation(double x, double z) {
        return Text.literal("[%.2f,~,%.2f]".formatted(x, z))
                .setStyle(Style.EMPTY
                        .withClickEvent(
                                new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "%.2f ~ %.2f".formatted(x, z)))
                        .withHoverEvent(
                                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("click to copy coord"))))
                .formatted(Formatting.GREEN);
    }

    @ApiMethod
    public static Text getDisplayedLocation(Vec3d vec3d) {
        return getDisplayedLocation(vec3d.x, vec3d.y, vec3d.z);
    }

    @ApiMethod
    public static Text getDisplayedLocationDouble(Vec3d vec3d) {
        return getDisplayedLocationDouble(vec3d.x, vec3d.y, vec3d.z);
    }

    @ApiMethod
    public static Text getDisplayedLocationDouble(double x, double y, double z) {
        return Text.literal("[%.2f,%.2f,%.2f]".formatted(x, y, z))
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.COPY_TO_CLIPBOARD, "%.2f %.2f %.2f".formatted(x, y, z)))
                        .withHoverEvent(
                                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("click to copy coord"))))
                .formatted(Formatting.GREEN);
    }

    @ApiMethod
    public static Text getDisplayedLong(long l) {
        return Text.literal("[" + Long.toString(l) + "]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, Long.toString(l)))
                        .withHoverEvent(
                                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("click to copy coord"))))
                .formatted(Formatting.GREEN);
    }

    @ApiMethod
    public static Text getDisplayedLocation(double x, double y, double z) {
        return Text.literal("[%d,%d,%d]".formatted((int) x, (int) y, (int) z))
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.COPY_TO_CLIPBOARD, "%.2f %.2f %.2f".formatted(x, y, z)))
                        .withHoverEvent(
                                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("click to copy coord"))))
                .formatted(Formatting.GREEN);
    }

    @ApiMethod
    public static MutableText getClickCopyTargetText(String literal) {
        String targetShow = "[%s]".formatted(literal);
        return getClickCopyText(targetShow, literal);
    }

    @ApiMethod
    public static MutableText getClickCopyText(String literal, String copy) {
        return Text.literal(literal)
                .setStyle(Style.EMPTY
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("click to copy text")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copy)));
    }

    @ApiMethod
    public static ClickEvent getOpenFile(File path) {
        return new ClickEvent(ClickEvent.Action.OPEN_FILE, path.getAbsolutePath());
    }

    @ApiMethod
    public static ClickEvent getClickCopyText(String copy) {
        return new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copy);
    }

    @ApiMethod
    public static ClickEvent getRunCommand(String command) {
        return new ClickEvent.RunCommand(command);
    }

    @ApiMethod
    public static ClickEvent getSuggestCommand(String name) {
        return new ClickEvent.SuggestCommand(name);
    }

    @ApiMethod
    public static MutableText concatLineText(List<Text> texts) {
        int size = texts.size();
        MutableText text = Text.empty();

        for (int i = 0; i < size; i++) {
            Text text0 = texts.get(i);
            text.append(text0);
            if (i < size - 1) {
                text.append("\n");
            }
        }

        return text;
    }

    @ApiMethod
    public static MutableText getHoverShowText(String literal, List<Text> showText) {
        return Text.literal(literal)
                .setStyle(Style.EMPTY.withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, concatLineText(showText))));
    }

    @ApiMethod
    public static HoverEvent getHoverShowText(List<Text> showText) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, concatLineText(showText));
    }

    @ApiMethod
    @Nullable
    public static String parseTranslation(String key) {
        return Language.getInstance().get(key, key);
    }

    public static boolean hasTranslation(String key) {
        return Language.getInstance().hasTranslation(key);
    }

    @ApiMethod
    public static List<Text> parseTooltipsTranslation(String key, String defaultVal) {
        String tooltipValue = Language.getInstance().get(key, defaultVal);
        if (tooltipValue == null || tooltipValue.isEmpty()) return List.of();
        String[] splites = tooltipValue.split("\n");
        return Arrays.stream(splites).map(Text::literal).map(Text.class::cast).toList();
    }

    public static String getOrderedTextString(OrderedText... text) {
        var re = new SimpleOrderedTextVisitor();
        for (var txt : text) {
            txt.accept(re);
        }
        return re.getContent().toString();
    }

    @ApiMethod
    public static MutableText copyText(Text text) {
        MutableText newLine = MutableText.of(text.getContent());
        newLine.setStyle(text.getStyle());
        text.getSiblings().forEach(newLine::append);
        return newLine;
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    @ApiMethod
    public static String textToJsonString(Text text) {
        if (text == null) return null;
        try {
            var re = TextCodecs.CODEC
                    .encodeStart(ItemStackUtils.registry().getOps(JsonOps.INSTANCE), text)
                    .getOrThrow(JsonParseException::new);
            return GSON.toJson(re);
        } catch (Throwable e) {
            return null;
        }
    }

    @ApiMethod
    public static Text textFromJsonString(String jsonRaw) {
        try {
            if (jsonRaw == null) return null;
            JsonElement jsonElement = JsonParser.parseString(jsonRaw);
            return jsonElement == null
                    ? null
                    : TextCodecs.CODEC
                            .parse(ItemStackUtils.registry().getOps(JsonOps.INSTANCE), jsonElement)
                            .getOrThrow(JsonParseException::new);
        } catch (Throwable e) {
            return null;
        }
    }

    @ApiMethod
    public static TextBuilder builder() {
        return new TextBuilder();
    }

    public static class TextBuilder
            implements StringVisitable.StyledVisitor<Unit>, CharacterVisitor, StringVisitable.Visitor<Unit> {
        Style style = Style.EMPTY;
        MutableText empty = Text.empty();
        StringBuilder builder = new StringBuilder();

        public TextBuilder() {}

        public Style currentStyle() {
            return style;
        }

        private void write() {
            if (!builder.isEmpty()) {
                String str = builder.toString();
                builder = new StringBuilder();
                empty.append(Text.literal(str).setStyle(style));
            }
        }

        public TextBuilder withStyle(Style style) {
            if (!builder.isEmpty() && !Objects.equals(style, this.style)) {
                write();
            }
            this.style = style;
            return this;
        }

        public TextBuilder withReset(Formatting color, boolean hasReset) {
            Style previous = this.style;
            Style currentStyle = ((!hasReset ? RESET : EMPTY).withColor(color));
            // currentStyle = (!hasReset ? RESET : EMPTY).withColor(format);
            if (previous.isBold()) {
                currentStyle = currentStyle.withBold(false);
            }
            if (previous.isItalic()) {
                currentStyle = currentStyle.withItalic(false);
            }
            if (previous.isObfuscated()) {
                currentStyle = currentStyle.withObfuscated(false);
            }
            if (previous.isStrikethrough()) {
                currentStyle = currentStyle.withStrikethrough(false);
            }
            if (previous.isUnderlined()) {
                currentStyle = currentStyle.withUnderline(false);
            }
            return withStyle(currentStyle);
        }

        public TextBuilder withFormat(Formatting format) {
            return withStyle(style.withFormatting(format));
        }

        @Override
        public Optional<Unit> accept(Style style, String asString) {
            withStyle(style).with(asString);
            return Optional.empty();
        }

        @Override
        public Optional<Unit> accept(String asString) {
            with(asString);
            return Optional.empty();
        }

        @Override
        public boolean accept(int index, Style style, int codePoint) {
            withStyle(style).with(codePoint);
            return true;
        }

        public TextBuilder end() {
            write();
            return this;
        }

        public MutableText build() {
            MutableText text = empty;
            empty = Text.empty();
            return text;
        }

        public MutableText peek() {
            return empty;
        }

        public TextBuilder withText(StringVisitable text) {
            text.visit(this, Style.EMPTY);
            return this;
        }

        public TextBuilder withContent(TextContent content) {
            content.visit(this, style.withParent(Style.EMPTY));
            return this;
        }

        public TextBuilder with(String string) {
            builder.append(string);
            return this;
        }

        // 追加字符
        public TextBuilder with(char c) {
            builder.append(c);
            return this;
        }

        // 追加字符数组
        public TextBuilder with(char[] chars) {
            builder.append(chars);
            return this;
        }

        // 追加字符数组的一部分
        public TextBuilder with(char[] chars, int offset, int len) {
            builder.append(chars, offset, len);
            return this;
        }

        // 追加整数
        public TextBuilder with(int i) {
            builder.append(i);
            return this;
        }

        // 追加长整数
        public TextBuilder with(long l) {
            builder.append(l);
            return this;
        }

        // 追加浮点数
        public TextBuilder with(float f) {
            builder.append(f);
            return this;
        }

        // 追加双精度浮点数
        public TextBuilder with(double d) {
            builder.append(d);
            return this;
        }

        // 追加布尔值
        public TextBuilder with(boolean b) {
            builder.append(b);
            return this;
        }

        // 追加任意 CharSequence（如 String、StringBuilder 等）
        public TextBuilder with(CharSequence cs) {
            builder.append(cs);
            return this;
        }

        // 追加任意对象（调用 String.valueOf）
        public TextBuilder with(Object obj) {
            builder.append(obj);
            return this;
        }

        // 追加换行符
        public TextBuilder withLine() {
            builder.append('\n');
            return this;
        }

        // 格式化追加（类似于 String.format）
        public TextBuilder withFormat(String format, Object... args) {
            builder.append(String.format(format, args));
            return this;
        }

        // ---- Style 包装方法 ----

        public TextBuilder withColor(@Nullable TextColor color) {
            return withStyle(style.withColor(color));
        }

        public TextBuilder withColor(@Nullable Formatting color) {
            return withStyle(style.withColor(color));
        }

        public TextBuilder withColor(int rgbColor) {
            return withStyle(style.withColor(rgbColor));
        }

        public TextBuilder withBold(@Nullable Boolean bold) {
            return withStyle(style.withBold(bold));
        }

        public TextBuilder withItalic(@Nullable Boolean italic) {
            return withStyle(style.withItalic(italic));
        }

        public TextBuilder withUnderline(@Nullable Boolean underline) {
            return withStyle(style.withUnderline(underline));
        }

        public TextBuilder withStrikethrough(@Nullable Boolean strikethrough) {
            return withStyle(style.withStrikethrough(strikethrough));
        }

        public TextBuilder withObfuscated(@Nullable Boolean obfuscated) {
            return withStyle(style.withObfuscated(obfuscated));
        }

        public TextBuilder withClickEvent(@Nullable ClickEvent clickEvent) {
            return withStyle(style.withClickEvent(clickEvent));
        }

        public TextBuilder withHoverEvent(@Nullable HoverEvent hoverEvent) {
            return withStyle(style.withHoverEvent(hoverEvent));
        }

        public TextBuilder withInsertion(@Nullable String insertion) {
            return withStyle(style.withInsertion(insertion));
        }

        public TextBuilder withFormatting(Formatting formatting) {
            return withStyle(style.withFormatting(formatting));
        }

        public TextBuilder withExclusiveFormatting(Formatting formatting) {
            return withStyle(style.withExclusiveFormatting(formatting));
        }

        public TextBuilder withFormatting(Formatting... formattings) {
            return withStyle(style.withFormatting(formattings));
        }

        public TextBuilder withParent(Style parent) {
            return withStyle(style.withParent(parent));
        }
    }
}
