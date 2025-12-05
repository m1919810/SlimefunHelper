package me.matl114.managers;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Getter;
import me.matl114.listenerUtils.Listener;
import me.matl114.utils.UtilClass.Event;

public class SimpleHotKey implements IHotKey {
    private IntList keyCodes = new IntArrayList(4);
    @Getter
    public String identifier;
//    public int triggerKey;
    public String defaultKeyCode;
    public String keyCode;
    public InputHandler inputHandler;
    public SimpleHotKey(String name, String defaultKeyCode, InputHandler inputHandler) {
        this.identifier = name;
        this.inputHandler = inputHandler;
        this.defaultKeyCode = defaultKeyCode;
        bindToConfigs();
    }

    public void bindToConfigs(){
        Config config = Configs.HOTKEY_CONFIG;
        Config.StringRef refs;
        String[] idPath = Config.cutToPath(this.identifier);
        if(!config.contains(idPath)){
            config.defaultVal(this.defaultKeyCode, idPath);
            config.save();
        }
        refs = config.getString(idPath);
        //load keyCodes
        setKeyCodes(refs.getValue());
        //set listener
        refs.addUpdateListener(this::setKeyCodes);
    }

    public void setKeyCodes(String keyCode){
        this.keyCode = keyCode;
        setValueFromString(this.keyCode);
    }

    @Override
    public String getDefaultKeyCodes() {
        return this.defaultKeyCode;
    }

    @Override
    public String getKeyCodes() {
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

    public void setValueFromString(String str)
    {
        this.clearKeys();
        String[] keys = str.split(",");

        for (String keyName : keys)
        {
            keyName = keyName.trim();

            if (!keyName.isEmpty())
            {
                int keyCode = KeyCode.getKeyCodeFromName(keyName);

                if (keyCode != KeyCode.KEY_NONE)
                {
                    this.addKey(keyCode);
                }
            }
        }
    }
}
