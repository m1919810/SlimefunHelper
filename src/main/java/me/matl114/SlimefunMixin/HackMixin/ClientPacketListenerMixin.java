package me.matl114.SlimefunMixin.HackMixin;

import me.matl114.Access.ClientPlayerAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.checkerframework.checker.units.qual.A;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "onCloseScreen",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;closeScreen()V",shift = At.Shift.BEFORE),cancellable = true)
    private void onCloseScreenClearKeepedInv(CloseScreenS2CPacket packet, CallbackInfo ci) {
        ClientPlayerAccess access=ClientPlayerAccess.of(MinecraftClient.getInstance().player);
        if(access.getKeepedInvHandler()!=null&&access.getKeepedInvHandler().syncId==packet.getSyncId()){
            access.clearKeepedInventory(true);
        }
        if(MinecraftClient.getInstance().player.currentScreenHandler.syncId!=packet.getSyncId()){
            ci.cancel();
        }
    }
    @Inject(method = "onScreenHandlerSlotUpdate",at=@At(value = "RETURN"),locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onScreenHandlerSlotUpdateSyncToKeeped(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        if(MinecraftClient.getInstance().player!=null){
            ClientPlayerAccess access=ClientPlayerAccess.of(MinecraftClient.getInstance().player);
            if (access.getKeepedInvHandler()!=null&& packet.getSyncId() == access.getKeepedInvHandler().syncId  && (packet.getSyncId() != 0)) {
                access.getKeepedInvHandler().setStackInSlot(packet.getSlot(), packet.getRevision(), packet.getItemStack());
            }
        }
    }
}
