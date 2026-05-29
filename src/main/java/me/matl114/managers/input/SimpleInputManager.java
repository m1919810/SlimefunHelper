package me.matl114.managers.input;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.managers.InputState;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;

public class SimpleInputManager implements IInputManager {
    protected static final SimpleInputManager instance = new SimpleInputManager();

    protected SimpleInputManager() {
        this.mc = MinecraftClient.getInstance();
    }

    protected final Map<String, IHotKey> hotkeyRegistry = new HashMap<>();
    protected final Multimap<Integer, IHotKey> keyBindings = LinkedHashMultimap.<Integer, IHotKey>create();

    public IHotKey getHotkey(String id) {
        return hotkeyRegistry.get(id);
    }

    public void registerHotKeys(IHotKey key) {
        hotkeyRegistry.put(key.getIdentifier(), key);
        for (Integer i : key.getRelatedKeyCode()) {
            keyBindings.put(i, key);
        }
        key.addRegisteredManager(this);
    }

    public void unregisterHotKeys(IHotKey key) {
        hotkeyRegistry.remove(key.getIdentifier());
        keyBindings.entries().removeIf(e -> e.getValue() == key);
    }

    public static SimpleInputManager getInstance() {
        return instance;
    }

    protected MinecraftClient mc;

    public MinecraftClient getClient() {
        return mc;
    }

    protected Map<Integer, InputState> PRESSED_KEYS = new ConcurrentHashMap<>();

    public InputState getKeyState(int t) {
        return PRESSED_KEYS.get(t);
    }

    public InputState getKeyStateOrCreate(int t) {
        return PRESSED_KEYS.computeIfAbsent(t, (s) -> new InputState());
    }

    @Override
    public boolean isKeyPressed(int key) {
        var state = PRESSED_KEYS.get(key);
        return state != null && state.isPressed();
    }

    public boolean ignoreKeyCode(int keyCode) {
        return false;
    }

    public boolean onKeyInputPre(int keyCode, int scanCode, int modifiers, int action) {
        if (keyCode != -1) {
            boolean pressed = action != GLFW.GLFW_RELEASE;
            InputState state = getKeyStateOrCreate(keyCode);
            if (pressed) {

                if (!state.isPressed()) {

                    if (!ignoreKeyCode(keyCode)) {
                        state.setPressed(true);
                        return true;
                    }
                }
            } else {
                state.setPressed(false);
                return true;
            }
        }
        return false;
    }

    public boolean onKeyInput(int keyCode, int scanCode, int modifiers, int action) {
        // Update record key states
        boolean stateChange = onKeyInputPre(keyCode, scanCode, modifiers, action);
        // fire event to ask if the input is consumed
        Event<Keyboard> hardWareInput = new Event<>(mc.keyboard, true, false, keyCode, scanCode, action, modifiers);
        Listener.getKeyboardInput().handleValue(hardWareInput);
        if (hardWareInput.isCancelled()) {
            return true;
        }

        // will trigger Click handler
        boolean isKeyClicking = action != GLFW.GLFW_RELEASE;
        boolean canceled = checkKeyBindsForChanges(keyCode, stateChange, isKeyClicking);

        return canceled;
    }

    public boolean onMouseClick(int mouseX, int mouseY, int eventButton, int action, int mode) {
        boolean cancel = false;
        int transferedKeyCode = KeyCode.getKeyCodeFromMouseAction(eventButton);
        if (eventButton != -1) {
            boolean isMouseClicked = action == GLFW.GLFW_PRESS;
            // Update the cached pressed keys status
            boolean stateChange = onKeyInputPre(transferedKeyCode, 0, 0, action);
            Event<Mouse> hardWareInput = new Event<>(mc.mouse, true, false, eventButton, action, mode);
            Listener.getMouseButton().handleValue(hardWareInput);
            if (hardWareInput.isCancelled()) {
                return true;
            }
            cancel = this.checkKeyBindsForChanges(transferedKeyCode, stateChange, isMouseClicked);
        }
        return cancel;
    }

    public boolean onMouseScroll(double horizontal, double vertical) {
        Event<Mouse> scrollEvent = new Event<>(mc.mouse, true, false, horizontal, vertical);
        Listener.getMouseScroll().handleValue(scrollEvent);
        if (scrollEvent.isCancelled()) {
            return true;
        }
        return false;
    }

    public boolean onCharTyped(int codePoint, int modifiers) {

        if (Character.charCount(codePoint) == 1) {
            Event<Character> charTypedInput = new Event<>((char) codePoint, true, false, codePoint, modifiers);
            Listener.getCharTyped().handleValue(charTypedInput);
            if (charTypedInput.isCancelled()) {
                return true;
            }
        } else {
            char[] var6 = Character.toChars(codePoint);
            int var7 = var6.length;

            for (int var8 = 0; var8 < var7; ++var8) {
                char c = var6[var8];
                Event<Character> charTypedInput = new Event<>((char) c, true, false, codePoint, modifiers, var8);
                Listener.getCharTyped().handleValue(charTypedInput);
                if (charTypedInput.isCancelled()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkKeyBindsForChanges(int eventKey, boolean stateChange, boolean isClicked) {
        boolean cancel = false;
        Collection<IHotKey> keybinds = this.keyBindings.get(eventKey);
        if (!keybinds.isEmpty()) {
            for (IHotKey keybind : keybinds) {
                boolean keyInput = keybind.handleKeyInput(this, eventKey, stateChange, isClicked);
                cancel |= keyInput;
            }
        }
        return cancel;
    }
}
