package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.ChatRecv;
import me.matl114.versioned.api.VRecord;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(MessageHandler.class)
public abstract class MessageHandlerEvents {
    @Shadow
    private long lastProcessTime;

    @Shadow
    @Final
    private MinecraftClient client;

    @WrapOperation(
            method = "method_45745",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;)V"))
    private void onProfilelessChatReceive(ChatHud instance, Text message, Operation<Void> original) {
        Event<ChatRecv> recv = new Event<>(
                new ChatRecv(message, Optional.empty(), Optional.empty(), false, Optional.empty()), true, false);
        Listener.getChatMessageReceive().handleValue(recv);
        if (recv.isCancelled()) {
            return;
        }
        original.call(instance, recv.context.text());
    }

    @WrapOperation(
            method = "method_45749",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/message/MessageHandler;processChatMessageInternal(Lnet/minecraft/network/message/MessageType$Parameters;Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/text/Text;Lcom/mojang/authlib/GameProfile;ZLjava/time/Instant;)Z"))
    private boolean onProcessInternalChatMessaage(
            MessageHandler instance,
            MessageType.Parameters params,
            SignedMessage message,
            Text decorated,
            GameProfile sender,
            boolean onlyShowSecureChat,
            Instant receptionTimestamp,
            Operation<Boolean> original) {
        Event<ChatRecv> recv = new Event<>(
                new ChatRecv(
                        decorated,
                        Optional.ofNullable(sender),
                        Optional.ofNullable(sender).map(VRecord::getName),
                        false,
                        Optional.ofNullable(params.type())),
                true,
                false);
        Listener.getChatMessageReceive().handleValue(recv);
        if (recv.isCancelled()) {
            this.lastProcessTime = Util.getMeasuringTimeMs();
            return true;
        }
        return original.call(
                instance, params, message, recv.context.text(), sender, onlyShowSecureChat, receptionTimestamp);
    }

    @WrapOperation(
            method = "method_53489",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"))
    private void onUnverifiedMessageReceive(
            ChatHud instance,
            Text message,
            MessageSignatureData signatureData,
            MessageIndicator indicator,
            Operation<Void> original,
            @Local(argsOnly = true) UUID uuid,
            @Local(argsOnly = true) MessageType.Parameters parameters) {
        Event<ChatRecv> recv = new Event<>(
                new ChatRecv(
                        message,
                        Optional.of(new GameProfile(uuid, "")),
                        Optional.empty(),
                        false,
                        Optional.ofNullable(parameters.type())),
                true,
                false);
        Listener.getChatMessageReceive().handleValue(recv);
        if (recv.isCancelled()) {
            return;
        }
        original.call(instance, recv.context.text(), signatureData, indicator);
    }
}
