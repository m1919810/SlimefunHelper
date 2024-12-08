package me.matl114.ManageUtils;

import lombok.Getter;
import me.matl114.HackUtils.Tasks;
import me.matl114.ModConfig;
import me.matl114.SlimefunUtils.Debug;
import me.matl114.SlimefunUtils.SlimefunUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class HotKeys {
    public static void init(){
        KeyCode.init();
        initToggleSaves();
        initHotkeyTasks();
        initButtonToggles();
        initHotkeyToggles();
        initButtonTasks();
        Debug.info("HotKeys enabled!");
    }
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
    private static SimpleHotKey getTaskHotKey(String key,Runnable task){
        return new SimpleHotKey(key,ModConfig.getFuncHotKeys(key),(manager -> {
            ClientPlayerEntity player= manager.getClient().player;
            if(player!=null){
                task.run();
            }
            return true;
        })).register(SimpleInputManager.getInstance());
    }
    private static SimpleHotKey getTaskHotKey(String key, Predicate<IInputManager> task){
        return new SimpleHotKey(key,ModConfig.getFuncHotKeys(key),(manager -> {
            ClientPlayerEntity player= manager.getClient().player;
            if(player!=null){
                return task.test(manager);
            }
            return true;
        })).register(SimpleInputManager.getInstance());
    }
    private static HashMap<String,Boolean> defaultToggles=new HashMap<>();
    @Getter
    private static ToggleManager buttonToggleManager=ToggleManager.of();
    @Getter
    private static ToggleManager hotkeyToggleManager=ToggleManager.of();
    @Getter
    private static TaskManager buttonTaskManager=TaskManager.of();
    public static final String SLIMEFUNID_COPY="slimefunid-copy";
    public static final String CLEAR_KEEPED="clear-keep";
    public static final String KEEP_INV="keep-inv";
    public static final String QUICK_MINE="quick-mine";
    public static final String REACH="reach";
    public static final String MINEBOT="mine-bot";
    private static void initButtonToggles(){
        buttonToggleManager.register(KEEP_INV,false);
        buttonToggleManager.register("test1",false);
        buttonToggleManager.register("test2",false);
        buttonToggleManager.register("test3",false);
    }
    private static void initHotkeyToggles(){
        getToggleHotKey(QUICK_MINE,false);
        getToggleHotKey(REACH,false);
        getToggleHotKey(MINEBOT,false);
    }
    private static void initButtonTasks(){
        buttonTaskManager.register(CLEAR_KEEPED, Tasks::clearKeepedInv);
    }
    private static void initHotkeyTasks(){
        getTaskHotKey(SLIMEFUNID_COPY,(manager)->{
            ClientPlayerEntity player= manager.getClient().player;
            if(player!=null){
                return SlimefunUtils.copySfIdInHand(player,manager.getClient());
            }
            return false;
        });
        getTaskHotKey("test-func",(manager -> {
            Debug.info(manager.getClient().player.currentScreenHandler);
            return true;
        }));
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

}
