package me.matl114.events.catchers;

import me.matl114.events.Event;

public interface PacketCatcher {
    boolean catchEvent(Event<?> packet);
}
