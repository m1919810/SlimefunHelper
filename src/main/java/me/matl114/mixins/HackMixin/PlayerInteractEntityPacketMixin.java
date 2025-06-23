package me.matl114.mixins.HackMixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerInteractEntityC2SPacket.class)
@Environment(EnvType.CLIENT)
public abstract class PlayerInteractEntityPacketMixin  implements Packet<ServerPlayPacketListener> {

}
