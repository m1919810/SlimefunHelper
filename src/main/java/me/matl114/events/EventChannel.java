package me.matl114.events;

public class EventChannel<T> extends ListenerPoint<Event<T>> {
    private static final Object[] VALUES = new Object[0];

    public void broadcast(T val) {
        broadcast(val, VALUES);
    }

    public void broadcast(T val, Object... val2) {
        if (!isEmpty()) {
            handleValue(new Event<>(val, false, false, val2));
        }
    }

    public boolean fireEvent(T val) {
        if (isEmpty()) {
            return true;
        } else {
            Event<T> event = new Event<>(val, true, false);
            handleValue(event);
            if (event.isCancelled()) {
                return false;
            }
            return true;
        }
    }
}
