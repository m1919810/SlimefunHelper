package me.matl114.hooks;

import com.google.common.base.Preconditions;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.api.ViaFabricPlusBase;
import com.viaversion.viaversion.api.protocol.packet.PacketType;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import me.matl114.versioned.SupportVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

public abstract class ViaFabricPlusHooks implements IHooks {
    public static ViaFabricPlusHooks instance;

    public static ViaFabricPlusHooks getInstance() {
        if (instance == null) {
            try {
                instance = new Impl();
            } catch (Throwable e) {
                instance = new Default();
            }
        }
        return instance;
    }

    public abstract SupportVersion getCurrentVersion();

    public SupportVersion getServerVersion() {
        return getServerVersion(MinecraftClient.getInstance().getCurrentServerEntry());
    }

    public abstract SupportVersion getServerVersion(ServerInfo server);

    public static class Default extends ViaFabricPlusHooks {
        @Override
        public SupportVersion getCurrentVersion() {
            return SupportVersion.CURRENT;
        }

        @Override
        public SupportVersion getServerVersion(ServerInfo server) {
            return SupportVersion.CURRENT;
        }

        @Override
        public ViaPacketWrapper createViaPacket() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    }

    public static class Impl extends ViaFabricPlusHooks {
        ProtocolVersion lastProtocol = null;
        SupportVersion lastVersion = null;
        ProtocolVersion lastServerProtocol = null;
        SupportVersion lastServerVersion = null;

        @Override
        public SupportVersion getCurrentVersion() {

            ProtocolVersion currentProtocol = base.getTargetVersion();
            // lazily update protocol instance
            if (!Objects.equals(currentProtocol, lastProtocol) || lastVersion == null) {
                try {
                    lastVersion = SupportVersion.parse(currentProtocol.getIncludedVersions().stream()
                            .findFirst()
                            .orElseThrow());
                    lastProtocol = currentProtocol;
                } catch (Throwable e) {
                    lastVersion = SupportVersion.CURRENT;
                    lastProtocol = currentProtocol;
                }
            }
            return lastVersion;
        }

        @Override
        public SupportVersion getServerVersion(ServerInfo server) {
            ProtocolVersion currentProtocol = base.getServerVersion(server);
            if (!Objects.equals(currentProtocol, lastServerProtocol) || lastServerVersion == null) {
                try {
                    lastServerVersion = SupportVersion.parse(currentProtocol.getIncludedVersions().stream()
                            .findFirst()
                            .orElseThrow());
                    lastServerProtocol = currentProtocol;
                } catch (Throwable e) {
                    lastServerVersion = SupportVersion.CURRENT;
                    lastServerProtocol = currentProtocol;
                }
            }
            return lastServerVersion;
        }

        @Override
        public ViaPacketWrapper createViaPacket() {
            return new ViaPacketWrapperImpl();
        }

        public boolean isEnabled() {
            return true;
        }

        ViaFabricPlusBase base;

        public Impl() {
            Class<?> clazz = ViaFabricPlus.class;
            base = Objects.requireNonNull(ViaFabricPlus.getImpl());
        }
    }

    public abstract ViaPacketWrapper createViaPacket();

    public static interface ViaPacketWrapper {
        default ViaPacketWrapper writePacketType(
                String protocolVersion, net.minecraft.network.packet.PacketType<?> packetType) {
            return writePacketType(protocolVersion, packetType.id().getPath().toUpperCase(Locale.ROOT));
        }

        public ViaPacketWrapper writePacketType(String protocolVersion, String packetType);

        public ViaPacketWrapper write(String type, Object val);

        public void scheduleSendToServer(String protocol, boolean skipPipeline);

        public void sendToServer(String protocol, boolean skipPipeline);

        public void sendRaw(boolean currentThread);
    }

    public static class ViaPacketWrapperImpl implements ViaPacketWrapper {
        PacketWrapper delegate;
        static Map<String, Map<String, PacketType>> types = new HashMap<>();

        static {
        }

