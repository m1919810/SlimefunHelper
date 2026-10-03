package me.matl114.hacks.utils.gui;

import me.matl114.gui.basic.TextAlign;
import me.matl114.managers.config.ConfigEnum;

public enum AlignType implements ConfigEnum {
    LEFT(TextAlign.LEFT),
    MIDDLE(TextAlign.MIDDLE),
    RIGHT(TextAlign.RIGHT),;
    final TextAlign mapping;
    AlignType(TextAlign mapping){
        this.mapping = mapping;
    }

    public TextAlign textAlign() {
        return mapping;
    }

    @Override
    public String getConfigEnumType() {
        return "text_align_type";
    }
}
