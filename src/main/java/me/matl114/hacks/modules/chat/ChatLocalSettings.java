package me.matl114.hacks.modules.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.Ref;
import me.matl114.managers.config.StringRef;
import me.matl114.managers.file.FileStorage;

public final class ChatLocalSettings {
    public static final String TITLE_TRANSLATION_KEY = "widget.chat.local-settings";

    private static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("chat-cache", "").forGetter(Data::chatCache),
                    Codec.BOOL.optionalFieldOf("password-encrypt", false).forGetter(Data::encryptPassword),
                    Codec.STRING.optionalFieldOf("password-encrypt-salt", "").forGetter(Data::passwordSalt))
            .apply(instance, Data::new));

    private static final FileStorage STORAGE = FileManager.getInstance().getInternalStorage("chat-local-settings.nbt");
    private static Data data = load();

    private ChatLocalSettings() {}

    public static String getChatCache() {
        return data.chatCache();
    }

    public static void setChatCache(String chatCache) {
        save(new Data(chatCache == null ? "" : chatCache, data.encryptPassword(), data.passwordSalt()));
    }

    public static boolean isPasswordEncryptionEnabled() {
        return data.encryptPassword();
    }

    public static void setPasswordEncryptionEnabled(boolean enabled) {
        save(new Data(data.chatCache(), enabled, data.passwordSalt()));
    }

    public static String getPasswordSalt() {
        return data.passwordSalt();
    }

    public static void setPasswordSalt(String passwordSalt) {
        save(new Data(data.chatCache(), data.encryptPassword(), passwordSalt == null ? "" : passwordSalt));
    }

    private static void save(Data newData) {
        data = newData;
        STORAGE.write(CODEC, data);
    }

    private static Data load() {
        Data defaultData = new Data("", false, "");
        if (STORAGE.getFile().exists()) {
            return STORAGE.read(CODEC, () -> defaultData);
        }

        Ref<?> cachedRef = Configs.CHAT_CONFIG.get("chat-screen-tools", "cached");
        Ref<?> encryptRef = Configs.CHAT_CONFIG.get("chat-screen-tools", "password-encrypt");
        Ref<?> saltRef = Configs.CHAT_CONFIG.get("chat-screen-tools", "password-encrypt-salt");
        if (cachedRef == null && encryptRef == null && saltRef == null) {
            return defaultData;
        }

        Data migrated = new Data(
                cachedRef instanceof StringRef ref ? ref.get() : "",
                encryptRef instanceof FlagRef ref && ref.get(),
                saltRef instanceof StringRef ref ? ref.get() : "");
        if (STORAGE.write(CODEC, migrated).isSuccess()) {
            if (cachedRef instanceof StringRef ref) ref.setValue("");
            if (encryptRef instanceof FlagRef ref) ref.setValue(false);
            if (saltRef instanceof StringRef ref) ref.setValue("");
        }
        return migrated;
    }

    private record Data(String chatCache, boolean encryptPassword, String passwordSalt) {}
}
