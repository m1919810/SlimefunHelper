package me.matl114.events.channels;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

public abstract class EntryPoint<W> {
    @AllArgsConstructor
    protected static class H<T> implements Comparable<H<T>>, Predicate<T> {
        int priority;
        T value;

        @Override
        public int compareTo(@NotNull EntryPoint.H<T> th) {
            return this.priority - th.priority;
        }

        @Override
        public boolean test(T t) {
            if (value instanceof Consumer con) {
                con.accept(t);
                return true;
            } else if (value instanceof Predicate pred) {
                return pred.test(t);
            } else return false;
        }
    }

    protected List<H<W>> handlers = new ArrayList<>();

    public void registerHandler(Predicate<W> val) {
        registerHandlerInternal(val, 0);
    }

    public void registerHandler(Consumer<W> val) {
        registerHandlerInternal(val, 0);
    }

    public void registerHandler(Consumer<W> val, int p) {
        registerHandlerInternal(val, p);
    }

    public void registerHandler(Predicate<W> val, int p) {
        registerHandlerInternal(val, p);
    }

    private void registerHandlerInternal(Object val, int p) {
        H newHandler = new H(p, val);

        int index = handlers.size() - 1;
        while (index >= 0 && handlers.get(index).priority > p) {
            --index;
        }

        handlers.add(index + 1, newHandler);
    }

    public void unregisterPredicate(Predicate<Predicate<W>> p) {
        handlers.removeIf(h -> h.value instanceof Predicate pd && p.test(pd));
    }

    public void unregisterConsumer(Predicate<Consumer<W>> p) {
        handlers.removeIf(h -> h.value instanceof Consumer pd && p.test(pd));
    }

    public void unregisterHandler(Predicate p) {
        handlers.removeIf(h -> p.test(h.value));
    }

    public abstract boolean handleValue(W express);

    public boolean isEmpty() {
        return this.handlers.isEmpty();
    }
}
