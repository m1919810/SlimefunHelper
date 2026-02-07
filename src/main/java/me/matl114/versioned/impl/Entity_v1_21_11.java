package me.matl114.versioned.impl;

import me.matl114.versioned.api.VEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;

public class Entity_v1_21_11 implements VEntity {
    @Override
    public NbtCompound serializeNBT(Entity entity) {
        var writeView = NbtWriteView.create(ErrorReporter.EMPTY);
        entity.writeData(writeView);
        return writeView.getNbt();
    }
}
