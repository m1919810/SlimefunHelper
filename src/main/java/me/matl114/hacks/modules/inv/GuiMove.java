package me.matl114.hacks.modules.inv;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.KeyboardAction;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.managers.input.SimpleInputManager;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class GuiMove extends BaseModule {
    public GuiMove() {
        super("GuiMove");
        bindFlag(enable);
    }

    public ModulePath path = makePath(Configs.INV_CONFIG, "inventory.gui-move");

    public final FlagRef enable = flagBuilder(path.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(path.addHotkey(), new MultiKeyBind(), path.addEnable()).build();

    public final FlagRef allGui = flagBuilder(path.add("all-gui-move")).build();

    public final FlagRef noShiftInChest = builder(path.add("no-shift-in-chest"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getKeyboardInput(), this::onKeyInput);
        registerListener(Listener.getPostSetScreen(), this::onPostSetScreen);
    }

    public KeyBinding[] inputBindings;
    public KeyBinding[] inputBindingsNoSneak;

    private void initBinding() {
        if (inputBindings == null || inputBindingsNoSneak == null) {
            inputBindings = new KeyBinding[] {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
                mc.options.sneakKey,
                mc.options.sprintKey
            };
            inputBindingsNoSneak = new KeyBinding[] {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
                mc.options.sprintKey
            };
        }
    }

    public KeyBinding[] getBindings() {
        initBinding();
        return noShiftInChest.get() && mc.currentScreen instanceof HandledScreen<?>
                ? inputBindingsNoSneak
                : inputBindings;
    }

    public void onKeyInput(Event<KeyboardAction> eventInput) {
        if (checkNull()) return;
        if (enable.get()) {
            if (skip()) return;
            int keyCode = eventInput.context.keyCode();
            int action = eventInput.context.action();
            for (var re : getBindings()) {
                if (handle(re, keyCode, action)) {}
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
            for (var re : getBindings()) {
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
                || mc.currentScreen.getFocused() instanceof TextFieldWidget
                || (mc.currentScreen.getFocused() instanceof DrawableWidget widget && checkCustomWidget(widget)))
            return true;
        if (allGui.get()) return false;
        return !(mc.currentScreen instanceof HandledScreen<?>);
    }

    public boolean checkCustomWidget(DrawableWidget drawableWidget) {
        DrawableWidget drawable = WidgetUtils.getFocusedWidget(drawableWidget);
        return drawable != null && WidgetUtils.isInputWidget(drawable);
    }
}
