package me.matl114.hacks.modules.chat;

import me.matl114.accessors.access.ChatScreenAccess;
import me.matl114.events.Listener;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.TaskManagers;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.events.Event;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;

public class ChatExtra extends BaseModule {
    public ChatExtra() {

    }
    //todo: move all static Chat config to here
    public static final String[] CACHE={"chat-helper","cached"};
    
    public static final String[] ENABLE_CHATSCREEN_TOOLS = {"chat-helper", "enable-chatscreen-tools"};

    public static final String[] SPECIALCHARS={"chat-helper","special-chars"};

    public static final String[] IGNORE_INPUT_LIMIT = {"chat-helper","ignore-chat-len-limit"};

    public static final String[] ESCAPE_TRIM = {"chat-helper","escape-trim-chat"};

    public static final String[] ESCAPE_NORMALIZE_SPACE = {"chat-helper", "escape-normalize-space-chat"};

    public static final String[] CHECK_MESSAGE_LENGTH = {"chat-helper","check-chat-len"};

    public static final String[] MESSAGE_LENGTH_LIMIT = {"chat-helper","chat-len-limit"};

    public static final String[] CHECK_COMMAND_LENGTH = {"chat-helper","check-command-len"};

    public static final String[] COMMAND_LENGTH_LIMIT = {"chat-helper","command-len-limit"};

    public static final String[] LIMITATION_WARN_FORMAT = {"chat-helper", "limit-warn-format"};

    public static final String[] CHAT_HISTORY_LENGTH_OVERRIDE = {"chat-helper", "override-chat-history-len"};

    public static final String[] CHAT_HISTORY_LENGTH = {"chat-helper", "chat-history-len"};

    public static final String[] ADD_HISTORY_WHE_CLOSE = {"chat-helper", "add-to-history-when-close"};

    public static final String[] DO_NOT_SEND_EMPTY_MESSAGE = {"chat-helper", "dont-send-empty-message"};


    public final StringRef chatCache = builder(Configs.CHAT_CONFIG, String.class)
        .path(CACHE)
        .defaultValue("")
        .build();

    public final FlagRef enableChatScreenTools = flagBuilder(Configs.CHAT_CONFIG, ENABLE_CHATSCREEN_TOOLS)
        .build();

    public final StringRef specialChars = builder(Configs.CHAT_CONFIG, String.class)
        .path(SPECIALCHARS)
        .defaultValue("\uD83D\uDE21\uD83E\uDD13\uD83E\uDD75\uD83D\uDE2D\uD83E\uDD21\uD83D\uDE0B\uD83E\uDD24\uD83D\uDE0A\uD83D\uDE04\uD83E\uDD72\uD83D\uDE01\uD83D\uDC49\uD83D\uDC46\uD83E\uDD14\uD83D\uDE0E\uD83D\uDC0D\uD83D\uDE05♂♀")
        .build();
    
    public final FlagRef noChathudInputLimit = flagBuilder(Configs.CHAT_CONFIG, IGNORE_INPUT_LIMIT)
        .build();
    
    public final FlagRef escapeChatTrim = flagBuilder(Configs.CHAT_CONFIG, ESCAPE_TRIM)
        .build();

    public final FlagRef escapeNormalize = flagBuilder(Configs.CHAT_CONFIG, ESCAPE_NORMALIZE_SPACE)
        .build();

    public final FlagRef checkMessageLength = flagBuilder(Configs.CHAT_CONFIG, CHECK_MESSAGE_LENGTH)
        .build();

    public final IntRef messageLengthLimit = builder(Configs.CHAT_CONFIG, Integer.class)
        .path(MESSAGE_LENGTH_LIMIT)
        .defaultValue(256)
        .build();


    public final FlagRef checkCommandLength = flagBuilder(Configs.CHAT_CONFIG, CHECK_COMMAND_LENGTH)
        .build();

