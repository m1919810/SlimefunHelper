package me.matl114.jsApi;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class KeyBindingHelper {
    public static GameOptions options = MinecraftClient.getInstance().options;
    private static final Map<String, KeyBinding> keyBindings = new HashMap<String, KeyBinding>();
    static{
        try{
            Class<?> clazz = GameOptions.class;
            for(Field field : clazz.getDeclaredFields()){
                if(field.getType() == KeyBinding.class){
                    field.setAccessible(true);
                    KeyBinding keyBinding = (KeyBinding)field.get(options);
                    keyBindings.put(keyBinding.getTranslationKey(), keyBinding);
                }
            }
        }catch (Throwable e){
            throw new RuntimeException(e);
        }
    }
    public static KeyBinding getKeyBinding(String key){
        return keyBindings.get(key);
    }
    public static KeyBinding getFowardKeyBinding() {
        return getKeyBinding("key.forward");
    }
    public static KeyBinding getBackwardKeyBinding() {
        return getKeyBinding("key.back");
    }
    public static KeyBinding getLeftKeyBinding() {
        return getKeyBinding("key.left");
    }
    public static KeyBinding getRightKeyBinding() {
        return getKeyBinding("key.right");
    }
    public static KeyBinding getJumpKeyBinding() {
        return getKeyBinding("key.jump");
    }
    public static KeyBinding getSneakKeyBinding() {
        return getKeyBinding("key.sneak");
    }
    public static KeyBinding getSprintBinding() {
        return getKeyBinding("key.sprint");
    }
    public static KeyBinding getSwapKeyBinding() {
        return getKeyBinding("key.swapOffhand");
    }
    public static KeyBinding getInventoryKeyBinding() {
        return getKeyBinding("key.inventory");
    }
    public static KeyBinding getUseKeyBinding() {
        return getKeyBinding("key.use");
    }
    public static KeyBinding getAttackKeyBinding() {
        return getKeyBinding("key.attack");
    }
    public static KeyBinding getDropKeyBinding() {
        return getKeyBinding("key.drop");
    }

    public static void setPress(KeyBinding keyBinding, boolean pressed){
        keyBinding.setPressed(pressed);
    }

    public static boolean isPressed(KeyBinding keyBinding){
        return keyBinding.isPressed();
    }
    public static boolean wasPressed(KeyBinding keyBinding){
        return keyBinding.wasPressed();
    }
    public static void reset(KeyBinding keyBinding){
        while(keyBinding.wasPressed()){
        }
        keyBinding.setPressed(false);
    }
}
