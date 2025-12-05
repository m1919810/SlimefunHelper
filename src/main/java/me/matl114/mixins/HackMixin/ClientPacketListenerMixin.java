package me.matl114.mixins.HackMixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.CommandDispatcher;
import me.matl114.access.ClientPlayerAccess;
import me.matl114.hackUtils.ChatTasks;
import me.matl114.hackUtils.MovTasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.utils.UtilClass.Event;
import me.matl114.utils.UtilClass.LazyList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

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
            access.clearKeepedInventory(false);
            HandledScreen<?> screen = access.getServerHandledScreen();
            //check for open failure
            if(screen != null && screen.getScreenHandler().syncId == id){
                Listener.getScreenOpenPoint().handleValue(screen);
            }
        }
    }

//    @ModifyVariable(method = "sendChatCommand", at = @At(value = "HEAD"), argsOnly = true)
//    private String onChat0(String args){
//
//    }
    // test
    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void onChat0(String command, CallbackInfo ci, @Local(argsOnly = true)LocalRef<String> commandRef){
        String chatContent = "/" + command;
        Event<String> value = new Event<>(chatContent, true, true);
        Listener.getChatEntryPoint().handleValue(value);
        if(value.isCancelled()){
            ci.cancel();
            return;
        }
        if(value.context() == null || value.context().isEmpty()){
            ci.cancel();
            return;
        }
        if(!Objects.equals(chatContent, value.context())){
            commandRef.set(value.context());
        }
    }

//    //stop sending empty commands
//    @Inject(method = "sendChatCommand",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;parse(Ljava/lang/String;)Lcom/mojang/brigadier/ParseResults;"), cancellable = true)
//    private void onChat1(String command, CallbackInfo ci){
//        // empty command will be ignored
//        if(command == null || command.isEmpty()){
//            ci.cancel();
//        }

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChat2(String content, CallbackInfo ci, @Local(argsOnly = true)LocalRef<String> contentRef){
        Event<String> value = new Event<>(content, true, true);
        Listener.getChatEntryPoint().handleValue(value);
        if(value.isCancelled()){
            //set null string to trigger ret
            ci.cancel();
            return;
        }
        if(value.context() == null){
            ci.cancel();
            return;
        }
        contentRef.set(value.context());
    }
