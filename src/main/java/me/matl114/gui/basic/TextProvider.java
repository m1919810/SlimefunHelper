package me.matl114.gui.basic;

import net.minecraft.text.Text;

public interface TextProvider {
    Text getLabel(DrawableWidget element);
    static TextProvider of(Text text){
        return (b)->text;
    }
}
