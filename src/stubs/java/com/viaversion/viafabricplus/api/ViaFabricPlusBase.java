package com.viaversion.viafabricplus.api;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.Channel;
import java.nio.file.Path;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;

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

    UserConnection getPlayNetworkUserConnection();

    UserConnection getUserConnection(ClientConnection var1);

    void setTargetVersion(ProtocolVersion var1, boolean var2);

    ProtocolVersion getServerVersion(ServerInfo var1);

    int getMaxChatLength(ProtocolVersion var1);

    Item translateItem(ItemStack var1, ProtocolVersion var2);

    ItemStack translateItem(Item var1, ProtocolVersion var2);

    boolean itemExists(Item var1, ProtocolVersion var2);

    boolean itemExistsInConnection(Item var1);

    boolean itemExistsInConnection(ItemStack var1);

    int getStackCount(ItemStack var1);
}
