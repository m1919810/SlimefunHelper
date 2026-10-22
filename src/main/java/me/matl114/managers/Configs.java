package me.matl114.managers;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.ConfigLoader;
import me.matl114.plugins.SlimefunHelperPlugin;
import me.matl114.utils.config.NumberClamp;
import net.minecraft.util.Identifier;

public class Configs {

    public static void loadConfigs(SlimefunHelperPlugin plugin) {
        plugin.registerConfig(MINE_CONFIG);
        plugin.registerConfig(CHAT_CONFIG);
        plugin.registerConfig(RENDER_CONFIG);
        plugin.registerConfig(EXTRA_CONFIG);
        plugin.registerConfig(COMBAT_CONFIG);
        plugin.registerConfig(INV_CONFIG);
        plugin.registerConfig(MOV_CONFIG);
        plugin.registerConfig(INTERACT_CONFIG);
        plugin.registerConfig(SURVIVAL_CONFIG);
        plugin.registerConfig(SLIMEFUN_CONFIG);
        plugin.registerConfig(MODEL_CONFIG);
        plugin.registerConfig(MISC_CONFIG);
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

    public static NumberClamp<Integer> intRange(int min, int max) {
        return new NumberClamp<>(min, max);
    }

    public static NumberClamp<Integer> intHigher(int min) {
        return new NumberClamp<>(min, null);
    }

    public static NumberClamp<Integer> intLower(int mAX) {
        return new NumberClamp<>(null, mAX);
    }

    public static NumberClamp<Double> doubleRange(double min, double max) {
        Double min1 = min == Double.MIN_VALUE ? null : min;
        Double max1 = max == Double.MAX_VALUE ? null : max;
        return new NumberClamp<>(min1, max1);
    }

    public static final NumberClamp<Integer> INT_NONNEGATIVE = intHigher(0);
    public static final NumberClamp<Integer> INT_POSITIVE = intHigher(1);

    private static boolean init = false;

    public static final Config MINE_CONFIG =
            ConfigLoader.loadRuntimeConfig("mine.yml", "mine settings").markForSave();

    public static final Config CHAT_CONFIG =
            ConfigLoader.loadRuntimeConfig("chat.yml", "chat settings").markForSave();

    public static final Config RENDER_CONFIG =
            ConfigLoader.loadRuntimeConfig("render.yml", "render settings").markForSave();

    public static final Config EXTRA_CONFIG =
            ConfigLoader.loadRuntimeConfig("test.yml", "test settings").markForSave();

    public static final Config COMBAT_CONFIG =
            ConfigLoader.loadRuntimeConfig("combat.yml", "combat settings").markForSave();

    public static final Config INV_CONFIG =
            ConfigLoader.loadRuntimeConfig("inv.yml", "inv settings").markForSave();

    public static final Config MOV_CONFIG =
            ConfigLoader.loadRuntimeConfig("mov.yml", "mov settings").markForSave();

    public static final Config INTERACT_CONFIG =
            ConfigLoader.loadRuntimeConfig("interact.yml", "interact settings").markForSave();

    public static final Config SURVIVAL_CONFIG =
            ConfigLoader.loadRuntimeConfig("survival.yml", "survival settings").markForSave();

    public static final Config SLIMEFUN_CONFIG =
            ConfigLoader.loadRuntimeConfig("slimefun.yml", "slimefun settings").markForSave();

    public static final Config MODEL_CONFIG =
            ConfigLoader.loadRuntimeConfig("models.yml", "model settings").markForSave();

    public static final Config MISC_CONFIG =
            ConfigLoader.loadRuntimeConfig("misc.yml", "misc settings").markForSave();
}
