package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.modules.extra.BadPacketsFix;
import me.matl114.hacks.modules.move.AutoResync;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

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

    @WrapWithCondition(
            method = "onPlayerPositionLook",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(DDD)V"))
    private boolean setVelocity(PlayerEntity player, double x, double y, double z) {
        if (MovTasks.getAutoResync().noVelocitySetback.get()) {
            return false;
        }
        return true;
    }

    @Inject(
            method = "onPlayerList",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V",
                            shift = At.Shift.BEFORE,
                            remap = false),
            cancellable = true)
    public void onInvalidPlayerEntry(PlayerListS2CPacket packet, CallbackInfo ci) {
        if (BadPacketsFix.INSTANCE.fixInvalidPlayerEntryUpdate.get()) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "onPlayerPositionLook",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayNetworkHandler;setPosition(Lnet/minecraft/entity/player/PlayerPosition;Ljava/util/Set;Lnet/minecraft/entity/Entity;Z)Z"))
    private boolean wrapSetPositionLook(
            PlayerPosition pos, Set<PositionFlag> flags, Entity entity, boolean bl, Operation<Boolean> original) {
        if (AutoResync.INSTANCE.noVelocitySetback.get()) {
            Vec3d currentVelocity = entity.getVelocity();
            boolean re = original.call(pos, flags, entity, bl);
            entity.setVelocity(currentVelocity);
            return re;
        } else {
            return original.call(pos, flags, entity, bl);
        }
    }
}
