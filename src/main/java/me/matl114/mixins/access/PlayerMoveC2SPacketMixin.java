package me.matl114.mixins.access;

import lombok.Getter;
import lombok.Setter;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(PlayerMoveC2SPacket.class)
public abstract class PlayerMoveC2SPacketMixin implements PlayerMoveC2SPacketAccess {
    @Override
    @Mutable
    @Accessor("onGround")
    public abstract void setOnGround(boolean onGround);

    @Override
    @Mutable
    @Accessor("pitch")
    public abstract void setPitch(float pitch);

    @Override
    @Mutable
    @Accessor("yaw")
    public abstract void setYaw(float yaw);

    @Unique
    @Getter
    @Setter
    Cause cause;

    //    boolean manual = false;
    //
    //    @Unique
    //    @Override
    //    public void setManual(boolean manual){
    //        this.manual = manual;
    //    }
    //
    //    @Unique
    //    @Override
    //    public boolean isManual(){
    //        return manual;
    //    }
}
