package me.matl114.managers;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import me.matl114.listenerUtils.Listener;
import me.matl114.utils.UtilClass.Event;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SimpleInputManager implements IInputManager{
    protected static final SimpleInputManager instance=new SimpleInputManager();
    protected SimpleInputManager() {
        this.mc=MinecraftClient.getInstance();
    }
    protected final Map<String, IHotKey> hotkeyRegistry = new HashMap<>();
    protected final Multimap<Integer, IHotKey> keyBindings= LinkedHashMultimap.<Integer, IHotKey>create();
    public IHotKey getHotkey(String id){
        return hotkeyRegistry.get(id);
    }
    public void registerHotKeys(IHotKey key){
        hotkeyRegistry.put(key.getIdentifier(), key);
        for (Integer i:key.getRelatedKeyCode()){
            keyBindings.put(i,key);
        }
    }
    public void unregisterHotKeys(IHotKey key){
        hotkeyRegistry.remove(key.getIdentifier());
        keyBindings.entries().removeIf(e-> e.getValue() == key);
    }
    public static SimpleInputManager getInstance() {
        return instance;
    }
    protected MinecraftClient mc;
    public MinecraftClient getClient() {
        return mc;
    }

    protected HashMap<Integer, InputState> PRESSED_KEYS=new HashMap<>();
    public synchronized InputState getKeyState(int t){
        return PRESSED_KEYS.computeIfAbsent(t,(s)->new InputState());
    }
    public boolean ignoreKeyCode(int keyCode){
        return false;
    }
    public boolean onKeyInputPre(int keyCode, int scanCode, int modifiers, int action)
    {
        if (keyCode!=-1)
        {
            boolean pressed = action != GLFW.GLFW_RELEASE;
            InputState state = getKeyState(keyCode);
            if (pressed)
            {

                if (!state.isPressed())
                {

                    if (!ignoreKeyCode(keyCode))
                    {
                        state.setPressed(true);
                        return true;
                    }
                }
            }
            else
            {
                state.setPressed(false);
                return true;
            }
        }
        return false;
    }
    public boolean onKeyInput(int keyCode, int scanCode, int modifiers, int action){
        Event<Integer> hardWareInput = new Event<>(keyCode, true, false, keyCode, scanCode, action, modifiers);
        Listener.getKeyboardInputListener().handleValue(hardWareInput);
        if(hardWareInput.isCancelled()){
            return true;
        }
        keyCode = hardWareInput.context();
        // Update record key states, return value represents whether the state of this key change
        boolean stateChange= onKeyInputPre(keyCode, scanCode, modifiers, action);

        //will trigger Click handler
        boolean isKeyClicking = action != GLFW.GLFW_RELEASE;
        boolean canceled = checkKeyBindsForChanges(keyCode,stateChange,isKeyClicking);

        return canceled;
    }
    public boolean onMouseClick(int mouseX, int mouseY, int eventButton, int action, int mode){
        boolean cancel = false;

        Event<Integer> hardWareInput = new Event<>(eventButton, true, false, eventButton, action, mode);
        Listener.getMouseButtonListener().handleValue(hardWareInput);
        if(hardWareInput.isCancelled()){
            return true;
        }
        int transferedKeyCode = KeyCode.getKeyCodeFromMouseAction(eventButton);
        if (eventButton != -1)
        {
            boolean isMouseClicked = action == GLFW.GLFW_PRESS;
            // Update the cached pressed keys status
            boolean stateChange= onKeyInputPre(transferedKeyCode, 0, 0, action);
            cancel = this.checkKeyBindsForChanges(transferedKeyCode,stateChange,isMouseClicked);
        }
        return cancel;
    }

    public boolean onMouseScroll(double horizontal, double vertical){
        Event<Void> scrollEvent = new Event<>(null, true, false, horizontal, vertical);
        Listener.getMouseScrollListener().handleValue(scrollEvent);
        if (scrollEvent.isCancelled()){
            return true;
        }
        return false;
    }

    public boolean onCharTyped(int codePoint, int modifiers){

        if (Character.charCount(codePoint) == 1) {
            Event<Character> charTypedInput = new Event<>((char)codePoint, true, false, codePoint, modifiers);
            Listener.getCharTypedListener().handleValue(charTypedInput);
            if(charTypedInput.isCancelled()){
                return true;
            }
        } else {
            char[] var6 = Character.toChars(codePoint);
            int var7 = var6.length;

            for(int var8 = 0; var8 < var7; ++var8) {
                char c = var6[var8];
                Event<Character> charTypedInput = new Event<>((char)c, true, false, codePoint, modifiers, var8);
                Listener.getCharTypedListener().handleValue(charTypedInput);
                if(charTypedInput.isCancelled()){
                    return true;
                }
            }
        }
        return false;
    }


    public boolean checkKeyBindsForChanges(int eventKey,boolean stateChange,boolean isClicked)
    {
        boolean cancel = false;
        Collection<IHotKey> keybinds = this.keyBindings.get(eventKey);
        if (!keybinds.isEmpty() )
        {
            for (IHotKey keybind : keybinds)
            {
                cancel |= keybind.handleKeyInput(this,eventKey, stateChange,isClicked);
            }
        }
        return cancel;
    }
}
