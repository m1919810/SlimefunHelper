package me.matl114.hacks.modules.inv;

import com.google.common.util.concurrent.Runnables;
import java.util.*;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ButtonElement;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.slimefun.SlimefunGuide;
import me.matl114.managers.Configs;
import me.matl114.managers.TaskManagers;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.ListRef;
import me.matl114.managers.task.ToggleManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;

public class QuickButtons extends BaseModule {
    public final ModulePath quickButtons = makePath(Configs.INV_CONFIG, "quick-buttons");
    public final FlagRef enable = flagBuilder(quickButtons.add("enable-buttons")).build();

    public QuickButtons() {
        bindFlag(enable);
    }

    public static int resizeCreativeYv(int y) {
        return y - 30;
    }

    public final ListRef taskList = builder(quickButtons.add("button-tasks"), ListRef.TYPE)
            .defaultValue(List.of(KeepInv.CLEAR_KEEP, FastChest.TAKE_ALL, FastChest.SAVE_ALL, SlimefunGuide.OPEN_GUIDE))
            .build();

    public final ListRef toggleList = builder(quickButtons.add("button-toggles"), ListRef.TYPE)
            .defaultValue(List.of("keep-inv", "fast-inv", "auto-store", "left-one"))
            .build();

    //

    private static final int buttonHeight = 12;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostInitializeScreen(), this::onHandledScreenInitialized);
    }

    public void onHandledScreenInitialized(Event<Screen> event) {
        if (event.context() instanceof HandledScreen<?> screen) {
            if (enable.get()) {
                initButton(screen);
            }
        }
    }

    public void initButton(HandledScreen<?> handledScreen) {
        int xv, yv;
        HandledScreenAccess access = HandledScreenAccess.of(handledScreen);
        if (handledScreen instanceof CreativeInventoryScreen handled) {
            xv = access.getScreenX();
            yv = resizeCreativeYv(access.getScreenY());
        } else {
            xv = access.getScreenX();
            yv = access.getScreenY();
        }

        Map<String, Runnable> buttonTasks = new LinkedHashMap<>();
        for (var re : taskList.get()) {
            Runnable task = TaskManagers.getTaskManager().getTask(TaskManagers.PREFIX_BUTTON_TASKS + "." + re);
            buttonTasks.put(re, task == null ? Runnables.doNothing() : task);
        }
        Map<String, Optional<FlagRef>> buttonToggles = new LinkedHashMap<>();
        for (var re : toggleList.get()) {
            FlagRef flagRef = TaskManagers.getToggleManager().getFlag(TaskManagers.PREFIX_BUTTON_TOGGLE + "." + re);
            buttonToggles.put(re, Optional.ofNullable(flagRef));
        }

        int line = 0;
        int buttonWidth = (access.getScreenBackgroundX() / 4) - 1;
        int size1 = buttonTasks.size();
        line += ((size1 - 1) / 4) + 1;
        int size2 = buttonToggles.size();
        line += ((size2 - 1) / 4) + 1;
        int y0 = -(buttonHeight + 2) * line - 6;
        int x0 = 0;
        for (Map.Entry<String, Optional<FlagRef>> entry : buttonToggles.entrySet()) {
            final String key = entry.getKey();
            final FlagRef flagRef = entry.getValue().orElse(null);
            String fullKey = TaskManagers.PREFIX_BUTTON_TOGGLE + "." + key;
            final Runnable stateChange =
                    flagRef != null ? ToggleManager.wrapFlagAsToggle(fullKey, flagRef) : Runnables.doNothing();
            ExecutableWidget widget = ExecutableWidget.instance(
                            xv + x0 * (buttonWidth + 1), yv + y0, buttonWidth, buttonHeight)
                    .setElementHandler(
                            new ButtonElement(TextProvider.of(Text.literal(key)), ((element, widget1, mouseButton) -> {
                                stateChange.run();
                                if (flagRef != null) {
                                    widget1.setAlpha(flagRef.get() ? 1.0f : 0.4f);
                                }
                                return true;
                            })))
                    .addTo(handledScreen);
            widget.setAlpha((flagRef != null && flagRef.get()) ? 1.0f : 0.4f);
            x0 += 1;
            if (x0 == 4) {
                x0 = 0;
                y0 += (buttonHeight + 2);
            }
        }
        // 换行
        x0 = 0;
        y0 = buttonHeight + 2;
        for (Map.Entry<String, Runnable> entry : buttonTasks.entrySet()) {
            final Runnable task = entry.getValue();
            ExecutableWidget.instance(xv + x0 * (buttonWidth + 1), yv - y0, buttonWidth, buttonHeight)
                    .setElementHandler(
                            new ButtonElement(TextProvider.of(Text.literal(entry.getKey())), ButtonAction.run(task)))
                    .addTo(handledScreen);
            x0 += 1;
            if (x0 == 4) {
                x0 = 0;
                y0 += (buttonHeight + 2);
            }
        }
    }
}
