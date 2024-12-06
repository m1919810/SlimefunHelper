package me.matl114.SlimefunMixin.HackMixin;

import me.matl114.Access.ClientPlayerAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onCloseScreen",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;closeScreen()V",shift = At.Shift.BEFORE),cancellable = true)
    private void onCloseScreenClearKeepedInv(CloseScreenS2CPacket packet, CallbackInfo ci) {
        ClientPlayerAccess access=ClientPlayerAccess.of(this.client.player);
        if(access.getKeepedInvHandler()!=null&&access.getKeepedInvHandler().syncId==packet.getSyncId()){
            access.clearKeepedInventory(true);
        }
        if(this.client.player.currentScreenHandler.syncId!=packet.getSyncId()){
            ci.cancel();
        }
    }
}
