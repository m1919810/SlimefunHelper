package me.matl114.ManageUtils;

public class Configs {
    public static void loadConfigs(){
        if(init){
            Config.reloadAll();
        }else {
            init=true;
        }
    }
    private static boolean init=false;
    public static String MINE_BOT_MINE_ABOVE="mine-bot-only-mine-above";
    public static String MINE_BOT_MAX_PER_TICK="mine-bot-max-per-tick";
    public static String[] MINE_BOT_ONLY_MINE_ABOVE={"mine-bot","only-mine-above"};
    public static String[] MINE_BOT_MAX_INSTANT_MINE={"mine-bot","max-instant-mine"};
    public static String[] MINE_ENABLE_FAKE_INSTANT_BREAK={"fast-break","use-fake-instant-break"};
    public static String[] MINE_BOT_WHITELIST={"mine-bot","whitelist"};
    public static String[] MINE_BOT_PACKET_MULTIPLE={"mine-bot","multiple-packets"};
    public static String[] MINE_ONEBLOCK_={"mine-oneblock","?"};
    public static Config MINE_CONFIG=ConfigLoader.loadExternalConfig("sfhelper-configs/mine.yml","mine settings")
            .defaultVal(false,MINE_BOT_ONLY_MINE_ABOVE)
            .defaultVal(30,MINE_BOT_MAX_INSTANT_MINE)
            .defaultVal(true,MINE_ENABLE_FAKE_INSTANT_BREAK)
            .defaultVal("^(cobblestone|stone|.*ore)$",MINE_BOT_WHITELIST)
            .defaultVal(5,MINE_BOT_PACKET_MULTIPLE)
            .save();



}
