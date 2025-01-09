package me.matl114.SlimefunMixin.AccessMixin;

import me.matl114.Access.StringNbtReaderAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Environment(EnvType.CLIENT)
@Mixin(StringNbtReader.class)
public abstract class StringNbtReaderMixin implements StringNbtReaderAccess {
    @Shadow
    protected abstract NbtElement parsePrimitive(String input);
    public NbtElement type(String input){
        return parsePrimitive(input);
    }
}
