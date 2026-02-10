package me.matl114.utils.entity;

public interface ProgressWrapper<ARGUMENT> {
    public void preProgress(ARGUMENT args);

    public void postProgress(ARGUMENT args);

    public boolean stillWrap(ARGUMENT args);
}
