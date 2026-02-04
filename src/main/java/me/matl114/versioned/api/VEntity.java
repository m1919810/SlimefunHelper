package me.matl114.versioned.api;

import me.matl114.versioned.impl.Entity_v1_21_1;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;

public interface VEntity {
    public static final VEntity INSTANCE = new Entity_v1_21_1();

    public static VEntity getInstance() {
        return INSTANCE;
    }

    public static NbtCompound saveEntityNbt(Entity entity){
        return getInstance().serializeNBT(entity);
    }

    public NbtCompound serializeNBT(Entity entity);


}
