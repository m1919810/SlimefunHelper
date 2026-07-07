package me.matl114.mixins.hack;

import java.util.*;
import me.matl114.accessors.access.ClientPlayerAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {

    @Inject(
            method = "onCloseScreen",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/ClientPlayerEntity;closeScreen()V",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private void onCloseScreenClearKeepedInv(CloseScreenS2CPacket packet, CallbackInfo ci) {
        ClientPlayerAccess access = ClientPlayerAccess.of(MinecraftClient.getInstance().player);
        if (access.getKeepedInvHandler() != null && access.getKeepedInvHandler().syncId == packet.getSyncId()) {
            access.clearKeepedInventory(true);
        }
        if (MinecraftClient.getInstance().player.currentScreenHandler.syncId != packet.getSyncId()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At(value = "RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onScreenHandlerSlotUpdateSyncToKeeped(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        // Debug.info("Received screen handler slot update packet
        // ",packet.getSyncId(),packet.getSlot(),packet.getItemStack());
        if (MinecraftClient.getInstance().player != null) {
            ClientPlayerAccess access = ClientPlayerAccess.of(MinecraftClient.getInstance().player);
            if (access.getKeepedInvHandler() != null && packet.getSyncId() == access.getKeepedInvHandler().syncId) {
                access.getKeepedInvHandler().setStackInSlot(packet.getSlot(), packet.getRevision(), packet.getStack());
            }
        }
    }

    @Inject(method = "onInventory", at = @At(value = "RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onInventorySyncToKeeped(InventoryS2CPacket packet, CallbackInfo ci) {
        if (MinecraftClient.getInstance().player != null) {
            ClientPlayerAccess access = ClientPlayerAccess.of(MinecraftClient.getInstance().player);
            if (access.getKeepedInvHandler() != null && packet.getSyncId() == access.getKeepedInvHandler().syncId) {
                access.getKeepedInvHandler()
                        .updateSlotStacks(packet.getRevision(), packet.getContents(), packet.getCursorStack());
            }
        }
    }

    @Inject(
            method = "onOpenScreen",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screen/ingame/HandledScreens;open(Lnet/minecraft/screen/ScreenHandlerType;Lnet/minecraft/client/MinecraftClient;ILnet/minecraft/text/Text;)V",
                            shift = At.Shift.BEFORE))
    private void onInventoryOpenCloseKeepInventory(OpenScreenS2CPacket packet, CallbackInfo ci) {
        // for keepInv
        if (MinecraftClient.getInstance().player != null) {
            ClientPlayerAccess access = ClientPlayerAccess.of(MinecraftClient.getInstance().player);
            access.clearKeepedInventory(false);
        }
    }
}
