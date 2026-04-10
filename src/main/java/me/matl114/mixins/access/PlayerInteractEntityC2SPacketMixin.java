package me.matl114.mixins.access;

import me.matl114.accessors.access.PlayerInteractEntityC2SPacketAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(PlayerInteractEntityC2SPacket.class)
public abstract class PlayerInteractEntityC2SPacketMixin implements PlayerInteractEntityC2SPacketAccess {
    @Override
    @Mutable
    @Accessor("entityId")
    public abstract void setEntityId(int entityId);

    @Override
    @Mutable
    @Accessor("type")
    public abstract void setType(PlayerInteractEntityC2SPacket.InteractTypeHandler type);

    @Override
    @Mutable
    @Accessor("playerSneaking")
    public abstract void setPlayerSneaking(boolean playerSneaking);
}
