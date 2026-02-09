package me.matl114.versioned.impl;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.matl114.versioned.api.VNbt;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.visitor.StringNbtWriter;

import java.util.regex.Pattern;

public class Nbt_v1_21_1 implements VNbt {
    @Override
    public String writeNbt(NbtElement element) {
        StringNbtWriter writer = new StringNbtWriter();
        return writer.apply(element);
    }

    @Override
    public NbtElement readNbt(String element) {
        try{
            return new StringNbtReader(new StringReader(element.replace("\\n", "\n").replace("\\s", "\s"))).parseElement();
        }catch (CommandSyntaxException e){
            throw new RuntimeException("Could not deserialize nbt element ", e);
        }
    }

    @Override
    public NbtElement readNbtNoRegistry(String element) {
        return readNbt(element);
    }
}
