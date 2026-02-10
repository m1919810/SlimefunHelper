package me.matl114.utils.chat;

import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.Style;

public class SimpleOrderedTextVisitor implements CharacterVisitor {
    StringBuilder builder;

    public SimpleOrderedTextVisitor() {
        builder = new StringBuilder();
    }

    public SimpleOrderedTextVisitor(StringBuilder bu) {
        builder = bu;
    }

    @Override
    public boolean accept(int index, Style style, int codePoint) {
        builder.appendCodePoint(codePoint);
        return true;
    }

    public StringBuilder getContent() {
        return this.builder;
    }
}
