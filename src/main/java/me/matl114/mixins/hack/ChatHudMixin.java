package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import java.util.List;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.modules.chat.ChatExtra;
import me.matl114.hacks.modules.render.SleepMode;
import me.matl114.hacks.modules.survival.XaeroHelper;
import me.matl114.hooks.XaeroHooks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Shadow
    @Final
    private List<ChatHudLine.Visible> visibleMessages;

    @Shadow
    @Final
    private MinecraftClient client;

    // mixin for chatHistoryLength override
    @Inject(
            method = "addVisibleMessage",
            at = @At(value = "INVOKE", target = "Ljava/util/List;remove(I)Ljava/lang/Object;", shift = At.Shift.BEFORE),
            cancellable = true)
    private void resizeChatHistoryMaxLength(ChatHudLine message, CallbackInfo ci) {
        if (ChatExtra.INSTANCE.overrideChatHistoryLength.get()) {
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
        if (SleepMode.INSTANCE.isScreenSleeping()
                && SleepMode.INSTANCE.getCurrentRenderingSleeping() instanceof ChatScreen) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderChatScreen(
        DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci, @Local(argsOnly = true) LocalBooleanRef expanding) {
        if (XaeroHelper.INSTANCE.transparentGuiMapFix.get()
                && XaeroHooks.getInstance().isXaeroWorldMapEnable()
                && XaeroHooks.getInstance().isGuiMap(client.currentScreen)) {
            expanding.set(true);
        }
    }
}
