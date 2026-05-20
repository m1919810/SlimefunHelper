package com.viaversion.viafabricplus.api;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.Channel;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

public interface ViaFabricPlusBase {
    default int apiVersion() {
        return 6;
    }

    String getVersion();

    String getImplVersion();

    Path getPath();

    ProtocolVersion getTargetVersion();

    void setTargetVersion(ProtocolVersion var1);

    ProtocolVersion getTargetVersion(Channel var1);

    ProtocolVersion getTargetVersion(ClientConnection var1);

    @Nullable
    UserConnection getPlayNetworkUserConnection();

    @Nullable UserConnection getUserConnection(ClientConnection var1);

    void setTargetVersion(ProtocolVersion var1, boolean var2);

    @Nullable ProtocolVersion getServerVersion(ServerInfo var1);

    int getMaxChatLength(ProtocolVersion var1);

    @Nullable Item translateItem(ItemStack var1, ProtocolVersion var2);

    @Nullable
    ItemStack translateItem(Item var1, ProtocolVersion var2);

    boolean itemExists(Item var1, ProtocolVersion var2);

    boolean itemExistsInConnection(Item var1);

    boolean itemExistsInConnection(ItemStack var1);

    int getStackCount(ItemStack var1);
}