    public final IntRef commandLengthLimit = builder(Configs.CHAT_CONFIG, Integer.class)
        .path(COMMAND_LENGTH_LIMIT)
        .defaultValue(32760)
        .build();

    public final StringRef warnFormat = builder(Configs.CHAT_CONFIG, String.class)
        .path(LIMITATION_WARN_FORMAT)
        .defaultValue("&c你的输入内容太长了! %d / %d")
        .build();

    public final FlagRef overrideChatHistoryLength = flagBuilder(Configs.CHAT_CONFIG, CHAT_HISTORY_LENGTH_OVERRIDE)
        .build();

    public final IntRef chatHistoryLength = builder(Configs.CHAT_CONFIG, Integer.class)
        .path(CHAT_HISTORY_LENGTH)
        .defaultValue(100)
        .validator(Configs.intHigher(100))
        .build();

    @ApiStatus.Experimental
    public final FlagRef addToHistoryWhenClose = flagBuilder(Configs.CHAT_CONFIG, ADD_HISTORY_WHE_CLOSE)
        .build();
    
    public final FlagRef doNotSendEmptyMessage = flagBuilder(Configs.CHAT_CONFIG, DO_NOT_SEND_EMPTY_MESSAGE)
        .build();


    public void sendCachedMessage(){
        String val = chatCache.get();
        if(val != null){
            ChatTasks.sayMessage(val, false);
        }
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getChatSend(), this::onChatScreenSendMessage, Integer.MAX_VALUE - 1);
        registerListener(Listener.getPostInitializeScreen(), this::onChatScreenInitialized);
        registerListener(Listener.getPostCloseScreen(), this::onChatScreenClose);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
    }

    public void onChatScreenSendMessage(Event<String> stringEvent){
        // check command empty
        if(doNotSendEmptyMessage.get()){
            String value = stringEvent.context();
            //ignore meaningless shit, do not addToMessageHistory
            if(value.isEmpty() || Objects.equals(value, "/")
                || Objects.equals(value, "!!") || Objects.equals(value, "/!!")
            ){
                stringEvent.cancel();
            }
        }
        // check command length
        String command = stringEvent.context();
        if(command.startsWith("/")){
            if(checkCommandLength(command)){
                stringEvent.cancel();
                return;
            }
        }else if(checkMessageLength(command)){
            stringEvent.cancel();
            return;
        }

    }

    public String normalizeSendText(String sent){
        if(!escapeChatTrim.get()){
            sent = sent.trim();
        }
        if(!escapeNormalize.get()){
            sent = StringUtils.normalizeSpace(sent);
        }
        if(!noChathudInputLimit.get()){
            sent = StringHelper.truncateChat(sent);
        }
        return sent;
    }



    @Unique
    private void tranlateInt2char(TextFieldWidget int2CharInputField){
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
    private static final String[] KEEP_CHAT_INV = {"simple-toggle", "keep-chat-inv"};
    public final FlagRef keepChatInv = toggle(KEEP_CHAT_INV)
        .build();
    private String int2CharFieldContent;
    //add chat screen extra things
    public void onChatScreenInitialized(Event<Screen> screenEvent){
        if(screenEvent.context() instanceof ChatScreen chat){
            ChatScreenAccess access = ChatScreenAccess.of(chat);
            if(noChathudInputLimit.get()){
                var chatField = access.getInputWidget();
                chatField.setMaxLength(32768);
            }

            if(enableChatScreenTools.get()){
                TextFieldWidget helperInputField;
                TextFieldWidget int2CharInputField;

                helperInputField = new TextFieldWidget(mc.textRenderer, chat.width - 250, chat.height - 56, 140, 20, Text.of(""));
                helperInputField.setMaxLength(32768);  // 设置最大输入字符数
                helperInputField.setEditable(true);  // 设置为可编辑
                helperInputField.setText(chatCache.get());  // 设置默认文本
                helperInputField.setChangedListener(chatCache::set);
                access.addDrawableChildTo(helperInputField);
                //todo： add translatable to buttons and everything
                var but1= ButtonWidget
                    .builder(Text.literal("save and send"), b ->{
                        ChatTasks.sayMessage(helperInputField.getText(), true);
                    })
                    .dimensions(chat.width - 110 ,chat.height-56 , 60, 20).build();
                but1.setFocused(false);

                access.addDrawableChildTo(but1);

                Runnable toggle= TaskManagers.getToggleTask(AutoChat.AUTO_SEND);

                access.addDrawableChildTo(ButtonWidget
                    .builder(Text.literal("auto-send"), b ->{
                        chatCache.set(helperInputField.getText());
                        toggle.run();
                    })
                    .dimensions(chat.width - 50 ,chat.height-56 , 50, 20).build());
                Runnable toggle2= TaskManagers.getToggleTask(KEEP_CHAT_INV);
                access.addDrawableChildTo(ButtonWidget
                    .builder(Text.literal("keep-chat-inv"), b ->{
                        toggle2.run();
                    })
                    .dimensions(chat.width - 70 ,chat.height-80 , 70, 20).build());
                access.addDrawableChildTo(ButtonWidget
                    .builder(Text.literal("sel-to-unicode"), b ->{
                        if(chat.getFocused() instanceof TextFieldWidget text) {
                            text.setText(ChatUtils.toUnicodedString(text.getText()));
                        }
                    })
                    .dimensions(chat.width - 140 ,chat.height-80 , 70, 20).build());
                int2CharInputField = new TextFieldWidget(mc.textRenderer, chat.width - 250, chat.height - 80, 50, 20, Text.of(""));
                int2CharInputField .setMaxLength(256);  // 设置最大输入字符数
                int2CharInputField .setEditable(true);  // 设置为可编辑
                int2CharInputField .setText(int2CharFieldContent);  // 设置默认文本
                int2CharInputField.setChangedListener(s -> int2CharFieldContent = s);
                access.addDrawableChildTo(int2CharInputField);
                access.addDrawableChildTo(ButtonWidget
                    .builder(Text.literal("int<->char"), b ->{
                        tranlateInt2char(int2CharInputField);
                    })
                    .dimensions(chat.width - 200 ,chat.height-80 , 50, 20).build());
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
                    access.addDrawableChildTo(ButtonWidget.builder(Text.literal(valueOfChar),b->{
                        Element el = chat.getFocused();
                        if(el instanceof TextFieldWidget text){
                            text.write(valueOfChar);
                        }
                    }).dimensions(chat.width-25*x,chat.height-104-y*24,22,20 ).build());
                    if(x>=xm){
                        x=0;y+=1;
                    }
                }
            }

        }
    }


    public void onChatScreenClose(Event<Screen> chatScreenSave){
        if(chatScreenSave.context() instanceof ChatScreen chat){
            if(addToHistoryWhenClose.get()){
                String chatInput = ChatScreenAccess.of(chat).getInputWidget().getText();
                //ignore two default input
                if(!chatInput.isEmpty() && !Objects.equals("/", chatInput)){
                    if(mc.inGameHud != null){
                        mc.inGameHud.getChatHud().addToMessageHistory(chatInput);
                    }
                }
            }

        }
    }

    private boolean checkCommandLength(String command){
        if(this.checkCommandLength.get() && command.length() > commandLengthLimit.get()){
            String val = warnFormat.get();
            if(val != null && !val.isEmpty()){
                Debug.chat(ChatUtils.stringToText(val.formatted(command.length(), commandLengthLimit.get())));
            }


            return true;
        }
        return false;
    }
    private boolean checkMessageLength(String command){
        if(this.checkMessageLength.get() && command.length() > messageLengthLimit.get()){
            String val = warnFormat.get();
            if(val != null && !val.isEmpty()){
                Debug.chat(ChatUtils.stringToText(val.formatted(command.length(), messageLengthLimit.get())));
            }
            return true;
        }
        return false;
    }




}
