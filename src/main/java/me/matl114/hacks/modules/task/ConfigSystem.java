package me.matl114.hacks.modules.task;

import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.Text;

public class ConfigSystem extends BaseModule {
    public static final String[] OPEN_MENU_HOTKEY = new String[] {"hotkeys", "open-menu"};

    public ConfigSystem() {}

    public final KeyBindRef keyBind = hotkey(Configs.HOTKEY_CONFIG, OPEN_MENU_HOTKEY)
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_G))
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::openConfigMenu))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostInitializeScreen(), this::onScreenInitialize);
    }

    public void openConfigMenu() {
        MainTasks.openConfigNewStyleScreen();
    }

    public void openConfigScreen(Config config) {
        MainTasks.openConfigScreen(config);
    }

    private final ContentDelegateWidget<ExecutableWidget> delegateWidget = McWidgetHelpers.createDynamicDelegateWidget(
            () -> mc.currentScreen instanceof MultiplayerScreen mp ? mp.width - 205 : 0, () -> 5, 100, 20);

    public void onScreenInitialize(Event<Screen> screenEvent) {
        if (screenEvent.context() instanceof MultiplayerScreen mp) {
            ExecutableWidget executableWidget = ExecutableWidget.instance(0, 0, 100, 20)
                    .setElementHandler(new ButtonElement(
                            TextProvider.of(Text.literal("SlimefunHelper")),
                            ButtonAction.run(() -> MainTasks.getConfigSystem().openConfigScreen(Configs.HTTP_CONFIG))));
            delegateWidget.setContentDelegate(executableWidget);
            ScreenAccess.of(mp).removeChildFrom(delegateWidget);
            delegateWidget.addTo(mp);
        }
    }
}
