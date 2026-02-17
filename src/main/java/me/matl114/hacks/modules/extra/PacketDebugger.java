package me.matl114.hacks.modules.extra;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ExtraTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.StringRef;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class PacketDebugger extends BaseModule {
    public static final String[] DEBUG_PACKET_IN = new String[] {"packet-debugger", "debug-packet-in"};

    public static final String[] DEBUG_PACKET_OUT = new String[] {"packet-debugger", "debug-packet-out"};

    public static final String[] DEBUG_PACKET_TYPE = new String[] {"packet-debugger", "debug-packet-type"};

    public static final String[] INTERCEPT_PACKET = new String[] {"packet-debugger", "intercept-packet"};

    public static final String[] INTERCEPT_PACKET_TYPE = new String[] {"packet-debugger", "intercept-packet-type"};

    public PacketDebugger() {}

    private Set<PacketType<?>> typesDebug = new HashSet<>();

    private Set<PacketType<?>> typesIntercept = new HashSet<>();

    private Set<PacketType<?>> getDebugTypes(String regex) {
        Set<PacketType<?>> types = new HashSet<>();
        for (var entry : Listener.getRegisteredPacketTypes().keySet()) {
            if (Pattern.matches(regex, entry.id().getPath())) {
                types.add(entry);
            }
        }
        return types;
    }

    public final FlagRef debugIn =
            flagBuilder(Configs.TEST_CONFIG, DEBUG_PACKET_IN).build();

    public final FlagRef debugOut =
            flagBuilder(Configs.TEST_CONFIG, DEBUG_PACKET_OUT).build();

    public final StringRef debugPacketType = builder(Configs.TEST_CONFIG, DEBUG_PACKET_TYPE, StringRef.TYPE)
            .defaultValue("^(move_player_.*)$")
            .validator(Configs.REGEX_VALIDATOR)
            .updateListener((v) -> typesDebug = getDebugTypes(v))
            .build();

    public final FlagRef interceptPacket =
            flagBuilder(Configs.TEST_CONFIG, INTERCEPT_PACKET).build();

    public final StringRef interceptPacketType = builder(Configs.TEST_CONFIG, INTERCEPT_PACKET_TYPE, StringRef.TYPE)
            .defaultValue("^()$")
            .validator(Configs.REGEX_VALIDATOR)
            .updateListener((v) -> typesIntercept = getDebugTypes(v))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPreHandlePoint(), this::onPacketHandle);
        registerListener(Listener.getPacketPostSendPoint(), this::onPacketSend);
        registerListener(Listener.getPacketAcceptPoint(), this::onPacketReceive);
    }

    public void onPacketHandle(Event<Packet<?>> packetEvent) {
        if (packetEvent.isCancelled()) return;
        if (debugIn.get()) {
            Packet<?> type = packetEvent.context();
            if (typesDebug.contains(type.getPacketId())) {
                if (type instanceof PlayerPositionLookS2CPacket positionLookS2CPacket) {
                    Vec3d vec3d = new Vec3d(
                            positionLookS2CPacket.getX(), positionLookS2CPacket.getY(), positionLookS2CPacket.getZ());
                    ExtraTasks.debug(
                            "Accept",
                            type.getPacketId().id(),
                            vec3d.x,
                            vec3d.y,
                            vec3d.z,
                            ", Pitch:",
                            positionLookS2CPacket.getPitch(),
                            ", Yaw:",
                            positionLookS2CPacket.getYaw());
                } else {
                    ExtraTasks.debug("Accept", type.getPacketId().id());
                }
            }
        }
    }

    public void onPacketSend(Event<Packet<?>> packetEvent) {
        if (packetEvent.isCancelled()) return;
        if (debugOut.get()) {
            Packet<?> type = packetEvent.context();
            if (typesDebug.contains(type.getPacketId())) {
                if (type instanceof PlayerMoveC2SPacket moveC2SPacket) {
                    ExtraTasks.debug(
                            "Send",
                            type.getPacketId().id(),
                            moveC2SPacket.getX(0.0),
                            moveC2SPacket.getY(0.0),
                            moveC2SPacket.getZ(0.0),
                            ", Pitch:",
                            moveC2SPacket.getPitch(0.0F),
                            ", Yaw:",
                            moveC2SPacket.getYaw(0.0F),
                            ", onGround:",
                            moveC2SPacket.isOnGround());
                } else {
                    ExtraTasks.debug("Send", type.getPacketId().id());
                }
            }
        }
    }

    public void onPacketReceive(Event<Packet<?>> packetEvent) {
        if (packetEvent.isCancelled()) return;
        if (interceptPacket.get()) {
            Packet<?> type = packetEvent.context();
            if (typesIntercept.contains(type.getPacketId())) {
                packetEvent.cancel();
            }
        }
    }

    // todo: move packet debug here

}
