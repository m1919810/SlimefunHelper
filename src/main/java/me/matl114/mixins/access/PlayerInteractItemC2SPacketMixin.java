package me.matl114.mixins.access;

import me.matl114.accessors.access.PlayerInteractItemC2SPacketAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(PlayerInteractItemC2SPacket.class)
public abstract class PlayerInteractItemC2SPacketMixin implements PlayerInteractItemC2SPacketAccess {

    @Override
    @Mutable
    @Accessor("hand")
    public abstract void setHand(Hand hand);

    @Override
    @Mutable
    @Accessor("yaw")
    public abstract void setYaw(float yaw);

    @Override
    @Mutable
    @Accessor("pitch")
    public abstract void setPitch(float pitch);
}
