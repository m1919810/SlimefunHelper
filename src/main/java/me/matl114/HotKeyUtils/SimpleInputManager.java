package me.matl114.HotKeyUtils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.HashMap;

public class SimpleInputManager implements IInputManager{
    protected static final SimpleInputManager instance=new SimpleInputManager();
    protected SimpleInputManager() {
        this.mc=MinecraftClient.getInstance();
    }
    protected final Multimap<Integer, IHotKey> keyBindings= ArrayListMultimap.<Integer, IHotKey>create();
    public void registerHotKeys(IHotKey key){
        for (Integer i:key.getRelatedKeyCode()){
            keyBindings.put(i,key);
        }
    }
    public static SimpleInputManager getInstance() {
        return instance;
    }
    protected MinecraftClient mc;
    public MinecraftClient getClient() {
        return mc;
    }

    protected HashMap<Integer,InputState> PRESSED_KEYS=new HashMap<>();
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

        // Update record key states, return value represents whether the state of this key change
        boolean stateChange= onKeyInputPre(keyCode, scanCode, modifiers, action);

        //will trigger Click handler
        boolean isKeyClicking = action != GLFW.GLFW_RELEASE;
        boolean canceled = checkKeyBindsForChanges(keyCode,stateChange,isKeyClicking);

        return canceled;
    }
    public boolean onMouseClick(int mouseX, int mouseY, int eventButton, int action){
        boolean cancel = false;

        if (eventButton != -1)
        {
            boolean isMouseClicked = action == GLFW.GLFW_PRESS;
            int transferedKeyCode=KeyCode.getKeyCodeFromMouseAction(eventButton);
            // Update the cached pressed keys status
            boolean stateChange= onKeyInputPre(transferedKeyCode, 0, 0, action);
            cancel = this.checkKeyBindsForChanges(transferedKeyCode,stateChange,isMouseClicked);
        }
        return cancel;
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
