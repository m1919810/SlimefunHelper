package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.matl114.accessors.events.ChatHudAccess;
import me.matl114.accessors.events.ChatHudLineAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ChatHud.class)
public abstract class ChatHudEvents implements ChatHudAccess {
    @Shadow
    @Final
    private List<ChatHudLine.Visible> visibleMessages;

    @Shadow
    @Final
    private List<ChatHudLine> messages;

    @Unique
    public String uniqueId;

    @Unique
    private final Set<String> pendingUniqueMessageIds = new HashSet<>();

    @Unique
    @Override
    public void setUniqueMessageId(String id) {
        this.uniqueId = id;
    }

    @Unique
    @Override
    public void addPendingUniqueMessageId(String id) {
        if (id != null) pendingUniqueMessageIds.add(id);
    }

    @Unique
    @Override
    public void clearPendingUniqueMessageIds() {
        pendingUniqueMessageIds.clear();
    }

    @Unique
    @Override
    public List<ChatHudLine.Visible> getVisibleLines() {
        return this.visibleMessages;
    }

    @Inject(
            method =
                    "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void onMessageAdd(
            Text message,
            MessageSignatureData signatureData,
            MessageIndicator indicator,
            CallbackInfo ci,
            @Local(argsOnly = true) LocalRef<Text> textLocalRef) {
        clearPendingUniqueMessageIds();
        if (!Listener.getMessageAddToHud().isEmpty()) {
            Event<Text> addMessageEvent = new Event<>(message, true, true, signatureData, indicator);
            Listener.getMessageAddToHud().handleValue(addMessageEvent);
            if (addMessageEvent.isCancelled()) {
                clearPendingUniqueMessageIds();
                ci.cancel();
                return;
            }
            textLocalRef.set(addMessageEvent.context());
        }
    }

    @Inject(
            method =
                    "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/hud/ChatHud;logChatMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
                            shift = At.Shift.AFTER))
    private void onChatHudLineCreate(
            Text message,
            MessageSignatureData signatureData,
            MessageIndicator indicator,
            CallbackInfo ci,
            @Local ChatHudLine line) {
        ChatHudLineAccess.of(line).addUniqueMessageId(uniqueId);
        pendingUniqueMessageIds.forEach(ChatHudLineAccess.of(line)::addUniqueMessageId);
        clearPendingUniqueMessageIds();
    }

    @Unique
    @Override
    public void clearUniqueMessages(String id) {
        this.visibleMessages.removeIf(
                s -> ChatHudLineAccess.of(s).getUniqueMessageIds().contains(id));
        this.messages.removeIf(
                s -> ChatHudLineAccess.of(s).getUniqueMessageIds().contains(id));
    }

    @Inject(method = "addVisibleMessage", at = @At("HEAD"), cancellable = true)
    private void onVisibleMessageAdd(
            ChatHudLine message, CallbackInfo ci, @Local(argsOnly = true) LocalRef<ChatHudLine> lineLocalRef) {
        if (!Listener.getMessageAddToVisible().isEmpty()) {
            Event<ChatHudLine> addMessageEvent = new Event<>(message, true, true);
            Listener.getMessageAddToVisible().handleValue(addMessageEvent);
            if (addMessageEvent.isCancelled()) {
                ci.cancel();
                return;
            }
            lineLocalRef.set(addMessageEvent.context());
        }
    }

    @ModifyExpressionValue(
            method = "addVisibleMessage",
            at =
                    @At(
                            value = "NEW",
                            target =
                                    "(ILnet/minecraft/text/OrderedText;Lnet/minecraft/client/gui/hud/MessageIndicator;Z)Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;"))
    private ChatHudLine.Visible onVisibleLineCreate(
            ChatHudLine.Visible original, @Local(argsOnly = true) ChatHudLine line) {
        ChatHudLineAccess.of(line).getUniqueMessageIds().forEach(ChatHudLineAccess.of(original)::addUniqueMessageId);
        return original;
    }
}
