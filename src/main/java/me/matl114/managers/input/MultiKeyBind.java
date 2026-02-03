package me.matl114.managers.input;

import com.google.common.base.Preconditions;
import lombok.Getter;

import java.util.Arrays;

@Getter
public class MultiKeyBind {
    String[] keys;
    int[] keyCodes;
    private void validateKeys(){
        Preconditions.checkNotNull(keys);
        for (int i =0 ; i < keys.length ; i++)
        {
            String keyName = keys[i];
            keyName = keyName.trim();
            Preconditions.checkArgument(!keyName.isEmpty());
            int keyCode = KeyCode.getKeyCodeFromName(keyName);

            if (keyCode != KeyCode.KEY_NONE)
            {
                keyCodes[i] = (keyCode);
            }else{
                throw new RuntimeException("Invalid key code: " + keyName);
            }
        }
    }
    public MultiKeyBind(String rawStr) throws RuntimeException{
        if(rawStr.startsWith("hotkey:")){
            rawStr = rawStr.substring("hotkey:".length());
        }
        keys = rawStr.isEmpty() ? new String[0] : rawStr.split(",");
        keyCodes = new int[keys.length];
        validateKeys();
    }

    public MultiKeyBind(int... keyCodes) throws RuntimeException{
        Preconditions.checkNotNull(keyCodes);
        this.keyCodes = Arrays.copyOf(keyCodes, keyCodes.length);
        this.keys = new String[keyCodes.length];
        for (int i =0 ; i < keyCodes.length ; i++){
            this.keys[i] = KeyCode.getNameForKey(this.keyCodes[i]);
        }
        validateKeys();
    }


    public String asString(){
        return "hotkey:" + String.join(",", keys);
    }

    public String getKeyStr(){
        return String.join(",", keys);
    }

     @Override public boolean equals(Object obj){
        if(obj  == this) return true;
        else if(obj instanceof MultiKeyBind other){
            return Arrays.equals(keyCodes, other.keyCodes);
        }else return false;
     }
}
