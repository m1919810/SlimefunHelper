package me.matl114.Utils;

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

}