        private static Map<String, PacketType> computeAndGuessTypes(String protocolVersion) {
            protocolVersion = protocolVersion.toLowerCase(Locale.ROOT);
            String[] splits = protocolVersion.split("to");
            if (splits.length == 2) {
                String higherProtocol = splits[1].toLowerCase(Locale.ROOT);
                String[] availableScannPath = {"ServerboundPackets", "ClientboundPackets"};
                Map<String, PacketType> types = new HashMap<>();
                for (String scannPath : availableScannPath) {
                    String fullPath = "com.viaversion.viaversion.protocols.v" + protocolVersion + ".packet." + scannPath
                            + higherProtocol;
                    try {
                        Class<?> clazz = Class.forName(fullPath);
                        if (Enum.class.isAssignableFrom(clazz) && PacketType.class.isAssignableFrom(clazz)) {
                            for (var re : clazz.getEnumConstants()) {
                                types.put(((Enum) re).name(), (PacketType) re);
                            }
                        }
                    } catch (Throwable e) {
                    }
                }
                return types;
            } else {
                throw new IllegalArgumentException("Can not parse protocol " + protocolVersion);
            }
        }

        public static PacketType getPacketType(String protocolVersion, String packetType) {
            String version = protocolVersion.startsWith("v") ? protocolVersion.substring(1) : protocolVersion;
            return types.computeIfAbsent(version, ViaPacketWrapperImpl::computeAndGuessTypes)
                    .get(packetType);
        }

        static final Map<String, Type<?>> typeMap = new HashMap<>();

        static {
            Field[] fields = Types.class.getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                if (Type.class.isAssignableFrom(field.getType()) && Modifier.isStatic(field.getModifiers())) {
                    try {
                        Type type = (Type) field.get(null);
                        typeMap.put(field.getName(), type);
                    } catch (Throwable e) {
                    }
                }
            }
        }

        public static <T> Type<T> getType(String type) {
            return Objects.requireNonNull((Type<T>) typeMap.get(type));
        }

        @Override
        public ViaPacketWrapper writePacketType(String protocolVersion, String packetType) {
            PacketType type = getPacketType(protocolVersion, packetType);
            if (delegate == null) {
                delegate = PacketWrapper.create(type, ViaFabricPlus.getImpl().getPlayNetworkUserConnection());
            } else {
                delegate.setPacketType(type);
            }
            return this;
        }

        @Override
        public ViaPacketWrapper write(String type, Object val) {
            Preconditions.checkNotNull(delegate, "Set packet type before write");
            delegate.write(getType(type), val);
            return this;
        }

        static Map<String, Class<?>> protocolCache = new HashMap<>();

        public static Class<?> getOrCache(String protocol) {
            String tryFindVersion =
                    (protocol.startsWith("v") ? protocol.substring(1) : protocol).toLowerCase(Locale.ROOT);

            if (protocolCache.containsKey(tryFindVersion)) {
                return protocolCache.get(tryFindVersion);
            }
            String className = "com.viaversion.viaversion.protocols.v" + tryFindVersion + ".Protocol"
                    + tryFindVersion.replace("to", "To");
            try {
                Class<?> clazz = Class.forName(className);
                protocolCache.put(tryFindVersion, clazz);
                return clazz;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void scheduleSendToServer(String protocol, boolean skipPipeline) {
            Preconditions.checkNotNull(delegate, "Set packet type before send");
            delegate.scheduleSendToServer(getOrCache(protocol), skipPipeline);
        }

        @Override
        public void sendToServer(String protocol, boolean skipPipeline) {
            Preconditions.checkNotNull(delegate, "Set packet type before send");
        }

        @Override
        public void sendRaw(boolean currentThread) {
            Preconditions.checkNotNull(delegate, "Set packet type before send");
        }
    }

    public static boolean isSupportEndTick() {
        return getInstance().getCurrentVersion().isHigherOrEqualTo(21, 2);
    }

    public static boolean isSupportDupRot() {
        return getInstance().getCurrentVersion().isLowerOrEqualTo(20, 7);
    }
}
