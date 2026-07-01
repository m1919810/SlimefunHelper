package me.matl114.hacks.modules.extra;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ExtraTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.StringRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class PacketDebugger extends BaseModule {
    public final ModulePath packetDebugger = makePath(Configs.TEST_CONFIG, "packet-debugger");

    public PacketDebugger() {
        super("PacketDebug");
        bindFlag(enable);
    }

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

    public final FlagRef enable = flagBuilder(packetDebugger.addEnable()).build();

    public final KeyBindRef enablePressShow = moduleEntry(
                    packetDebugger.addHotkey(), new MultiKeyBind(), packetDebugger.addEnable())
            .build();

    public final FlagRef debugIn =
            flagBuilder(packetDebugger.add("debug-packet-in")).build();

    public final FlagRef debugOut =
            flagBuilder(packetDebugger.add("debug-packet-out")).build();

    public final StringRef debugPacketType = builder(packetDebugger.add("debug-packet-type"), StringRef.TYPE)
            .defaultValue("^(move_player_.*)$")
            .validator(Configs.REGEX_VALIDATOR)
            .updateListener((v) -> typesDebug = getDebugTypes(v))
            .build();

    public final FlagRef debugTime =
            flagBuilder(packetDebugger.add("debug-time")).build();

    public final FlagRef interceptPacket =
            flagBuilder(packetDebugger.add("intercept-packet")).build();

    public final StringRef interceptPacketType = builder(packetDebugger.add("intercept-packet-type"), StringRef.TYPE)
            .defaultValue("^()$")
            .validator(Configs.REGEX_VALIDATOR)
            .updateListener((v) -> typesIntercept = getDebugTypes(v))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPreHandlePoint(), this::onPacketHandle, Integer.MIN_VALUE);
        registerListener(Listener.getPacketPostSendPoint(), this::onPacketSend, Integer.MIN_VALUE);
        registerListener(Listener.getPacketPoint(), this::onPacket);
    }

    public static String simplifyId(Identifier id) {
        if (Objects.equals("minecraft", id.getNamespace())) {
            return "mc:" + id.getPath();
        } else return id.toString();
    }

    public void onPacketHandle(Event<Packet<?>> packetEvent) {
        if (packetEvent.isCancelled()) return;
        if (enable.get() && debugIn.get()) {
            Packet<?> type = packetEvent.context();
            if (typesDebug.contains(type.getPacketId())) {
                String timeStr = debugTime.get() ? (", Tick: " + Tasks.getTick()) : "";
                if (type instanceof PlayerPositionLookS2CPacket positionLookS2CPacket) {
                    Vec3d vec3d = positionLookS2CPacket.change().position();
                    ExtraTasks.debug(
                            "Accept",
                            simplifyId(type.getPacketId().id()),
                            vec3d.x,
                            vec3d.y,
                            vec3d.z,
                            ", Pitch:",
                            positionLookS2CPacket.change().pitch(),
                            ", Yaw:",
                            positionLookS2CPacket.change().yaw(),
                            timeStr);
                } else {
                    ExtraTasks.debug("Accept", simplifyId(type.getPacketId().id()), timeStr);
                }
            }
        }
    }

    public void onPacketSend(Event<Packet<?>> packetEvent) {
        if (packetEvent.isCancelled()) return;
        if (enable.get() && debugOut.get()) {
            Packet<?> type = packetEvent.context();
            if (typesDebug.contains(type.getPacketId())) {
                String timeStr = debugTime.get() ? (", Tick: " + Tasks.getTick()) : "";
                if (type instanceof PlayerMoveC2SPacket moveC2SPacket) {
                    ExtraTasks.debug(
                            "Send",
                            simplifyId(type.getPacketId().id()),
                            moveC2SPacket.getX(0.0),
                            moveC2SPacket.getY(0.0),
                            moveC2SPacket.getZ(0.0),
                            ", Pitch:",
                            moveC2SPacket.getPitch(0.0F),
                            ", Yaw:",
                            moveC2SPacket.getYaw(0.0F),
                            ", onGround:",
                            moveC2SPacket.isOnGround(),
                            timeStr);
                } else if (type instanceof PlayerInputC2SPacket playerInputC2SPacket) {
                    PlayerInputUtils.Input input = PlayerInputUtils.of(playerInputC2SPacket);
                    ExtraTasks.debug("Send", simplifyId(type.getPacketId().id()), input, timeStr);
                } else if (type instanceof PlayerActionC2SPacket actionC2SPacket) {
                    ExtraTasks.debug(
                            "Send",
                            simplifyId(type.getPacketId().id()),
                            actionC2SPacket.getAction().name(),
                            actionC2SPacket.getPos(),
                            actionC2SPacket.getSequence(),
                            timeStr);
                } else if (type instanceof PlayerInteractEntityC2SPacket interact) {
                    ExtraTasks.debug(
                            "Send",
                            simplifyId(type.getPacketId().id()),
                            ((Enum) interact.type.getType()).name(),
                            interact.entityId,
                            timeStr);
                } else if (type instanceof ClientCommandC2SPacket ccmd) {
                    ExtraTasks.debug(
                            "Send",
                            simplifyId(type.getPacketId().id()),
                            ccmd.getMode().name(),
                            timeStr);
                } else {
                    ExtraTasks.debug("Send", simplifyId(type.getPacketId().id()), timeStr);
                }
            }
        }
    }

    public void onPacket(Event<Packet<?>> packetEvent) {
        if (packetEvent.isCancelled()) return;
        if (enable.get() && interceptPacket.get()) {
            Packet<?> type = packetEvent.context();
            if (typesIntercept.contains(type.getPacketId())) {
                packetEvent.cancel();
            }
        }
    }
}
