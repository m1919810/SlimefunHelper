package me.matl114.mixins.access;

import me.matl114.accessors.access.PlayerInteractEntityC2SPacketAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(PlayerInteractEntityC2SPacket.class)
public abstract class PlayerInteractEntityC2SPacketMixin implements PlayerInteractEntityC2SPacketAccess {
    @Shadow
    @Final
    public PlayerInteractEntityC2SPacket.InteractTypeHandler type;

    @Shadow
    @Final
    public static PlayerInteractEntityC2SPacket.InteractTypeHandler ATTACK;

    @Override
    @Mutable
    @Accessor("entityId")
    public abstract void setEntityId(int entityId);

    @Mutable
    @Accessor("entityId")
    @Override
    public abstract int getEntityId();

    @Override
    @Mutable
    @Accessor("type")
    public abstract void setType(PlayerInteractEntityC2SPacket.InteractTypeHandler type);

    @Override
    @Mutable
    @Accessor("playerSneaking")
    public abstract void setPlayerSneaking(boolean playerSneaking);

    @Override
    public boolean isAttack() {
        return this.type.getType() == ATTACK.getType();
    }
}
