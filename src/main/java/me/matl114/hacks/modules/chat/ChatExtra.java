package me.matl114.hacks.modules.chat;

import com.google.common.hash.Hashing;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.matl114.accessors.access.ChatScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.ApiStatus;

public class ChatExtra extends BaseModule {
    public static ChatExtra INSTANCE;

    public ChatExtra() {
        INSTANCE = this;
    }

    public final ModulePath chat = makePath(Configs.CHAT_CONFIG, "chat-helper");
    public final ModulePath chatTools = makePath(Configs.CHAT_CONFIG, "chat-screen-tools");

    public final FlagRef noChathudInputLimit =
            flagBuilder(chat.add("ignore-chat-len-limit")).build();

    public final FlagRef escapeChatTrim =
            flagBuilder(chat.add("escape-trim-chat")).build();

    public final FlagRef escapeNormalize =
            flagBuilder(chat.add("escape-normalize-space-chat")).build();

    public final FlagRef checkMessageLength =
            flagBuilder(chat.add("check-chat-len")).build();

    public final IntRef messageLengthLimit =
            intBuilder(chat.add("chat-len-limit")).defaultValue(256).build();

    public final FlagRef checkCommandLength =
            flagBuilder(chat.add("check-command-len")).build();

    public final IntRef commandLengthLimit =
            intBuilder(chat.add("command-len-limit")).defaultValue(32760).build();

    public final StringRef warnFormat = builder(chat.add("limit-warn-format"), String.class)
            .defaultValue("&c你的输入内容太长了! %d / %d")
            .build();

    public final FlagRef overrideChatHistoryLength =
            flagBuilder(chat.add("override-chat-history-len")).build();

    public final IntRef chatHistoryLength = intBuilder(chat.add("chat-history-len"))
            .defaultValue(100)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    @ApiStatus.Experimental
    public final FlagRef addToHistoryWhenClose =
            flagBuilder(chat.add("add-to-history-when-close")).build();

    public final FlagRef doNotSendEmptyMessage =
            flagBuilder(chat.add("dont-send-empty-message")).build();

