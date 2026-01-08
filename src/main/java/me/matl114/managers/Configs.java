package me.matl114.managers;

import me.matl114.SlimefunHelper;
import me.matl114.utils.Debug;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class Configs {
    //todo add schema for config
    public static void loadConfigs(){
        if(SlimefunHelper.HACK_VERSION){
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
        }else{
            CHAT_CONFIG.registerGlobal();
            INV_CONFIG.registerGlobal();
            HTTP_CONFIG.registerGlobal();
            SLIMEFUN_CONFIG.registerGlobal();
            MODEL_CONFIG.registerGlobal();
            HOTKEY_CONFIG.registerGlobal();
            TOGGLE_CONFIG.registerGlobal();
        }
        if(init){
            Config.reloadAll();
        }else {
            init=true;
        }

    }
    public enum LegalTargetingMode implements Config.ConfigEnum{
        DELAY_MOVEMENT,
        USEITEM_PACKET
        ;

        @Override
        public Text getDisplay() {
            return Text.translatable("configenum.legal-targeting-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }
    public enum BypassMode implements Config.ConfigEnum{
        NO_BYPASS,
        BYPASS_GRIM
        ;

        @Override
        public Text getDisplay() {
            return Text.translatable("configenum.bypass-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    public enum HttpProxyType implements Config.ConfigEnum{
        SOCKS,
        HTTP,
        HTTPS
        ;
        public Text getDisplay() {
            return Text.literal(name().toLowerCase(Locale.ROOT));
        }
    }
    static{
        //load Enums
        Config.ConfigEnum.register(LegalTargetingMode.class);
        Config.ConfigEnum.register(BypassMode.class);
        Config.ConfigEnum.register(HttpProxyType.class);
    }

    public static final Predicate<String> REGEX_VALIDATOR = x -> {
        try{
            Pattern.compile(x);
            return true;
        }catch (PatternSyntaxException | NullPointerException pse){
            return false;
        }
    };

    public static Predicate<Integer> intRange(int min, int max){
        return x -> x >= min && x <= max;
    }

    public static Predicate<Integer> intHigher(int min){
        return x -> x >= min;
    }

    public static Predicate<Integer> intLower(int mAX){
        return x -> x <= mAX;
    }

    public static Predicate<Double> doubleRange(double min, double max){
        return x -> x >= min && x <= max;
    }
    public static final Predicate<Integer> INT_POSITIVE = intHigher(0);



    private static boolean init=false;
    public static final String[] MINE_BOT_MINE_MIN_DY={"mine-bot","min-dy"};
    public static final String[] MINE_BOT_MINE_MAX_DY={"mine-bot","max-dy"};
    public static final String[] MINE_BOT_DOWN_PRIORITY={"mine-bot","y-low-first"};
    public static final String[] MINE_BOT_MAX_INSTANT_MINE={"mine-bot","max-instant-mine"};
    public static final String[] MINE_ENABLE_FAKE_INSTANT_BREAK={"fast-break","use-fake-instant-break"};

    public static final String[] MINE_BYPASS_FAST_BREAK_BYPASS_MODE = {"fast-break", "bypass-mode"};

    public static final String[] MINE_BOT_WHITELIST={"mine-bot","whitelist"};
    public static final String[] MINE_BOT_PACKET_MULTIPLE={"mine-bot","multiple-packets"};
    public static final String[] MINE_BOT_LEGAL_MODE = {"mine-bot", "legal-mode"};
    public static final String[] MINE_ONEBLOCK_PACKET_MULTIPLE={"mine-oneblock","multiple-packets"};
    public static final String[] MINE_FASTBREAK_THRESHOLD={"fast-break","break-threshold"};
    public static final String[] MINE_FASTBREAK_BREAKCOOLDOWN={"fast-break","break-cooldown"};
    public static final String[] MINE_FASTBREAK_REACH={"fast-break","reach-distance"};
    public static final String[] MINE_DOUBLE_BREAK = {"fast-break", "double-break"};
    public static final String[] MINE_BOT_RIGHT_CLICK={"mine-bot","right-click"};
    public static final String[] MINE_BOT_DURABILITY_PROTECT = {"mine-bot", "durability-protect"};
    public static final String[] MINE_FASTBREAK_SAME_BLOCK_OPTIMIZE = {"fast-break", "same-block-optimize"};
    public static final String[] MINE_RENDER_CURRENT_MINING_BLOCK = {"fast-break", "render-current-break-pos"};
    public static final String[] FAST_BREAK_GRIMAC_THRESHOLD = {"fast-break", "grim-punishment-threshold"};
    public static final String[] XRAY_ENABLE = {"aaxray", "enable"};
    public static final String[] XRAY_ENABLE_SIMPLE = {"aaxray", "enable-simple"};
    public static final String[] XRAY_ENABLE_SEED = {"aaxray", "enable-seed"};
    public static final String[] XRAY_ENABLE_SEED_RADIUS = {"aaxray", "seed-radius"};
    public static final String[] XRAY_RENDER_FAKE_ORE = {"aaxray", "render-seed-ore"};
    public static final String[] XRAY_MAKE_CLIENTSIDE_ORE = {"aaxray", "clientside-ore"};
    public static final String[] XRAY_OVERRIDE_SERVER_ORES = {"aaxray", "clientside-override-ores"};
    public static final String[] XRAY_ORE_TYPE = {"aaxray", "show-ore-type"};
    public static final String[] MINEARUA_WHILELIST = {"mine-arua", "block-whitelist"};

    public static final Config MINE_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/mine.yml","mine settings")
        .defaultVal(-1,MINE_BOT_MINE_MIN_DY)
        .defaultVal(6,MINE_BOT_MINE_MAX_DY)
        .defaultVal(false,MINE_BOT_DOWN_PRIORITY)
        .defaultVal(30,MINE_BOT_MAX_INSTANT_MINE)
        .defaultVal(true,MINE_ENABLE_FAKE_INSTANT_BREAK)
        .defaultVal(BypassMode.NO_BYPASS, MINE_BYPASS_FAST_BREAK_BYPASS_MODE)
        .defaultVal("^(cobblestone|stone|.*ore)$",MINE_BOT_WHITELIST)
        .validator(REGEX_VALIDATOR, MINE_BOT_WHITELIST)
        .defaultVal(5,MINE_BOT_PACKET_MULTIPLE)
        .defaultVal(1,MINE_ONEBLOCK_PACKET_MULTIPLE)
        .defaultVal(0.72d,MINE_FASTBREAK_THRESHOLD )
        .validator(doubleRange(0.0D, 1.01D), MINE_FASTBREAK_THRESHOLD)

        .defaultVal(0,MINE_FASTBREAK_BREAKCOOLDOWN)
        .defaultVal(5.5,MINE_FASTBREAK_REACH)
        .defaultVal(false, MINE_DOUBLE_BREAK)
        .defaultVal(false,MINE_BOT_RIGHT_CLICK)
        .defaultVal(false, XRAY_ENABLE)
        .defaultVal(false, XRAY_ENABLE_SEED)
        .defaultVal(false, XRAY_ENABLE_SIMPLE)
        .defaultVal(6, XRAY_ENABLE_SEED_RADIUS)
        .defaultVal(false, XRAY_RENDER_FAKE_ORE)
        .defaultVal(false, XRAY_MAKE_CLIENTSIDE_ORE)
        .defaultVal(false, MINE_BOT_DURABILITY_PROTECT)
        .defaultVal(false, XRAY_OVERRIDE_SERVER_ORES)
        .defaultVal("^(diamond)$", XRAY_ORE_TYPE)
        .validator(REGEX_VALIDATOR, XRAY_ORE_TYPE)
        .defaultVal(false, MINE_BOT_LEGAL_MODE)
        .defaultVal(false, MINE_FASTBREAK_SAME_BLOCK_OPTIMIZE)
        .defaultVal(false, MINE_RENDER_CURRENT_MINING_BLOCK)
        .defaultVal("^(.*bed)$", MINEARUA_WHILELIST)
        .validator(REGEX_VALIDATOR, MINEARUA_WHILELIST)
        .defaultVal(750, FAST_BREAK_GRIMAC_THRESHOLD)
        .save();
    public static final String[] CHAT_HELPER_CACHE={"chat-helper","cached"};
    public static final String[] CHAT_HELPER_PERIOD={"chat-helper","period"};
    public static final String[] CHAT_HELPER_MULTIPLE={"chat-helper","multiple"};
    public static final String[] CHAT_HELPER_SPECIALCHARS={"chat-helper","special-chars"};
    public static final String[] CHAT_HELPER_IGNORE_INPUT_LIMIT = {"chat-helper","ignore-chat-len-limit"};
    public static final String[] CHAT_HELPER_ESCAPE_TRIM = {"chat-helper","escape-trim-chat"};
    public static final String[] CHAT_HELPER_ESCAPE_NORMALIZE_SPACE = {"chat-helper", "escape-normalize-space-chat"};

    public static final String[] CHAT_HELPER_CLIENT_GIVE = {"chat-helper","client-side-give"};
    public static final String[] CHAT_HELPER_CHECK_MESSAGE_LENGTH = {"chat-helper","check-chat-len"};
    public static final String[] CHAT_HELPER_CHECK_COMMAND_LENGTH = {"chat-helper","check-command-len"};
    public static final String[] CHAT_HELPER_CHAT_HISTORY_LENGTH = {"chat-helper", "chat-history-len"};
    public static final String[] CHAT_HELPER_COMBINE_SAME_CHAT = {"chat-helper", "combine-same-chat"};
    public static final String[] CHAT_HELPER_ADD_HISTORY_WHE_CLOSE = {"chat-helper", "add-to-history-when-close"};
    public static final String[] CHAT_HELPER_DO_NOT_SEND_EMPTY_MESSAGE = {"chat-helper", "dont-send-empty-message"};
    public static final String[] CHAT_HELPER_CHAT_BOX_IN_GUI = {"chat-helper", "chat-box-in-gui"};
    public static final Config CHAT_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/chat.yml","chat settings")
        .defaultVal("",CHAT_HELPER_CACHE)
        .defaultVal(20,CHAT_HELPER_PERIOD)
        .defaultVal(1,CHAT_HELPER_MULTIPLE)
        .defaultVal("\uD83D\uDE21\uD83E\uDD13\uD83E\uDD75\uD83D\uDE2D\uD83E\uDD21\uD83D\uDE0B\uD83E\uDD24\uD83D\uDE0A\uD83D\uDE04\uD83E\uDD72\uD83D\uDE01\uD83D\uDC49\uD83D\uDC46\uD83E\uDD14\uD83D\uDE0E\uD83D\uDC0D\uD83D\uDE05♂♀",CHAT_HELPER_SPECIALCHARS)
        .defaultVal(true, CHAT_HELPER_IGNORE_INPUT_LIMIT)
        .defaultVal(false, CHAT_HELPER_ESCAPE_TRIM)
        .defaultVal(false, CHAT_HELPER_ESCAPE_NORMALIZE_SPACE)
        .defaultVal(false, CHAT_HELPER_CLIENT_GIVE)
        .defaultVal(256, CHAT_HELPER_CHECK_MESSAGE_LENGTH)
        .defaultVal(32760, CHAT_HELPER_CHECK_COMMAND_LENGTH)
        .defaultVal(-1, CHAT_HELPER_CHAT_HISTORY_LENGTH)
        .defaultVal(false, CHAT_HELPER_COMBINE_SAME_CHAT)
        .defaultVal(false, CHAT_HELPER_ADD_HISTORY_WHE_CLOSE)
        .defaultVal(true, CHAT_HELPER_DO_NOT_SEND_EMPTY_MESSAGE)
        .defaultVal(false, CHAT_HELPER_CHAT_BOX_IN_GUI)
        .save();

    public static final String[] RENDER_DETECT_SPAWN_WHITELIST={"detect-entity","spawn-whitelist"};
    public static final String[] RENDER_LOG_ON_SCREEN= {"detect-entity", "log-to-chat"};
    public static final String[] RENDER_RAYTRACE_ENTITY = {"detect-entity", "ray-trace-entity"};
    public static final String[] RENDER_ENTITY_HITBOX = {"detect-entity", "render-trace-hit-box"};
    public static final String[] RENDER_NO_EFFECT = {"render","no-effect"};
    public static final String[] RENDER_NIGHTVISION = {"render","nightvision"};
    public static final String[] RENDER_NO_EFFECT_FORCE = {"render","eff-setting","force-no"};
    public static final String[] RESOURCE_IGNORE_SERVER = {"resource","server","ignore-server-request"};
    public static final String[] CAL_FIREBALL_TRACE = {"detect-entity","cal-fireball"};
    public static final String[] CAL_PROJECTILE_TRACE = {"detect-entity","cal-projectile"};
    public static final String[] RENDER_FIREBALL_TRACE = {"detect-entity", "render-fireball"};
    public static final String[] RENDER_PROJECTILE_TRACE = {"detect-entity","render-projectile"};
    public static final String[] RENDER_REJECT_WURST = {"render","disable-wurst-hud"};
    public static final String[] RENDER_DETECT_PLAYER_IO = {"detect-entity", "log-player-io"};
    public static final String[] RENDER_ENHANCED_DEBUG_HUD = {"render", "enhanced-debug-hud"};
    public static final Config RENDER_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/render.yml","render settings")
        .defaultVal("player,wither",RENDER_DETECT_SPAWN_WHITELIST)
        .defaultVal(false, RENDER_LOG_ON_SCREEN)
        .defaultVal(false, RENDER_RAYTRACE_ENTITY)
        .defaultVal(false, RENDER_ENTITY_HITBOX)
        .defaultVal(true,RENDER_NO_EFFECT)
        .defaultVal(true,RENDER_NIGHTVISION)
        .defaultVal(false,RENDER_NO_EFFECT_FORCE)
        .defaultVal(false,RESOURCE_IGNORE_SERVER)
        .defaultVal(false,CAL_FIREBALL_TRACE)
        .defaultVal(false, CAL_PROJECTILE_TRACE)
        .defaultVal(false, RENDER_FIREBALL_TRACE)
        .defaultVal(false, RENDER_PROJECTILE_TRACE)
        .defaultVal(false,RENDER_REJECT_WURST)
        .defaultVal(false, RENDER_DETECT_PLAYER_IO)
        .defaultVal(true, RENDER_ENHANCED_DEBUG_HUD)
        .save();
    public static final String[] TEST_ARGS1={"test","arg1"};
    public static final String[] TEST_ARGS2={"test","arg2"};
    public static final String[] TEST_MOVEMENT_TEST = {"test", "movement-test-1"};
    public static final String[] CLIENT_BRAND_NAME ={"other", "client-brand-name"};
    public static final String[] IGNORE_PROTOCOL_ERROR = {
        "other", "no-disconnect-on-network-error"
    };
    public static final String[] PORTAL_GUI = {
        "other", "keep-gui-open-on-portal"
    };
    public static final String[] FAKE_SPRINT_TEST = {
        "test", "fake-sprint"
    };
    public static final Config TEST_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/test.yml","test settings")
        .defaultVal(480000,TEST_ARGS1)
        .defaultVal(461,TEST_ARGS2)
        .defaultVal(false, TEST_MOVEMENT_TEST)
        .defaultVal("", CLIENT_BRAND_NAME)
        .defaultVal(false, IGNORE_PROTOCOL_ERROR)
        .defaultVal(true, PORTAL_GUI)
        .defaultVal(false, FAKE_SPRINT_TEST)
        .save();

    public static final String[] COMBAT_INTERVEL={"attack","cancel-interval"};
    public static final String[] COMBAT_RIDING={"attack","riding-attack"};
    public static final String[] ATTACK_RANGE={"attack","att-range"};
    public static final String[] COMBAT_SHIELDING = {"attack","shielding-attack"};
    public static final String[] COMBAT_AUTOSHIELD = {"attack","no-grim-shield-setback"};
    public static final String[] ATTACK_WHITELISTED={"att-bot","whitelist"};
    public static final String[] ATTACK_PLAYER_FRIENDLIST = {"att-bot","friends"};
    public static final String[] ATTACK_NAMED = {"att-bot","att-named"};
    public static final String[] ATTACK_TEAMMATE = {"att-bot", "att-teammate"};
    public static final String[] AUTOATTACK_DO_INTERVEL_WEAPON = {"att-bot","respect-cooldown","weapon"};
    public static final String[] AUTOATTACK_DO_INTERVEL_HAND = { "att-bot","respect-cooldown","hand"};
    public static final String[] AUTOATTACK_ONCE_MAX = {"att-bot","max-at-once"};
    public static final String[] COMBAT_TP_REACH = {"att-bot", "tp-reach"};
    public static final String[] COMBAT_LEGAL_MOD = {"att-bot", "legal-mode"};
    public static final String[] COMBAT_MACE_HACK = {"att-bot", "mace-height-multiply"};
    public static final String[] COMBAT_OPPOSITE_ATTACK_MULTIPLY = {"att-bot", "opposite-attack-multiply"};
    public static final String[] COMBAT_PLAYER_ATTACK_MULTIPLY = {"att-bot", "player-attack-multiply"};
    public static final String[] COMBAT_EXACT_ATTACK = {"att-bot", "exact-tp"};
    public static final String[] COMBAT_EXACT_ATTACK_SHIELD = {"att-bot", "exact-tp-anti-shield"};
    public static final String[] COMBAT_LEGAL_TARGETTING = {"att-bot", "legal-targeting"};
    public static final String[] COMBAT_CRITIC = {"att-bot", "critic"};
    public static final String[] COMBAT_RENDER_TARGET = {"att-bot", "render-target"};
    public static final String[] COMBAT_BOW_TP_TOGGLE = {"projectile", "toggle-tp-accelerate"};
    public static final String[] COMBAT_PROJECTILE_TP = {"projectile", "tp-accelerate"};
    public static final String[] COMBAT_BOW_EXACT_TP = {"projectile", "tp-accelerate-exact-tp"};
    //this is a key error, don't worry about that
    public static final String[] COMBAT_BOW_AIM_LEGALLY = {"projectile", "bow-tp-legal"};
    public static final String[] COMBAT_BOW_TICKS_PREDICT = {"projectile", "bow-movement-tick-predict"};
    public static final String[] COMBAT_BOW_LEGAL_TARGETTING = {"projectile", "legal-targeting"};
    public static final String[] COMBAT_USE_ITEM_AUTOAIM = {"projectile", "use-item-auto-aim-whitelist"};
    public static final String[] COMBAT_PEARL_TP = {"projectile", "pearl-tp"};
    public static final String[] COMBAT_TRIDENT_AUTO_DUPE = {"projectile", "trident-auto-dupe"};
    public static final String[] COMBAT_PROJECTILE_USE_1_20_4_RULES = {"projectile", "version-lower-than-121"};
    public static final Config COMBAT_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/combat.yml","combat settings")
        .defaultVal(false,COMBAT_INTERVEL)
        .defaultVal(false,COMBAT_RIDING)
        .defaultVal("^(monster|!endermite)$",ATTACK_WHITELISTED)
        .validator(REGEX_VALIDATOR, ATTACK_WHITELISTED)
        .defaultVal("^(.*NPC.*|matl114)$",ATTACK_PLAYER_FRIENDLIST)
        .validator(REGEX_VALIDATOR, ATTACK_PLAYER_FRIENDLIST)
        .defaultVal(1.0f,ATTACK_RANGE)
        .defaultVal(true,COMBAT_SHIELDING)
        .defaultVal(true,AUTOATTACK_DO_INTERVEL_WEAPON)
        .defaultVal(false,AUTOATTACK_DO_INTERVEL_HAND)
        .defaultVal(false,ATTACK_NAMED)
        .defaultVal(true, ATTACK_TEAMMATE)
        .defaultVal(20,AUTOATTACK_ONCE_MAX)
        .defaultVal(true, COMBAT_LEGAL_MOD)
        .defaultVal(false, COMBAT_AUTOSHIELD)
        .defaultVal(0.0d, COMBAT_TP_REACH)
        .defaultVal(0.0d, COMBAT_MACE_HACK)
        .defaultVal(114514.0D, COMBAT_OPPOSITE_ATTACK_MULTIPLY)
        .defaultVal(0.0D, COMBAT_PLAYER_ATTACK_MULTIPLY)
        .defaultVal(false, COMBAT_EXACT_ATTACK)
        .defaultVal(false, COMBAT_EXACT_ATTACK_SHIELD)
        .defaultVal(false, COMBAT_CRITIC)
        .defaultVal(false, COMBAT_RENDER_TARGET)
        .defaultVal( 0.0D, COMBAT_PROJECTILE_TP)
        .defaultVal(false, COMBAT_BOW_EXACT_TP)
        .defaultVal(true, COMBAT_BOW_AIM_LEGALLY)
        .defaultVal(1.0D, COMBAT_BOW_TICKS_PREDICT)
        .defaultVal(LegalTargetingMode.DELAY_MOVEMENT, COMBAT_LEGAL_TARGETTING)
        .defaultVal(LegalTargetingMode.DELAY_MOVEMENT, COMBAT_BOW_LEGAL_TARGETTING)
        .defaultVal("^(LOGITECH_LASER_GUN)$", COMBAT_USE_ITEM_AUTOAIM)
        .validator(REGEX_VALIDATOR, COMBAT_USE_ITEM_AUTOAIM)
        .defaultVal(false, COMBAT_BOW_TP_TOGGLE)
        .defaultVal(false, COMBAT_PEARL_TP)
        .defaultVal(false, COMBAT_TRIDENT_AUTO_DUPE)
        .defaultVal(false, COMBAT_PROJECTILE_USE_1_20_4_RULES)
        .save();

    public static final String[] INV_CLICK_LIMIT={"inventory","packet-limit"};
    public static final String[] FAST_INV_DO_SHIFT = {"fastinv","apply-shift"};
    public static final String[] FAST_INV_DO_DROP ={"fastinv","apply-drop"};
    public static final Config INV_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/inv.yml","inv settings")
        .defaultVal(40,INV_CLICK_LIMIT)
        .defaultVal(true,FAST_INV_DO_SHIFT)
        .defaultVal(false,FAST_INV_DO_DROP)
        .save();
    public static final String[] MOV_MAX_DISTANCE = {"move-distance","max-distance"};
    public static final String[] MOVE_SPEED_OVERRIDE_WALK = {"move-speed","walk-speed-override"};
    public static final String[] MOVE_SPEED_OVERRIDE_FLY = {"move-speed","fly-speed-override"};
    public static final String[] MOVE_SPEED_WALK_VAL = {"move-speed","walk-speed"};
    public static final String[] MOVE_SPEED_FLY_VAL = {"move-speed","fly-speed"};
    public static final String[] MOVE_SPEED_FLY_VAL_CREATIVE = {"move-speed","fly-speed-creative"};
    public static final String[] QUICK_MOVE_IGNORE_COLLISION = {"quick-move","ignore-move-collision"};
   // public static final String[] MOVE_SPEED_NO_SLOW_DOWN = {"move-speed","no-slowdown"};
    public static final String[] MOVE_SPEED_NO_SLOW_DOWN_SNEAK = {"move-speed","no-slowdown", "when-sneak"};
    public static final String[] MOVE_SPEED_NO_SLOW_DOWN_USEITEM = {"move-speed","no-slowdown", "when-use-item"};
//    public static final String[] MOVE_SPEED_NO_SLOW_DOWN_BLOCK_FRAC = {"move-speed","no-slowdown", "when-walk-on-block"};
    public static final String[] MOVE_SPEED_NO_SLOW_DOWN_BLOCK_SLOW = {"move-speed","no-slowdown", "when-with-block"};
    public static final String[] MOVE_SPEED_NO_SLOW_DOWN_BLOCK_FRAC = {"move-speed","no-slowdown", "when-on-block"};
    public static final String[] MOVE_SPEED_NO_SLOW_DOWN_BLOCK_IN = {"move-speed","no-slowdown", "when-in-block"};
    public static final String[] MOVE_SPEED_NO_SLOW_DOWN_BLOCK_SPECIAL = {"move-speed","no-slowdown", "when-special-block"};
    public static final String[] MOVE_LOG_RESYNC_PACKETS = {"move-safety", "log-resync-packets"};
    public static final String[] MOVE_CHECK_SETBACK = {"move-safety","check-setback-packets"};
    public static final String[] MOVE_NOFALL = {"move-safety", "no-fall", "toggle"};
    public static final String[] MOVE_NOFALL_MODE = {"move-safety", "no-fall", "bypass-mode"};

    public static final String[] MOVE_COMPATE_HIGHER_VERSION = {"move-safety", "disable-stepheight-feature"};
    public static final String[] MOVE_AUTO_TOGGLE_SPRINT = {"move-speed","sprint", "legal-auto-sprint"};
    public static final String[] MOVE_ALL_DIRECTION_SPRINT = {"move-speed", "sprint", "all-direction-sprint"};
    public static final String[] MOVE_SPRINT_BYPASS_MODE = {"move-speed", "sprint", "bypass-mode"};

    public static final String[] MOVE_TICK_TIMER = {"move-speed", "timer"};
    public static final String[] MOVE_UNBREAKABLE_ELYTRA = {"move-safety", "unbreakable-elytra"};
    public static final String[] MOVE_ENHANCED_STEPHEIGHT = {"move-safety", "enhance-stepheight"};
    //todo test it in anticheat environment
    public static final String[] MOVE_DISABLE_SETBACK_VELOCITY_RESET = {"move-safety", "disable-setback-velocity-reset"};
    public static final String[] MOVE_LEGAL_MODE_MOVE_CORRECTION = {"move-safety", "legal-mode-move-correction"};
    public static final Config MOV_CONFIG =ConfigLoader.loadExternalConfig("sfhelper-configs/mov.yml","mov settings")
        .defaultVal(9.5d, MOV_MAX_DISTANCE)
        .defaultVal(false, MOVE_SPEED_OVERRIDE_WALK)
        .defaultVal(false, MOVE_SPEED_OVERRIDE_FLY)
        .defaultVal(0.1d, MOVE_SPEED_WALK_VAL)
        .defaultVal(0.8d, MOVE_SPEED_FLY_VAL_CREATIVE)
        .defaultVal(0.8d, MOVE_SPEED_FLY_VAL)
        .defaultVal(false,QUICK_MOVE_IGNORE_COLLISION)
        .defaultVal(false, MOVE_SPEED_NO_SLOW_DOWN_SNEAK)
        .defaultVal(false, MOVE_SPEED_NO_SLOW_DOWN_USEITEM)
//        .defaultVal(false, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_FRAC)
        .defaultVal(false, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_SLOW)
        .defaultVal(false, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_FRAC)
        .defaultVal(false, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_IN)
        .defaultVal(false, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_SPECIAL)
        .defaultVal(false, MOVE_CHECK_SETBACK)
        .defaultVal(false, MOVE_NOFALL)
        .defaultVal(BypassMode.NO_BYPASS, MOVE_NOFALL_MODE)
        .defaultVal(false, MOVE_COMPATE_HIGHER_VERSION)
        .defaultVal(false, MOVE_AUTO_TOGGLE_SPRINT)
        .defaultVal(false, MOVE_ALL_DIRECTION_SPRINT)
        .defaultVal(BypassMode.NO_BYPASS, MOVE_SPRINT_BYPASS_MODE)
        .defaultVal(0, MOVE_TICK_TIMER)
        .defaultVal(false, MOVE_UNBREAKABLE_ELYTRA)
        .defaultVal(false, MOVE_LOG_RESYNC_PACKETS)
        .defaultVal(false, MOVE_ENHANCED_STEPHEIGHT)
        .defaultVal(false, MOVE_DISABLE_SETBACK_VELOCITY_RESET)
        //todo need test
        .defaultVal(true, MOVE_LEGAL_MODE_MOVE_CORRECTION)
        .save();
    public static final String[] HTTP_PROXY_SERVER = {"proxy-server","host"};
    public static final String[] HTTP_PROXY_PORT = {"proxy-server", "port"};
    public static final String[] HTTP_PROXY_ENABLE = {"proxy-server","enable"};
    public static final String[] HTTP_PROXY_USERNAME ={"proxy-server","username"};
    public static final String[] HTTP_PROXY_OPTIONAL_PASSWORD = {"proxy-server","password?"};
    public static final String[] HTTP_PROXY_TYPE = {"proxy-server","type"};
    public static final Config HTTP_CONFIG = ConfigLoader.loadExternalConfig(
        "sfhelper-configs/http.yml","http settings"
    )
        .defaultVal("127.0.0.1",HTTP_PROXY_SERVER)
        .defaultVal(-1,HTTP_PROXY_PORT)
        .defaultVal(false,HTTP_PROXY_ENABLE)
        .defaultVal("",HTTP_PROXY_USERNAME)
        .defaultVal("",HTTP_PROXY_OPTIONAL_PASSWORD)
        .defaultVal(HttpProxyType.SOCKS, HTTP_PROXY_TYPE)
        .save();

    public static final String[] INTERACT_NO_COOLDOWN = {"interact-fix", "cool-down-rewrite"};
    public static final String[] INTERACT_WHEN_RIDING = {"interact-fix", "allow-ride-interact"};
    public static final String[] INTERACT_SCAFFOLD_LEGAL = {"interact-scaffold", "legal-mode"};
    public static final String[] INTERACT_SCAFFOLD_TARGET_MODE = {"interact-scaffold", "legal-targeting"};
    public static final String[] INTERACT_SCAFFOLD_COOLDOWN_OVERRIDE = {"interact-scaffold", "scaffold-cooldown-override"};
    public static final Config INTERACT_CONFIG = ConfigLoader.loadExternalConfig(
            "sfhelper-configs/interact.yml","interact settings"
    )
        .defaultVal(-1, INTERACT_NO_COOLDOWN)
        .defaultVal(false, INTERACT_WHEN_RIDING)
        //todo track to preset command
        .defaultVal(false, INTERACT_SCAFFOLD_LEGAL)
        .defaultVal(LegalTargetingMode.DELAY_MOVEMENT, INTERACT_SCAFFOLD_TARGET_MODE)
        .defaultVal(-1, INTERACT_SCAFFOLD_COOLDOWN_OVERRIDE)
        //
        .save();


    public static final String[] SLIMEFUN_RECIPE_RECORD = {"recipe-record","enable"};
    public static final String[] SLIMEFUN_RECIPE_LOCKED = {"recipe-record", "lock-current-data"};
    public static final String[] SLIMEFUN_RECIPE_SAVE = {"recipe-record","save-data"};
    public static final String[] SLIMEFUN_RECIPE_TITLE = {"recipe-record","rp-title"};
    public static final String[] SLIMEFUN_MULTIBLOCK_MATCHER = {"recipe-record", "multiblock-pattern"};
    public static final String[] SLIMEFUN_LOCK_EXISTING = {"recipe-record", "lock-existing"};
    public static final String[] SLIMEFUN_MULTIBLOCK_CLICKER = {"multi-block-clicker","enable"};
    public static final String[] SLIMEFUN_MB_RATE = {"multi-block-clicker","rate"};
    public static final String[] SLIMEFUN_MB_LEGAL = {"multi-block-clicker","bypass-anticheat"};
    public static final String[] SLIMEFUN_MB_LEGAL_MODE = {"multi-block-clicker", "bypass-targeting-mode"};
//    public static final String[] SLIMEFUN_MATCH_UP_AND_DOWN = {"multi-block-clicker","only-when-at-middle"};
//    public static final String[] SLIMEFUN_AUTO_CLICK_FACING = {"multi-block-clicker","auto-click-facing"};
    public static final Config SLIMEFUN_CONFIG = ConfigLoader.loadExternalConfig(
        "sfhelper-configs/slimefun.yml", "slimefun settings"
    )
        .defaultVal(false, SLIMEFUN_RECIPE_RECORD)
        .defaultVal(false, SLIMEFUN_RECIPE_LOCKED)
        .defaultVal(true, SLIMEFUN_RECIPE_SAVE)
        .defaultVal("^(Slimefun 指南.*)$", SLIMEFUN_RECIPE_TITLE)
        .validator(REGEX_VALIDATOR, SLIMEFUN_RECIPE_TITLE)
        .defaultVal("^(多方块结构|MultiBlock)$", SLIMEFUN_MULTIBLOCK_MATCHER)
        .validator(REGEX_VALIDATOR, SLIMEFUN_MULTIBLOCK_MATCHER)
        .defaultVal(false, SLIMEFUN_MULTIBLOCK_CLICKER)
        .defaultVal(12, SLIMEFUN_MB_RATE)
//        .defaultVal(true, SLIMEFUN_MATCH_UP_AND_DOWN)
//        .defaultVal(false,SLIMEFUN_AUTO_CLICK_FACING)
        .defaultVal(false, SLIMEFUN_MB_LEGAL)
        .defaultVal(LegalTargetingMode.DELAY_MOVEMENT, SLIMEFUN_MB_LEGAL_MODE)
        .defaultVal(false, SLIMEFUN_LOCK_EXISTING)
        .save();
    public static final String[] SEED_MAP = new String[]{"seed", "seed-cache"};
    public static final Config INTERNAL_CONFIG = ConfigLoader.loadExternalConfig("sfhelper-configs/internal.yml", "internal settings")
        .defaultVal("{}", SEED_MAP)
        .save()
        ;

    public static final String[] MODEL_PROTECT = {"model-config", "enable-block-model-protect"};
    public static final String[] SLIMEFUN_MODEL_ID = {"model-config", "enable-slimefun-cmd-override"};
    public static final String[] ITEM_MODEL_OVERRIDE = {"model-config", "enable-item-model-override"};
    public static final String[] ENABLE_STORAGE_DISPLAY = {"model-config", "enable-storage-display"};
    public static final String[] ENABLE_SF_TOOLTIPS = {"model-config", "enable-tooltips-display"};
    public static final String[] CUSTOM_TEXTURE_PATTERN = {"slimefun-models", "namespace-for-slimefun-textures"};
    public static final String[] AUTO_MODEL_PATTERN = {"slimefun-models", "path-pattern-for-slimefun-model"};
    public static final Config MODEL_CONFIG = ConfigLoader.loadExternalConfig("sfhelper-configs/models.yml", "model settings")
        .defaultVal(true, MODEL_PROTECT)
        .defaultVal(true, SLIMEFUN_MODEL_ID)
        .defaultVal(true, ITEM_MODEL_OVERRIDE)
        .defaultVal(true, ENABLE_STORAGE_DISPLAY)
        .defaultVal(true, ENABLE_SF_TOOLTIPS)
        .defaultVal(List.of("ae2", "slimefunhelper", "infinityexpansion", "avaritia"), CUSTOM_TEXTURE_PATTERN)
        .defaultVal(List.of("^slimefunhelper:slimefunitem/.*$", "^slimefunhelper:test/.*$"), AUTO_MODEL_PATTERN)
        .<List<String>>validator(s -> {
            try{
                String pattern = s.stream().map(i->"("+i+")").collect(Collectors.joining("|"));
                Pattern.compile(pattern);
                return true;
            }catch (Throwable e){
                return false;
            }
        }, AUTO_MODEL_PATTERN)
        .save()
        ;


    public static final String[] HOTKEY_WORKS_ONLY_WHEN_NOT_AT_SCREEN = new String[]{"hotkey-settings", "only-works-if-no-screen"};
    public static final Config HOTKEY_CONFIG = ConfigLoader.loadExternalConfig("sfhelper-configs/hotkeys.yml", "hotkey settings")
        .defaultVal(true, HOTKEY_WORKS_ONLY_WHEN_NOT_AT_SCREEN)
        .save()
        ;
    //TODO: add other-hotkeys
    //TODO: add Shulker display and shulker storage display
    //TODO: remove recipe display
    //TODO: add entity inspect in info command
    //TODO: add thread check or add runInMain in ApiMethod
    //TODO: parser system
    //TODO: item editor template
    //TODO: !!travel add arguments
    //
    public static final Config TOGGLE_CONFIG = ConfigLoader.loadExternalConfig("sfhelper-configs/toggles.yml", "toggle settings")
        .save();
    static {
        MODEL_CONFIG.getList(AUTO_MODEL_PATTERN).getElementValidator().add(REGEX_VALIDATOR);
        try{
            File file =  FabricLoader.getInstance().getConfigDir().resolve("slimefunhelper-config.yml").toFile();
            if(file.exists() && file.isFile()){
                try(var fileReader =  new FileReader(file)){
                    var obj = (Map)new Yaml().load(fileReader);
                    if(obj.containsKey("namespace-for-slimefun-texture")){
                        List<String> list = (List)obj.get("namespace-for-slimefun-texture");
                        MODEL_CONFIG.setValue(list, CUSTOM_TEXTURE_PATTERN);
                    }
                    if (obj.containsKey("path-pattern-for-slimefun-model")){
                        List<String> list = (List<String>)obj.get("path-pattern-for-slimefun-model");
                        MODEL_CONFIG.setValue(list ,AUTO_MODEL_PATTERN);
                    }
                    MODEL_CONFIG.save();
                }finally {
                    file.delete();
                }
            }
        }catch (Throwable e){

        }
    }
}
