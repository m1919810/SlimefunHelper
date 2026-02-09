package me.matl114.versioned.impl;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VNbt;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.visitor.StringNbtWriter;

public class Nbt_v1_21_11 implements VNbt {
    @Override
    public String writeNbt(NbtElement element) {
        StringNbtWriter writer = new StringNbtWriter();
        element.accept(writer);
        return writer.getString();
    }

    @Override
    public NbtElement readNbt(String element) {
        try{
            return StringNbtReader.fromOps(ItemStackUtils.registry().getOps(NbtOps.INSTANCE)).read(element);
        }catch (CommandSyntaxException e){
            throw new RuntimeException("Could not deserialize found element ", e);
        }
    }

    @Override
    public NbtElement readNbtNoRegistry(String element) {
        try{
            return StringNbtReader.fromOps(NbtOps.INSTANCE).read(element);
        }catch (CommandSyntaxException e){
            throw new RuntimeException("Could not deserialize found element ", e);
        }
    }
}
