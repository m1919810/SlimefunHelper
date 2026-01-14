package me.matl114.managers;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Getter;
import me.matl114.listenerUtils.Listener;
import me.matl114.utils.UtilClass.Event;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class SimpleHotKey implements IHotKey{
    private final IntList keyCodes = new IntArrayList(4);
    @Getter
    public String identifier;
//    public int triggerKey;
    public MultiKeyBind defaultKeyCode;
    public MultiKeyBind keyCode;
    private Config.KeyBindRef ref;
    public InputHandler inputHandler;
    private final List<IInputManager> registeredManagers = new ArrayList<>();

    @Override
    public <T extends IHotKey> T register(IInputManager manager) {
        if(!registeredManagers.contains(manager)){
            registeredManagers.add(manager);
            return (IHotKey.super.register(manager));

        }return (T) this;
    }

    public void reload(){
        for(IInputManager manager : registeredManagers){
            manager.unregisterHotKeys(this);
            manager.registerHotKeys(this);
        }
    }

    public SimpleHotKey(String name, String defaultKeyCode, InputHandler inputHandler) {
        this.identifier = name;
        this.inputHandler = inputHandler;
        this.defaultKeyCode =  new MultiKeyBind(defaultKeyCode);
        bindToConfigs();
    }

    public void bindToConfigs(){
        Config config = Configs.HOTKEY_CONFIG;
        String[] idPath = Config.cutToPath(this.identifier);

        config.defaultVal(this.defaultKeyCode, idPath);
        config.save();

        ref = config.getKeyBind(idPath);

        //set listener with current call
        ref.addUpdateListenerWithUpdate(this::setKeyCodes);
    }

    public void setKeyCodes(MultiKeyBind keyCode){
        this.keyCode = keyCode;
        setValueFromString(this.keyCode);
    }

    @Override
    public MultiKeyBind getDefaultKeyCodes() {
        return this.defaultKeyCode;
    }

    @Override
    public MultiKeyBind getKeyCodes() {
        return keyCode;
    }

    public void clearKeys(){
        keyCodes.clear();
//        this.triggerKey = 0;
    }
    public interface InputHandler{
        public boolean handle(IInputManager manager);
    }
    public boolean handleKeyInput(IInputManager manager, int keyCode, boolean isStateChanged,boolean isClicked){
        if(isStateChanged && isClicked){
            if(!isEmpty() && keyCode == getTriggeredKey()){
                boolean allpressed=true;

                for(int keyNeeded : this.getRelatedKeyCode()){
                    allpressed &= manager.getKeyState(keyNeeded).isPressed();
                }
                if(allpressed){
                    Event<IHotKey> hotKeyEvent = new Event<>(this, true, false, manager);
                    Listener.getHotKeyTriggeredListener().handleValue(hotKeyEvent);
                    if(hotKeyEvent.isCancelled()){
                        return false;
                    }
                    if(inputHandler != null){
                        return inputHandler.handle(manager);
                    }
                    return true;
                }

            }
        }
        return false;
    }
    public IntList getRelatedKeyCode(){
        return this.keyCodes;
    }
    public void addKey(int keyCode){
        this.keyCodes.add(keyCode);
    }
    public int getTriggeredKey(){
        if(isEmpty()) return 0;
        return keyCodes.get(keyCodes.size()-1);
    }

    public boolean isEmpty(){
        return keyCodes == null || keyCodes.isEmpty();
    }

    private void setValueFromString(MultiKeyBind str)
    {

        this.clearKeys();
        for (var keycode : str.getKeyCodes()){
            this.addKey(keycode);
        }
        this.reload();

    }
}
