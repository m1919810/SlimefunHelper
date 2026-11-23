package me.matl114.hacks.utils.clickgui;

import me.matl114.hacks.modules.task.ClickGui;
import me.matl114.hacks.modules.task.ClickGui.ClickGuiMetaData;

public interface ClickGuiFactory {
    void openClickGui(ClickGui clickGui, ClickGuiMetaData meta);
}
