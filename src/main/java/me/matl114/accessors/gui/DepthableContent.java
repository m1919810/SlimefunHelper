package me.matl114.accessors.gui;

public interface DepthableContent {
    public int getExtraDepth();
    public <T extends DepthableContent> T setExtraDepth(int v);
}
