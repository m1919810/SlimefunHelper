package me.matl114.managers.input;

import com.google.common.util.concurrent.Runnables;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.managers.config.KeyBindRef;

public class SimpleHotKey implements IHotKey {
    private final IntList keyCodes = new IntArrayList(4);

    @Getter
    public String identifier;
    //    public int triggerKey;
    public final MultiKeyBind defaultKeyCode;
    public MultiKeyBind keyCode;
    private KeyBindRef ref;

    @Setter
    private InputHandler inputHandler = InputHandler.EMPTY;

    private final Set<IInputManager> registeredManagers = new LinkedHashSet<>();

    public void reload() {
        for (IInputManager manager : registeredManagers) {
            manager.unregisterHotKeys(this);
            manager.registerHotKeys(this);
        }
    }

    @Deprecated
    public SimpleHotKey(String name, String defaultKeyCode) {
        this.identifier = name;
        this.defaultKeyCode = new MultiKeyBind(defaultKeyCode);
        setKeyCodes(this.defaultKeyCode);
    }

    public SimpleHotKey(String[] path, MultiKeyBind defaultKeyCode) {
        this.identifier = String.join(".", path);
        this.defaultKeyCode = defaultKeyCode;
        setKeyCodes(this.defaultKeyCode);
    }

    public void setKeyCodes(MultiKeyBind keyCode) {
        // lazy update
        if (!Objects.equals(keyCode, this.keyCode)) {
            this.keyCode = keyCode;
            setValueFromString(this.keyCode);
        }
    }

    @Override
    public void addRegisteredManager(IInputManager manager) {
        registeredManagers.add(manager);
    }

    @Override
    public MultiKeyBind getDefaultKeyCodes() {
        return this.defaultKeyCode;
    }

    @Override
    public MultiKeyBind getKeyCodes() {
        return keyCode;
    }

    public void clearKeys() {
        keyCodes.clear();
        //        this.triggerKey = 0;
    }

    public interface InputHandler {
        public static InputHandler EMPTY = HotKeyUtils.wrapAsHandler(Runnables.doNothing());

        public boolean handle(IInputManager manager);
    }

    public boolean handleKeyInput(IInputManager manager, int keyCode, boolean isStateChanged, boolean isClicked) {
        if (isStateChanged && isClicked) {
            if (!isEmpty() && keyCode == getTriggeredKey()) {
                boolean allpressed = true;

                for (int keyNeeded : this.getRelatedKeyCode()) {
                    allpressed &= manager.isKeyPressed(keyNeeded); // getKeyState(keyNeeded).isPressed();
                }
                if (allpressed) {
                    Event<IHotKey> hotKeyEvent = new Event<>(this, true, false, manager);
                    Listener.getHotKeyTriggeredListener().handleValue(hotKeyEvent);
                    if (hotKeyEvent.isCancelled()) {
                        return false;
                    }
                    if (inputHandler != null) {
                        return inputHandler.handle(manager);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public IntList getRelatedKeyCode() {
        return this.keyCodes;
    }

    public void addKey(int keyCode) {
        this.keyCodes.add(keyCode);
    }

    public int getTriggeredKey() {
        if (isEmpty()) return 0;
        return keyCodes.get(keyCodes.size() - 1);
    }

    public boolean isEmpty() {
        return keyCodes == null || keyCodes.isEmpty();
    }

    private void setValueFromString(MultiKeyBind str) {

        this.clearKeys();
        for (var keycode : str.getKeyCodes()) {
            this.addKey(keycode);
        }
        this.reload();
    }
}
