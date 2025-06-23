package me.matl114.utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ChatUtils {
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
                return  ((char) (c + 0xFEE0));
            } else if (c >= '0' && c <= '9') {
                // 转换为全角数字
                return ((char) (c + 0xFEE0));
            } else {
                // 其他字符保持不变
                return c;
            }

    }
    private static final Pattern FORMAT_PATTERN = Pattern.compile("(§[0-9a-fk-orx])|(\\n)", Pattern.CASE_INSENSITIVE);
    private static final Style EMPTY = Style.EMPTY.withItalic(false); // Paper - Improve Legacy Component serialization size
    private static final Style RESET = Style.EMPTY.withBold(false).withItalic(false).withUnderline(false).withStrikethrough(false).withObfuscated(false);
    private static final Map<Character, Formatting> formatMap;
    private static final Map<TextColor, Formatting> colorToFormat;
    static {
        ImmutableMap.Builder<Character, Formatting> builder = ImmutableMap.builder();
        for (Formatting format : Formatting.values()) {
            builder.put(Character.toLowerCase(format.toString().charAt(1)), format);
        }
        formatMap = builder.build();
        colorToFormat = new HashMap<>();
        TextColor.FORMATTING_TO_COLOR.forEach((f,t)->colorToFormat.put(t,f));
    }
    public static MutableText textFromLegacyString(String value){
        if(value == null){
            return Text.empty();
        }
        MutableText base = Text.empty();
        //Object currentStyle = ChatEnum.STYLE_EMPTY;
        Style currentStyle = EMPTY;
        Matcher matcher = FORMAT_PATTERN.matcher(value);
        String match = null;
        StringBuilder hexColor = null;
        int currentIndex = 0;
        boolean hasReset = false;
        boolean needsAdd = false;
        find_any:
        while (matcher.find()){
            int groupId = 0;
            while((match = matcher.group(++ groupId)) == null){
            }
            int index = matcher.start(groupId);
            if(index > currentIndex){
                needsAdd = false;
                Text addition = Text.literal(value.substring(currentIndex, index)).setStyle(currentStyle);
                currentIndex = index;
                base.append(addition);
            }
            switch (groupId){
                case 1:
                    char c = match.toLowerCase(java.util.Locale.ENGLISH).charAt(1);
                    if(c == 'x'){
                        hexColor = new StringBuilder("#");
                    }else if(hexColor != null){
                        hexColor.append(c);
                        if(hexColor.length() == 7){
                            currentStyle = RESET.withColor(TextColor.parse(hexColor.toString()).result().get());
                            hexColor = null;
                        }
                    }else {
                        Formatting format = formatMap.get(c);
                        if (format.isModifier() && format != Formatting.RESET) {
                            switch (format) {
                                case BOLD:
                                    currentStyle = currentStyle.withBold(Boolean.TRUE);
                                    break;
                                case ITALIC:
                                    currentStyle = currentStyle.withItalic(Boolean.TRUE);
                                    break;
                                case STRIKETHROUGH:
                                    currentStyle = currentStyle.withStrikethrough(Boolean.TRUE);
                                    break;
                                case UNDERLINE:
                                    currentStyle = currentStyle.withUnderline(Boolean.TRUE);
                                    break;
                                case OBFUSCATED:
                                    currentStyle = currentStyle.withObfuscated(Boolean.TRUE);
                                    break;
                                default:
                                    throw new AssertionError("Unexpected message format");
                            }
                        } else { // Color resets formatting
                            // Paper start - Improve Legacy Component serialization size
                            Style previous = currentStyle;
                            currentStyle = (!hasReset ? RESET : EMPTY).withColor(format);
                            hasReset = true;
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
                            // Paper end - Improve Legacy Component serialization size
                        }
                    }
                    needsAdd = true;
                    break;
                case 2:
                    if(needsAdd){
                        Text addition = Text.literal(value.substring(currentIndex, index)).setStyle(currentStyle);
                        base.append(addition);
                    }
                    //换行 means end
                    return base;
            }
            currentIndex = matcher.end(groupId);
        }
        int len = value.length();
        if(currentIndex < value.length() || needsAdd){
            Text addition = Text.literal(value.substring(currentIndex, len)).setStyle(currentStyle);
            base.append(addition);
        }
        return base;
    }

    public static Stream<Text> textStream(Text comp) {
        return com.google.common.collect.Streams.concat(new Stream[]{Stream.of(comp), comp.getSiblings().stream().flatMap(ChatUtils::textStream)});
    }
    public static String textToLegacyString(Text component){
        if (component == null) return "";
        StringBuilder out = new StringBuilder();

        boolean hadFormat = false;
        Iterator<Text> textIterator = textStream(component).iterator();
        while (textIterator.hasNext()){
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
    public static String translateAlternateColorCodes(char altColorChar, char translateTo, @NotNull String textToTranslate) {
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
    public static String textToString(Text com){
        try{
            String val = textToLegacyString(com);
            return translateAlternateColorCodes('§','&', val);
        }catch (Throwable e){
            return "";
        }

    }
    public static MutableText stringToText(String origin){
        try{
            String val = translateAlternateColorCodes('&','§', origin);
            return  textFromLegacyString(val);
        }catch (Throwable e){
            return Text.empty();
        }
    }


}
