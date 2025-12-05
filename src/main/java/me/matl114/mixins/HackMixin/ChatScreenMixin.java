package me.matl114.mixins.HackMixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.matl114.access.ButtonNotFocusedScreenAccess;
import me.matl114.access.ChatScreenAccess;
import me.matl114.hackUtils.ChatTasks;
import me.matl114.hackUtils.Tasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.UtilClass.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen implements ButtonNotFocusedScreenAccess, ChatScreenAccess {
    @Shadow public abstract void sendMessage(String chatText, boolean addToHistory);

    @Shadow protected TextFieldWidget chatField;

    @Shadow private int messageHistoryIndex;

    public TextFieldWidget getInputWidget(){
        return chatField;
    }

    @Unique
    private static Config.StringRef stored=Configs.CHAT_CONFIG.getString(Configs.CHAT_HELPER_CACHE);
    @Unique
    private static Config.StringRef specialChars=Configs.CHAT_CONFIG.getString(Configs.CHAT_HELPER_SPECIALCHARS);
    @Unique
    private TextFieldWidget helperInputField;
    @Unique
    private TextFieldWidget int2CharInputField;

    protected ChatScreenMixin(Text title) {
        super(title);
    }
    @Unique
    private static final Config.FlagRef changeInputLimit = Configs.CHAT_CONFIG.getBoolean(Configs.CHAT_HELPER_IGNORE_INPUT_LIMIT);
    @Unique
    private static final Config.FlagRef escapeTrimChatMessage = Configs.CHAT_CONFIG.getBoolean(Configs.CHAT_HELPER_ESCAPE_TRIM);
    @Inject(method = "init",at = @At("RETURN"))
    private void onInitAdd(CallbackInfo ci) {
        //change input maxLen to 32768, so commands can be executed
        if(changeInputLimit.get()){
            Tasks.scheduleDelayed(()->this.chatField.setMaxLength(32768),1);
        }

        this.helperInputField = new TextFieldWidget(this.textRenderer, this.width - 250, this.height - 56, 140, 20, Text.of(""));
        this.helperInputField.setMaxLength(32768);  // 设置最大输入字符数
        this.helperInputField.setEditable(true);  // 设置为可编辑
        this.helperInputField.setText(stored.getValue());  // 设置默认文本
        addDrawableChild(this.helperInputField);
        var but1=ButtonWidget
                .builder(Text.literal("save and send"), b ->{
                    saveEntryToValues();
                    this.sendMessage(this.helperInputField.getText(), true);
                })
                .dimensions(this.width - 110 ,this.height-56 , 60, 20).build();
        but1.setFocused(false);

        addDrawableChild(but1);

        Runnable toggle= HotKeys.getSimpleToggleManager().getToggle(HotKeys.AUTO_CHAT);

        addDrawableChild(ButtonWidget
                .builder(Text.literal("auto-send"), b ->{
                    saveEntryToValues();
                    toggle.run();
                })
                .dimensions(this.width - 50 ,this.height-56 , 50, 20).build());
        Runnable toggle2=HotKeys.getSimpleToggleManager().getToggle(HotKeys.KEEP_CHATINV);
        addDrawableChild(ButtonWidget
                .builder(Text.literal("keep-chat-inv"), b ->{
                    toggle2.run();
                })
                .dimensions(this.width - 70 ,this.height-80 , 70, 20).build());
        addDrawableChild(ButtonWidget
                .builder(Text.literal("sel-to-unicode"), b ->{
                    if(getFocused() instanceof TextFieldWidget text) {
                        text.setText(ChatUtils.toUnicodedString(text.getText()));
                    }
                })
                .dimensions(this.width - 140 ,this.height-80 , 70, 20).build());
        this.int2CharInputField = new TextFieldWidget(this.textRenderer, this.width - 250, this.height - 80, 50, 20, Text.of(""));
        this.int2CharInputField .setMaxLength(256);  // 设置最大输入字符数
        this.int2CharInputField .setEditable(true);  // 设置为可编辑
        this.int2CharInputField .setText("");  // 设置默认文本
        addDrawableChild(this.int2CharInputField );
        addDrawableChild(ButtonWidget
                .builder(Text.literal("int<->char"), b ->{
                    tranlateInt2char();
                })
                .dimensions(this.width - 200 ,this.height-80 , 50, 20).build());
        String specialChar= specialChars.getValue();
        int len=specialChar.length();
        int x=0,xm=4;
        int y=0;
        for(int i=0;i<len;i++){
            x+=1;
            char c=specialChar.charAt(i);
            String value=String.valueOf(c);
            if(!ChatUtils.isNormalCharacter(c)){
                ++i;
                if(i<len){
                    char d=specialChar.charAt(i);
                    value=new String(new char[]{c,d});
                }
            }
            final String valueOfChar=value;
            addDrawableChild(ButtonWidget.builder(Text.literal(valueOfChar),b->{
                Element el=getFocused();
                if(el instanceof TextFieldWidget text){
                    text.write(valueOfChar);
                }
            }).dimensions(this.width-25*x,this.height-104-y*24,22,20 ).build());
            if(x>=xm){
                x=0;y+=1;
            }
        }
        ChatTasks.initChatScreen(this);

    }
    //关于选择Element这件事
    //在mouseClick中选择

    //防止选中原输出框时候不进行setFocus
    @Redirect(method = "mouseClicked",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;mouseClicked(DDI)Z"))
    private boolean fixMouseClickedOnChatFocusLost(TextFieldWidget instance, double v, double w, int i) {
        boolean returnValue=instance.mouseClicked(v, w, i);
        if(returnValue){
            this.setFocused(instance);
        }
        return returnValue;
    }
    //interface
    @Unique
    public Element getDefaultElement(){
        return this.chatField;
    }
    @Unique
    public boolean doFocusButtonWhenClicked(){
        return false;
    }
    //resize
    @Unique
    private String saveWhenRezie;
    @Inject(method = "resize",at=@At("HEAD"))
    private void onResizeAddHead(CallbackInfo ci) {
        saveEntryToValues();
        this.saveWhenRezie=this.int2CharInputField.getText();
    }
    @Inject(method = "resize",at = @At("RETURN"))
    private void onResizeAddReturn(CallbackInfo ci) {
        this.int2CharInputField.setText(saveWhenRezie);
    }

    //save
    @Unique
    public void saveEntryToValues(){
        Configs.CHAT_CONFIG.setValue(this.helperInputField.getText(), Configs.CHAT_HELPER_CACHE);
    }

//    @Inject(method = "keyPressed",at=@At("HEAD"))
//    private void onCheck(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir){
//        //Debug.info()
//    }
    //keep-inv and save config when send
    @Inject(method="keyPressed",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ChatScreen;sendMessage(Ljava/lang/String;Z)V", shift = At.Shift.AFTER), cancellable = true)
    private void onCancelCloseScreenAfterSend(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir){
        saveEntryToValues();
        if(HotKeys.getSimpleToggleManager().getState(HotKeys.KEEP_CHATINV)){
            //FIX: reset history index so pgup pgdown can work correctly
            messageHistoryIndex = MinecraftClient.getInstance().inGameHud.getChatHud().getMessageHistory().size();
            cir.setReturnValue(true);
        }
    }
    //fix conflict with nochatreport
//    @Redirect(method = "sendMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ChatScreen;normalize(Ljava/lang/String;)Ljava/lang/String;"))
//    private String cancelNormalizeString(ChatScreen instance, String chatText){
//        if(!escapeTrimChatMessage.get()){
//            chatText = chatText.trim();
//        }
//        if(!changeInputLimit.get()){
//            chatText = StringHelper.truncateChat(chatText);
//        }
//        return chatText;
//    }
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void onSendInput(String chatText, boolean addToHistory, CallbackInfo ci, @Local(argsOnly = true)LocalRef<String> chatTextRef){
        Event<String> stringEvent = new Event<>(chatText, true, true);
        Listener.getChatScreenSendInput().handleValue(stringEvent);
        if(stringEvent.isCancelled()){
            ci.cancel();
        }
        chatTextRef.set(stringEvent.context());
    }

    @Redirect(method = "normalize", at = @At(value = "INVOKE", target = "Ljava/lang/String;trim()Ljava/lang/String;"))
    private String cancelTrim(String instance){
        if(!escapeTrimChatMessage.get()){
            return instance.trim();
        }
        return instance;
    }


    @Redirect(method = "normalize", at = @At(value = "INVOKE", target = "Lorg/apache/commons/lang3/StringUtils;normalizeSpace(Ljava/lang/String;)Ljava/lang/String;"))
    private String cancelNormalize(String actualChar){
        return actualChar;
    }

    @Redirect(method = "normalize", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringHelper;truncateChat(Ljava/lang/String;)Ljava/lang/String;"))
    private String cancelTruncate(String text){
        if(!changeInputLimit.get()){
            return StringHelper.truncateChat(text);
        }
        return text;
    }
    //
    public void close(){
        super.close();
        saveEntryToValues();
        //Debug.info("saved");
    }

    @Unique
    private void tranlateInt2char(){
        String value=int2CharInputField.getText();
        if(value.isEmpty())return;
        try{
            int val=Integer.parseInt(value);
            try{
                char ch=(char)val;
                int2CharInputField.setText(String.valueOf(ch));
            }catch (Throwable e){
                int2CharInputField.setText("Error");
            }
        }catch (Throwable e){
            char ch=value.charAt(0);
            int2CharInputField.setText(String.valueOf(((int)ch)));
        }
    }
}
