package me.matl114.ManageUtils;

import java.util.*;

public class SimpleHotKey implements IHotKey {
    private List<Integer> keyCodes = new ArrayList<>(4);
    public String identifier;
    public int triggerKey;
    public String keyCode;
    public InputHandler inputHandler;
    public SimpleHotKey(String name,String defaultKeyCode,InputHandler inputHandler) {
        this.identifier = name;
        this.keyCode = defaultKeyCode;
        this.inputHandler = inputHandler;
        setValueFromString(this.keyCode);
    }
    public void clearKeys(){
        keyCodes.clear();
        this.triggerKey = 0;
    }
    public interface InputHandler{
        public boolean handle(IInputManager manager);
    }
    public boolean handleKeyInput(IInputManager manager, int keyCode, boolean isStateChanged,boolean isClicked){
        if(isStateChanged&&isClicked){
            if(keyCode==getTriggeredKey()){
                boolean allpressed=true;

                for(int keyNeeded:this.getRelatedKeyCode()){
                    allpressed&=manager.getKeyState(keyNeeded).isPressed();
                }
                if(allpressed){
                    if(inputHandler!=null&&inputHandler.handle(manager)){
                        return true;
                    }
                }

            }
        }
        return false;
    }
    public Collection<Integer> getRelatedKeyCode(){
        return this.keyCodes;
    }
    public void addKey(int keyCode){
        this.keyCodes.add(keyCode);
    }
    public int getTriggeredKey(){
        if(keyCodes==null||keyCodes.isEmpty()) return 0;
        return keyCodes.get(keyCodes.size()-1);
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
