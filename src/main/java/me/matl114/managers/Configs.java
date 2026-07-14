package me.matl114.managers;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.File;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.ConfigLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

public class Configs {
    public static void loadConfigs() {
        if (true) {
            MINE_CONFIG.registerGlobal();
            CHAT_CONFIG.registerGlobal();
            RENDER_CONFIG.registerGlobal();
            TEST_CONFIG.registerGlobal();
            COMBAT_CONFIG.registerGlobal();
            INV_CONFIG.registerGlobal();
            MOV_CONFIG.registerGlobal();
            INTERACT_CONFIG.registerGlobal();
            SLIMEFUN_CONFIG.registerGlobal();
            MODEL_CONFIG.registerGlobal();
            MISC_CONFIG.registerGlobal();
            TOGGLE_CONFIG.registerGlobal();
        } else {
            CHAT_CONFIG.registerGlobal();
            INV_CONFIG.registerGlobal();
            SLIMEFUN_CONFIG.registerGlobal();
            MODEL_CONFIG.registerGlobal();
            MISC_CONFIG.registerGlobal();
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
        // PRE_MOVEMENT,
        // USEITEM_PACKET,
        LEGACY_SLIENT_ROT;

        public boolean isMovement() {
            return this == LegalTargetingMode.DELAY_MOVEMENT; // || this == LegalTargetingMode.PRE_MOVEMENT;
        }

        public boolean isLegacy() {
            return this == LegalTargetingMode.LEGACY_SLIENT_ROT;
        }

        @Override
        public String getConfigEnumType() {
            return "legal_targeting_mode";
        }
    }

    public enum LegalInteractMode implements ConfigEnum {
        NONE,
        MOVEMENT_POST,
        DELAY_MOVEMENT,
        USEITEM_PACKET,
        LEGACY_SLIENT_ROT;
        ;

        public static LegalInteractMode getFromPreset(ModulePreset preset) {
            return switch (preset) {
                case HACKING, VANILLA -> Configs.LegalInteractMode.NONE;
                case AC_GRIM_LEGACY -> Configs.LegalInteractMode.LEGACY_SLIENT_ROT;
                default -> Configs.LegalInteractMode.DELAY_MOVEMENT;
            };
        }

        @Override
        public String getConfigEnumType() {
            return "legal_interact_mode";
        }

        public boolean isLegal() {
            return this != NONE;
        }

        public boolean canMultiRotPlace() {
            return this == NONE || this == LEGACY_SLIENT_ROT;
        }
    }

    public enum BypassMode implements ConfigEnum {
        NO_BYPASS,
        BYPASS_GRIM;

        public static BypassMode getFromPreset(ModulePreset preset) {
            return switch (preset) {
                case AC_GRIM_LEGACY, AC_GRIM -> BYPASS_GRIM;
                default -> NO_BYPASS;
            };
        }

        public boolean hasAc() {
            return this != NO_BYPASS;
        }

        @Override
        public String getConfigEnumType() {
            return "bypass_mode";
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
        public String getConfigEnumType() {
            return "mine_targeting_mode";
        }
    }

    public enum AutoInvMode implements ConfigEnum {
        LAZY,
        TICK;

        @Override
        public String getConfigEnumType() {
            return "auto_inv_mode";
        }
    }

    public enum SetBackTriggerType implements ConfigEnum {
        SIMULATION,
        CRASH_PACKETS;

        @Override
        public String getConfigEnumType() {
            return "setback_trigger_type";
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

    public static final Config MOV_CONFIG = ConfigLoader.loadExternalConfig("sfhelper-configs/mov.yml", "mov settings")
            .markForSave();

    public static final Config INTERACT_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/interact.yml", "interact settings")
            .markForSave();

    public static final Config SLIMEFUN_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/slimefun.yml", "slimefun settings")
            .markForSave();

    public static final Config MODEL_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/models.yml", "model settings")
            .markForSave();

    static {
        final File cfgFile = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("sfhelper-configs/hotkeys.yml")
                .toFile();
        File tgtFile = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("sfhelper-configs/misc.yml")
                .toFile();
        if (cfgFile.exists() && !tgtFile.exists()) {
            cfgFile.renameTo(tgtFile);
        }
        File internalFile = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("sfhelper-configs/internal.yml")
                .toFile();
        if (internalFile.exists()) {
            internalFile.delete();
        }
        internalFile = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("sfhelper-configs/http.yml")
                .toFile();
        if (internalFile.exists()) {
            internalFile.delete();
        }
    }

    public static final Config MISC_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/misc.yml", "misc settings")
            .markForSave();

    public static final Config TOGGLE_CONFIG = ConfigLoader.loadExternalConfig(
                    "sfhelper-configs/toggles.yml", "toggle settings")
            .markForSave();
}
