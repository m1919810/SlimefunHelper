package me.matl114.utils;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

@ApiMethod
public class CollectionUtils {
    public static <T> List<T> newArrayList() {
        return new ArrayList<>();
    }

    public static <T> List<T> newArrayList(List<T> list) {
        return new ArrayList<>(list);
    }

    public static <T> List<T> newArrayList(T[] list) {
        return Arrays.stream(list).collect(Collectors.toCollection(ArrayList::new));
    }

    public static <T, V> Map<T, V> newHashMap() {
        return new LinkedHashMap<>();
    }

    public static <T, V> Map<T, V> newHashMap(Map<T, V> map) {
        return new LinkedHashMap<>(map);
    }

    public static <T, V> Map<T, V> newHashMap(T[] array, V[] arr) {
        int len = Math.min(array.length, arr.length);
        Map<T, V> map = new LinkedHashMap<>(len);
        for (int i = 0; i < len; i++) {
            map.put(array[i], arr[i]);
        }
        return map;
    }

    public static <T> T resolvePath(@Nullable Object tree, String path) {
        if (tree == null) return null;
        if (path == null || path.isEmpty()) return (T) tree;
        int idx = path.indexOf('.');
        String currentPath = idx == -1 ? path : path.substring(0, idx);
        String remainPath = idx == -1 ? null : path.substring(idx + 1);
        return resolvePath(resolveOne(tree, currentPath), remainPath);
    }

    private static final Pattern PATTERN = Pattern.compile("^([^\\[\\]]*?)((?:\\[\\-?\\d+\\])*)$");
    private static final Pattern NUM_PATTERN = Pattern.compile("\\[(\\-?[\\d]+)\\]");

    private static Object resolveOne(@Nullable Object tree, String path) {
        // be like name[a][b][c]
        if (tree == null) return null;
        Matcher matcher = PATTERN.matcher(path);
        if (matcher.matches()) {
            String name = matcher.group(1);
            String idx = matcher.group(2);
            if (!name.isEmpty()) {
                if (tree instanceof Map<?, ?> map) {
                    tree = map.get(name);
                } else {
                    return null;
                }
            }
            if (tree == null) {
                return null;
            }
            if (!idx.isEmpty()) {
                Matcher numMatcher = NUM_PATTERN.matcher(idx);
                while (numMatcher.find()) {
                    String numStr = numMatcher.group(1);
                    int num = Integer.parseInt(numStr);
                    if (tree instanceof List<?> list) {
                        if (num >= 0) {
                            tree = list.get(num);
                        } else {
                            tree = list.get(list.size() + num);
                        }
                    } else if (tree.getClass().isArray()) {
                        int length = java.lang.reflect.Array.getLength(tree);
                        if (num < 0) {
                            tree = java.lang.reflect.Array.get(tree, length + num);
                        } else {
                            tree = java.lang.reflect.Array.get(tree, num);
                        }
                    }
                    if (tree == null) {
                        return null;
                    }
                }
            }
            return tree;
        } else {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
    }

    public static <A, B> Pair<A, B> entryToPair(Map.Entry<A, B> entry) {
        return Pair.of(entry.getKey(), entry.getValue());
    }

    public static <A, B> List<Pair<A, B>> mapToPairList(Map<A, B> map) {
        List<Pair<A, B>> list = new ArrayList<>();
        for (Map.Entry<A, B> entry : map.entrySet()) {
            list.add(Pair.of(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    public static <A, B> Map<A, B> pairListToMap(List<Pair<A, B>> map) {
        Map<A, B> list = new LinkedHashMap<>();
        for (Pair<A, B> entry : map) {
            list.put(entry.getFirst(), entry.getSecond());
        }
        return list;
    }

    public static <A, B> Map<A, B> ofOrdered(Object... objects) {
        Map<A, B> map = new LinkedHashMap<>();
        int size = objects.length;
        for (int i = 0; i < size - 1; i += 2) {
            Object k1 = objects[i];
            Object k2 = objects[i + 1];
            ((Map) map).put(k1, k2);
        }
        return map;
    }
}
