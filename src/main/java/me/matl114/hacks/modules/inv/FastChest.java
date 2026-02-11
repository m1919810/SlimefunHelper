package me.matl114.hacks.modules.inv;

import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.TaskManagers;

public class FastChest extends BaseModule {

    public FastChest() {}

    public static final String TAKE_ALL = "take-all";
    public static final String SAVE_ALL = "save-all";

    @Override
    public void registerAll() {
        super.registerAll();
        TaskManagers.getTaskManager().register(TaskManagers.PREFIX_BUTTON_TASKS + "." + TAKE_ALL, this::takeAll);
        TaskManagers.getTaskManager().register(TaskManagers.PREFIX_BUTTON_TASKS + "." + SAVE_ALL, this::saveAll);
    }

    public void takeAll() {
        InvTasks.takeAllContainerItem();
    }

    public void saveAll() {
        InvTasks.saveAllPlayerItem();
    }
}
