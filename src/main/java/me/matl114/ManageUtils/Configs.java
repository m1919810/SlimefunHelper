package me.matl114.ManageUtils;

import me.matl114.HackUtils.CombatTasks;

public class Configs {
    public static void loadConfigs(){
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
    public static final Config MINE_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/mine.yml","mine settings")
            //.defaultVal(false,MINE_BOT_ONLY_MINE_ABOVE)
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
            .save();
    public static final String[] CHAT_HELPER_CACHE={"chat-helper","cached"};
    public static final String[] CHAT_HELPER_PERIOD={"chat-helper","period"};
    public static final String[] CHAT_HELPER_MULTIPLE={"chat-helper","multiple"};
    public static final String[] CHAT_HELPER_SPECIALCHARS={"chat-helper","special-chars"};
    public static final Config CHAT_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/chat.yml","chat settings")
            .defaultVal("",CHAT_HELPER_CACHE)
            .defaultVal(20,CHAT_HELPER_PERIOD)
            .defaultVal(1,CHAT_HELPER_MULTIPLE)
            .defaultVal("\uD83D\uDE21\uD83E\uDD13\uD83E\uDD75\uD83D\uDE2D\uD83E\uDD21\uD83D\uDE0B\uD83E\uDD24\uD83D\uDE0A\uD83D\uDE04\uD83E\uDD72\uD83D\uDE01\uD83D\uDC49\uD83D\uDC46\uD83E\uDD14\uD83D\uDE0E\uD83D\uDC0D\uD83D\uDE05♂♀",CHAT_HELPER_SPECIALCHARS)
            .save();

    public static final String[] RENDER_DETECT_SPAWN_WHITELIST={"detect-entity","spawn-whitelist"};
    public static final Config RENDER_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/render.yml","render settings")
            .defaultVal("player,wither",RENDER_DETECT_SPAWN_WHITELIST)
            .save();
    public static final String[] TEST_ARGS1={"test","arg1"};
    public static final String[] TEST_ARGS2={"test","arg2"};
    public static final Config TEST_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/test.yml","test settings")
            .defaultVal(480000,TEST_ARGS1)
            .defaultVal(461,TEST_ARGS2)
            .save();

    public static final String[] COMBAT_INTERVEL={"attack","cancel-interval"};
    public static final String[] COMBAT_RIDING={"attack","riding-attack"};
    public static final String[] AUTOATTACK_AUTO={"att-bot","auto-click"};
    public static final String[] ATTACK_RANGE={"attack","att-range"};
    public static final String[] AUTOATTACK_WHITELISTED={"att-bot","whitelist"};
    public static final Config COMBAT_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/combat.yml","combat settings")
            .defaultVal(false,COMBAT_INTERVEL)
            .defaultVal(false,COMBAT_RIDING)
            .defaultVal("^(monster|!endermite)$",AUTOATTACK_WHITELISTED)
            .defaultVal(6.0f,ATTACK_RANGE)
            .save();

    public static final String[] INV_CLICK_LIMIT={"inventory","packet-limit"};
    public static final Config INV_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/inv.yml","inv settings")
            .defaultVal(40,INV_CLICK_LIMIT)
            .save();


    static{
       //none
    }
}
