package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerEvents {
    @Inject(method = "onOpenScreen", at = @At("RETURN"))
    private void onPostInventoryOpen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> screen) {
            Listener.getPostOpenHandledScreen().broadcast(screen);
        }
    }

    @Unique
    boolean escapeSendEvent = false;

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void onChat0(String command, CallbackInfo ci, @Local(argsOnly = true) LocalRef<String> commandRef) {
        if (escapeSendEvent) {
            escapeSendEvent = false;
            return;
        }
        String chatContent = "/" + command;
        Event<String> value = new Event<>(chatContent, true, true);
        Listener.getChatSend().handleValue(value);
        if (value.isCancelled()) {
            ci.cancel();
            return;
        }
        String valueChange = value.context();
        if (valueChange == null || valueChange.isEmpty()) {
            ci.cancel();
            return;
        }
        if (!Objects.equals(chatContent, valueChange)) {
            if (valueChange.startsWith("/")) {
                commandRef.set(valueChange.substring(1));
            } else {
                // change a command to a chat message
                ci.cancel();
                escapeSendEvent = true;
                sendChatMessage(valueChange);
            }
        }
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChat2(String content, CallbackInfo ci, @Local(argsOnly = true) LocalRef<String> contentRef) {
        if (escapeSendEvent) {
            escapeSendEvent = false;
            return;
        }
        Event<String> value = new Event<>(content, true, true);
        Listener.getChatSend().handleValue(value);
        if (value.isCancelled()) {
            // set null string to trigger ret
            ci.cancel();
            return;
        }
        if (value.context() == null) {
            ci.cancel();
            return;
        }
        String valueChange = value.context();
        if (!Objects.equals(content, valueChange)) {
            if (!valueChange.startsWith("/")) {
                contentRef.set(valueChange);
            } else {
                ci.cancel();
                escapeSendEvent = true;
                sendChatCommand(valueChange.substring(1));
            }
        }
    }

    @Shadow
    private ClientWorld world;

    @Unique
    private boolean playerRecreateOnJoin = false;

    @Inject(
            method = "onGameJoin",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerInteractionManager;createPlayer(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/stat/StatHandler;Lnet/minecraft/client/recipebook/ClientRecipeBook;)Lnet/minecraft/client/network/ClientPlayerEntity;",
                            shift = At.Shift.AFTER))
    private void onGameJoinCreatePlayer0(GameJoinS2CPacket packet, CallbackInfo ci) {
        playerRecreateOnJoin = true;
    }

    @Inject(method = "onGameJoin", at = @At("RETURN"))
    private void onGameJoinEntryPoint(GameJoinS2CPacket packet, CallbackInfo ci) {
        Listener.getGameJoinPoint().broadcast(MinecraftClient.getInstance().player);
        Listener.getWorldSwitchPoint().broadcast(this.world);
        if (playerRecreateOnJoin) {
            playerRecreateOnJoin = false;
            Listener.getThisPlayerSpawnPoint().broadcast(MinecraftClient.getInstance().player);
        }
    }

    @Unique
    private boolean worldChangeOnRespawn = false;

    @Inject(
            method = "onPlayerRespawn",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/world/ClientWorld;<init>(Lnet/minecraft/client/network/ClientPlayNetworkHandler;Lnet/minecraft/client/world/ClientWorld$Properties;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/registry/entry/RegistryEntry;IILnet/minecraft/client/render/WorldRenderer;ZJI)V",
                            shift = At.Shift.AFTER))
    private void onPlayerSwitchDimension0(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        worldChangeOnRespawn = true;
    }

    @Inject(method = "onPlayerRespawn", at = @At("RETURN"))
    private void onPlayerSwitchDimension(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        if (worldChangeOnRespawn) {
            worldChangeOnRespawn = false;
            Listener.getWorldSwitchPoint().broadcast(this.world);
        }
        Listener.getThisPlayerSpawnPoint().broadcast(MinecraftClient.getInstance().player);
    }

    @Shadow
    public abstract ClientConnection getConnection();

    @Shadow
    public abstract void sendChatMessage(String content);

    @Shadow
    public abstract void sendChatCommand(String command);

    @Inject(
            method = "onPlayerPositionLook",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/packet/Packet;)V",
                            ordinal = 1,
                            shift = At.Shift.BEFORE),
            locals = LocalCapture.CAPTURE_FAILHARD,
            cancellable = true)
    private void onTeleportConfirmResponse(
            PlayerPositionLookS2CPacket packet, CallbackInfo ci, @Local PlayerEntity playerEntity) {
        // turn this into Event

        {
            MovTasks.MovInfo eventContext = new MovTasks.MovInfo(
                    playerEntity.getPos(), false, false, new Vec2f(playerEntity.getPitch(), playerEntity.getYaw()));
            Event<MovTasks.MovInfo> setBackEvent = new Event<>(eventContext, false, true);
            Listener.getTeleportConfirmResponsePoint().handleValue(setBackEvent);
            eventContext = setBackEvent.context();
            Vec2f override = eventContext.rotationOverride();
            float pitch = override == null ? playerEntity.getPitch() : override.x;
            float yaw = override == null ? playerEntity.getYaw() : override.y;
            boolean onGround =
                    eventContext.oGroundOverride() == null ? playerEntity.isOnGround() : eventContext.oGroundOverride();
            // send and cancel
            this.getConnection()
                    .send(PlayerMoveC2SPacketAccess.setCause(
                            new PlayerMoveC2SPacket.Full(
                                    eventContext.vec3d().x,
                                    eventContext.vec3d().y,
                                    eventContext.vec3d().z,
                                    yaw,
                                    pitch,
                                    onGround,
                                    false),
                            PlayerMoveC2SPacketAccess.Cause.SET_BACK));

            ci.cancel();
        }
    }

    @Inject(
            method = "onPlayerList",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/SocialInteractionsManager;setPlayerOnline(Lnet/minecraft/client/network/PlayerListEntry;)V",
                            shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void onOtherPlayerJoin(
            PlayerListS2CPacket packet, CallbackInfo ci, @Local PlayerListEntry playerListEntry) {
        Listener.getOtherPlayerJoinPoint().broadcast(playerListEntry);
    }

    @Inject(
            method = "onPlayerRemove",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;remove(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void onOtherPlayerExit(
            PlayerRemoveS2CPacket packet, CallbackInfo ci, @Local PlayerListEntry playerListEntry) {
        Listener.getOtherPlayerExitPoint().broadcast(playerListEntry);
    }

    @Inject(method = "handlePlayerListAction", at = @At("RETURN"))
    private void onPlayerListUpdate(
            PlayerListS2CPacket.Action action,
            PlayerListS2CPacket.Entry receivedEntry,
            PlayerListEntry currentEntry,
            CallbackInfo ci) {
        Listener.getOtherPlayerEntryUpdate().broadcast(currentEntry, action);
    }

    @WrapOperation(
            method = "onEntityVelocityUpdate",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/Entity;setVelocityClient(Lnet/minecraft/util/math/Vec3d;)V"))
    private void onEntityVelocityUpdate(Entity instance, Vec3d clientVelocity, Operation<Void> original) {
        if (!Listener.getEntityClientVelocityUpdate().isEmpty()) {
            Event<Vec3d> vcUpdate = new Event<>(clientVelocity, true, true, instance);
            Listener.getEntityClientVelocityUpdate().handleValue(vcUpdate);
            if (vcUpdate.isCancelled()) {
                return;
            } else {
                Vec3d vec3d1 = vcUpdate.context();
                original.call(instance, vec3d1);
            }
        } else {
            original.call(instance, clientVelocity);
        }
    }

    @WrapOperation(
            method = "onExplosion",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
    private void onExplosionVelocityUpdate(
            Optional instance, Consumer<? super Vec3d> action, Operation<Void> original) {
        if (instance.isPresent()) {
            Vec3d vec3d = (Vec3d) instance.get();
            Event<Vec3d> updateDeltaEvent = new Event<>(vec3d, true, true);
            Listener.getPlayerExplosionVelocity().handleValue(updateDeltaEvent);
            if (!updateDeltaEvent.isCancelled()) {
                original.call(
                        (Optional)
                                (vec3d == updateDeltaEvent.context()
                                        ? instance
                                        : Optional.ofNullable(updateDeltaEvent.context())),
                        action);
            }
        }
    }

    @Inject(
            method = "onEntitySpawn",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/world/ClientWorld;addEntity(Lnet/minecraft/entity/Entity;)V",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private void onEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci, @Local Entity playerEntity) {
        if (!Listener.getServerEntitySpawnListener().isEmpty()) {
            Event<Entity> entityAdd = new Event<>(playerEntity, true, false);
            Listener.getServerEntitySpawnListener().handleValue(entityAdd);
            if (entityAdd.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @WrapOperation(
            method = "onBundle",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V"))
    private void wrapBundledPacket(Packet instance, PacketListener t, Operation<Void> original) {
        // do not handle serverbound packet
        if (t.getSide() == NetworkSide.SERVERBOUND) {
            original.call(instance, t);
            return;
        }
        Listener.callPacketHandleEvent(instance, t, original::call);
    }

    @ModifyExpressionValue(
            method = "startTagReload",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/registry/tag/TagPacketSerializer$Serialized;toRegistryTags(Lnet/minecraft/registry/Registry;)Lnet/minecraft/registry/tag/TagGroupLoader$RegistryTags;"))
    private static <T> TagGroupLoader.RegistryTags<T> onRegistryTagReload(
            TagGroupLoader.RegistryTags<T> original,
            @Local(argsOnly = true) RegistryKey<? extends Registry<? extends T>> registryKey) {
        Map<TagKey<T>, List<RegistryEntry<T>>> tagMap = original.tags();
        Event<Map<TagKey<T>, List<RegistryEntry<T>>>> event = new Event<>(tagMap, false, true, original.key());
        Listener.getRegistryTagKeyReload().handleValue((Event) event);
        if (event.context != tagMap) {
            return new TagGroupLoader.RegistryTags<>(original.key(), event.context);
        }
        return original;
    }
}
