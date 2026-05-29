package me.matl114.hacks.modules.inv;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.managers.input.SimpleInputManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class GuiMove extends BaseModule {
    public GuiMove() {
        bindFlag(enable);
    }

    public ModulePath path = makePath(Configs.INV_CONFIG, "inventory.gui-move");

    public final FlagRef enable = flagBuilder(path.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(path.addHotkey(), new MultiKeyBind(), path.addEnable()).build();

    public final FlagRef allGui = flagBuilder(path.add("all-gui-move")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getKeyboardInput(), this::onKeyInput);
        registerListener(Listener.getPostSetScreen(), this::onPostSetScreen);
    }

    public KeyBinding[] inputBindings;

    private void initBinding() {
        if (inputBindings == null) {
            inputBindings = new KeyBinding[] {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
                mc.options.sneakKey,
                mc.options.sprintKey
            };
        }
    }

    public void onKeyInput(Event<Keyboard> eventInput) {
        if (checkNull()) return;
        if (enable.get()) {
            if (skip()) return;
            int keyCode = eventInput.getArgs(0);
            int action = eventInput.getArgs(2);
            initBinding();
            for (var re : inputBindings) {
                if (handle(re, keyCode, action)) {
                    eventInput.cancel();
                }
            }
        }
    }

    public boolean handle(KeyBinding keyBinding, int keyCode, int action) {
        if (keyBinding.boundKey.getCode() != keyCode) {
            return false;
        }
        if (action == GLFW.GLFW_PRESS) {
            keyBinding.setPressed(true);
            return true;
        } else if (action == GLFW.GLFW_RELEASE) {
            keyBinding.setPressed(false);
            return true;
        }
        return false;
    }

    public void onPostSetScreen(Event<Screen> event) {
        if (checkNull()) return;
        if (enable.get() && event.context != null) {
            initBinding();
            for (var re : inputBindings) {
                re.setPressed(SimpleInputManager.getInstance().isKeyPressed(re.boundKey.getCode()));
            }
        }
    }

    public boolean skip() {
        if (mc.currentScreen == null
                || mc.currentScreen instanceof CreativeInventoryScreen
                || mc.currentScreen instanceof ChatScreen
                || mc.currentScreen instanceof SignEditScreen
                || mc.currentScreen instanceof AnvilScreen
                || mc.currentScreen instanceof CommandBlockScreen
                || mc.currentScreen instanceof StructureBlockScreen
                || mc.currentScreen.getFocused() instanceof TextFieldWidget) return true;
        if (allGui.get()) return false;
        return !(mc.currentScreen instanceof HandledScreen<?>);
    }
}
