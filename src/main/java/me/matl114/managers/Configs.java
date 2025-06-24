package me.matl114.managers;

import me.matl114.SlimefunHelper;

public class Configs {
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
        }else{
            CHAT_CONFIG.registerGlobal();
            INV_CONFIG.registerGlobal();
            HTTP_CONFIG.registerGlobal();
            SLIMEFUN_CONFIG.registerGlobal();
        }
        if(init){
            Config.reloadAll();
        }else {
            init=true;
        }

    }
    private static boolean init=false;
    public static final String MINE_BOT_MINE_ABOVE="mine-bot-only-mine-above";
    public static final String MINE_BOT_MAX_PER_TICK="mine-bot-max-per-tick";
    //public static final String[] MINE_BOT_ONLY_MINE_ABOVE={"mine-bot","only-mine-above"};
    public static final String[] MINE_BOT_MINE_MIN_DY={"mine-bot","min-dy"};
    public static final String[] MINE_BOT_MINE_MAX_DY={"mine-bot","max-dy"};
    public static final String[] MINE_BOT_DOWN_PRIORITY={"mine-bot","y-low-first"};
    public static final String[] MINE_BOT_MAX_INSTANT_MINE={"mine-bot","max-instant-mine"};
    public static final String[] MINE_ENABLE_FAKE_INSTANT_BREAK={"fast-break","use-fake-instant-break"};
    public static final String[] MINE_BOT_WHITELIST={"mine-bot","whitelist"};
    public static final String[] MINE_BOT_PACKET_MULTIPLE={"mine-bot","multiple-packets"};
    public static final String[] MINE_ONEBLOCK_PACKET_MULTIPLE={"mine-oneblock","multiple-packets"};
    public static final String[] MINE_FASTBREAK_THRESHOLD={"fast-break","break-threshold"};
    public static final String[] MINE_FASTBREAK_BREAKCOOLDOWN={"fast-break","break-cooldown"};
    public static final String[] MINE_FASTBREAK_REACH={"fast-break","reach-distance"};
    public static final String[] MINE_BOT_RIGHT_CLICK={"mine-bot","right-click"};
    public static final Config MINE_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/mine.yml","mine settings")
        .defaultVal(-1,MINE_BOT_MINE_MIN_DY)
        .defaultVal(6,MINE_BOT_MINE_MAX_DY)
        .defaultVal(false,MINE_BOT_DOWN_PRIORITY)
        .defaultVal(30,MINE_BOT_MAX_INSTANT_MINE)
        .defaultVal(true,MINE_ENABLE_FAKE_INSTANT_BREAK)
        .defaultVal("^(cobblestone|stone|.*ore)$",MINE_BOT_WHITELIST)
        .defaultVal(5,MINE_BOT_PACKET_MULTIPLE)
        .defaultVal(1,MINE_ONEBLOCK_PACKET_MULTIPLE)
        .defaultVal(0.72d,MINE_FASTBREAK_THRESHOLD )
        .defaultVal(0,MINE_FASTBREAK_BREAKCOOLDOWN)
        .defaultVal(5.5,MINE_FASTBREAK_REACH)
        .defaultVal(false,MINE_BOT_RIGHT_CLICK)
        .save();
    public static final String[] CHAT_HELPER_CACHE={"chat-helper","cached"};
    public static final String[] CHAT_HELPER_PERIOD={"chat-helper","period"};
    public static final String[] CHAT_HELPER_MULTIPLE={"chat-helper","multiple"};
    public static final String[] CHAT_HELPER_SPECIALCHARS={"chat-helper","special-chars"};
    public static final String[] CHAT_HELPER_IGNORE_INPUT_LIMIT = {"chat-helper","ignore-chat-len-limit"};
    public static final String[] CHAT_HELPER_CLIENT_GIVE = {"chat-helper","client-side-give"};
    public static final String[] CHAT_HELPER_CHECK_MESSAGE_LENGTH = {"chat-helper","check-chat-len"};
    public static final String[] CHAT_HELPER_CHECK_COMMAND_LENGTH = {"chat-helper","check-command-len"};
    public static final Config CHAT_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/chat.yml","chat settings")
        .defaultVal("",CHAT_HELPER_CACHE)
        .defaultVal(20,CHAT_HELPER_PERIOD)
        .defaultVal(1,CHAT_HELPER_MULTIPLE)
        .defaultVal("\uD83D\uDE21\uD83E\uDD13\uD83E\uDD75\uD83D\uDE2D\uD83E\uDD21\uD83D\uDE0B\uD83E\uDD24\uD83D\uDE0A\uD83D\uDE04\uD83E\uDD72\uD83D\uDE01\uD83D\uDC49\uD83D\uDC46\uD83E\uDD14\uD83D\uDE0E\uD83D\uDC0D\uD83D\uDE05♂♀",CHAT_HELPER_SPECIALCHARS)
        .defaultVal(true, CHAT_HELPER_IGNORE_INPUT_LIMIT)
        .defaultVal(false, CHAT_HELPER_CLIENT_GIVE)
        .defaultVal(256, CHAT_HELPER_CHECK_MESSAGE_LENGTH)
        .defaultVal(32760, CHAT_HELPER_CHECK_COMMAND_LENGTH)
        .save();

    public static final String[] RENDER_DETECT_SPAWN_WHITELIST={"detect-entity","spawn-whitelist"};
    public static final String[] RENDER_NO_EFFECT = {"render","no-effect"};
    public static final String[] RENDER_NIGHTVISION = {"render","nightvision"};
    public static final String[] RENDER_NO_EFFECT_FORCE = {"render","eff-setting","force-no"};
    public static final String[] RESOURCE_IGNORE_SERVER = {"resource","server","ignore-server-request"};
    public static final String[] CAL_FIREBALL_TRACE = {"detect-entity","cal-fireball"};
    public static final Config RENDER_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/render.yml","render settings")
        .defaultVal("player,wither",RENDER_DETECT_SPAWN_WHITELIST)
        .defaultVal(true,RENDER_NO_EFFECT)
        .defaultVal(true,RENDER_NIGHTVISION)
        .defaultVal(false,RENDER_NO_EFFECT_FORCE)
        .defaultVal(false,RESOURCE_IGNORE_SERVER)
        .defaultVal(true,CAL_FIREBALL_TRACE)
        .save();
    public static final String[] TEST_ARGS1={"test","arg1"};
    public static final String[] TEST_ARGS2={"test","arg2"};
    public static final Config TEST_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/test.yml","test settings")
        .defaultVal(480000,TEST_ARGS1)
        .defaultVal(461,TEST_ARGS2)
        .save();

    public static final String[] COMBAT_INTERVEL={"attack","cancel-interval"};
    public static final String[] COMBAT_RIDING={"attack","riding-attack"};
    public static final String[] ATTACK_RANGE={"attack","att-range"};
    public static final String[] COMBAT_SHIELDING = {"attack","shielding-attack"};
    public static final String[] ATTACK_WHITELISTED={"att-bot","whitelist"};
    public static final String[] ATTACK_PLAYER_FRIENDLIST = {"att-bot","friends"};
    public static final String[] ATTACK_NAMED = {"att-bot","att-named"};
    public static final String[] AUTOATTACK_DO_INTERVEL_WEAPON = {"att-bot","respect-cooldown","weapon"};
    public static final String[] AUTOATTACK_DO_INTERVEL_HAND = { "att-bot","respect-cooldown","hand"};
    public static final String[] AUTOATTACK_ONCE_MAX = {"att-bot","max-at-once"};
    public static final Config COMBAT_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/combat.yml","combat settings")
        .defaultVal(false,COMBAT_INTERVEL)
        .defaultVal(false,COMBAT_RIDING)
        .defaultVal("^(monster|!endermite)$",ATTACK_WHITELISTED)
        .defaultVal("^(.*NPC.*|matl114)$",ATTACK_PLAYER_FRIENDLIST)
        .defaultVal(6.0f,ATTACK_RANGE)
        .defaultVal(true,COMBAT_SHIELDING)
        .defaultVal(true,AUTOATTACK_DO_INTERVEL_WEAPON)
        .defaultVal(false,AUTOATTACK_DO_INTERVEL_HAND)
        .defaultVal(false,ATTACK_NAMED)
        .defaultVal(20,AUTOATTACK_ONCE_MAX)
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
    public static final String[] MOVE_SPEED_NO_SLOW_DOWN = {"move-speed","no-slowdown"};
    public static final Config MOV_CONFIG =ConfigLoader.loadExternalConfig("sfhelper-configs/mov.yml","mov settings")
        .defaultVal(9.5d, MOV_MAX_DISTANCE)
        .defaultVal(false, MOVE_SPEED_OVERRIDE_WALK)
        .defaultVal(false, MOVE_SPEED_OVERRIDE_FLY)
        .defaultVal(0.1d, MOVE_SPEED_WALK_VAL)
        .defaultVal(0.8d, MOVE_SPEED_FLY_VAL_CREATIVE)
        .defaultVal(0.8d, MOVE_SPEED_FLY_VAL)
        .defaultVal(false,QUICK_MOVE_IGNORE_COLLISION)
        .defaultVal(false, MOVE_SPEED_NO_SLOW_DOWN)
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
        .defaultVal("socks",HTTP_PROXY_TYPE)
        .save();

    public static final String[] INTERACT_NO_COOLDOWN = {"interact-fix", "cool-down-rewrite"};
    public static final String[] INTERACT_WHEN_RIDING = {"interact-fix", "allow-ride-interact"};
    public static final Config INTERACT_CONFIG = ConfigLoader.loadExternalConfig(
            "sfhelper-configs/interact.yml","interact settings"
    )
        .defaultVal(-1, INTERACT_NO_COOLDOWN)
        .defaultVal(false, INTERACT_WHEN_RIDING)
        .save();


    public static final String[] SLIMEFUN_RECIPE_RECORD = {"recipe-record","enable"};
    public static final String[] SLIMEFUN_RECIPE_SAVE = {"recipe-record","save-data"};
    public static final String[] SLIMEFUN_RECIPE_TITLE = {"recipe-record","rp-title"};
    public static final String[] SLIMEFUN_MULTIBLOCK_MATCHER = {"recipe-record", "multiblock-pattern"};
    public static final String[] SLIMEFUN_MULTIBLOCK_CLICKER = {"multi-block-clicker","enable"};
    public static final String[] SLIMEFUN_MB_RATE = {"multi-block-clicker","rate"};
    public static final String[] SLIMEFUN_MATCH_UP_AND_DOWN = {"multi-block-clicker","only-when-at-middle"};
    public static final String[] SLIMEFUN_AUTO_CLICK_FACING = {"multi-block-clicker","auto-click-facing"};
    public static final Config SLIMEFUN_CONFIG = ConfigLoader.loadExternalConfig(
        "sfhelper-configs/slimefun.yml", "slimefun settings"
    )
        .defaultVal(false, SLIMEFUN_RECIPE_RECORD)
        .defaultVal(true, SLIMEFUN_RECIPE_SAVE)
        .defaultVal("^(Slimefun 指南.*)$", SLIMEFUN_RECIPE_TITLE)
        .defaultVal("^(多方块结构)$", SLIMEFUN_MULTIBLOCK_MATCHER)
        .defaultVal(false, SLIMEFUN_MULTIBLOCK_CLICKER)
        .defaultVal(12, SLIMEFUN_MB_RATE)
        .defaultVal(true, SLIMEFUN_MATCH_UP_AND_DOWN)
        .defaultVal(false,SLIMEFUN_AUTO_CLICK_FACING)
        .save();

    static{
       //none
    }
}
