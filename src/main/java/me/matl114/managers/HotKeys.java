package me.matl114.managers;

import lombok.Getter;
import me.matl114.access.HandledScreenAccess;
import me.matl114.hackUtils.*;
import me.matl114.ModConfig;
import me.matl114.renders.implement.SlimefunRender;
import me.matl114.utils.Debug;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import  static me.matl114.SlimefunHelper.HACK_VERSION;

public class HotKeys {
    public static void init(){
        KeyCode.init();
        initToggleSaves();
        initHotkeyTasks();
        initButtonTasks();
        initButtonToggles();
        initHotkeyToggles();
        initSimpleToggles();
        Debug.info("HotKeys enabled!");
    }
    private static SimpleHotKey getToggleHotKey(String key,boolean defaultValue){
        hotkeyToggleManager.register(key, defaultValue);
        Runnable runnable = hotkeyToggleManager.getToggle(key);
        return new SimpleHotKey("hotkeys-toggle." + key, ModConfig.getToggleHotkeys(key),(m -> {
            ClientPlayerEntity player= m.getClient().player;
            if(player!=null && HotKeyUtils.isValidState()){
                runnable.run();
            }
            return true;
        })).register(SimpleInputManager.getInstance());
    }
//    private static SimpleHotKey getTaskHotKey(String key,Runnable task){
//        return new SimpleHotKey(key,ModConfig.getFuncHotKeys(key),(manager -> {
//            ClientPlayerEntity player= manager.getClient().player;
//            if(player!=null){
//                task.run();
//            }
//            return true;
//        })).register(SimpleInputManager.getInstance());
//    }
    private static SimpleHotKey getTaskHotKey(String key, Predicate<IInputManager> task){
        return new SimpleHotKey("hotkeys." + key,ModConfig.getFuncHotKeys(key),(task::test)).register(SimpleInputManager.getInstance());
    }
//    private static HashMap<String,Boolean> defaultToggles=new HashMap<>();
    @Getter
    private static ToggleManager buttonToggleManager=ToggleManager.of();
    @Getter
    private static ToggleManager hotkeyToggleManager=ToggleManager.of();
    @Getter
    private static ToggleManager simpleToggleManager=ToggleManager.of();
    @Getter
    private static TaskManager buttonTaskManager=TaskManager.of();
    public static final String SLIMEFUNID_COPY="slimefunid-copy";
    public static final String CLEAR_KEEPED="clear-keep";
    public static final String KEEP_INV="keep-inv";
    public static final String FAST_INV = "fast-inv";
    public static final String FAST_MOVE = "fast-mov";
    public static final String FAST_DROP = "fast-drop";
    public static final String FAST_PLAYER_MOVE = "quick-move";
    public static final String FAST_PLAYER_MOVE_WALL = "quick-to-wall";
    public static final String TOGGLE_FLYSPEED = "toggle-flight-speed";
    public static final String PICK_ITEM = "pick-item";
    public static final String QUICK_MINE="quick-mine";
    public static final String REACH="reach";
    public static final String MINEBOT="mine-bot";
    public static final String MINE_ONEBLOCK="mine-oneblock";
    public static final String OPEN_MENU="open-menu";
    public static final String AUTO_CHAT="auto-chat";
    public static final String KEEP_CHATINV="keep-chat-inv";
    public static final String DETECT_ENTITY="detect-entity";
    public static final String QUICK_DROP="quick-drop";
    public static final String TAKE_ALL="take-all";
    public static final String SAVE_ALL="save-all";
    public static final String ALWAYS_ATTACK="always-att";
    public static final String AUTO_ATTACK = "auto-att";
    public static final String DROP_CRAFT = "drop-craft";
    public static final String TOGGLE_FLIGHT = "toggle-flight";
    public static final String AUTO_STORE ="auto-store";
    public static final String LEFT_ONE = "left-1";
    public static final String SF_RECIPE_INTERNAL = "recipe-display-add";
    public static final String SF_SAVEITEM_INTERNAL = "save-slot-item";
    public static final String ITEMEDITOR_OPEN = "open-editor";
    public static final String OPEN_INV_CACHE = "open-inv-cache";
    public static final String WAKE_UP_SCREEN = "wake-up-screen";
//    public static final String TOGGLE_FAKE_FLIGHT = "fake-flight";
    public static final String TOGGLE_AUTO_AIM = "bow-aim";
    public static final String TOGGLE_SPEED_TIMER = "speed-timer";
    public static final String SCAFFOLD_WALK = "scaffold";
    public static final String MINEARUA = "mine-arua";
    public static final String BUTTON_TASK_1="btask1";
    public static final String BUTTON_TASK_2="btask2";
    public static final String HOTKEY_TEST1="hktest1";
    public static final String HOTKEY_TEST2="hktest2";
    public static final String HOTKEY_TEST3="hktest3";
    public static final String HOTKEY_TEST4="hktest4";

