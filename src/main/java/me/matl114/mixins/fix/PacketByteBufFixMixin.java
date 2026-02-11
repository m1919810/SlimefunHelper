package me.matl114.mixins.fix;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(PacketByteBuf.class)
public abstract class PacketByteBufFixMixin {
    @ModifyVariable(
            method = "writeString(Ljava/lang/String;I)Lnet/minecraft/network/PacketByteBuf;",
            at = @At(value = "HEAD"),
            argsOnly = true,
            index = 2)
    public int modifyPacketStringLength(int var) {
        return 262144;
    }
}
