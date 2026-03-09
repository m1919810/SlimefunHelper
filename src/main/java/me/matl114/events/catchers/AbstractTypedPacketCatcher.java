package me.matl114.events.catchers;

import me.matl114.events.Event;
import net.minecraft.network.packet.Packet;

public abstract class AbstractTypedPacketCatcher<T extends Packet<?>> implements PacketCatcher {
    public Class<T> packetClass;

    public AbstractTypedPacketCatcher(Class<T> packetClass) {
        this.packetClass = packetClass;
    }

    @Override
    public boolean catchEvent(Event<?> packet) {
        if (packetClass.isInstance(packet.context())) {
            return onEvent((Event<T>) packet);
        }
        return false;
    }

    public abstract boolean onEvent(Event<T> packet);
}
