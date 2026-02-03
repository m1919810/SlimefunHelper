package me.matl114.gui.other;

import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.config.PropertyTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.Colors;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;

public class ChatLikeInputSubScreen extends SubScreenWidget {
    private Consumer<String> callback;
    public ChatLikeInputSubScreen(int x, int y, int dx, int dy, Consumer<String> callback) {
        super(x, y, dx, dy);
        this.callback = callback;
        this.init();
    }
     MinecraftClient mc = MinecraftClient.getInstance();
    int messageHistoryIndex;
    String chatLastMessage = "";
    ContentDelegateWidget<TextFieldWidget> chatFieldWidget;
    ChatInputSuggestor suggestor ;
    ContentDelegateWidget<DrawableWidget> delegateInputSuggestor;

    protected void init(){
        resetHistoryIndex();
        chatFieldWidget = McWidgetHelpers.createTextFieldEditBox(0,0,this.dx, this.dy, PropertyTracker.event(this::onChatInputUpdate), "");
        chatFieldWidget.getDelegate().setDrawsBackground(false);
        chatFieldWidget.addToSub(this);

       // suggestor = new ChatInputSuggestor()
        //currently not decided
    }
    protected void onChatInputUpdate(String value){

    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(super.keyPressed(keyCode, scanCode, modifiers)){
            return true;
        }else if(this.isFocused()){
            if (keyCode != 257 && keyCode != 335) {
                if (keyCode == 265) {
                    this.setChatFromHistory(-1);
                    return true;
                } else if (keyCode == 264) {
                    this.setChatFromHistory(1);
                    return true;
                }
                //remove scroll chat function
//           else if (keyCode == 266) {
//                MinecraftClient.getInstance().inGameHud.getChatHud().scroll(MinecraftClient.getInstance().inGameHud.getChatHud().getVisibleLineCount() - 1);
//                 return true;
//            } else if (keyCode == 267) {
//                MinecraftClient.getInstance().inGameHud.getChatHud().scroll(-MinecraftClient.getInstance().inGameHud.getChatHud().getVisibleLineCount() + 1);
//                return true;
//            }
                else {
                    return false;
                }
            } else {
                this.onAcceptCallback(this.chatFieldWidget.getDelegate().getText());
                this.chatFieldWidget.getDelegate().setText("");
                return true;
            }
        }else{
            if(keyCode == 265){
                this.setFocused(true);
                return true;
            }
            return false;
        }

    }

    @Override
    public void renderInDefaultMatrix(DrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        super.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        //draw gray background for chatField
        context.fill(0,-2,  this.chatFieldWidget.getWidth(),  this.chatFieldWidget.getHeight() -2, mc.options.getTextBackgroundColor(Integer.MIN_VALUE));
        RenderUtils.drawHighlightFrame(context, -1, -3, this.chatFieldWidget.getWidth() + 2, this.chatFieldWidget.getHeight() + 2, this.isFocused() ? Colors.WHITE : Colors.GRAY);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (
           false// this.chatInputSuggestor.mouseClicked((double)((int)mouseX), (double)((int)mouseY), button)
        ) {
            return true;
        } else {
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    public void onAcceptCallback(String value){
        if(callback != null){
            callback.accept(value);
        }
        //"send" action may trigger
        resetHistoryIndex();
        //avoid focus failure
//        setFocused(false);
    }
    public void resetHistoryIndex(){
        this.messageHistoryIndex = mc.inGameHud.getChatHud().getMessageHistory().size();
    }
    public void setChatFromHistory(int offset) {
        int i = this.messageHistoryIndex + offset;
        int j = mc.inGameHud.getChatHud().getMessageHistory().size();
        i = MathHelper.clamp(i, 0, j);
        if (i != this.messageHistoryIndex) {
            if (i == j) {
                this.messageHistoryIndex = j;
                this.chatFieldWidget.getDelegate().setText(this.chatLastMessage);
            } else {
                if (this.messageHistoryIndex == j) {
                    //save temp message
                    this.chatLastMessage = this.chatFieldWidget.getDelegate().getText();
                }

                this.chatFieldWidget.getDelegate().setText((String)mc.inGameHud.getChatHud().getMessageHistory().get(i));
               // this.chatInputSuggestor.setWindowActive(false);
                this.messageHistoryIndex = i;
            }
        }
    }
}
