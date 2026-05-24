package me.matl114.hacks.modules.task;

import java.util.ArrayList;
import java.util.List;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.gui.presets.single.SimpleScreen;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class ConfigSystem extends BaseModule {
    public final ModulePath hotkeys = makePath(Configs.HOTKEY_CONFIG, "hotkeys");

    public ConfigSystem() {}

    public final KeyBindRef keyBind = hotkey(
                    Configs.HOTKEY_CONFIG, hotkeys.add("open-menu").toPath())
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_G))
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::openConfigMenu))
            .build();

    public final KeyBindRef optionsKeyBind = hotkey(
                    Configs.HOTKEY_CONFIG, hotkeys.add("open-options-menu").toPath())
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.asHandler(this::openGameOptionsMenu))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostInitializeScreen(), this::onScreenInitialize);
    }
    // todo:
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

    public void openGameOptionsMenu() {
        GameOptions options = mc.options;
        List<SimpleOption<?>> options1 = new ArrayList<>();
        for (var field : GameOptions.class.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getType().isAssignableFrom(SimpleOption.class)) {
                try {
                    SimpleOption<?> option = (SimpleOption<?>) field.get(options);
                    if (option != null) {
                        options1.add(option);
                    }
                } catch (Throwable e) {

                }
            }
        }
        ScrollableListWidget widget = new ScrollableListWidget(20, 20, 360, 280);
        int yLevel = 0;
        for (var sim : options1) {
            var re = sim.createWidget(mc.options);
            widget.addScrollingWidget(new ContentDelegateWidget<>(20, yLevel, 320, 40).setContentDelegate(re));
            //                SubScreenWidget.instance(20, 0 , 320, 40)
            //                    .addDrawableChild(
            ////                        DisplayWidget.instance(0,0, 150, 40)
            ////                            .setRenderHandler(
            ////                                new ButtonElement(TextProvider.of(sim.))
            ////                            )
            //                    )
            //            )
            yLevel += re.getHeight();
        }
        SimpleScreen screen = new SimpleScreen(Text.literal("Options Screen"), 400, 320, widget);

        ScreenAccess.of(screen).openFromCurrent();
    }
}
