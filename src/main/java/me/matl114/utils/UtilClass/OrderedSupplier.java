package me.matl114.utils.UtilClass;

import lombok.AllArgsConstructor;

import java.util.function.Function;

@AllArgsConstructor
public abstract class OrderedSupplier<T,W> implements Function<T,W>, Comparable<OrderedSupplier<?,?>> {
    public int priority ;
    public int compareTo(OrderedSupplier<?,?> var1){
        return Integer.compare(priority, var1.priority);
    }
    public static <R,S> OrderedSupplier<R,S> create(int priority, Function<R,S> func){
        return new OrderedSupplier<R, S>(priority) {
            @Override
            public S apply(R r) {
                return func.apply(r);
            }
        };
    }
}
