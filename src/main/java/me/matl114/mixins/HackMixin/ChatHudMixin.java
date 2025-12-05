package me.matl114.mixins.HackMixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.matl114.access.ChatHudAccess;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.utils.UtilClass.Event;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Environment(EnvType.CLIENT)
@Mixin(ChatHud.class)
public abstract class ChatHudMixin implements ChatHudAccess {
    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;

    @Unique
    @Override
    public ArrayList<ChatHudLine.Visible> getVisibleLines(){
        return (ArrayList<ChatHudLine.Visible>) this.visibleMessages;
    }


    private static final Config.IntRef chatHistoryVisbleLen = Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_CHAT_HISTORY_LENGTH);
    @Inject(method = "addVisibleMessage", at = @At(value = "INVOKE", target = "Ljava/util/List;remove(I)Ljava/lang/Object;", shift = At.Shift.BEFORE), cancellable = true)
    private void resizeChatHistoryMaxLength(ChatHudLine message, CallbackInfo ci){
        int chat = chatHistoryVisbleLen.get();
        if (chat >0){
            //提前结束
            if(this.visibleMessages.size() <= chat){
                ci.cancel();
            }
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
    private void onMessageAdd(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci, @Local(argsOnly = true)LocalRef<Text> textLocalRef){
        if(!Listener.getMessageAddToHudPoint().isEmpty()){
            Event<Text> addMessageEvent = new Event<>(message, true, true);
            Listener.getMessageAddToHudPoint().handleValue(addMessageEvent);
            if(addMessageEvent.isCancelled()){
                ci.cancel();
            }
            textLocalRef.set(addMessageEvent.context());
        }

    }
    @Inject(method = "addVisibleMessage", at = @At("HEAD"), cancellable = true)
    private void onVisibleMessageAdd(ChatHudLine message, CallbackInfo ci, @Local(argsOnly = true)LocalRef<ChatHudLine> lineLocalRef){
        if(!Listener.getMessageAddToVisiblePoint().isEmpty()){
            Event<ChatHudLine> addMessageEvent = new Event<>(message, true, true);
            Listener.getMessageAddToVisiblePoint().handleValue(addMessageEvent);
            if(addMessageEvent.isCancelled()){
                ci.cancel();
            }
            lineLocalRef.set(addMessageEvent.context());
        }
    }
}
