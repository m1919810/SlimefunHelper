package me.matl114.utils.collections;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LazyList<S extends List<V>,V> implements List<V> {
    @NoArgsConstructor
    @AllArgsConstructor
    public static class State<S> implements Cloneable{
        public S value = null;
        public boolean state = false;
        private static final State INSTANCE = new State();
        public static <T> State<T> newInstance(){
            return INSTANCE.clone();
        }

        @Override
        public State<S> clone() {
            try {
                State clone = (State) super.clone();
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }

    public static class ListIndexIterator<T> implements Iterator<T>{
        List<T> delegate;
        int index;

        public ListIndexIterator(List<T> delegate){
            this.delegate =delegate;
            this.index = 0;
        }

        @Override
        public boolean hasNext() {
            return index < this.delegate.size();
        }

        @Override
        public T next() {
            return this.delegate.get(index++);
        }

        @Override
        public void remove() {
            this.delegate.remove(--index );

        }



    }
    public static class BidirListIndexIterator<T> extends ListIndexIterator<T> implements ListIterator<T> {
        public BidirListIndexIterator(List<T> delegate) {
            super(delegate);
        }
        public BidirListIndexIterator(List<T> delegate, int index) {
            super(delegate);
            this.index = index;
        }
        int lastVisit = -1;

        @Override
        public boolean hasPrevious() {
            return index >= 0;
        }

        @Override
        public T previous() {
            this.lastVisit = --index;
            return this.delegate.get(this.lastVisit);
        }


        @Override
        public int nextIndex() {
            return index;
        }

        public T next(){
            this.lastVisit = index;
            return super.next();
        }

        @Override
        public int previousIndex() {
            return index - 1;
        }


        @Override
        public void add(T t) {
            delegate.add(index, t);
            index++; // 插入后索引后移
            lastVisit = -1; // 重置
        }

        @Override
        public void remove() {
            if (lastVisit == -1) {
                throw new IllegalStateException("No element to remove");
            }
            delegate.remove(lastVisit);
            // 调整索引（如果删除的是通过 next() 访问的元素）
            if(lastVisit < index){
                --index;
            }
            lastVisit = -1;
        }
        @Override
        public void set(T t) {
            if (lastVisit == -1) {
                throw new IllegalStateException("No element to set");
            }
            delegate.set(lastVisit, t);
        }
    }
    public static class SubListWindow<T> extends AbstractList<T> {
        int fromHead;
        int toEnd;
        List<T> delegate;
        public SubListWindow(List<T> value, int from, int to){
            this.delegate = value;
            this.fromHead = from;
            this.toEnd = this.delegate.size() - to;
        }
        @Override
        public T get(int index) {
            return this.delegate.get(index + fromHead);
        }

        @Override
        public int size() {
            return this.delegate.size() - toEnd - fromHead;
        }

        public void add(int index, T element){
            this.delegate.add(index + fromHead, element);
        }
        public T remove(int index){
            return this.delegate.remove(index + fromHead);
        }
        public T set(int index, T element){
            return this.delegate.set(index + fromHead, element);
        }
    }



    protected volatile State<S> delegate;
    public LazyList(S value){
        this.delegate =State.newInstance();
        this.delegate.value = value;
        this.delegate.state = true;

    }
    //make
//    protected static final AtomicReferenceFieldUpdater<COWImmutableCollectionViewImpl, State> UPDATER = AtomicReferenceFieldUpdater.newUpdater(COWImmutableCollectionViewImpl.class, State.class, "delegate");

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends V> c) {
        preWrite();
        return this.delegate.value.addAll(index, c);
    }

    @Override
    public V get(int index) {
        return this.delegate.value.get(index);
    }

    @Override
    public V set(int index, V element) {
        preWrite();
        return this.delegate.value.set(index, element);
    }

    @Override
    public void add(int index, V element) {
        preWrite();
        this.delegate.value.add(index, element);
    }

    @Override
    public V remove(int index) {
        preWrite();
        return this.delegate.value.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return this.delegate.value.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return this.delegate.value.lastIndexOf(o);
    }



    @NotNull
    @Override
    public ListIterator<V> listIterator() {
        return new BidirListIndexIterator<>(this);
    }

    @NotNull
    @Override
    public ListIterator<V> listIterator(int index) {
        return new BidirListIndexIterator<>(this, index);
    }

    @NotNull
    @Override
    public List<V> subList(int fromIndex, int toIndex) {
        return new SubListWindow<>(this, fromIndex, toIndex);
    }

    protected void preWrite(){
        if(!this.delegate.state){
            return;
        }
        var ls = this.delegate.value;
        this.delegate = State.newInstance();
        this.delegate.value = (S) new ArrayList<>(ls);
    }


    public S getHandle() {
        return this.delegate.value;
    }

    @Override
    public int size() {
        return this.delegate.value.size();
    }

    @Override
    public boolean isEmpty() {
        return this.delegate.value.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return this.delegate.value.contains(o);
    }


    @NotNull
    @Override
    public Object[] toArray() {
        return this.delegate.value.toArray();
    }


    @Override
    public <T> T[] toArray(T[] a) {
        return this.delegate.value.toArray(a);
    }

    @Override
    public boolean add(V v) {
        preWrite();
        return this.delegate.value.add(v);
    }

    @Override
    public boolean remove(Object o) {
        preWrite();
        return this.delegate.value.remove(o);
    }

    @Override
    public boolean containsAll( Collection<?> c) {
        return this.delegate.value.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        preWrite();
        return this.delegate.value.addAll(c);
    }

    @Override
    public boolean retainAll( Collection<?> c) {
        preWrite();
        return this.delegate.value.retainAll(c);
    }

    @Override
    public boolean removeAll( Collection<?> c) {
        preWrite();
        return this.delegate.value.removeAll(c);
    }

    @Override
    public void clear() {
        if(!this.delegate.value.isEmpty()){
            preWrite();
            this.delegate.value.clear();
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Set<?> val && val.equals(this.delegate);
    }

    @Override
    public int hashCode() {
        return this.delegate.value.hashCode();
    }

    @NotNull
    @Override
    public Iterator<V> iterator() {
        return this.delegate.value.iterator();
    }

}
