package me.matl114.hacks.utils;

import java.util.function.Consumer;

public class NamedConsumer<W, R> implements Consumer<W>, Named<R> {
    private final R name;
    private final Consumer<W> delegate;
    private final String reason;

    public NamedConsumer(R name, Consumer<W> delegate, String reason) {
        this.name = name;
        this.delegate = delegate;
        this.reason = reason;
    }

    @Override
    public void accept(W w) {
        this.delegate.accept(w);
    }

    @Override
    public R getOwner() {
        return this.name;
    }

    @Override
    public String getRegisterReason() {
        return reason;
    }
}
