package me.matl114.hacks.modules.inv;

import com.google.common.util.concurrent.Runnables;
import java.util.*;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.slimefun.SlimefunGuide;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.TaskManagers;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.ListRef;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;

public class QuickButton extends BaseModule {
    public final ModulePath quickButtons = makePath(Configs.INV_CONFIG, "quick-buttons");
    public final FlagRef enable =
            flagBuilder(quickButtons.add("enable-buttons")).build();

    public QuickButton() {
        super("QuickButton");
        bindFlag(enable);
    }

    public static int resizeCreativeYv(int y) {
        return y - 30;
    }

    public final ListRef taskList = builder(quickButtons.add("button-tasks"), ListRef.TYPE)
            .defaultValue(List.of(InvExtra.CLEAR_KEEP, FastInv.TAKE_ALL, FastInv.SAVE_ALL, SlimefunGuide.OPEN_GUIDE))
            .build();

    public final ListRef toggleList = builder(quickButtons.add("button-toggles"), ListRef.TYPE)
            .defaultValue(List.of("keep-inv", "fast-inv", "auto-store", "left-one"))
            .build();

    //    @Override
    //    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
    //        super.addCustomWidgets(acceptor, dx, dy, dblank);
    //        var subScreen = createSubWidget(0, dblank, dx, dy);
    //        acceptor.accept(subScreen);
    //        subScreen.addDrawableChild(
    //            createLabel(quickButtons.add("button-tasks").asString(), 0,0, indexWidth, dy)
    //        );
    //        subScreen.addDrawableChild(
    //            createExecuteButton("widget.gui.constants.open-list-edit", ButtonAction.run(()->{
    //                MutableObject<ListMultiSelectWidget<String>> multiSelectWidget = new MutableObject<>(null);
    //                new ConfirmingWidgetScreen(
    //                    Text.translatable("widget.quick-button.button-editor.button-task"),
    //                    (cl) -> {
    //                        multiSelectWidget.setValue(
    //                            ListMultiSelectWidget.stringCollection(
    //                                TaskManagers.getTaskManager().getTasks().keySet().stream().toList(),
    //                                multiSelectWidget.getValue() == null ? taskList.get().stream().map(s ->
    // TaskManagers.PREFIX_BUTTON_TASKS + s).collect(Collectors.toSet()) : multiSelectWidget.getValue().buildSelected(),
    //                                0,0,330, cl.getContentHeight(),
    //                                20
    //                            )
    //                        );
    //                        return multiSelectWidget.getValue();
    //                    },
    //                    ()->{
    //
    //                    }
    //                )
    //            }), indexWidth + blankWidth, 0, dx - indexWidth - blankWidth, dy)
    //        );
    //    }

    //

    private static final int buttonHeight = 12;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPostInitializeScreen().getChannel(HandledScreen.class), this::onHandledScreenInitialized);
    }

    public void onHandledScreenInitialized(Event<HandledScreen<?>> event) {
        if (enable.get()) {
            initButton(event.context);
        }
    }

    public void initButton(HandledScreen<?> handledScreen) {
        HandledScreenAccess access = HandledScreenAccess.of(handledScreen);
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
        int buttonWidth = (access.getScreenBackgroundX() / 4);
        int size1 = buttonTasks.size();
        line += ((size1 - 1) / 4) + 1;
        int size2 = buttonToggles.size();
        line += ((size2 - 1) / 4) + 1;
        int y0 = -(buttonHeight) * line - 6;
        int x0 = 0;
        DynamicSubScreenWidget widgetSet = new DynamicSubScreenWidget(
                ValueAccessor.ofIgnore(access::getScreenX),
                ValueAccessor.of(() -> handledScreen instanceof CreativeInventoryScreen
                        ? resizeCreativeYv(access.getScreenY())
                        : access.getScreenY()));
        widgetSet.addTo(handledScreen);
        for (Map.Entry<String, Optional<FlagRef>> entry : buttonToggles.entrySet()) {
            final String key = entry.getKey();
            final FlagRef flagRef = entry.getValue().orElse(null);
            String fullKey = TaskManagers.PREFIX_BUTTON_TOGGLE + "." + key;
            final Runnable stateChange =
                    flagRef != null ? HotKeyUtils.wrapFlagAsToggle(fullKey, flagRef) : Runnables.doNothing();
            createToggleButton(
                            () -> Text.literal(key),
                            List::of,
                            ValueAccessor.of(() -> flagRef == null || flagRef.get(), (bl) -> {
                                boolean current = flagRef == null || flagRef.get();
                                if (current != bl) {
                                    stateChange.run();
                                }
                            }),
                            x0 * (buttonWidth + 1),
                            y0,
                            buttonWidth,
                            buttonHeight)
                    .addToSub(widgetSet);
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
            createExecuteButton(
                            () -> Text.literal(entry.getKey()),
                            List::of,
                            ButtonAction.run(task),
                            x0 * (buttonWidth + 1),
                            -y0,
                            buttonWidth,
                            buttonHeight)
                    .addToSub(widgetSet);
            x0 += 1;
            if (x0 == 4) {
                x0 = 0;
                y0 += (buttonHeight + 2);
            }
        }
    }
}
