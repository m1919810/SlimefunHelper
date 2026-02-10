package me.matl114.events;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class EventChannelDispatcher<T> extends EventChannel<T> {
    public Function<T, ?> dispatcher;

    public Map<?, EventChannel<? extends T>> channels;

    public EventChannelDispatcher(Function<T, ?> dispatcher) {
        this.dispatcher = dispatcher;
        this.channels = new HashMap<>();
    }

    @Override
    public boolean handleValue(Event<T> express) {
        boolean val = super.handleValue(express);
        Object typeDispatch = dispatcher.apply(express.context());
        if (channels.containsKey(typeDispatch)) {
            var channel = channels.get(typeDispatch);
            if (channel != null && !channel.isEmpty()) {
                channel.handleValue((Event) express);
            }
        }
        return val;
    }

    public <W extends T> EventChannel<W> getChannel(Object val) {
        return (EventChannel<W>) ((Map) this.channels).computeIfAbsent(val, (v) -> new EventChannel<>());
    }
}
