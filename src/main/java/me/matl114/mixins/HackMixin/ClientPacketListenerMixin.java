package me.matl114.mixins.HackMixin;

import me.matl114.access.ClientPlayerAccess;
import me.matl114.hackUtils.ChatTasks;
import me.matl114.listenerUtils.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Objects;

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
       // Debug.info("Received screen handler slot update packet ",packet.getSyncId(),packet.getSlot(),packet.getItemStack());
        if(MinecraftClient.getInstance().player!=null){
            ClientPlayerAccess access=ClientPlayerAccess.of(MinecraftClient.getInstance().player);
            if (access.getKeepedInvHandler()!=null&& packet.getSyncId() == access.getKeepedInvHandler().syncId ) {
                access.getKeepedInvHandler().setStackInSlot(packet.getSlot(), packet.getRevision(), packet.getStack());
            }
        }
    }
    @Inject(method = "onInventory",at=@At(value = "RETURN"),locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onInventorySyncToKeeped(InventoryS2CPacket packet, CallbackInfo ci) {
        if(MinecraftClient.getInstance().player!=null){
            ClientPlayerAccess access=ClientPlayerAccess.of(MinecraftClient.getInstance().player);
            if (access.getKeepedInvHandler()!=null&& packet.getSyncId() == access.getKeepedInvHandler().syncId ) {
                access.getKeepedInvHandler().updateSlotStacks(packet.getRevision(),packet.getContents(),packet.getCursorStack());
            }
        }
    }

    @Inject(method = "onOpenScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreens;open(Lnet/minecraft/screen/ScreenHandlerType;Lnet/minecraft/client/MinecraftClient;ILnet/minecraft/text/Text;)V",shift = At.Shift.AFTER))
    private void onInventoryOpen(OpenScreenS2CPacket packet, CallbackInfo ci){
        int id = packet.getSyncId();
        if(MinecraftClient.getInstance().player!=null){
            ClientPlayerAccess access=ClientPlayerAccess.of(MinecraftClient.getInstance().player);
            HandledScreen<?> screen = access.getServerHandledScreen();
            //check for open failure
            if(screen != null && screen.getScreenHandler().syncId == id){
                Listener.getScreenOpenPoint().handleValue(screen);
            }
        }
    }

    @ModifyVariable(method = "sendChatCommand", at = @At(value = "HEAD"), argsOnly = true)
    private String onChat0(String args){
        String chatContent = "/" + args;
        MutableObject<String> value = new MutableObject<>(chatContent);
        if(!ChatTasks.getChatEntryPoint().handleValue(value)){
            //set null string to trigger ret
            return "";
        }
        if(!Objects.equals(chatContent, value.getValue())){
            return value.getValue().substring(1);
        }
        return args;
    }
    //stop sending empty commands
    @Inject(method = "sendChatCommand",at = @At(value = "INVOKE", target = "Ljava/time/Instant;now()Ljava/time/Instant;"), cancellable = true)
    private void onChat1(String command, CallbackInfo ci){
        // empty command will be ignored
        if(command == null || command.isEmpty()){
            ci.cancel();
        }
    }
    @ModifyVariable(method = "sendChatMessage", at = @At(value = "HEAD"), argsOnly = true)
    private String onChat2(String args){
        MutableObject<String> value = new MutableObject<>(args);
        if(!ChatTasks.getChatEntryPoint().handleValue(value)){
            //set null string to trigger ret
            return null;
        }
        return value.getValue();
    }
    @Inject(method = "sendChatMessage", at = @At(value = "INVOKE", target = "Ljava/time/Instant;now()Ljava/time/Instant;"), cancellable = true)
    private void onChat3(String content, CallbackInfo ci){
        if(content == null){
            ci.cancel();
        }
    }

    @Inject(method = "onGameJoin", at = @At("RETURN"))
    private void onGameJoinEntryPoint(GameJoinS2CPacket packet, CallbackInfo ci){
        Listener.getGameJoinPoint().handleValue(null);
    }
}
