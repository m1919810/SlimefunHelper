package me.matl114.events;

import lombok.Getter;

@Getter
public class EventContainer<T> {
    public Class<T> type;
    public T value;

    public EventContainer(Class<T> type, T value) {
        this.type = type;
        this.value = value;
    }
}
