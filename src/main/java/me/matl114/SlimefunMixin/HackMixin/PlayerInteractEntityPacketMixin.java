package me.matl114.SlimefunMixin.HackMixin;

import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(PlayerInteractEntityC2SPacket.class)
public abstract class PlayerInteractEntityPacketMixin  implements Packet<ServerPlayPacketListener> {

}
