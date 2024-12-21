package me.matl114.SlimefunMixin.HackMixin;

import me.matl114.SlimefunUtils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkState;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.listener.ClientLoginPacketListener;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.listener.ServerLoginPacketListener;
import net.minecraft.network.listener.ServerPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Slime;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientConnection.class)
public class ClientConnectionMixin {
   // @Inject(method = "handlePacket",at=@At("HEAD"))
    private static void acceptPacket(Packet packet, PacketListener listener, CallbackInfo ci) {
        if(packet instanceof ParticleS2CPacket||packet instanceof ScoreboardPlayerUpdateS2CPacket||packet instanceof WorldTimeUpdateS2CPacket||packet instanceof ChunkDataS2CPacket||packet instanceof EntityS2CPacket.MoveRelative) {
            return;
        }

        Debug.info("accept packet",packet.getClass().getSimpleName());
        if(packet instanceof ChatMessageS2CPacket cp){
            Debug.info("Chat Message",cp.body().content(),cp.signature(),cp.unsignedContent(),cp.signature());
            Debug.info("No Wrong");
            // Debug.stackTrace();
        }else if (packet instanceof GameMessageS2CPacket cp){
            Debug.info("GameMessage",cp.content());
        }

    }
   // @Inject(method = "sendInternal",at=@At("HEAD"))
    private void sendPacket(Packet<?> packet, @Nullable PacketCallbacks callbacks, NetworkState packetState, NetworkState currentState, CallbackInfo ci) {
        Debug.info("send packet",packet.getClass().getSimpleName());
        if(packet instanceof ChatMessageC2SPacket cp){
            Debug.info("ChatMessage",cp.chatMessage());

            // Debug.stackTrace();

        }

    }
}
