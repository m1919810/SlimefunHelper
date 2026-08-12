package me.matl114.hacks.modules.chat;

import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static me.matl114.utils.EncryptUtils.*;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import javax.annotation.Nonnull;
import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.annotations.Cancelable;
import me.matl114.events.annotations.ExtraArgs;
import me.matl114.events.annotations.Modifiable;
import me.matl114.events.channels.EventChannel;
import me.matl114.gui.Constants;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.config.ListModifyWidget;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.elements.LabelElement;
import me.matl114.gui.elements.MultiLineTextElement;
import me.matl114.gui.presets.lists.ListEntryWidgetController;
import me.matl114.gui.presets.single.CenterScreen;
import me.matl114.gui.presets.single.ConfirmingWidgetScreen;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.task.ClickGui;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.StringRef;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.mutable.MutableInt;

public class EncryptChat extends BaseModule {
    private final ModulePath encryptChat = makePath(Configs.CHAT_CONFIG, "encrypt-chat");

    public EncryptChat() {
        super("EncryptChat");
    }

    @Getter // cancelable, modifiable
    @Cancelable
    @Modifiable
    @ExtraArgs({String.class, String.class})
    private static final EventChannel<String> decryptMessage = new EventChannel<>();

    public final FlagRef encrypt =
            flagBuilder(encryptChat.add("encrypt-message-out")).build();

    public final FlagRef decrypt =
            flagBuilder(encryptChat.add("decrypt-message-in")).build();

    public final StringRef prefixEncrypt = builder(encryptChat.add("encrypt-prefix"), StringRef.TYPE)
            .defaultValue("")
            .validator(s -> s.isEmpty() || s.endsWith(" "))
            .build();

    private CharSet ignoredSuffixCharSet = new CharArraySet();

    public final StringRef suffixDecrypt = builder(encryptChat.add("decrypt-ignore-suffix"), StringRef.TYPE)
            .defaultValue("喵")
            .updateListener(this::reloadIgnoredSuffix)
            .build();

    public final NBTRef<Regex> commandPattern = builder(encryptChat.add("encrypt-command-message-pattern"), Regex.class)
            .defaultValue(new Regex("^/(minecraft:)?(msg|say|me) ([^\\s]+) (.*)$"))
            .build();

    private final FileStorage keyStorage = FileManager.getInstance().getInternalStorage("encrypt-chat-keys.nbt");
    private KeyList keyList = keyStorage.read(KeyList.CODEC, () -> new KeyList(-1, List.of()));

