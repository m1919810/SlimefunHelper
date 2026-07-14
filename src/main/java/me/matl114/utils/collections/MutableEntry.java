package me.matl114.utils.collections;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class MutableEntry<K, V> {
    public K key;
    public V value;
}
