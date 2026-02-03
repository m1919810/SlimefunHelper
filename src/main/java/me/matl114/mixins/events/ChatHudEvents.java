package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.matl114.events.Listener;
import me.matl114.events.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ChatHud.class)
public abstract class ChatHudEvents {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
    private void onMessageAdd(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci, @Local(argsOnly = true) LocalRef<Text> textLocalRef){
        if(!Listener.getMessageAddToHud().isEmpty()){
            Event<Text> addMessageEvent = new Event<>(message, true, true);
            Listener.getMessageAddToHud().handleValue(addMessageEvent);
            if(addMessageEvent.isCancelled()){
                ci.cancel();
            }
            textLocalRef.set(addMessageEvent.context());
        }

    }
    @Inject(method = "addVisibleMessage", at = @At("HEAD"), cancellable = true)
    private void onVisibleMessageAdd(ChatHudLine message, CallbackInfo ci, @Local(argsOnly = true)LocalRef<ChatHudLine> lineLocalRef){
        if(!Listener.getMessageAddToVisible().isEmpty()){
            Event<ChatHudLine> addMessageEvent = new Event<>(message, true, true);
            Listener.getMessageAddToVisible().handleValue(addMessageEvent);
            if(addMessageEvent.isCancelled()){
                ci.cancel();
            }
            lineLocalRef.set(addMessageEvent.context());
        }
    }
}
