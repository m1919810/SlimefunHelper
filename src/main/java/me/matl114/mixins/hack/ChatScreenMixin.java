package me.matl114.mixins.hack;

import me.matl114.accessors.access.ChatScreenAccess;
import me.matl114.accessors.gui.CustomFocusBehaviourScreenAccess;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.utils.chat.ChatScreenTextFieldWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen implements CustomFocusBehaviourScreenAccess, ChatScreenAccess {
    @Shadow
    public abstract void sendMessage(String chatText, boolean addToHistory);

    @Shadow
    protected TextFieldWidget chatField;

    @Shadow
    private int messageHistoryIndex;

    @Unique
    public void resetMessageHistoryIndex() {
        messageHistoryIndex = MinecraftClient.getInstance()
                .inGameHud
                .getChatHud()
                .getMessageHistory()
                .size();
    }

    @Accessor("chatInputSuggestor")
    public abstract ChatInputSuggestor getSuggestor();

    public TextFieldWidget getInputWidget() {
        return chatField;
    }

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    // 关于选择Element这件事
    // 在mouseClick中选择

    // 防止选中原输出框时候不进行setFocus
    // already fixed by ojng

    //    private boolean fixMouseClickedOnChatFocusLost(ChatInputSuggestor instance, Click click, Operation<Boolean>
    // original) {
    //        boolean returnValue = original.call(instance, click);
    //        if(returnValue){
    //            this.setFocused(instance);
    //        }
    //        return returnValue;
    //    }
    // interface
    @Unique
    public Element getDefaultElement() {
        return this.chatField;
    }

    @Unique
    public boolean canFocusButtonWhenClicked() {
        return false;
    }
    // resize

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

    @Inject(
            method = "init",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;setMaxLength(I)V",
                            shift = At.Shift.BEFORE))
    private void modifyTextFieldWidget(CallbackInfo ci) {
        this.chatField = new ChatScreenTextFieldWidget((ChatScreen) (Screen) this);
    }
}
