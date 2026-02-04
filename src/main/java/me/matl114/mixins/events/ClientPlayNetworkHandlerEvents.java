package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.events.Event;
import me.matl114.utils.collections.LazyList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.Objects;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerEvents {
    @Inject(method = "onOpenScreen", at = @At("RETURN"))
    private void onPostInventoryOpen(OpenScreenS2CPacket packet, CallbackInfo ci){
        if(MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> screen){
            Listener.getPostOpenHandledScreen().broadcast(screen);
        }
    }

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void onChat0(String command, CallbackInfo ci, @Local(argsOnly = true) LocalRef<String> commandRef){
        String chatContent = "/" + command;
        Event<String> value = new Event<>(chatContent, true, true);
        Listener.getChatSend().handleValue(value);
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

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChat2(String content, CallbackInfo ci, @Local(argsOnly = true)LocalRef<String> contentRef){
        Event<String> value = new Event<>(content, true, true);
        Listener.getChatSend().handleValue(value);
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

    @Shadow
    private ClientWorld world;
    @Unique
    private boolean playerRecreateOnJoin = false;
    @Inject(method = "onGameJoin", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;createPlayer(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/stat/StatHandler;Lnet/minecraft/client/recipebook/ClientRecipeBook;)Lnet/minecraft/client/network/ClientPlayerEntity;", shift = At.Shift.AFTER))
    private void onGameJoinCreatePlayer0(GameJoinS2CPacket packet, CallbackInfo ci){
        playerRecreateOnJoin = true;
    }
    @Inject(method = "onGameJoin", at = @At("RETURN"))
    private void onGameJoinEntryPoint(GameJoinS2CPacket packet, CallbackInfo ci){
        Listener.getGameJoinPoint().broadcast(MinecraftClient.getInstance().player);
        Listener.getWorldSwitchPoint().broadcast(this.world);
        if(playerRecreateOnJoin){
            playerRecreateOnJoin = false;
            Listener.getThisPlayerSpawnPoint().broadcast(MinecraftClient.getInstance().player);
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
            Listener.getWorldSwitchPoint().broadcast(this.world);
        }
        Listener.getThisPlayerSpawnPoint().broadcast(MinecraftClient.getInstance().player);
    }

    @Redirect(method = "onPlayerPositionLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(DDD)V"))
    private void onTeleportConfirmVelocityUpdate(PlayerEntity instance, double v, double v2, double v3){
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



    @Shadow public abstract ClientConnection getConnection();


    @Inject(method = "onPlayerPositionLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/packet/Packet;)V", ordinal = 1,shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void onTeleportConfirmResponse(PlayerPositionLookS2CPacket packet, CallbackInfo ci, @Local PlayerEntity playerEntity){
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
    //todo: remove this
    @ModifyArg(method = "onEntityTrackerUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/data/DataTracker;writeUpdatedEntries(Ljava/util/List;)V"))
    private List<DataTracker.SerializedEntry<?>> makeModifiableListForTrackerUpdateEvents(List<DataTracker.SerializedEntry<?>> entries){
        return new LazyList<>(entries);
    }

    @Inject(method = "onPlayerList", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/SocialInteractionsManager;setPlayerOnline(Lnet/minecraft/client/network/PlayerListEntry;)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void onOtherPlayerJoin(PlayerListS2CPacket packet, CallbackInfo ci, @Local PlayerListEntry playerListEntry){
        Listener.getOtherPlayerJoinPoint().broadcast(playerListEntry);
    }


    @Inject(method = "onPlayerRemove", at = @At(value = "INVOKE", target = "Ljava/util/Set;remove(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void onOtherPlayerExit(PlayerRemoveS2CPacket packet, CallbackInfo ci, @Local PlayerListEntry playerListEntry){
        Listener.getOtherPlayerExitPoint().broadcast(playerListEntry);
    }

    @Redirect(method = "onEntityVelocityUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setVelocityClient(DDD)V"))
    private void onEntityVelocityUpdate(Entity instance, double x, double y, double z){
        if(!Listener.getEntityClientVelocityUpdate().isEmpty()){
            Vec3d vec3d = new Vec3d(x, y, z);
            Event<Vec3d> vcUpdate = new Event<>(vec3d  , true, true, instance);
            Listener.getEntityClientVelocityUpdate().handleValue(vcUpdate);
            if(vcUpdate.isCancelled()){
                return;
            }else{
                Vec3d vec3d1 = vcUpdate.context();
                instance.setVelocityClient(vec3d1.getX(), vec3d1.getY(), vec3d1.getZ());
            }
        }else {
            instance.setVelocityClient(x, y, z);
        }
    }

}
