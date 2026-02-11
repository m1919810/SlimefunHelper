package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.accessors.access.ChatScreenAccess;
import me.matl114.accessors.gui.ButtonNotFocusedScreenAccess;
import me.matl114.hacks.ChatTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen implements ButtonNotFocusedScreenAccess, ChatScreenAccess {
    @Shadow
    public abstract void sendMessage(String chatText, boolean addToHistory);

    @Shadow
    protected TextFieldWidget chatField;

    @Shadow
    private int messageHistoryIndex;

    public TextFieldWidget getInputWidget() {
        return chatField;
    }

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    // 关于选择Element这件事
    // 在mouseClick中选择

    // 防止选中原输出框时候不进行setFocus
    @WrapOperation(
            method = "mouseClicked",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;mouseClicked(DDI)Z"))
    private boolean fixMouseClickedOnChatFocusLost(
            TextFieldWidget instance, double v, double v2, int i, Operation<Boolean> original) {
        boolean returnValue = original.call(instance, v, v2, i);
        if (returnValue) {
            this.setFocused(instance);
        }
        return returnValue;
    }
    // interface
    @Unique
    public Element getDefaultElement() {
        return this.chatField;
    }

    @Unique
    public boolean doFocusButtonWhenClicked() {
        return false;
    }
    // resize

    // keep-inv and save config when send
    @Inject(
            method = "keyPressed",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/screen/ChatScreen;sendMessage(Ljava/lang/String;Z)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void onCancelCloseScreenAfterSend(
            int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (ChatTasks.getChatExtra().keepChatInv.get()) {
            // FIX: reset history index so pgup pgdown can work correctly
            messageHistoryIndex = MinecraftClient.getInstance()
                    .inGameHud
                    .getChatHud()
                    .getMessageHistory()
                    .size();
            cir.setReturnValue(true);
        }
    }

    // warn: do not cancel normalize, conflict with other mods
    @Redirect(method = "normalize", at = @At(value = "INVOKE", target = "Ljava/lang/String;trim()Ljava/lang/String;"))
    private String cancelTrim(String instance) {
        if (!ChatTasks.getChatExtra().escapeChatTrim.get()) {
            return instance.trim();
        }
        return instance;
    }

    @Redirect(
            method = "normalize",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/apache/commons/lang3/StringUtils;normalizeSpace(Ljava/lang/String;)Ljava/lang/String;"))
    private String cancelNormalize(String actualChar) {
        if (!ChatTasks.getChatExtra().escapeNormalize.get()) {
            return StringUtils.normalizeSpace(actualChar);
        }
        return actualChar;
    }

    @Redirect(
            method = "normalize",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/util/StringHelper;truncateChat(Ljava/lang/String;)Ljava/lang/String;"))
    private String cancelTruncate(String text) {
        if (!ChatTasks.getChatExtra().noChathudInputLimit.get()) {
            return StringHelper.truncateChat(text);
        }
        return text;
    }
}
