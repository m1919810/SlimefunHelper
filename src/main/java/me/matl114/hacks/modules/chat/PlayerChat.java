package me.matl114.hacks.modules.chat;

import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static me.matl114.utils.EncryptUtils.*;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import it.unimi.dsi.fastutil.chars.CharSet;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.With;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.PairLikeFactory;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.ObjectTextContent;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.object.PlayerTextObjectContents;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

public class PlayerChat extends BaseModule {
    public static final String[] GAME_MESSAGE_PATTERN_AS_PLAYER_MESSAGE = new String[] {
        "player-chat", "game-message-as-player-message",
    };
    public static final String[] DETECT_PLAYER_NAMES_IN_MESSAGE =
            new String[] {"player-chat", "detect-all-message-with-player-names"};
    public static final String[] APPEND_CHAT_HEAD = new String[] {"player-chat", "append-chat-head"};
    public static final String[] APPEND_TIME_STAMP = new String[] {"player-chat", "append-time-stamp"};

    public static final String[] ENCRYPT_MESSAGE_OUT = new String[] {"player-chat", "encrypt-message-out"};
    public static final String[] DECRYPT_MESSAGE_IN = new String[] {"player-chat", "decrypt-message-in"};
    public static final String[] ENCRYPT_ALGORITHM = new String[] {"player-chat", "encrypt-algorithm"};
    public static final String[] ENCRYPT_PASS_PHRASE = new String[] {"player-chat", "encrypt-key-pass-phrase"};
    public static final String[] ENCRYPT_KEY = new String[] {"player-chat", "encrypt-key"};
    public static final String[] ENCRYPT_COMMAND_MESSAGE =
            new String[] {"player-chat", "encrypt-command-message-pattern"};
    public final String[] ENCRYPT_PREFIX = new String[] {"player-chat", "encrypt-prefix"};
    public final String[] DECRYPT_SUFFIX = new String[] {"player-chat", "decrypt-ignore-suffix"};

    public PlayerChat() {}

    public List<Pattern> compile;

    public final ListRef chatMessageFormat = builder(
                    Configs.CHAT_CONFIG, GAME_MESSAGE_PATTERN_AS_PLAYER_MESSAGE, ListRef.TYPE)
            .defaultValue(List.of(
                    "^.*\\[([^\\]\\[\\s]+)\\]\\s*[:➟→»》]\\s*(.*)$",
                    "^.*\\[[^\\]\\[]+\\].* ([^\\]\\[\\s]+)\\s*[:➟→»》]\\s*(.*)$",
                    "^.*<([^><\\s]+)>\\s*[:➟→»》]\\s*(.*)$",
                    "^.*《([^》《\\s]+)》\\s*[:➟→»》]\\s*(.*)$",
                    "^.*«([^»«\\s]+)»\\s+(.*)$"))
            .listValidator(Configs.REGEX_VALIDATOR)
            .updateListener(s -> compile = s.stream().map(Pattern::compile).toList())
            .build();

    public final FlagRef detectPlayerName =
            flagBuilder(Configs.CHAT_CONFIG, DETECT_PLAYER_NAMES_IN_MESSAGE).build();

    public final FlagRef timeStamp =
            flagBuilder(Configs.CHAT_CONFIG, APPEND_TIME_STAMP).build();

    public final FlagRef playerHead =
            flagBuilder(Configs.CHAT_CONFIG, APPEND_CHAT_HEAD).build();

    public final FlagRef encrypt =
            flagBuilder(Configs.CHAT_CONFIG, ENCRYPT_MESSAGE_OUT).build();

    public final FlagRef decrypt =
            flagBuilder(Configs.CHAT_CONFIG, DECRYPT_MESSAGE_IN).build();
    boolean dirty = true;

    public final EnumRef<EncryptAlgorithm> algorithm = builder(
                    Configs.CHAT_CONFIG, ENCRYPT_ALGORITHM, EncryptAlgorithm.class)
            .defaultValue(EncryptAlgorithm.NONE)
            .updateListener(s -> dirty = true)
            .build();

    public final NBTRef<EncryptionKey> key = builder(Configs.CHAT_CONFIG, ENCRYPT_KEY, EncryptionKey.class)
            .defaultValue(EncryptionKey.EMPTY)
            .updateListener(s -> dirty = true)
            .build();

