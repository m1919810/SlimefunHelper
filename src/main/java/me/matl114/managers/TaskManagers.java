package me.matl114.managers;

import lombok.Getter;
import me.matl114.managers.input.*;
import me.matl114.managers.task.TaskManager;
import me.matl114.managers.task.ToggleManager;

public class TaskManagers {
    public static void init() {
        KeyCode.init();
    }

    public static final String PREFIX_BUTTON_TOGGLE = "button-toggle";
    public static final String PREFIX_BUTTON_TASKS = "button-task";
    public static final String PREFIX_HOTKEY = "hotkeys-toggle";
    public static final String PREFIX_SIMPLE = "simple-toggle";

    public static final String PREFIX_CONFIG = "toggle";
    public static final String PREFIX_HOTKEY_TASKS = "hotkeys";

    @Getter
    private static final TaskManager taskManager = TaskManager.of();

    @Getter
    private static final ToggleManager toggleManager = ToggleManager.of();
}
