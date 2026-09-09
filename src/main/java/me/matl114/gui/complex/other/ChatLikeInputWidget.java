package me.matl114.gui.complex.other;

import java.util.function.Consumer;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.MathHelper;

public class ChatLikeInputWidget extends TextFieldWidget {
    Consumer<String> callback;
    int messageHistoryIndex;
    String chatLastMessage = "";

    public ChatLikeInputWidget(
            TextRenderer textRenderer, int x, int y, int width, int height, Consumer<String> enterCallback) {
        super(textRenderer, x, y, width, height, Text.empty());
        this.callback = enterCallback;
        this.setDrawsBackground(false);
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public void setChatFromHistory(int offset) {
        int i = this.messageHistoryIndex + offset;
        int j = mc.inGameHud.getChatHud().getMessageHistory().size();
        i = MathHelper.clamp(i, 0, j);
        if (i != this.messageHistoryIndex) {
            if (i == j) {
                this.messageHistoryIndex = j;
                setText(this.chatLastMessage);
            } else {
                if (this.messageHistoryIndex == j) {
                    // save temp message
                    this.chatLastMessage = getText();
                }

                setText((String) mc.inGameHud.getChatHud().getMessageHistory().get(i));
                // this.chatInputSuggestor.setWindowActive(false);
                this.messageHistoryIndex = i;
            }
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (this.isFocused()) {
            if (keyCode != 257 && keyCode != 335) {
                if (keyCode == 265) {
                    this.setChatFromHistory(-1);
                    return true;
                } else if (keyCode == 264) {
                    this.setChatFromHistory(1);
                    return true;
                } else {
                    return false;
                }
            } else {
                this.onAcceptCallback(getText());
                setText("");
                return true;
            }
        } else {
            if (keyCode == 265) {
                this.setFocused(true);
                return true;
            }
            return false;
        }
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(
                this.getX(),
                this.getY() - 2,
                this.getX() + width,
                this.getY() + height - 2,
                mc.options.getTextBackgroundColor(Integer.MIN_VALUE));
        RenderHandler.drawHighlightFrame(
                VDrawContext.of(context),
                this.getX() - 1,
                this.getY() - 3,
                width + 2,
                height + 2,
                this.isFocused() ? Colors.WHITE : Colors.GRAY);
        super.renderWidget(context, mouseX, mouseY, deltaTicks);
    }

    public void onAcceptCallback(String value) {
        if (callback != null) {
            callback.accept(value);
        }
        // "send" action may trigger
        resetHistoryIndex();
        // avoid focus failure
        //        setFocused(false);
    }

    public void resetHistoryIndex() {
        this.messageHistoryIndex = mc.inGameHud.getChatHud().getMessageHistory().size();
    }
}
