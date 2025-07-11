package me.matl114.utils.UtilClass;

import lombok.val;

public interface ProgressWrapper<ARGUMENT> {
    public void preProgress(ARGUMENT args);
    public void postProgress(ARGUMENT args);
    public boolean stillWrap(ARGUMENT args);
}
