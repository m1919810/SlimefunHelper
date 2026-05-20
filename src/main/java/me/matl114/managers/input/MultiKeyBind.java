package me.matl114.managers.input;

import com.google.common.base.Preconditions;
import java.util.Arrays;
import lombok.Getter;

@Getter
public class MultiKeyBind {
    String[] keys;
    int[] keyCodes;

    private void validateKeys() {
        Preconditions.checkNotNull(keys);
        for (int i = 0; i < keys.length; i++) {
            String keyName = keys[i];
            keyName = keyName.trim();
            Preconditions.checkArgument(!keyName.isEmpty());
            int keyCode = KeyCode.getKeyCodeFromName(keyName);

            if (keyCode != KeyCode.KEY_NONE) {
                keyCodes[i] = (keyCode);
            } else {
                throw new RuntimeException("Invalid key code: " + keyName);
            }
        }
    }

    public MultiKeyBind(String rawStr) throws RuntimeException {
        if (rawStr.startsWith("hotkey:")) {
            rawStr = rawStr.substring("hotkey:".length());
        }
        keys = rawStr.isEmpty() ? new String[0] : rawStr.split(",");
        keyCodes = new int[keys.length];
        validateKeys();
    }

    public MultiKeyBind(int... keyCodes) throws RuntimeException {
        Preconditions.checkNotNull(keyCodes);
        this.keyCodes = Arrays.copyOf(keyCodes, keyCodes.length);
        this.keys = new String[keyCodes.length];
        for (int i = 0; i < keyCodes.length; i++) {
            this.keys[i] = KeyCode.getNameForKey(this.keyCodes[i]);
        }
        validateKeys();
    }

    public MultiKeyBind(){
        this(new int[0]);
    }

    public String asString() {
        return "hotkey:" + String.join(",", keys);
    }

    public String getKeyStr() {
        return String.join(",", keys);
    }

    public boolean isAllPressed() {
        if (keyCodes.length == 0) {
            return false;
        }
        for (int i = 0; i < keyCodes.length; i++) {
            if (!SimpleInputManager.getInstance().isKeyPressed(keyCodes[i])) {
                return false;
            }
        }
        return true;
    }

    public boolean isLastKeyPressed() {
        if (keyCodes.length == 0) {
            return false;
        }
        return SimpleInputManager.getInstance().isKeyPressed(keyCodes[keyCodes.length - 1]);
    }

    public int getLastKey() {
        if (keyCodes.length == 0) {
            return KeyCode.KEY_NONE;
        }
        return keyCodes[keyCodes.length - 1];
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        else if (obj instanceof MultiKeyBind other) {
            return Arrays.equals(keyCodes, other.keyCodes);
        } else return false;
    }
}
