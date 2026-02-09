package me.matl114.versioned.api;

import me.matl114.versioned.impl.Nbt_v1_21_11;
import net.minecraft.nbt.NbtElement;

public interface VNbt {
    VNbt INSTANCE = new Nbt_v1_21_11();
    public static VNbt getInstance(){
        return INSTANCE;
    }

    public String writeNbt(NbtElement element);

    public NbtElement readNbt(String element);

    public NbtElement readNbtNoRegistry(String element);
}
