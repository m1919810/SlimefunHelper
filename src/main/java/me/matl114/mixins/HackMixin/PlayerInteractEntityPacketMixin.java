package me.matl114.mixins.HackMixin;

import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerInteractEntityC2SPacket.class)
public abstract class PlayerInteractEntityPacketMixin  implements Packet<ServerPlayPacketListener> {

}
