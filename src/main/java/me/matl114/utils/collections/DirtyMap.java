package me.matl114.utils.impl.collections;

import lombok.Getter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DirtyMap<K,V> implements Map<K, V>{
    @Getter
    private final Map<K,V> delegate;
    public DirtyMap(Map<K,V> map){
        this.delegate = map;
    }
    public DirtyMap(int size){
        this(new HashMap<>(size));
    }
    public void setDirty(){
        setDirty(true);
    }
    volatile boolean dirty = false;

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }


    public boolean isDirty() {
        return dirty;
    }

    @Override
    public V put(K key, V value) {
        V oldValue = delegate.put(key, value);
        if (oldValue != value) {
            setDirty();
        }
        return oldValue;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        delegate.putAll(m);
        setDirty();
    }

    @Override
    public V remove(Object key) {
        V removed = delegate.remove(key);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    @Override
    public void clear() {
        if (!delegate.isEmpty()) {
            setDirty();
            delegate.clear();
        }
    }

    //------------------- 只读操作直接委托 -------------------//
    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return delegate.get(key);
    }

    @Override
    public Set<K> keySet() {

        return new DirtyDelegateCollection<Set<K>,K>(delegate.keySet());
    }

    @Override
    public Collection<V> values() {
        return new DirtyDelegateCollection<Collection<V>,V>(delegate.values());
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new DirtyDelegateCollection<Set<Entry<K,V>>,Entry<K, V>>(delegate.entrySet());
    }


    private class DirtyDelegateCollection<W extends Collection<R>, R> extends DirtyCollectionImpl<W,R> {

        public DirtyDelegateCollection(W value) {
            super(value);
        }

        @Override
        public void setDirty(boolean dirty) {
            if(dirty){
                DirtyMap.this.setDirty();
            }
        }
    }


    @Override
    public boolean equals(Object o) {
        return o instanceof Map<?, ?> map && map.equals(this.delegate);
    }

    @Override
    public int hashCode() {
        return this.delegate.hashCode();
    }

    public String toString(){
        return "DirtyMap:[handle = " +this.delegate.toString()+ ", dirty = ]"+dirty;
    }
}
