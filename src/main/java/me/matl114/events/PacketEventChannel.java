package me.matl114.events;

import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;

public class PacketEventChannel extends EventChannelDispatcher<Packet<?>> {
    public PacketEventChannel() {
        super((v) -> Listener.getMappedPacketClass(v.getClass()));
    }

    public EventChannel<Packet<?>> getPacketSendChannel() {
        return getChannel(Boolean.FALSE);
    }

    public EventChannel<Packet<?>> getPacketReceiveChannel() {
        return getChannel(Boolean.TRUE);
    }

    @Override
    public boolean handleValue(Event<Packet<?>> express) {
        if (express.context.getPacketId().side() == NetworkSide.CLIENTBOUND) {
            getPacketReceiveChannel().handleValue(express);
        } else {
            getPacketSendChannel().handleValue(express);
        }
        return super.handleValue(express);
    }

    public <W extends Packet<?>> EventChannel<W> getChannel(Class<W> val) {
        return super.getChannel((Object) val);
    }
}