    public final FlagRef tabFix = flagBuilder(chat.add("enable-tab-fix")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getChatSend(), this::onChatScreenSendMessage, Integer.MAX_VALUE - 1);
        registerListener(
                Listener.getPostInitializeScreen().getChannel(ChatScreen.class), this::onChatScreenInitialized);
        registerListener(
                Listener.getPostInitializeScreen().getChannel(HandledScreen.class), this::onChatScreenInitialized);
        registerListener(Listener.getPostCloseScreen().getChannel(ChatScreen.class), this::onChatScreenClose);
        registerListener(Listener.getChatSend(), this::onChatPasswordEncrypt, -999);
        registerListener(Listener.getChatSend(), this::onStringReplace, Integer.MAX_VALUE - 10);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
    }

    public void onChatScreenSendMessage(Event<String> stringEvent) {
        // check command empty
        if (doNotSendEmptyMessage.get()) {
            String value = stringEvent.context();
            // ignore meaningless shit, do not addToMessageHistory
            if (value.isEmpty()
                    || Objects.equals(value, "/")
                    || Objects.equals(value, "!!")
                    || Objects.equals(value, "/!!")) {
                stringEvent.cancel();
            }
        }
        // check command length
        String command = stringEvent.context();
        if (command.startsWith("/")) {
            if (checkCommandLength(command)) {
                stringEvent.cancel();
                return;
            }
        } else if (checkMessageLength(command)) {
            stringEvent.cancel();
            return;
        }
    }

    public String normalizeSendText(String sent) {
        if (!escapeChatTrim.get()) {
            sent = sent.trim();
        }
        if (!escapeNormalize.get()) {
            sent = StringUtils.normalizeSpace(sent);
        }
        if (!noChathudInputLimit.get()) {
            sent = StringHelper.truncateChat(sent);
        }
        return sent;
    }

    private final NBTRef<Regex> regexLogin = builder(chatTools.add("login-command-pattern"), Regex.class)
            .defaultValue(new Regex("^(/login|/l|/reg|/register|/changepass|/changepassword) (.+)$"))
            .build();

    public final FlagRef encryptPass =
            flagBuilder(chatTools.add("password-encrypt")).build();

    public final IntRef encryptLength = intBuilder(chatTools.add("password-encrypt-length"))
            .defaultValue(20)
            .build();

    public final StringRef encryptSalt = builder(chatTools.add("password-encrypt-salt"), String.class)
            .defaultValue("")
            .build();

    // add chat screen extra things
    public void onChatScreenInitialized(Event<Screen> screenEvent) {
        if (noChathudInputLimit.get()) {
            if (screenEvent.context() instanceof ChatScreen chat) {
                ChatScreenAccess access = ChatScreenAccess.of(chat);

                var chatField = access.getInputWidget();
                chatField.setMaxLength(32768);

            } else if (screenEvent.context() instanceof AnvilScreen anvilScreen) {
                if (anvilScreen.getFocused() instanceof TextFieldWidget widget) {
                    widget.setMaxLength(32768);
                }
            }
        }
    }

    public void onChatScreenClose(Event<ChatScreen> chatScreenSave) {
        var chat = chatScreenSave.context();
        if (addToHistoryWhenClose.get()) {
            String chatInput = ChatScreenAccess.of(chat).getInputWidget().getText();
            // ignore two default input
            if (!chatInput.isEmpty() && !Objects.equals("/", chatInput)) {
                if (mc.inGameHud != null) {
                    mc.inGameHud.getChatHud().addToMessageHistory(chatInput);
                }
            }
        }
    }

    private boolean checkCommandLength(String command) {
        if (this.checkCommandLength.get() && command.length() > commandLengthLimit.get()) {
            String val = warnFormat.get();
            if (val != null && !val.isEmpty()) {
                Debug.chat(ChatUtils.stringToText(val.formatted(command.length(), commandLengthLimit.get())));
            }

            return true;
        }
        return false;
    }

    private boolean checkMessageLength(String command) {
        if (this.checkMessageLength.get() && command.length() > messageLengthLimit.get()) {
            String val = warnFormat.get();
            if (val != null && !val.isEmpty()) {
                Debug.chat(ChatUtils.stringToText(val.formatted(command.length(), messageLengthLimit.get())));
            }
            return true;
        }
        return false;
    }

    private TextFieldWidget sampleWidget;

    public boolean onChatObfRender(TextFieldWidget widget, DrawContext context, int x, int y, float partialTicks) {
        String text = widget.getText();
        var matcher = regexLogin.get().pattern().matcher(text);
        if (matcher.find() && matcher.groupCount() > 0) {
            String result = matcher.group(1) + " <password-hidden>";
            if (sampleWidget == null) {
                sampleWidget = new TextFieldWidget(mc.textRenderer, 0, 0, 0, 0, Text.empty());
                {
                    sampleWidget.setDrawsBackground(false);
                    sampleWidget.setFocusUnlocked(false);
                }
            }
            TextFieldWidget newWidget = sampleWidget;
            newWidget.setX(widget.getX());
            newWidget.setY(widget.getY());
            newWidget.setWidth(widget.getWidth());
            newWidget.setHeight(widget.getHeight());
            newWidget.setText(result);
            newWidget.setCursorToEnd(false);
            newWidget.render(context, x, y, partialTicks);
            return true;
        } else {
            return false;
        }
    }

    public void onChatPasswordEncrypt(Event<String> commandChat) {
        if (mc.player != null && encryptPass.get() && !ScreenUtils.hasCtrlDown()) {
            String command = commandChat.context();
            if (regexLogin.get().test(command)) {
                String playerName = mc.player.getNameForScoreboard();
                String[] splits = command.split(" ");
                for (var i = 1; i < splits.length; i++) {
                    if (!splits[i].startsWith("plain:")) {
                        splits[i] = encryptWithPlayerName(splits[i], playerName);
                    } else {
                        splits[i] = splits[i].substring("plain:".length());
                    }
                }
                command = String.join(" ", splits);
                commandChat.context(command);
            }
        }
    }

    private String encryptWithPlayerName(String str, String playerName) {
        String concatStr = playerName + ":" + encryptSalt.get() + str;
        byte[] bytes =
                Hashing.sha256().hashString(concatStr, StandardCharsets.UTF_8).asBytes();
        BigInteger integer = new BigInteger(1, bytes);
        String hashResult = integer.toString(36);
        StringBuilder builder = new StringBuilder();
        int length = encryptLength.get();
        if (hashResult.length() > length) {
            builder.append(hashResult, 0, length);
        } else {
            int size = hashResult.length();
            builder.append(hashResult);
            while (size < length / 2 - 2) {
                size = 2 * size + 1;
                builder.append("@").append(hashResult);
            }
        }
        return builder.toString();
    }

    public final FlagRef enableFormat =
            flagBuilder(chat.add("enable-chat-message-format")).build();

    public final StringRef formatStr = builder(chat.add("chat-message-format-str"), String.class)
            .defaultValue("%s喵 | SlimefunHelper client | {random6}")
            .build();

    public final StringRef commandEscapeFormatPattern = builder(chat.add("chat-message-escape-format"), String.class)
            .defaultValue("^[/#!.](.*)$")
            .build();
    private final Pattern randomPattern = Pattern.compile("\\{random(\\d+)\\}");

    private final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final Random rand = new Random();

    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(rand.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    public String generateFormatString(String string) {
        String template = formatStr.get();
        Matcher m = randomPattern.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int length = Integer.parseInt(m.group(1));
            String randomStr = generateRandomString(length);
            m.appendReplacement(sb, Matcher.quoteReplacement(randomStr));
        }
        m.appendTail(sb);
        String processedTemplate = sb.toString();

        return String.format(processedTemplate, string);
    }

    public void onStringReplace(Event<String> chatEvent) {
        if (chatEvent.isCancelled()) return;
        if (enableFormat.get()) {
            String originString = chatEvent.context();
            // remove our commands
            if (!Pattern.matches(commandEscapeFormatPattern.get(), originString)) {
                chatEvent.context(generateFormatString(originString));
            }
        }
    }
}