//    @Inject(method = "sendChatMessage", at = @At(value = "INVOKE", target = "Ljava/time/Instant;now()Ljava/time/Instant;"), cancellable = true)
//    private void onChat3(String content, CallbackInfo ci){
//        if(content == null){
//            ci.cancel();
//        }
//    }

    @Unique
    private boolean playerRecreateOnJoin = false;
    @Inject(method = "onGameJoin", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;createPlayer(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/stat/StatHandler;Lnet/minecraft/client/recipebook/ClientRecipeBook;)Lnet/minecraft/client/network/ClientPlayerEntity;", shift = At.Shift.AFTER))
    private void onGameJoinCreatePlayer0(GameJoinS2CPacket packet, CallbackInfo ci){
        playerRecreateOnJoin = true;
    }
    @Inject(method = "onGameJoin", at = @At("RETURN"))
    private void onGameJoinEntryPoint(GameJoinS2CPacket packet, CallbackInfo ci){
        Listener.getGameJoinPoint().handleValue(null);
        Listener.getWorldSwitchPoint().handleValue(null);
        if(playerRecreateOnJoin){
            playerRecreateOnJoin = false;
            Listener.getThisPlayerSpawnPoint().handleValue(MinecraftClient.getInstance().player);
        }

    }
    @Unique
    private boolean worldChangeOnRespawn = false;
    @Inject(method = "onPlayerRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;<init>(Lnet/minecraft/client/network/ClientPlayNetworkHandler;Lnet/minecraft/client/world/ClientWorld$Properties;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/registry/entry/RegistryEntry;IILjava/util/function/Supplier;Lnet/minecraft/client/render/WorldRenderer;ZJ)V", shift = At.Shift.AFTER))
    private void onPlayerSwitchDimension0(PlayerRespawnS2CPacket packet, CallbackInfo ci){
        worldChangeOnRespawn = true;
    }

    @Inject(method = "onPlayerRespawn", at = @At("RETURN"))
    private void onPlayerSwitchDimension(PlayerRespawnS2CPacket packet, CallbackInfo ci){
        if(worldChangeOnRespawn){
            worldChangeOnRespawn = false;
            Listener.getWorldSwitchPoint().handleValue(null);
        }
        Listener.getThisPlayerSpawnPoint().handleValue(MinecraftClient.getInstance().player);
    }


    @Shadow
    private CommandDispatcher<CommandSource> commandDispatcher;


    @Shadow public abstract ClientConnection getConnection();

    @Inject(method = "onCommandTree", at = @At("RETURN"))
    private void onCommandDispatcherReload(CommandTreeS2CPacket packet, CallbackInfo ci){
        Listener.getCommandReloadPoint().handleValue(commandDispatcher);
    }

    @Redirect(method = "onPlayerPositionLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(DDD)V"))
    private void onPosResyncDisableVelocityChange(PlayerEntity instance, double v, double v2, double v3){
        //disable velocity resync
        //fixme: turn this into Event
        Vec3d vec3d = new Vec3d(v, v2, v3);
        if(!Listener.getTeleportConfirmVelocityUpdatePoint().isEmpty()){
            Event<Vec3d> vcUpdate = new Event<>(vec3d, true, true);
            Listener.getTeleportConfirmVelocityUpdatePoint().handleValue(vcUpdate);
            if(vcUpdate.isCancelled()){
                return;
            }
            vec3d = vcUpdate.context();
        }
        instance.setVelocity(vec3d);
//        MovTasks.configurateTeleportBackVelocityUpdate(instance, new Vec3d(v, v2, v3));
    }
    @Inject(method = "onPlayerPositionLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/packet/Packet;)V", ordinal = 1,shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void onSetBackResponseAction(PlayerPositionLookS2CPacket packet, CallbackInfo ci, @Local PlayerEntity playerEntity){
        // turn this into Event

        if(!Listener.getTeleportConfirmResponsePoint().isEmpty()){
            MovTasks.MovInfo eventContext = new MovTasks.MovInfo(playerEntity.getPos(), false, false, new Vec2f(playerEntity.getPitch(), playerEntity.getYaw()));
            Event<MovTasks.MovInfo> setBackEvent = new Event<>(eventContext, false, true);
            Listener.getTeleportConfirmResponsePoint().handleValue(setBackEvent);
            eventContext = setBackEvent.context();
            Vec2f override = eventContext.rotationOverride();
            float pitch = override == null? playerEntity.getPitch() : override.x;
            float yaw = override == null ? playerEntity.getYaw(): override.y;
            boolean onGround = eventContext.oGroundOverride() == null? playerEntity.isOnGround(): eventContext.oGroundOverride();
            //send and cancel
            this.getConnection().send(new PlayerMoveC2SPacket.Full(eventContext.vec3d().x, eventContext.vec3d().y, eventContext.vec3d().z, yaw, pitch, onGround));

            ci.cancel();
        }

    }

    @ModifyArg(method = "onEntityTrackerUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/data/DataTracker;writeUpdatedEntries(Ljava/util/List;)V"))
    private List<DataTracker.SerializedEntry<?>> makeModifiableList(List<DataTracker.SerializedEntry<?>> entries){

        return new LazyList<>(entries);
    }


    @Inject(method = "onPlayerList", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/SocialInteractionsManager;setPlayerOnline(Lnet/minecraft/client/network/PlayerListEntry;)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void onPlayerJoinAndSetOnline(PlayerListS2CPacket packet, CallbackInfo ci, @Local PlayerListEntry playerListEntry){
        Listener.getOtherPlayerJoinPoint().handleValue(playerListEntry);
    }

    @Inject(method = "onPlayerRemove", at = @At(value = "INVOKE", target = "Ljava/util/Set;remove(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void onPlayerRemoveAndOffline(PlayerRemoveS2CPacket packet, CallbackInfo ci, @Local PlayerListEntry playerListEntry){
        Listener.getOtherPlayerExitPoint().handleValue(playerListEntry);
    }
}
