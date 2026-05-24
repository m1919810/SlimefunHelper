package me.matl114.hacks.modules.combat;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;

public class TransactionBlocker extends BaseModule {
    public final ModulePath lagUtils = makePath(Configs.COMBAT_CONFIG, "lag-utils");
    public final ModulePath transactionBlocker = lagUtils.add("transaction-blocker");

    public TransactionBlocker() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(transactionBlocker.add("enable")).build();

    public final FlagRef enableC =
            flagBuilder(transactionBlocker.add("bw-test-1")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(PacketManager.getPacketQueueEvent().getPacketSendChannel(), this::onPacketQueue);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerPositionLookS2CPacket.class), this::onPlayerRespawnLook);
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
    }

    public void onDisableModule() {
        super.onDisableModule();
        flush();
    }

    public void flush() {
        PacketManager.flushOutBound((packet) -> {
            if (isTransactionRelated(packet.packet())) {
                return PacketManager.FlushAction.DROP;
            } else {
                return PacketManager.FlushAction.QUEUE;
            }
        });
    }

    public boolean isTransactionRelated(Packet<?> packet) {
        return packet instanceof CommonPongC2SPacket || packet instanceof CommonPingS2CPacket;
    }

    public void onPacketQueue(Event<Packet<?>> packetEvent) {
        if (enable.get() && isTransactionRelated(packetEvent.context)) {
            packetEvent.cancel();
            Listener.sendPacketNoEvents(new CommonPongC2SPacket(0));
        }
    }

    public void onPlayerRespawn(Event<PlayerRespawnS2CPacket> event) {
        if (enableC.get()) {
            enable.set(true);
        }
    }

    public void onPlayerRespawnLook(Event<PlayerPositionLookS2CPacket> event) {
        if (enableC.get() && mc.player != null && mc.player.getAbilities().flying) {
            Debug.chat("Start");
            enable.set(true);
        }
    }
}
