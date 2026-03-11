package me.matl114.managers;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.ConfigLoader;
import net.minecraft.registry.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class Configs {
    // todo add schema for config
    public static void loadConfigs() {
        if (true) {
            MINE_CONFIG.registerGlobal();
            CHAT_CONFIG.registerGlobal();
            RENDER_CONFIG.registerGlobal();
            TEST_CONFIG.registerGlobal();
            COMBAT_CONFIG.registerGlobal();
            INV_CONFIG.registerGlobal();
            MOV_CONFIG.registerGlobal();
            HTTP_CONFIG.registerGlobal();
            INTERACT_CONFIG.registerGlobal();
            SLIMEFUN_CONFIG.registerGlobal();
            MODEL_CONFIG.registerGlobal();
            HOTKEY_CONFIG.registerGlobal();
            TOGGLE_CONFIG.registerGlobal();
        } else {
            CHAT_CONFIG.registerGlobal();
            INV_CONFIG.registerGlobal();
            HTTP_CONFIG.registerGlobal();
            SLIMEFUN_CONFIG.registerGlobal();
            MODEL_CONFIG.registerGlobal();
            HOTKEY_CONFIG.registerGlobal();
            TOGGLE_CONFIG.registerGlobal();
        }
        if (init) {
            Config.reloadAll();
        } else {
            init = true;
        }
    }

    public enum LegalTargetingMode implements ConfigEnum {
        DELAY_MOVEMENT,
        USEITEM_PACKET;

        @Override
        public Text getDisplay() {
            return Text.translatable(
                    "configenum.legal-targeting-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    public enum LegalInteractMode implements ConfigEnum {
        MOVEMENT,
        DELAY_MOVEMENT,
        USEITEM_PACKET;

        @Override
        public Text getDisplay() {
            return Text.translatable(
                    "configenum.legal-interact-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    public enum BypassMode implements ConfigEnum {
        NO_BYPASS,
        BYPASS_GRIM;

        public boolean hasAc() {
            return this != NO_BYPASS;
        }

        @Override
        public Text getDisplay() {
            return Text.translatable("configenum.bypass-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    public enum MineTargetingMode implements ConfigEnum {
        NO_BYPASS,
        SWING_HAND,
        SWING_HAND_AND_ROT,
        SWING_HAND_AND_TARGET;

        public boolean hasSwing() {
            return this != NO_BYPASS;
        }

        @Override
        public Text getDisplay() {
            return Text.translatable(
                    "configenum.mine-targeting-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    public enum AutoInvMode implements ConfigEnum {
        LAZY,
        TICK;

        public Text getDisplay() {
            return Text.translatable("configenum.auto-inv-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    static {
        // load Enums

    }

    public static final Predicate<String> REGEX_VALIDATOR = x -> {
        try {
            Pattern.compile(x);
            return true;
        } catch (PatternSyntaxException | NullPointerException pse) {
            return false;
        }
    };

    public static final Predicate<String> JSON_VALIDATOR = x -> {
        try {
            JsonParser.parseString(x);
            return true;
        } catch (JsonParseException | NullPointerException pse) {
            return false;
        }
    };

    public static final Predicate<String> IDENTIFIER_VALIDATOR = x -> {
        try {
            Objects.requireNonNull(Identifier.tryParse(x));
            return true;
        } catch (Throwable e) {
            return false;
        }
    };

    public static Predicate<Integer> intRange(int min, int max) {
        return x -> x >= min && x <= max;
    }

    public static Predicate<Integer> intHigher(int min) {
        return x -> x >= min;
    }

    public static Predicate<Integer> intLower(int mAX) {
        return x -> x <= mAX;
    }

    public static Predicate<Double> doubleRange(double min, double max) {
        return x -> x >= min && x <= max;
    }

    public static final Predicate<Integer> INT_NONNEGATIVE = intHigher(0);
    public static final Predicate<Integer> INT_POSITIVE = intHigher(1);

    private static boolean init = false;

    public static final Config MINE_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/mine.yml", "mine settings")
            .markForSave();

    public static final Config CHAT_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/chat.yml", "chat settings")
            .markForSave();

    public static final Config RENDER_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/render.yml", "render settings")
            .markForSave();

    public static final Config TEST_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/test.yml", "test settings")
            .markForSave();

    public static final Config COMBAT_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/combat.yml", "combat settings")
            .markForSave();

    public static final Config INV_CONFIG = ConfigLoader.loadExternalConfig("sfhelper-configs/inv.yml", "inv settings")
            .markForSave();

    // public static final String[] MOVE_SPEED_NO_SLOW_DOWN = {"move-speed","no-slowdown"};

    // todo: add move safety tp y limit , later

    // todo test it in anticheat environment

    public static final Config MOV_CONFIG = ConfigLoader.loadExternalConfig("sfhelper-configs/mov.yml", "mov settings")
            .markForSave();

    public static final Config HTTP_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/http.yml", "http settings")
            .markForSave();

    public static final Config INTERACT_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/interact.yml", "interact settings")
            .markForSave();

    //    public static final String[] SLIMEFUN_MATCH_UP_AND_DOWN = {"multi-block-clicker","only-when-at-middle"};
    //    public static final String[] SLIMEFUN_AUTO_CLICK_FACING = {"multi-block-clicker","auto-click-facing"};
    public static final Config SLIMEFUN_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/slimefun.yml", "slimefun settings")
            .markForSave();
    public static final Config INTERNAL_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/internal.yml", "internal settings")
            .markForSave();

    public static final Config MODEL_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/models.yml", "model settings")
            .markForSave();

    public static final String[] HOTKEY_WORKS_ONLY_WHEN_NOT_AT_SCREEN =
            new String[] {"hotkey-settings", "only-works-if-no-screen"};
    // todo: change to config mapping
    public static final Config HOTKEY_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/hotkeys.yml", "hotkey settings")
            .markForSave();

    static {
        HOTKEY_CONFIG
                .builder(Boolean.class)
                .path(HOTKEY_WORKS_ONLY_WHEN_NOT_AT_SCREEN)
                .defaultValue(true)
                .build();
    }
    // TODO: add other-hotkeys
    // TODO: add Shulker display and shulker storage display
    // TODO: remove recipe display
    // TODO: add entity inspect in info command
    // TODO: add thread check or add runInMain in ApiMethod
    // TODO: parser system
    // TODO: item editor template
    // TODO: !!travel add arguments
    //

    public static final Config TOGGLE_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/toggles.yml", "toggle settings")
            .markForSave();
}
