package me.matl114.managers;

import lombok.Getter;
import me.matl114.access.HandledScreenAccess;
import me.matl114.hackUtils.*;
import me.matl114.ModConfig;
import me.matl114.SlimefunHelper;
import me.matl114.renders.implement.SlimefunRender;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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
    private static final boolean HACK_VERSION = SlimefunHelper.HACK_VERSION;
    private static SimpleHotKey getToggleHotKey(String key,boolean defaultValue){
        hotkeyToggleManager.register(key, defaultValue);
        Runnable runnable = hotkeyToggleManager.getToggle(key);
        return new SimpleHotKey(key,ModConfig.getToggleHotkeys(key),(m -> {
            ClientPlayerEntity player= m.getClient().player;
            if(player!=null){
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
        return new SimpleHotKey(key,ModConfig.getFuncHotKeys(key),(task::test)).register(SimpleInputManager.getInstance());
    }
    private static HashMap<String,Boolean> defaultToggles=new HashMap<>();
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
    public static final String BUTTON_TASK_1="btask1";
    public static final String BUTTON_TASK_2="btask2";
    public static final String HOTKEY_TEST1="hktest1";
    public static final String HOTKEY_TEST2="hktest2";
    public static final String HOTKEY_TEST3="hktest3";
    public static final String HOTKEY_TEST4="hktest4";

    public static final String OPEN_SEI_SCREEN = "slime guide";

    public static final Config.StringRef SHARED_ARGUMENT=new Config.StringRef(""){
        @Override
        public String get() {
            String val=super.get();
            Debug.chat("Using shared argument 1: "+val);
            return val;
        }
    };
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
            buttonTaskManager.register(SAVE_ALL,InvTasks::saveAllPlayerInvItem);
            buttonTaskManager.register(OPEN_SEI_SCREEN, SlimefunTasks::handleClickGuideIcon);
        }else {
            buttonTaskManager.register(CLEAR_KEEPED, InvTasks::clearKeepedInv);
            buttonTaskManager.register(TAKE_ALL,InvTasks::takeAllContainerItem);
            buttonTaskManager.register(SAVE_ALL,InvTasks::saveAllPlayerInvItem);
            buttonTaskManager.register(OPEN_SEI_SCREEN, SlimefunTasks::handleClickGuideIcon);
            //buttonTaskManager.register(BUTTON_TASK_1, Tasks::doButtonTaskTest1);
        }
    }
    private static void initHotkeyTasks(){
        getTaskHotKey(SLIMEFUNID_COPY,(manager)->{
            ClientPlayerEntity player= manager.getClient().player;
            if(player!=null){
                return SlimefunRender.copySfIdInHand(player,manager.getClient());
            }
            return false;
        });
        getTaskHotKey(SF_RECIPE_INTERNAL, (manager)->{
            ClientPlayerEntity player= manager.getClient().player;
            if(player!=null && manager.getClient().currentScreen instanceof HandledScreen<?> handledScreen && handledScreen.getScreenHandler().getCursorStack().isEmpty()){
                return SlimefunTasks.clickToAddRecipeDisplay(handledScreen);
            }
            return false;
        });
        getTaskHotKey(SF_SAVEITEM_INTERNAL, (manager)->SlimefunTasks.clickToSaveItem());
        getTaskHotKey(ITEMEDITOR_OPEN, (iInputManager -> ItemEditTasks.openEditor()));
        getTaskHotKey(OPEN_INV_CACHE, (iInputManager -> InvTasks.openInventoryCacheScreen()));
        getTaskHotKey(WAKE_UP_SCREEN, (iInputManager -> RenderTasks.wakeUpScreen()));
        if(!HACK_VERSION){
            getTaskHotKey(QUICK_DROP,(iInputManager -> InvTasks.dropAllSelectedItem()));
            getTaskHotKey(FAST_MOVE,(iInputManager -> InvTasks.quickMoveAllSelectedItem()));
            getTaskHotKey(FAST_DROP,(iInputManager -> InvTasks.quickDropAllSelectedItem()));
            getTaskHotKey(OPEN_MENU,(manager->{
                InvTasks.openSelectScreen();
                return true;
            }));
        }else {
            getTaskHotKey("test-func",(manager -> {
                if(manager.getClient().player != null){
                    Debug.chat("Doing Test!!!");
                    Tasks.doTest();
                    return true;
                }else return false;
            }));
            getTaskHotKey(OPEN_MENU,(manager->{
                InvTasks.openSelectScreen();
                return true;
            }));
            getTaskHotKey(QUICK_DROP,(iInputManager -> InvTasks.dropAllSelectedItem()));
            getTaskHotKey(FAST_MOVE,(iInputManager -> InvTasks.quickMoveAllSelectedItem()));
            getTaskHotKey(FAST_DROP,(iInputManager -> InvTasks.quickDropAllSelectedItem()));
            getTaskHotKey(FAST_PLAYER_MOVE,(iInputManager -> MovTasks.quickMovFront()));
            getTaskHotKey(TOGGLE_FLYSPEED, (iInputManager -> MovTasks.toggleSpeedOverride()));
            getTaskHotKey(PICK_ITEM, (iInputManager -> InvTasks.pickUpSelectingSlot()));
        }

    }
    static File toggleSave;
    private static void save(){
        try(FileWriter writer=new FileWriter(toggleSave)){
            Yaml yaml=new Yaml();
            yaml.dump(defaultToggles,writer);
        }catch (Throwable e){
            e.printStackTrace();
        }
    }
    public static boolean getToggles(String key,boolean value){
        if(defaultToggles.containsKey(key)){
            return defaultToggles.get(key);
        }else {
            setToggles(key,value);
            return value;
        }
    }
    public static void setToggles(String key,boolean value){
        defaultToggles.put(key,value);
        save();
    }
    private static void initToggleSaves(){
        toggleSave= ModConfig.loadOrUseInternal("slimefunhelper-func-toggle.yml");
        try(FileReader readerConfig=new FileReader(toggleSave)){
            Yaml yaml=new Yaml();
            HashMap<String,Object> toggles=yaml.load(readerConfig);
            if(toggles==null|| toggles.isEmpty()){
                defaultToggles=new HashMap<>();
            }else {
                for (Map.Entry<String,Object> entry:toggles.entrySet()){
                    defaultToggles.put(entry.getKey(),Boolean.parseBoolean(entry.getValue().toString()));
                }
            }
        }catch (Throwable e){
            defaultToggles=new HashMap<>();
            Debug.info("AN INTERNAL ERROR WHILE LOADING DEFAULT CONFIG");
            e.printStackTrace();
        }
    }

    public static Map<String, String> getHotkeysMap(){
        Map<String, String> alls = new LinkedHashMap<>();
        ModConfig.getFuncHotKeys().forEach((i,j)->alls.put("function:"+i, j));
        ModConfig.getToggleHotKeys().forEach((i,j)->alls.put("toggle:"+i, j));
        return alls;
    }

}
