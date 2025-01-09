package me.matl114.SlimefunMixin.FixMixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ParticleCountLimitMixin {

    @ModifyVariable(method = "onParticle", at = @At("HEAD"), index = 1, argsOnly = true)
    private ParticleS2CPacket onParticle(ParticleS2CPacket packet) {
        if(packet.getCount()>1000){
            return new ParticleS2CPacket(packet.getParameters(),packet.isLongDistance(),packet.getX(),packet.getY(),packet.getZ(),packet.getOffsetX(),packet.getOffsetY(),packet.getOffsetZ(),packet.getSpeed(),1000);
        }
        return packet;

    }
}
