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
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.ApiStatus;

public class ChatExtra extends BaseModule {
    public ChatExtra() {}

    public static final String[] IGNORE_INPUT_LIMIT = {"chat-helper", "ignore-chat-len-limit"};

    public static final String[] ESCAPE_TRIM = {"chat-helper", "escape-trim-chat"};

    public static final String[] ESCAPE_NORMALIZE_SPACE = {"chat-helper", "escape-normalize-space-chat"};

    public static final String[] CHECK_MESSAGE_LENGTH = {"chat-helper", "check-chat-len"};

    public static final String[] MESSAGE_LENGTH_LIMIT = {"chat-helper", "chat-len-limit"};

    public static final String[] CHECK_COMMAND_LENGTH = {"chat-helper", "check-command-len"};

    public static final String[] COMMAND_LENGTH_LIMIT = {"chat-helper", "command-len-limit"};

    public static final String[] LIMITATION_WARN_FORMAT = {"chat-helper", "limit-warn-format"};

    public static final String[] CHAT_HISTORY_LENGTH_OVERRIDE = {"chat-helper", "override-chat-history-len"};

    public static final String[] CHAT_HISTORY_LENGTH = {"chat-helper", "chat-history-len"};

    public static final String[] ADD_HISTORY_WHE_CLOSE = {"chat-helper", "add-to-history-when-close"};

    public static final String[] DO_NOT_SEND_EMPTY_MESSAGE = {"chat-helper", "dont-send-empty-message"};

    public static final String[] TAB_FIX = {"chat-helper", "enable-tab-fix"};

    public final FlagRef noChathudInputLimit =
            flagBuilder(Configs.CHAT_CONFIG, IGNORE_INPUT_LIMIT).build();

    public final FlagRef escapeChatTrim =
            flagBuilder(Configs.CHAT_CONFIG, ESCAPE_TRIM).build();

    public final FlagRef escapeNormalize =
            flagBuilder(Configs.CHAT_CONFIG, ESCAPE_NORMALIZE_SPACE).build();

    public final FlagRef checkMessageLength =
            flagBuilder(Configs.CHAT_CONFIG, CHECK_MESSAGE_LENGTH).build();

    public final IntRef messageLengthLimit = builder(Configs.CHAT_CONFIG, Integer.class)
            .path(MESSAGE_LENGTH_LIMIT)
            .defaultValue(256)
            .build();

    public final FlagRef checkCommandLength =
            flagBuilder(Configs.CHAT_CONFIG, CHECK_COMMAND_LENGTH).build();

    public final IntRef commandLengthLimit = builder(Configs.CHAT_CONFIG, Integer.class)
            .path(COMMAND_LENGTH_LIMIT)
            .defaultValue(32760)
            .build();

    public final StringRef warnFormat = builder(Configs.CHAT_CONFIG, String.class)
            .path(LIMITATION_WARN_FORMAT)
            .defaultValue("&c你的输入内容太长了! %d / %d")
            .build();

    public final FlagRef overrideChatHistoryLength =
            flagBuilder(Configs.CHAT_CONFIG, CHAT_HISTORY_LENGTH_OVERRIDE).build();

    public final IntRef chatHistoryLength = builder(Configs.CHAT_CONFIG, Integer.class)
            .path(CHAT_HISTORY_LENGTH)
            .defaultValue(100)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    @ApiStatus.Experimental
    public final FlagRef addToHistoryWhenClose =
            flagBuilder(Configs.CHAT_CONFIG, ADD_HISTORY_WHE_CLOSE).build();

    public final FlagRef doNotSendEmptyMessage =
            flagBuilder(Configs.CHAT_CONFIG, DO_NOT_SEND_EMPTY_MESSAGE).build();

    public final FlagRef tabFix = flagBuilder(Configs.CHAT_CONFIG, TAB_FIX).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getChatSend(), this::onChatScreenSendMessage, Integer.MAX_VALUE - 1);
        registerListener(Listener.getPostInitializeScreen(), this::onChatScreenInitialized);
        registerListener(Listener.getPostCloseScreen(), this::onChatScreenClose);
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

    private static final String[] LOGIN_COMMAND_REGEX = {"chat-screen-tools", "login-command-pattern"};

    private static final String[] PASSWORD_ENCRYPT = {"chat-screen-tools", "password-encrypt"};
    private static final String[] PASSWORD_ENCRYPT_LENGTH = {"chat-screen-tools", "password-encrypt-length"};
    private static final String[] PASSWORD_ENCRYPT_SALT = {"chat-screen-tools", "password-encrypt-salt"};
    private Pattern pattern;
    private final StringRef regexLogin = builder(Configs.CHAT_CONFIG, LOGIN_COMMAND_REGEX, StringRef.TYPE)
            .defaultValue("^(/login|/l|/reg|/register|/changepass|/changepassword) (.+)$")
            .validator(Configs.REGEX_VALIDATOR)
            .updateListener(s -> pattern = Pattern.compile(s))
            .build();

    public final FlagRef encryptPass =
            flagBuilder(Configs.CHAT_CONFIG, PASSWORD_ENCRYPT).build();

    public final IntRef encryptLength = builder(Configs.CHAT_CONFIG, PASSWORD_ENCRYPT_LENGTH, IntRef.TYPE)
            .defaultValue(20)
            .build();

    public final StringRef encryptSalt = builder(Configs.CHAT_CONFIG, PASSWORD_ENCRYPT_SALT, StringRef.TYPE)
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

    public void onChatScreenClose(Event<Screen> chatScreenSave) {
        if (chatScreenSave.context() instanceof ChatScreen chat) {
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
        var matcher = pattern.matcher(text);
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
            if (Pattern.matches(regexLogin.get(), commandChat.context())) {
                String command = commandChat.context();
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

    public static final String[] AUTO_PREFIX_SUFFIX = {"chat-helper", "enable-chat-message-format"};

    public static final String[] AUTO_FORMAT = {"chat-helper", "chat-message-format-str"};

    public static final String[] AUTO_FORMAT_ESCAPE = {"chat-helper", "chat-message-escape-format"};

    public final FlagRef enableFormat =
            flagBuilder(Configs.CHAT_CONFIG, AUTO_PREFIX_SUFFIX).build();

    public final StringRef formatStr = builder(Configs.CHAT_CONFIG, AUTO_FORMAT, String.class)
            .defaultValue("%s喵 | SlimefunHelper client | {random6}")
            .build();

    public final StringRef commandEscapeFormatPattern = builder(Configs.CHAT_CONFIG, AUTO_FORMAT_ESCAPE, String.class)
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