    public static final String OPEN_SEI_SCREEN = "slime guide";
    @Deprecated
    public static final Config.StringRef SHARED_ARGUMENT=new Config.StringRef(""){
        @Override
        public String get() {
            String val=super.get();
            Debug.chat("Using shared argument 1: "+val);
            return val;
        }
    };
    @Deprecated
    public static final Config.StringRef SHARED_ARGUMENT_2=new Config.StringRef(""){
        @Override
        public String get() {
            String val=super.get();
            Debug.chat("Using shared argument 2: "+val);
            return val;
        }
    };
    static{
        SHARED_ARGUMENT.addUpdateListener(str->{

            if(MinecraftClient.getInstance().currentScreen instanceof HandledScreenAccess access){

                access.updateSharedArgument(str,null);
            }
        });
        SHARED_ARGUMENT_2.addUpdateListener(str->{
            if(MinecraftClient.getInstance().currentScreen instanceof HandledScreenAccess access){
                access.updateSharedArgument(null,str);
            }
        });
    }
    private static void initButtonToggles(){
        if(!HACK_VERSION){
            buttonToggleManager.register(FAST_INV,false);
            buttonToggleManager.register(LEFT_ONE,false);

        }else {
            buttonToggleManager.register(KEEP_INV,false);
            buttonToggleManager.register(FAST_INV,false);
            buttonToggleManager.register(AUTO_STORE,false);
            buttonToggleManager.register(LEFT_ONE,false);
        }


    }
    private static void initHotkeyToggles(){
        if(!HACK_VERSION){
            return;
        }
        getToggleHotKey(QUICK_MINE,false);
        getToggleHotKey(REACH,false);
        getToggleHotKey(MINEBOT,false);
        getToggleHotKey(MINE_ONEBLOCK,false);
        getToggleHotKey(DETECT_ENTITY,false);
        getToggleHotKey(ALWAYS_ATTACK,false);
        getToggleHotKey(AUTO_ATTACK,false);
        getToggleHotKey(TOGGLE_FLIGHT,false);
        getToggleHotKey(TOGGLE_SPEED_TIMER, false);
        getToggleHotKey(TOGGLE_AUTO_AIM, false);
        getToggleHotKey(SCAFFOLD_WALK, false);
        getToggleHotKey(MINEARUA, false);
        getToggleHotKey(HOTKEY_TEST1,false);
        getToggleHotKey(HOTKEY_TEST2,false);
        getToggleHotKey(HOTKEY_TEST3,false);
        getToggleHotKey(HOTKEY_TEST4,false);

    }
    private static void initSimpleToggles(){
        simpleToggleManager.register(AUTO_CHAT,false);
        simpleToggleManager.register(KEEP_CHATINV,false);
        simpleToggleManager.register(DROP_CRAFT,false);
    }

