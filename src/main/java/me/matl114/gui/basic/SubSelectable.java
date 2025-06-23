package me.matl114.gui.basic;

public interface SubSelectable {
    public <T extends SubSelectable> T setSelected(DrawableWidget subWidget);

    public DrawableWidget getSelected();
}
