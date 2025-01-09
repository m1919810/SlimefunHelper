package me.matl114.SlimefunMixin.HackMixin;

import me.matl114.Access.ButtonNotFocusedScreenAccess;
import me.matl114.ManageUtils.Config;
import me.matl114.ManageUtils.Configs;
import me.matl114.ManageUtils.HotKeys;
import me.matl114.SlimefunUtils.Debug;
import me.matl114.Utils.ChatUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.bukkit.inventory.meta.SkullMeta;
import org.checkerframework.checker.units.qual.A;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen implements ButtonNotFocusedScreenAccess {
    @Shadow public abstract boolean sendMessage(String chatText, boolean addToHistory);

    @Shadow protected TextFieldWidget chatField;
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

    @Inject(method = "init",at = @At("RETURN"))
    private void onInitAdd(CallbackInfo ci) {

        this.helperInputField = new TextFieldWidget(this.textRenderer, this.width - 250, this.height - 56, 140, 20, Text.of(""));
        this.helperInputField.setMaxLength(1024);  // 设置最大输入字符数
        this.helperInputField.setEditable(true);  // 设置为可编辑
        this.helperInputField.setText(stored.get());  // 设置默认文本
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
        String specialChar= specialChars.get();
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
    //todo disable pageup and pagedown switch drawables
    @Inject(method = "keyPressed",at=@At("HEAD"))
    private void onCheck(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir){
        //Debug.info()
    }
    //keep-inv and save config when send
    @Redirect(method="keyPressed",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ChatScreen;sendMessage(Ljava/lang/String;Z)Z"))
    private boolean onCancelCloseScreenAfterSend(ChatScreen instance, String chatText, boolean addToHistory){
        boolean val=instance.sendMessage(chatText, addToHistory);
        if(val&&HotKeys.getSimpleToggleManager().getState(HotKeys.KEEP_CHATINV)){
            val=false;
        }
        if(val){
            saveEntryToValues();
        }
        return val;

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
