package me.matl114.versioned.impl;

import me.matl114.versioned.api.VEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;

public class Entity_v1_21_4 implements VEntity {
    @Override
    public NbtCompound serializeNBT(Entity entity) {
        var nbt = new NbtCompound();
        entity.writeNbt(nbt);
        return nbt;
    }
}
