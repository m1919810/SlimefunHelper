package me.matl114.utils;

import it.unimi.dsi.fastutil.chars.Char2CharArrayMap;
import it.unimi.dsi.fastutil.chars.Char2CharMap;
import it.unimi.dsi.fastutil.chars.Char2CharMaps;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptUtils {
    private static Char2CharMap createBase64RShiftsReverse() {
        Char2CharMap map = createBase64RShifts();
        Char2CharMap reverse = new Char2CharArrayMap(64);

        for (char ch : map.keySet()) {
            reverse.put(map.get(ch), ch);
        }

        return Char2CharMaps.unmodifiable(reverse);
    }

    private static Char2CharMap createBase64RShifts() {
        Char2CharMap map = new Char2CharArrayMap(64);

        map.put('A', '!');
        map.put('B', '"');
        map.put('C', '#');
        map.put('D', '$');
        map.put('E', '%');
        map.put('F', '¼');
        map.put('G', '\'');
        map.put('H', '(');
        map.put('I', ')');
        map.put('J', ',');
        map.put('K', '-');
        map.put('L', '.');
        map.put('M', ':');
        map.put('N', ';');
        map.put('O', '<');
        map.put('P', '=');
        map.put('Q', '>');
        map.put('R', '?');
        map.put('S', '@');
        map.put('T', '[');
        map.put('U', '\\');
        map.put('V', ']');
        map.put('W', '^');
        map.put('X', '_');
        map.put('Y', '`');
        map.put('Z', '{');
        map.put('a', '|');
        map.put('b', '}');
        map.put('c', '~');
        map.put('d', '¡');
        map.put('e', '¢');
        map.put('f', '£');
        map.put('g', '¤');
        map.put('h', '¥');
        map.put('i', '¦');
        map.put('j', '¨');
        map.put('k', '©');
        map.put('l', 'ª');
        map.put('m', '«');
        map.put('n', '¬');
        map.put('o', '®');
        map.put('p', '¯');
        map.put('q', '°');
        map.put('r', '±');
        map.put('s', '²');
        map.put('t', '³');
        map.put('u', 'µ');
        map.put('v', '¶');
        map.put('w', '·');
        map.put('x', '×');
        map.put('y', '¹');
        map.put('z', 'º');
        map.put('0', '0');
        map.put('1', '1');
        map.put('2', '2');
        map.put('3', '3');
        map.put('4', '4');
        map.put('5', '5');
        map.put('6', '6');
        map.put('7', '7');
        map.put('8', '8');
        map.put('9', '9');
        map.put('+', '+');
        map.put('/', '»');
        map.put('=', '¿');

        return Char2CharMaps.unmodifiable(map);
    }

    public static final SecureRandom RANDOM = new SecureRandom();
    protected static final Char2CharMap BASE64R_SHIFTS = createBase64RShifts();
    protected static final Char2CharMap BASE64R_SHIFTS_REVERSE = createBase64RShiftsReverse();
    public static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    public static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    public static byte[] toBytes(String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }

    public static String fromBytes(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String ensureUTF8(String string) {
        return fromBytes(toBytes(string));
    }

    public static String shiftBase64R(String string) {
        char[] chars = ensureUTF8(string).toCharArray();

        for (int i = 0; i < chars.length; i++) {
            chars[i] = BASE64R_SHIFTS.get(chars[i]);
        }

        return new String(chars);
    }

    public static String unshiftBase64R(String string) {
        char[] chars = ensureUTF8(string).toCharArray();

        for (int i = 0; i < chars.length; i++) {
            chars[i] = BASE64R_SHIFTS_REVERSE.get(chars[i]);
        }

        return new String(chars);
    }

    public static String encodeBase64R(String string) {
        return encodeBase64R(toBytes(string));
    }

    public static String encodeBase64R(byte[] bytes) {
        return shiftBase64R(fromBytes(BASE64_ENCODER.encode(bytes)));
    }

    public static byte[] encodeBase64RBytes(String string) {
        return toBytes(encodeBase64R(toBytes(string)));
    }

    public static String decodeBase64R(String string) {
        return fromBytes(decodeBase64RBytes(string));
    }

    public static byte[] decodeBase64RBytes(String string) {
        return BASE64_DECODER.decode(toBytes(unshiftBase64R(string)));
    }

    public static String encodeBinaryKey(byte[] key) {
        return fromBytes(BASE64_ENCODER.encode(key));
    }

    public static byte[] decodeBinaryKey(String key) throws InvalidKeyException {
        try {
            return BASE64_DECODER.decode(toBytes(key));
        } catch (Exception ex) {
            throw new InvalidKeyException(ex);
        }
    }
}
