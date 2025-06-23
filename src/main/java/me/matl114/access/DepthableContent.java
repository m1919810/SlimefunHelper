package me.matl114.access;

public interface DepthableContent {
    public int getExtraDepth();
    public <T extends DepthableContent> T setExtraDepth(int v);
}