    private static void initButtonTasks(){
        if(!HACK_VERSION){
            buttonTaskManager.register(TAKE_ALL,InvTasks::takeAllContainerItem);
            buttonTaskManager.register(SAVE_ALL,InvTasks::saveAllPlayerItem);
            buttonTaskManager.register(OPEN_SEI_SCREEN, SlimefunTasks::handleClickGuideIcon);
        }else {
            buttonTaskManager.register(CLEAR_KEEPED, InvTasks::clearKeepedInv);
            buttonTaskManager.register(TAKE_ALL,InvTasks::takeAllContainerItem);
            buttonTaskManager.register(SAVE_ALL,InvTasks::saveAllPlayerItem);
            buttonTaskManager.register(OPEN_SEI_SCREEN, SlimefunTasks::handleClickGuideIcon);
            //buttonTaskManager.register(BUTTON_TASK_1, Tasks::doButtonTaskTest1);
        }
    }
    private static void initHotkeyTasks(){
        //internal hotkeys
        getTaskHotKey(SLIMEFUNID_COPY,(manager)->SlimefunRender.copySfIdInHand());
        getTaskHotKey(SF_SAVEITEM_INTERNAL, (manager)->SlimefunTasks.clickToSaveItem());
        getTaskHotKey(ITEMEDITOR_OPEN, (iInputManager -> ItemEditTasks.openEditor()));
        getTaskHotKey(OPEN_INV_CACHE, (iInputManager -> InvTasks.openInventoryCacheScreen()));
        getTaskHotKey(WAKE_UP_SCREEN, (iInputManager -> RenderTasks.wakeUpScreen()));

        getTaskHotKey(QUICK_DROP,(iInputManager -> InvTasks.dropAllCursorStack()));
        getTaskHotKey(FAST_MOVE,(iInputManager -> InvTasks.quickMoveAllSelectedItem()));
        getTaskHotKey(FAST_DROP,(iInputManager -> InvTasks.quickDropAllSelectedItem()));
        //usage hotkeys need check state
        getTaskHotKey(OPEN_MENU,(manager->{
            if (HotKeyUtils.isValidState()){
                InvTasks.openConfigNewStyleScreen();
                return true;
            }
            return false;
        }));
        if(!HACK_VERSION){
        }else {
            getTaskHotKey("test-func",(manager -> {
                if(manager.getClient().player != null && HotKeyUtils.isValidState()){
                    Debug.chat("Doing Test!!!");
                    Tasks.doTest();
                    return true;
                }else return false;
            }));
            getTaskHotKey(FAST_PLAYER_MOVE,(iInputManager -> {
                if(HotKeyUtils.isValidState()){
                    MovTasks.quickMovFront();
                    return true;
                }
                return false;
            }));
            getTaskHotKey(FAST_PLAYER_MOVE_WALL,(iInputManager -> {
                if(HotKeyUtils.isValidState()){
                    MovTasks.quickMovTowardsWall();
                    return true;
                }
                return false;
            }));
            getTaskHotKey(TOGGLE_FLYSPEED, (iInputManager -> {
                if(HotKeyUtils.isValidState()){
                    MovTasks.toggleSpeedOverride();
                    return true;
                }
                return false;
            }));
            getTaskHotKey(PICK_ITEM, (iInputManager -> InvTasks.pickUpSelectingSlot()));

        }

    }

//    static File toggleSave;
//    private static void save(){
//        try(FileWriter writer=new FileWriter(toggleSave)){
//            Yaml yaml=new Yaml();
//            yaml.dump(defaultToggles,writer);
//        }catch (Throwable e){
//            e.printStackTrace();
//        }
//    }
    public static Config.FlagRef getToggleFlag(String key,boolean value){
        Configs.TOGGLE_CONFIG.defaultVal(value, "toggle", key);
        return Configs.TOGGLE_CONFIG.getBoolean("toggle", key);

    }

    private static void initToggleSaves(){
        final File configFile = FabricLoader.getInstance().getConfigDir().resolve("slimefunhelper-func-toggle.yml").toFile();
        if(configFile.exists() && configFile.isFile()){
            //move to config
            try(FileReader readerConfig=new FileReader(configFile)){
                Yaml yaml=new Yaml();
                HashMap<String,Object> toggles=yaml.load(readerConfig);
                if(toggles==null|| toggles.isEmpty()){
                }else {
                    for (Map.Entry<String,Object> entry:toggles.entrySet()){
                        Configs.TOGGLE_CONFIG.defaultVal(Boolean.parseBoolean(entry.getValue().toString()), "toggle", entry.getKey());
                    }
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                Configs.TOGGLE_CONFIG.save();
                configFile.delete();
            }
            Config.launchSaveTasks();

        }
    }

    public static Map<String, String> getHotkeysMap(){
        Map<String, String> alls = new LinkedHashMap<>();
        ModConfig.getFuncHotKeys().forEach((i,j)->alls.put("function:"+i, j));
        ModConfig.getToggleHotKeys().forEach((i,j)->alls.put("toggle:"+i, j));
        return alls;
    }

}
