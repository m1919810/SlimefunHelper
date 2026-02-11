package me.matl114.mixins.hack;

import java.util.ArrayList;
import java.util.List;
import me.matl114.accessors.access.ChatHudAccess;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.RenderTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ChatHud.class)
public abstract class ChatHudMixin implements ChatHudAccess {
    @Shadow
    @Final
    private List<ChatHudLine.Visible> visibleMessages;

    @Unique
    @Override
    public ArrayList<ChatHudLine.Visible> getVisibleLines() {
        return (ArrayList<ChatHudLine.Visible>) this.visibleMessages;
    }

    // mixin for chatHistoryLength override
    @Inject(
            method = "addVisibleMessage",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Ljava/util/List;remove(I)Ljava/lang/Object;",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private void resizeChatHistoryMaxLength(ChatHudLine message, CallbackInfo ci) {
        if (ChatTasks.getChatExtra().overrideChatHistoryLength.get()) {
            int chat = ChatTasks.getChatExtra().chatHistoryLength.get();
            if (chat > 0) {
                // 提前结束
                if (this.visibleMessages.size() <= chat) {
                    ci.cancel();
                }
            }
        }
    }

    // mixin for chatHud usage

    @Inject(method = "isChatFocused", at = @At("HEAD"), cancellable = true)
    private void onSleepingChatScreenUseChatHud(CallbackInfoReturnable<Boolean> cir) {
        if (RenderTasks.getSleepMode().isScreenSleeping()
                && RenderTasks.getSleepMode().getCurrentRenderingSleeping() instanceof ChatScreen) {
            cir.setReturnValue(true);
        }
    }
}
