package me.matl114.hacks.utils.chat;

import me.matl114.accessors.access.ChatScreenAccess;
import me.matl114.hacks.ChatTasks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class ChatScreenTextFieldWidget extends TextFieldWidget {
    ChatScreen chatScreen;

    public ChatScreenTextFieldWidget(ChatScreen chatScreen) {
        super(
                MinecraftClient.getInstance().advanceValidatingTextRenderer,
                4,
                chatScreen.height - 12,
                chatScreen.width - 4,
                12,
                Text.translatable("chat.editBox"));
        this.chatScreen = chatScreen;
    }

    protected MutableText getNarrationMessage() {
        return super.getNarrationMessage()
                .append(ChatScreenAccess.of(chatScreen).getSuggestor().getNarration());
    }

    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (ChatTasks.getChatExtra().obfLogin.get()) {
            if (!ChatTasks.getChatExtra().onChatObfRender(this, context, mouseX, mouseY, deltaTicks)) {
                super.renderWidget(context, mouseX, mouseY, deltaTicks);
            }
        } else {
            super.renderWidget(context, mouseX, mouseY, deltaTicks);
        }
    }
}
