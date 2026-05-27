package me.matl114.gui.complex.task;

import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.Objects;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.presets.index.IndexedScreen;
import me.matl114.gui.presets.lists.ListEntryWidgetController;
import me.matl114.gui.presets.lists.ListUnmodifiableWidget;
import me.matl114.managers.task.TaskManager;
import me.matl114.utils.CollectionUtils;
import net.minecraft.text.Text;

public class TaskManageScreen extends IndexedScreen<Pair<String, TaskManager>, ListUnmodifiableWidget> {
    private static String selectingTaskManager;
    protected Map<String, TaskManager> tasks;

    public TaskManageScreen(Map<String, TaskManager> list, int backgroundWidth, int backgroundHeight) {
        super(list.entrySet().stream().map(CollectionUtils::entryToPair).toList(), backgroundWidth, backgroundHeight);
    }

    @Override
    public void setGlobal(Pair<String, TaskManager> config) {
        selectingTaskManager = config.getFirst();
    }

    @Override
    public Pair<String, TaskManager> getGlobal() {
        return Pair.of(selectingTaskManager, tasks.get(selectingTaskManager));
    }

    @Override
    protected ElementHandler createIndexHandler(Pair<String, TaskManager> val) {
        return new ButtonElement(
                        TextProvider.of(Text.literal(val.getFirst())), ButtonAction.run(() -> this.setGlobal(val)))
                .setInactiveId(ButtonElement.BUTTON)
                .setActiveId(ButtonElement.BUTTON_HIGHLIGHT)
                .setActivePredicate((el) -> Objects.equals(selectingTaskManager, val.getFirst()));
    }

    @Override
    protected ListUnmodifiableWidget createSelectingDisplayWidget(Pair<String, TaskManager> val) {
        ListEntryWidgetController controller = ListEntryWidgetController.immutable(
                val.getSecond().getTasks().entrySet().stream()
                        .map(CollectionUtils::entryToPair)
                        .toList(),
                pair -> {
                    var keyValue = new SubScreenWidget(0, 0, 0, 0);
                    return keyValue;
                },
                buttonHeight,
                backgroundWidth - configButtonWidth - 20);
        return new ListUnmodifiableWidget(controller, 0, 0, backgroundWidth - configButtonWidth - 20, backgroundHeight);
    }

    @Override
    public void saveSelected() {}
}
