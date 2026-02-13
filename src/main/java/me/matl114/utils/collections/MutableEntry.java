package me.matl114.utils.collections;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class MutableEntry<K, V> {
    public K key;
    public V value;
}
