package me.matl114.mixins.events;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.matl114.events.Listener;
import me.matl114.events.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public abstract class ChatScreenEvents {
    //fix conflict with nochatreport
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void onSendInput(String chatText, boolean addToHistory, CallbackInfo ci, @Local(argsOnly = true) LocalRef<String> chatTextRef){
        Event<String> stringEvent = new Event<>(chatText, true, true);
        Listener.getChatScreenSendMessage().handleValue(stringEvent);
        if(stringEvent.isCancelled()){
            ci.cancel();
        }
        if(!Objects.equals(stringEvent.context, chatText)){
            chatTextRef.set(stringEvent.context());
        }
    }
}
