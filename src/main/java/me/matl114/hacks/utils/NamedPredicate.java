package me.matl114.hacks.utils;


import java.util.function.Predicate;

public class NamedPredicate<W, R> implements Predicate<W>, Named<R>{
    private final R name;
    private final Predicate<W> delegate;
    private final String reason;
    public NamedPredicate(R name, Predicate<W> delegate, String reason){
        this.name = name;
        this.delegate = delegate;
        this.reason = reason;
    }


    @Override
    public boolean test(W w) {
        return this.delegate.test(w);
    }

    @Override
    public R getOwner() {
        return this.name;
    }

    @Override
    public String getRegisterReason() {
        return this.reason;
    }
}