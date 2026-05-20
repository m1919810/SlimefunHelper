package com.viaversion.viafabricplus.protocoltranslator;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.*;
import io.netty.util.AttributeKey;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.codec.PacketCodec;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class ProtocolTranslator {
    public static AttributeKey CLIENT_CONNECTION_ATTRIBUTE_KEY ;
    public static AttributeKey<ProtocolVersion> TARGET_VERSION_ATTRIBUTE_KEY ;
    public static ProtocolVersion NATIVE_VERSION;
    public static String VIA_FLOW_CONTROL = "via-flow-control";
    public static ProtocolVersion AUTO_DETECT_PROTOCOL;
    private static ProtocolVersion targetVersion;
    private static ProtocolVersion previousVersion;

    public ProtocolTranslator() {
    }


    public static void reorderPipeline(ChannelPipeline pipeline) {
        int decoderIndex = pipeline.names().indexOf("decompress");
        if (decoderIndex != -1) {
            if (decoderIndex > pipeline.names().indexOf("via-decoder")) {
                ChannelHandler decoderHandler = pipeline.get("via-decoder");
                ChannelHandler encoderHandler = pipeline.get("via-encoder");
                pipeline.remove(decoderHandler);
                pipeline.remove(encoderHandler);
                pipeline.addAfter("decompress", "via-decoder", decoderHandler);
                pipeline.addAfter("compress", "via-encoder", encoderHandler);
            }

        }
    }

    public static ProtocolVersion getTargetVersion() {
        return targetVersion;
    }

    public static ProtocolVersion getTargetVersion(Channel channel) {
        if (!channel.hasAttr(TARGET_VERSION_ATTRIBUTE_KEY)) {
            throw new IllegalStateException("ViaFabricPlus has not injected into that channel yet!");
        } else {
            return (ProtocolVersion)channel.attr(TARGET_VERSION_ATTRIBUTE_KEY).get();
        }
    }

    public static void setTargetVersion(ProtocolVersion newVersion) {
        setTargetVersion(newVersion, false);
    }

    public static void setTargetVersion(ProtocolVersion newVersion, boolean revertOnDisconnect) {

    }

    public static void injectPreviousVersionReset(Channel channel) {
        if (previousVersion != null) {
            channel.closeFuture().addListener((future) -> {
                setTargetVersion(previousVersion);
                previousVersion = null;
            });
        }
    }

    public static UserConnection createDummyUserConnection(ProtocolVersion clientVersion, ProtocolVersion serverVersion) {
        return null;
    }

    public static UserConnection getPlayNetworkUserConnection() {
        return null;
    }
}
