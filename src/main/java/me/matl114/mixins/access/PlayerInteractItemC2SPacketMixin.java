package me.matl114.mixins.access;

import me.matl114.accessors.access.PlayerInteractItemC2SPacketAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "<init>(Lnet/minecraft/util/Hand;IFF)V", at = @At("RETURN"))
    private void trackUseContext(Hand hand, int sequence, float yaw, float pitch, CallbackInfo ci) {
        if (MinecraftClient.getInstance().player != null) {
            useContext =
                    MinecraftClient.getInstance().player.getStackInHand(hand).copy();
        }
    }

    @Unique
    ItemStack useContext;

    public void setItemStack(ItemStack stack) {
        useContext = stack;
    }

    public ItemStack getItemStack() {
        return useContext;
    }
}