    private boolean dirty = true;
    private Encryptor encryptorCache = Encryptor.EMPTY;
    private ChatKeyEntry cacheEntry;
    private volatile boolean safeFlag = false;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getMessageAddToHud(), this::onChatAdd, -10);
        registerListener(Listener.getChatSend(), this::onChatEncrypt, Integer.MAX_VALUE - 2);

        reloadIgnoredSuffix(suffixDecrypt.get());
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        acceptor.accept(ExecutableWidget.instance(0, dblank, dx, dy)
                .setElementHandler(new ButtonElement(
                                el -> {
                                    ChatKeyEntry selected = keyList.getSelected();
                                    if (selected == null) {
                                        return Text.translatable("widget.encrypt-chat.key-list-editor")
                                                .append("None");
                                    }
                                    String detail = "%s(%s%s)"
                                            .formatted(
                                                    selected.getName(),
                                                    selected.getAlgorithm().name(),
                                                    selected.getPhase().isEmpty() ? "" : "/phrase");
                                    return Text.translatable("widget.encrypt-chat.key-list-editor")
                                            .append(detail);
                                },
                                ButtonAction.run(this::openKeyListEditScreen))
                        .withTooltips(TooltipHandler.of(Constants.openListEditTooltips()))));
    }

    private void openKeyListEditScreen() {
        List<ChatKeyEntry> currentList = new ArrayList<>(
                keyList.entries().stream().map(ChatKeyEntry::copy).toList());
        MutableInt index = new MutableInt(keyList.selected());
        ListEntryWidgetController controller = ListEntryWidgetController.mutable(
                currentList, ChatKeyEntry::empty, value -> createEditRenderHandler(value, currentList, index), 30, 220);
        ListModifyWidget listWidget = new ListModifyWidget(controller, 0, 0, 320, 260);
        ConfirmingWidgetScreen confirmScreen = new ConfirmingWidgetScreen(
                Text.translatable("widget.encrypt-chat.key-list-editor.title"),
                listWidget,
                () -> true,
                () -> setKeyList(new KeyList(index.getValue(), List.copyOf(currentList))));
        confirmScreen.access().openFromCurrent();
    }

    private DrawableWidget createEditRenderHandler(ChatKeyEntry entry, List<ChatKeyEntry> entryList, MutableInt index) {
        SubScreenWidget subScreen = new SubScreenWidget(0, 0, 220, 20);
        ExecutableWidget.instance(2, 2, 16, 16)
                .setElementHandler(IconElement.statedGuiPredicate(
                        ButtonElement.BUTTON,
                        ButtonElement.BUTTON_INACTIVE,
                        ButtonAction.run(() -> {
                            int currentIndex = index.getValue();
                            int elementIndex = entryList.indexOf(entry);
                            if (elementIndex != -1) {
                                index.setValue(currentIndex == elementIndex ? -1 : elementIndex);
                            }
                        }),
                        bl -> {
                            int idx = index.getValue();
                            return idx >= 0 && idx < entryList.size() && entryList.get(idx) == entry;
                        }))
                .addToSub(subScreen);
        DisplayWidget.instance(45, 0, 100, 20)
                .setRenderHandler(
                        new LabelElement(ClickGui.INSTANCE.backGroundColor.get().withAlpha(64)))
                .addToSub(subScreen);
        ExecutableWidget.instance(45, 0, 100, 20)
                .setElementHandler(new MultiLineTextElement(
                        el -> Text.literal("%s\n(%s)"
                                .formatted(entry.getName(), entry.getAlgorithm().name())),
                        ClickGui.INSTANCE.configColor.get().withAlpha(255),
                        0))
                .addToSub(subScreen);
        ExecutableWidget.instance(155, 0, 60, 20)
                .setElementHandler(new ButtonElement(
                        TextProvider.of(Text.translatable("widget.encrypt-chat.open-editor")), ButtonAction.run(() -> {
                            var screen = WidgetUtils.createValueAccessorsEditScreen(
                                    Text.translatable("widget.encrypt-chat.open-editor.title"),
                                    List::of,
                                    createChatKeyEntryAccessors(entry),
                                    WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT,
                                    ClickGui.CONFIG_PALETTE);
                            new CenterScreen(screen).access().openFromCurrent();
                        })))
                .addToSub(subScreen);
        return subScreen;
    }

    private List<Pair<String, ValueAccessor<?>>> createChatKeyEntryAccessors(ChatKeyEntry entry) {
        List<Pair<String, ValueAccessor<?>>> accessors = new ArrayList<>();
        accessors.add(Pair.of("widget.encrypt-chat.name", ValueAccessor.of(entry::getName, entry::setName)));
        accessors.add(Pair.of(
                "widget.encrypt-chat.algorithm",
                ValueAccessor.of(entry::getAlgorithm, algorithm -> entry.setAlgorithm((EncryptAlgorithm) algorithm))));
        accessors.add(Pair.of("widget.encrypt-chat.phase", ValueAccessor.of(entry::getPhase, entry::setPhase)));
        accessors.add(Pair.of("widget.encrypt-chat.key", ValueAccessor.of(entry::getKey, entry::setKey)));
        return accessors;
    }

    public void setKeyList(KeyList keyList) {
        this.keyList = keyList;
        this.keyStorage.write(KeyList.CODEC, keyList);
        this.dirty = true;
    }

    public boolean shouldEncryptSendMessage() {
        return encrypt.get() && !ScreenUtils.hasCtrlDown() && getSelectedKey() != null;
    }

    public void onChatAdd(Event<Text> chatAdd) {
        if (chatAdd.isCancelled() || safeFlag || !decrypt.get()) {
            return;
        }
        safeFlag = true;
        try {
            String text = ChatUtils.textToPlainString(chatAdd.context());
            DecryptScanResult result = detectEncryptedChunk(text);
            if (result == null) {
                return;
            }
            Event<String> decryptEvent = new Event<>(result.decrypted(), true, true, result.prefix(), result.cipher());
            getDecryptMessage().handleValue(decryptEvent);
            if (decryptEvent.isCancelled()) {
                chatAdd.cancel();
                return;
            }
            String replacement = Objects.requireNonNullElse(decryptEvent.context(), "");
            chatAdd.context(rebuildMessage(chatAdd.context(), result, replacement));
        } finally {
            safeFlag = false;
        }
    }

    private DecryptScanResult detectEncryptedChunk(String message) {
        int rawEnd = trimIgnoredSuffixEnd(message);
        if (rawEnd <= 0) {
            return null;
        }
        int start = rawEnd;
        while (start > 0 && isEncryptedMessageChar(message.charAt(start - 1))) {
            --start;
        }
        if (start == rawEnd) {
            return null;
        }
        String cipher = message.substring(start, rawEnd);
        String decrypted = decryptValidate(cipher);
        if (decrypted == null) {
            return null;
        }
        return new DecryptScanResult(
                start, rawEnd, message.substring(0, start), cipher, message.substring(rawEnd), decrypted);
    }

    private boolean isEncryptedMessageChar(char ch) {
        return "!\"#$%¼'(),-.:;<=>?@[\\]^_`{|}~¡¢£¤¥¦¨©ª«¬®¯°±²³µ¶·×¹º0123456789+»¿".indexOf(ch) >= 0;
    }

    private int trimIgnoredSuffixEnd(String message) {
        int idx = message.length() - 1;
        while (idx >= 0) {
            char ch = message.charAt(idx);
            if (!Character.isWhitespace(ch) && !ignoredSuffixCharSet.contains(ch)) {
                break;
            }
            --idx;
        }
        return idx + 1;
    }

    private Text rebuildMessage(Text origin, DecryptScanResult result, String replacement) {
        MutableInt counter = new MutableInt(0);
        ChatUtils.TextBuilder builder = ChatUtils.builder();
        builder.withStyle(Style.EMPTY);
        origin.visit(
                ((style, asString) -> {
                    int len = asString.length();
                    if (counter.intValue() + len > result.start()) {
                        int cut = result.start() - counter.intValue();
                        if (cut > 0) {
                            String cutStr = asString.substring(0, cut);
                            builder.accept(style, cutStr);
                            counter.add(cutStr.length());
                        }
                        return StringVisitable.TERMINATE_VISIT;
                    } else {
                        builder.accept(style, asString);
                        counter.add(len);
                        return Optional.empty();
                    }
                }),
                Style.EMPTY);
        builder.withHoverEvent(ChatUtils.getHoverShowText(List.of(
                        Text.literal("当前密文:" + result.cipher()),
                        Text.literal("点击拷贝").formatted(Formatting.YELLOW))))
                .withClickEvent(ChatUtils.getClickCopyText(result.cipher()))
                .with(replacement + result.suffix())
                .withHoverEvent(ChatUtils.getHoverShowText(List.of(Text.literal("当前消息由SlimefunHelper解密"))))
                .withClickEvent(null)
                .withBold(true)
                .withColor(Formatting.DARK_PURPLE)
                .with(" [!]")
                .withStyle(Style.EMPTY);
        return builder.end().build();
    }

    private void reloadIgnoredSuffix(String value) {
        CharSet set = new CharArraySet();
        for (int i = 0; i < value.length(); ++i) {
            set.add(value.charAt(i));
        }
        this.ignoredSuffixCharSet = set;
    }

    public void onChatEncrypt(Event<String> event) {
        if (event.isCancelled() || !shouldEncryptSendMessage()) {
            return;
        }
        Encryptor encryptor = getEncryptor();
        if (encryptor == Encryptor.EMPTY) {
            return;
        }

        Matcher matcher = this.commandPattern.get().pattern().matcher(event.context());
        if (matcher.matches() && matcher.groupCount() > 0) {
            int groupCount = matcher.groupCount();
            String replacement = matcher.group(groupCount);
            String encrypted = tryEncrypt(replacement, encryptor, 32000);
            StringBuilder builder = new StringBuilder(event.context());
            builder.replace(matcher.start(groupCount), matcher.end(groupCount), encrypted);
            event.context(builder.toString());
            return;
        }

        String content = event.context();
        if (!ChatTasks.getChatExtra().shouldEscapeFormatting(content)) {
            event.context(prefixEncrypt.get() + tryEncrypt(content, encryptor, 256));
        }
    }

    public String decryptValidate(String message) {
        Encryptor encryptor = getEncryptor();
        if (encryptor == Encryptor.EMPTY) {
            return null;
        }
        return tryDecrypt(message, encryptor).orElse(null);
    }

    @Nonnull
    public Encryptor getEncryptor() {
        ChatKeyEntry selected = getSelectedKey();
        if (selected == null || selected.getAlgorithm() == EncryptAlgorithm.NONE) {
            return Encryptor.EMPTY;
        }
        if (dirty || cacheEntry == null || cacheEntry != selected) {
            try {
                String secretKey = getSecretKey();
                if (secretKey.isEmpty()) {
                    encryptorCache = Encryptor.EMPTY;
                } else {
                    encryptorCache = Objects.requireNonNull(
                            selected.getAlgorithm().getEncryption().getEncryptor(secretKey));
                }
            } catch (Throwable e) {
                Debug.chat("[ChatEncrypt] 当前密钥格式不正确, 已跳过聊天加密/解密。");
                Debug.info(e);
                encryptorCache = Encryptor.EMPTY;
            }
            cacheEntry = selected;
            dirty = false;
        }
        return Objects.requireNonNull(encryptorCache);
    }

    public ChatKeyEntry getSelectedKey() {
        return keyList.getSelected();
    }

    public String getSecretKey() {
        ChatKeyEntry keyEntry = getSelectedKey();
        if (keyEntry == null) {
            return "";
        }
        if (!keyEntry.getKey().isEmpty()) {
            return keyEntry.getKey();
        }
        if (keyEntry.getPhase().isEmpty() || keyEntry.getAlgorithm() == EncryptAlgorithm.NONE) {
            return "";
        }
        String generated = generateSecretKey(keyEntry.getPhase(), keyEntry.getAlgorithm());
        keyEntry.setKey(generated);
        setKeyList(keyList);
        return generated;
    }

    public String generateSecretKey(String phrase, EncryptAlgorithm algorithm) {
        return algorithm.getEncryption().generateKey(phrase);
    }

    public static Optional<String> tryDecrypt(String message, Encryptor encryptor) {
        try {
            String decrypted = encryptor.decrypt(message);
            if (decrypted.startsWith("#%")) {
                return Optional.of(decrypted.substring(2));
            }
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public static String tryEncrypt(String encrypt, Encryptor encryptor, int maxLength) {
        while (!encrypt.isEmpty()) {
            String encrypted = encryptor.encrypt("#%" + encrypt);
            if (encrypted.length() <= maxLength) {
                return encrypted;
            }
            encrypt = encrypt.substring(0, encrypt.length() - 1);
        }
        return "";
    }

    public record DecryptScanResult(
            int start, int end, String prefix, String cipher, String suffix, String decrypted) {}

    public static final class ChatKeyEntry {
        public static final ChatKeyEntry EMPTY = new ChatKeyEntry("", EncryptAlgorithm.NONE, "", "");
        public static final Codec<ChatKeyEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("name").forGetter(ChatKeyEntry::getName),
                        CodecUtils.enumCodec(EncryptAlgorithm.class)
                                .fieldOf("algorithm")
                                .forGetter(ChatKeyEntry::getAlgorithm),
                        Codec.STRING.fieldOf("phase").forGetter(ChatKeyEntry::getPhase),
                        Codec.STRING.fieldOf("key").forGetter(ChatKeyEntry::getKey))
                .apply(instance, ChatKeyEntry::new));

        private String name;
        private EncryptAlgorithm algorithm;
        private String phase;
        private String key;

        public ChatKeyEntry(String name, EncryptAlgorithm algorithm, String phase, String key) {
            this.name = name;
            this.algorithm = algorithm;
            this.phase = phase;
            this.key = key;
            refreshDerivedKey();
        }

        public static ChatKeyEntry empty() {
            return new ChatKeyEntry("", EncryptAlgorithm.NONE, "", "");
        }

        public ChatKeyEntry copy() {
            return new ChatKeyEntry(name, algorithm, phase, key);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public EncryptAlgorithm getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(EncryptAlgorithm algorithm) {
            this.algorithm = algorithm == null ? EncryptAlgorithm.NONE : algorithm;
            refreshDerivedKey();
        }

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase == null ? "" : phase;
            refreshDerivedKey();
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            if (phase != null && !phase.isEmpty()) {
                return;
            }
            this.key = key == null ? "" : key;
        }

        private void refreshDerivedKey() {
            if (phase == null) {
                phase = "";
            }
            if (phase.isEmpty() || algorithm == null || algorithm == EncryptAlgorithm.NONE) {
                if (key == null) {
                    key = "";
                }
                return;
            }
            key = algorithm.getEncryption().generateKey(phase);
        }
    }

    public static record KeyList(int selected, List<ChatKeyEntry> entries) {
        public static final Codec<KeyList> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("selected").forGetter(KeyList::selected),
                        Codec.list(ChatKeyEntry.CODEC).fieldOf("entries").forGetter(KeyList::entries))
                .apply(instance, KeyList::new));

        public ChatKeyEntry getSelected() {
            return selected >= 0 && selected < entries.size() ? entries.get(selected) : null;
        }
    }

    public interface Encryption {
        Encryptor getEncryptor(String key) throws Throwable;

        default String generateKey(String pass) {
            return pass;
        }
    }

    @Getter
    @AllArgsConstructor
    public static class AESEncryption implements Encryption {
        String mode;
        String padding;
        boolean initialVector;

        @Override
        public Encryptor getEncryptor(String key) throws Throwable {
            return new AESEncryptor(new SecretKeySpec(decodeBinaryKey(key), "AES"), this);
        }

        @Override
        public String generateKey(String key) {
            try {
                byte[] salt = new byte[16];
                new Random(1738389128127L).nextBytes(salt);
                KeySpec spec = new PBEKeySpec(key.toCharArray(), salt, 65536, 128);
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

    public interface UseIV {
        Pair<AlgorithmParameterSpec, byte[]> generateIV();

        Pair<AlgorithmParameterSpec, byte[]> splitIV(byte[] message);
    }

    public static final AESEncryption AES_CFB8_IMPL = new AESCFB8();
    public static final AESEncryption AES_GCM_IMPL = new AESGCM();
    public static final AESEncryption AES_ECB_IMPL = new AESEncryption("ECB", "PKCS5Padding", false);

    @Getter
    public enum EncryptAlgorithm implements ConfigEnum {
        NONE(message -> Encryptor.EMPTY),
        AES_CFB8(AES_CFB8_IMPL),
        AES_GCM(AES_GCM_IMPL),
        AES_ECB(AES_ECB_IMPL);

        private final Encryption encryption;

        EncryptAlgorithm(Encryption encryption) {
            this.encryption = encryption;
        }

        @Override
        public String getConfigEnumType() {
            return "encryptalgorithm";
        }

        @Override
        public net.minecraft.text.Text getDisplay() {
            return net.minecraft.text.Text.translatable(
                    "configenum.encryptalgorithm." + name().toLowerCase(Locale.ROOT));
        }
    }

    public interface Encryptor {
        Encryptor EMPTY = new Encryptor() {
            @Override
            public String encrypt(String message) {
                return message;
            }

            @Override
            public String decrypt(String message) {
                return message;
            }
        };

        String encrypt(String message);

        String decrypt(String message);
    }

    public static class AESEncryptor implements Encryptor {
        SecretKey key;
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
            } catch (InvalidAlgorithmParameterException e) {
                throw new RuntimeException(e);
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
                }
                return encodeBase64R(this.encryptor.doFinal(toBytes(message)));
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
                }
                return fromBytes(this.decryptor.doFinal(decodeBase64RBytes(message)));
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
}
