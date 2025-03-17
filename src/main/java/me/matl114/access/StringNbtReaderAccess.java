package me.matl114.access;

import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;

public interface StringNbtReaderAccess {
    public NbtElement type(String type);
    static StringNbtReaderAccess of(StringNbtReader reader) {
        return (StringNbtReaderAccess) reader;
    }
}