    public final StringRef prefixEncrypt = builder(Configs.CHAT_CONFIG, ENCRYPT_PREFIX, StringRef.TYPE)
            .defaultValue("")
            .validator(s -> s.isEmpty() || s.endsWith(" "))
            .build();
    CharSet charSet = new CharArraySet();

    public final StringRef suffixDecrypt = builder(Configs.CHAT_CONFIG, DECRYPT_SUFFIX, StringRef.TYPE)
            .defaultValue("喵")
            .updateListener(s -> {
                CharSet cs = new CharArraySet();
                for (var i = 0; i < s.length(); ++i) {
                    cs.add(s.charAt(i));
                }
                charSet = cs;
            })
            .build();

    public final NBTRef<Regex> commandPattern = builder(Configs.CHAT_CONFIG, ENCRYPT_COMMAND_MESSAGE, Regex.class)
            .defaultValue(new Regex("^/(minecraft:)?(msg|say|me) ([^\\s]+) (.*)$"))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getMessageAddToHud(), this::onChatAdd);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(ChatMessageS2CPacket.class),
                (Consumer<Event<ChatMessageS2CPacket>>) this::<ChatMessageS2CPacket>onPacketIn);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(GameMessageS2CPacket.class),
                (Consumer<Event<GameMessageS2CPacket>>) this::<GameMessageS2CPacket>onPacketIn);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(ChatMessageS2CPacket.class),
                (Consumer<Event<ChatMessageS2CPacket>>) this::<ChatMessageS2CPacket>onPacketInPost);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(GameMessageS2CPacket.class),
                (Consumer<Event<GameMessageS2CPacket>>) this::<GameMessageS2CPacket>onPacketInPost);
        registerListener(Listener.getChatSend(), this::onChatEncrypt, Integer.MAX_VALUE - 2);
    }

    public MessageIndicator systemIndicator() {
        return mc.isConnectedToLocalServer() ? MessageIndicator.singlePlayer() : MessageIndicator.system();
    }

    public Matcher matcher(String message) {
        if (compile != null)
            for (Pattern pattern : compile) {
                Matcher matcher = pattern.matcher(message);
                if (matcher.matches() && matcher.groupCount() >= 1) {
                    return matcher;
                }
            }
        return null;
    }

    UUID lastAcceptUUID;

    public <T extends Packet<?>> void onPacketIn(Event<T> packetEvent) {
        handleChatMsg = true;
        if (packetEvent.context() instanceof ChatMessageS2CPacket chatMessageC2SPacket) {
            lastAcceptUUID = chatMessageC2SPacket.sender();
        }
    }

    public <T extends Packet<?>> void onPacketInPost(Event<T> packetEvent) {
        handleChatMsg = false;
        lastAcceptUUID = null;
    }

    volatile boolean handleChatMsg = false;
    volatile boolean safeFlag = false;

    public void onChatAdd(Event<Text> chatAdd) {
        if (chatAdd.isCancelled()) return;
        if (safeFlag) {
            return;
        }
        if (hasAnyFunctionEnable() && handleChatMsg) {
            safeFlag = true;
            try {
                MessageIndicator indicator = chatAdd.getArgs(1);
                String text = ChatUtils.textToPlainString(chatAdd.context);

                Matcher matcher = matcher(text);
                if (matcher != null && matcher.groupCount() >= 2) {
                    handleParsedChatMessage(
                            chatAdd, text, matcher.group(matcher.groupCount() - 1), indicator == systemIndicator());
                } else {
                    String caughtName = null;
                    if (lastAcceptUUID != null) {
                        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(lastAcceptUUID);
                        if (entry != null) {
                            caughtName = entry.getProfile().name();
                        }
                    }
                    // do not use
                    if (caughtName == null && detectPlayerName.get()) {
                        String findingMsg = text;
                        for (var playerListEntry : mc.getNetworkHandler().getPlayerList()) {
                            String playerName = playerListEntry.getProfile().name();
                            int index = findingMsg.indexOf(playerName);
                            if (index != -1) {

                                findingMsg = findingMsg.substring(0, index);
                                caughtName = playerName;
                            }
                            Text displayName = playerListEntry.getDisplayName();
                            if (displayName != null) {
                                String displayName2 =
                                        ChatUtils.textToPlainString(displayName); // playerListEntry.getDisplayName();
                                int idx = findingMsg.indexOf(displayName2);
                                if (idx != -1) {
                                    findingMsg = findingMsg.substring(0, idx);
                                    caughtName = playerListEntry.getProfile().name();
                                }
                            }
                            if (findingMsg.isEmpty()) {
                                break;
                            }
                        }
                    }
                    handleParsedChatMessage(chatAdd, text, caughtName, indicator == systemIndicator());
                }
            } finally {
                safeFlag = false;
            }
        }
    }

    public boolean hasAnyFunctionEnable() {
        return decrypt.get() || playerHead.get() || timeStamp.get();
    }

    public void handleParsedChatMessage(
            Event<Text> event, String message, @Nullable String capturedName, boolean isSystem) {
        // Debug.chat("Find chat message:", capturedName, "Msg:" , capturedMessage, isSystem);
        Text text = event.context();
        MutableInt keepIndex = new MutableInt(message.length());
        MutableBoolean mutableBoolean = new MutableBoolean(false);
        // handle
        List<Consumer<ChatUtils.TextBuilder>> appendToFirst = new ArrayList<>();
        appendToFirst.add(handleChatHead(capturedName, mutableBoolean));
        appendToFirst.add(handleTimeStampAdd(mutableBoolean, capturedName));
        List<Consumer<ChatUtils.TextBuilder>> appendToLast = new ArrayList<>();
        appendToLast.add(handleDecryptMessage(message, keepIndex));
        if (mutableBoolean.booleanValue() || keepIndex.intValue() < message.length()) {
            // remake this
            MutableInt counter = new MutableInt(0);
            ChatUtils.TextBuilder newBuilder = ChatUtils.builder();
            for (var re : appendToFirst) {
                if (re != null) {
                    re.accept(newBuilder);
                }
            }
            newBuilder.withStyle(Style.EMPTY);
            text.visit(
                    ((style, asString) -> {
                        int len = asString.length();
                        if (counter.intValue() + len > keepIndex.intValue()) {
                            int cut = keepIndex.intValue() - counter.intValue();
                            String cutStr = asString.substring(0, cut);
                            newBuilder.accept(style, cutStr);
                            counter.add(cutStr.length());
                            return StringVisitable.TERMINATE_VISIT;
                        } else {
                            newBuilder.accept(style, asString);
                            counter.add(asString.length());
                            return Optional.empty();
                        }
                    }),
                    Style.EMPTY);
            for (var re : appendToLast.reversed()) {
                if (re != null) {
                    re.accept(newBuilder);
                }
            }
            event.context(newBuilder.end().build());
        }
    }
    public Pattern pattern = Pattern.compile("^(?!_)(?![0-9]+$)[a-zA-Z0-9_]{3,16}$");
    public Consumer<ChatUtils.TextBuilder> handleChatHead(String playerName, MutableBoolean mutableBoolean) {
        if (!playerHead.get()) return null;
        PlayerListEntry entry;
        if (lastAcceptUUID != null) {

            ObjectTextContent content = new ObjectTextContent(
                    new PlayerTextObjectContents(ProfileComponent.ofDynamic(lastAcceptUUID), false));
            mutableBoolean.setTrue();
            entry = mc.getNetworkHandler().getPlayerListEntry(lastAcceptUUID);
            return (builder) -> {
                builder.withHoverEvent(ChatUtils.getHoverShowText(List.of(
                                Text.literal("玩家:"
                                        + (entry == null
                                                ? "未知"
                                                : entry.getProfile().name())),
                                Text.literal("玩家UUID:" + lastAcceptUUID))))
                        .withContent(content)
                        .withStyle(Style.EMPTY);
            };
        }
        if (playerName != null  && pattern.matcher(playerName).matches()) {

            ObjectTextContent content =
                    new ObjectTextContent(new PlayerTextObjectContents(ProfileComponent.ofDynamic(playerName), false));
            mutableBoolean.setTrue();
            return (builder) -> {
                builder.withHoverEvent(ChatUtils.getHoverShowText(List.of(Text.literal("玩家:" + playerName))))
                        .withContent(content)
                        .withStyle(Style.EMPTY);
            };
        }
        return null;
    }

    public Consumer<ChatUtils.TextBuilder> handleTimeStampAdd(MutableBoolean shouldModify, String capturedName) {
        if (timeStamp.get() && ((capturedName != null && pattern.matcher(capturedName).matches()) || lastAcceptUUID != null)) {
            shouldModify.setValue(true);
            SimpleDateFormat sdf = new SimpleDateFormat("[HH:mm:ss]");
            String time = sdf.format(new Date());
            return (builder) -> {
                builder.withFormat(Formatting.GRAY).with(time).withStyle(Style.EMPTY);
            };
        }
        return null;
    }

    public Consumer<ChatUtils.TextBuilder> handleDecryptMessage(String message, MutableInt keepIndex) {
        if (!decrypt.get()) return null;
        int lastIndex = keepIndex.intValue();
        int currentIndex;
        do {
            currentIndex = keepIndex.decrementAndGet();
        } while (currentIndex >= 0
                && (message.charAt(currentIndex) == ' ' || charSet.contains(message.charAt(currentIndex))));
        // can not find any useful message
        if (currentIndex <= 2) {
            keepIndex.setValue(lastIndex);
            return null;
        }

        keepIndex.increment();
        final int decryptStart = keepIndex.intValue();
        String mutableString = message.substring(0, decryptStart);
        int idx = mutableString.lastIndexOf(' ');
        String messagePart = mutableString.substring(idx + 1);
        keepIndex.setValue(idx + 1);
        String decrypt;
        if ((decrypt = decryptValidate(messagePart)) != null) {
            String msg = decrypt + message.substring(decryptStart, lastIndex);
            return (builder) -> builder.withHoverEvent(ChatUtils.getHoverShowText(List.of(
                            Text.literal("当前密文:" + messagePart),
                            Text.literal("点击拷贝").formatted(Formatting.YELLOW))))
                    .withClickEvent(ChatUtils.getClickCopyText(messagePart))
                    .with(msg)
                    .withHoverEvent(ChatUtils.getHoverShowText(List.of(Text.literal("当前消息由SlimefunHelper解密"))))
                    .withClickEvent(null)
                    .withBold(true)
                    .withColor(Formatting.DARK_PURPLE)
                    .with(" [!]")
                    .withStyle(Style.EMPTY);

        } else {
            // decrypt failure , not our format
            keepIndex.setValue(lastIndex);
            return null;
        }
    }

    public String decryptValidate(String message) {
        return tryDecrypt(message, getEncryptor()).orElse(null);
    }

    Encryptor cache = null;

    public String getSecretKey() {
        EncryptionKey keyInstance = key.get();
        String val = keyInstance.key();
        if (val.isEmpty()) {
            // generate by phrase
            String phrase = keyInstance.phase();
            if (!phrase.isEmpty()) {
                try {
                    key.set(keyInstance.withKey(generateSecretKey(phrase)));
                } catch (Throwable e) {
                }
            }
        }
        return key.get().key();
    }

    public String generateSecretKey(String phrase) {
        return algorithm.get().getEncryption().generateKey(phrase);
    }

    @Nonnull
    public Encryptor getEncryptor() {
        if (dirty || cache == null) {
            EncryptAlgorithm algorithm = this.algorithm.get();
            Encryption encryption = algorithm.getEncryption();
            try {
                cache = Objects.requireNonNull(encryption.getEncryptor(getSecretKey()));
            } catch (Throwable e) {
                Debug.chat("[ChatEncrypt] 当前密钥格式不正确,已自动禁用加密模式!");
                Debug.info(e);
                this.algorithm.set(EncryptAlgorithm.NONE);
                cache = Encryptor.EMPTY;
            }
            dirty = false;
        }
        return Objects.requireNonNull(cache);
    }

    public static Optional<String> tryDecrypt(String message, Encryptor encryptor) {
        try {
            String messageCopy = message.replace('：', ' ');
            String[] splat = messageCopy.contains(" ") ? messageCopy.split(" ") : new String[] {messageCopy};
            String decryptable = splat[splat.length - 1];

            String decrypted = encryptor.decrypt(decryptable);

            if (decrypted.startsWith("#%"))
                return Optional.of(message.substring(0, message.length() - decryptable.length())
                        + decrypted.substring(2, decrypted.length()));
            else return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public boolean shouldEncryptSendMessage() {
        return encrypt.get() && !ScreenUtils.hasCtrlDown();
    }

    public void onChatEncrypt(Event<String> event) {
        if (event.isCancelled()) return;
        if (shouldEncryptSendMessage()) {

            Matcher matcher = this.commandPattern.get().pattern().matcher(event.context());
            if (matcher.matches() && matcher.groupCount() > 0) {
                int gpcnt = matcher.groupCount();
                String replacement = matcher.group(gpcnt);
                String encrypt = tryEncrypt(replacement, getEncryptor(), 32000);
                StringBuilder builder = new StringBuilder(event.context());
                builder.replace(matcher.start(gpcnt), matcher.end(gpcnt), encrypt);
                event.context(builder.toString());
                return;
            }

            String str = event.context();
            if (!Pattern.matches(
                    ChatTasks.getChatExtra().commandEscapeFormatPattern.get(), str)) {
                event.context(prefixEncrypt.get() + tryEncrypt(str, getEncryptor(), 256));
            }
        }
    }

    public static String tryEncrypt(String encrypt, Encryptor encryptor, int maxLength) {
        while (!encrypt.isEmpty()) {
            String encrypted = encryptor.encrypt("#%" + encrypt);
            if (encrypted.length() <= maxLength) return encrypted;

            encrypt = encrypt.substring(0, encrypt.length() - 1);
        }
        return "";
    }

    public static interface Encryption {
        public abstract Encryptor getEncryptor(String k) throws Throwable;

        default String generateKey(String pass) {
            return pass;
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class AESEncryption implements Encryption {
        String mode;
        String padding;
        boolean initialVector;

        public Encryptor getEncryptor(String key) throws Throwable {
            return new AESEncryptor(new SecretKeySpec(decodeBinaryKey(key), "AES"), this);
        }

        public String generateKey(String key) {
            try {
                byte[] salt = new byte[16];
                new Random(1738389128127L).nextBytes(salt);

                KeySpec spec = new PBEKeySpec(key.toCharArray(), salt, 65536, 128); // AES-128
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
                byte[] keyV = factory.generateSecret(spec).getEncoded();

                return BASE64_ENCODER.encodeToString(new SecretKeySpec(keyV, "AES").getEncoded());
            } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static class AESCFB8 extends AESEncryption implements UseIV {
        public AESCFB8() {
            super("CFB8", "NoPadding", true);
        }

        @Override
        public Pair<AlgorithmParameterSpec, byte[]> generateIV() {
            long nonce = RANDOM.nextLong();
            byte[] iv = new byte[16];
            new Random(nonce).nextBytes(iv);
            return new Pair<>(
                    new IvParameterSpec(iv),
                    ByteBuffer.allocate(8).putLong(nonce).array());
        }

        @Override
        public Pair<AlgorithmParameterSpec, byte[]> splitIV(byte[] message) {
            ByteBuffer buffer = ByteBuffer.wrap(message);
            int size = buffer.capacity();
            long nonce = buffer.getLong();
            byte[] encrypted = new byte[size - 8];
            buffer.get(encrypted);
            byte[] iv = new byte[16];
            new Random(nonce).nextBytes(iv);
            return new Pair<>(new IvParameterSpec(iv), encrypted);
        }
    }

    public static AESEncryption AESCFB8 = new AESCFB8();

    public static AESEncryption AESECB = new AESEncryption("ECB", "PKCS5Padding", false);

    public static class AESGCM extends AESEncryption implements UseIV {
        public AESGCM() {
            super("GCM", "NoPadding", true);
        }

        @Override
        public Pair<AlgorithmParameterSpec, byte[]> generateIV() {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            return new Pair<>(new GCMParameterSpec(96, iv), iv);
        }

        @Override
        public Pair<AlgorithmParameterSpec, byte[]> splitIV(byte[] message) {
            byte[] iv = new byte[12];
            byte[] msg = new byte[message.length - 12];
            ByteBuffer.wrap(message).get(iv).get(msg);
            return new Pair<>(new GCMParameterSpec(96, iv), msg);
        }
    }

    public static AESEncryption AESGCM = new AESGCM();

    public static interface UseIV {
        public Pair<AlgorithmParameterSpec, byte[]> generateIV();

        public Pair<AlgorithmParameterSpec, byte[]> splitIV(byte[] message);
    }

    @Getter
    public static enum EncryptAlgorithm implements ConfigEnum {
        NONE((l) -> Encryptor.EMPTY),
        AES_CFB8(AESCFB8),
        AES_GCM(AESGCM),
        AES_ECB(AESECB);
        final Encryption encryption;

        EncryptAlgorithm(Encryption encryption) {
            this.encryption = encryption;
        }

        @Override
        public Text getDisplay() {
            return Text.translatable(
                    "configenum.encrypt-algorithm." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    public static interface Encryptor {
        public String encrypt(String message);

        public String decrypt(String message);

        public static Encryptor EMPTY = new Encryptor() {
            @Override
            public String encrypt(String message) {
                return message;
            }

            @Override
            public String decrypt(String message) {
                return message;
            }
        };
    }

    public static class AESEncryptor implements Encryptor {
        SecretKey key;

        @Getter
        boolean keyValid = false;

        AESEncryption encryption;
        Cipher encryptor;
        Cipher decryptor;

        public AESEncryptor(SecretKey key, AESEncryption algorithm) {
            this.key = key;
            this.encryption = algorithm;
            try {
                Cipher encryptor = Cipher.getInstance(
                        this.key.getAlgorithm() + "/" + this.encryption.getMode() + "/" + this.encryption.getPadding());
                if (this.encryption.initialVector) {
                    encryptor.init(ENCRYPT_MODE, this.key, this.generateIV().getFirst());
                } else {
                    encryptor.init(ENCRYPT_MODE, this.key);
                }
                this.encryptor = encryptor;

                Cipher decryptor = Cipher.getInstance(
                        this.key.getAlgorithm() + "/" + this.encryption.getMode() + "/" + this.encryption.getPadding());
                if (this.encryption.initialVector) {
                    decryptor.init(DECRYPT_MODE, this.key, this.generateIV().getFirst());
                } else {
                    decryptor.init(DECRYPT_MODE, this.key);
                }
                this.decryptor = decryptor;
                keyValid = true;
            } catch (InvalidAlgorithmParameterException e) {
                //
                keyValid = false;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        public Pair<AlgorithmParameterSpec, byte[]> generateIV() {
            return ((UseIV) encryption).generateIV();
        }

        public Pair<AlgorithmParameterSpec, byte[]> splitIV(byte[] message) {
            return ((UseIV) encryption).splitIV(message);
        }

        @Override
        public String encrypt(String message) {
            try {
                if (this.encryption.initialVector) {
                    var tuple = this.generateIV();

                    this.encryptor.init(ENCRYPT_MODE, this.key, tuple.getFirst());
                    byte[] encrypted = this.encryptor.doFinal(message.getBytes(StandardCharsets.UTF_8));

                    return encodeBase64R(ByteBuffer.allocate(encrypted.length + tuple.getSecond().length)
                            .put(tuple.getSecond())
                            .put(encrypted)
                            .array());
                } else return encodeBase64R(this.encryptor.doFinal(toBytes(message)));
            } catch (IllegalBlockSizeException
                    | BadPaddingException
                    | InvalidKeyException
                    | InvalidAlgorithmParameterException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public String decrypt(String message) {
            try {
                if (this.encryption.initialVector) {
                    var tuple = this.splitIV(decodeBase64RBytes(message));

                    this.decryptor.init(DECRYPT_MODE, this.key, tuple.getFirst());
                    return fromBytes(this.decryptor.doFinal(tuple.getSecond()));
                } else return fromBytes(this.decryptor.doFinal(decodeBase64RBytes(message)));
            } catch (AEADBadTagException ex) {
                return "???";
            } catch (IllegalBlockSizeException
                    | BadPaddingException
                    | InvalidKeyException
                    | InvalidAlgorithmParameterException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @With
    public record EncryptionKey(String phase, String key) implements NBTParsable<EncryptionKey> {
        public static EncryptionKey of(String phase, String key) {
            if (phase != null
                    && !phase.isEmpty()
                    && (key == null || key.isEmpty())
                    && ChatTasks.getPlayerChat() != null) {
                PlayerChat chat = ChatTasks.getPlayerChat();
                try {
                    key = chat.generateSecretKey(phase);
                } catch (Throwable e) {
                }
            }
            return new EncryptionKey(phase, key);
        }

        public static final NBTType<EncryptionKey> TYPE = NBTTypes.createPairLike(
                EncryptionKey.class,
                NBTTypes.STRING_TYPE,
                "phase",
                NBTTypes.STRING_TYPE,
                "key",
                PairLikeFactory.of(EncryptionKey::of, EncryptionKey::phase, EncryptionKey::key),
                AttrKeyValue.CustomWidgetFactory.cutSizeXLeft(0.3),
                AttrKeyValue.CustomWidgetFactory.cutSizeXRight(0.3));

        public static final EncryptionKey EMPTY = TYPE.empty();

        @Override
        public NBTType<EncryptionKey> type() {
            return TYPE;
        }
    }
}
