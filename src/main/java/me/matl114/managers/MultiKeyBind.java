package me.matl114.managers;

import com.google.common.base.Preconditions;
import lombok.Getter;

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


    public String asString(){
        return "hotkey:" + String.join(",", keys);
    }
}
