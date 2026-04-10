package me.matl114.versioned.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import me.matl114.versioned.impl.Nbt_v1_21_11;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

public interface VNbt {
    VNbt INSTANCE = new Nbt_v1_21_11();

    public static VNbt getInstance() {
        return INSTANCE;
    }

    public String writeNbt(NbtElement element);

    public NbtElement readNbt(String element);

    public NbtElement readNbtNoRegistry(String element);

    Codec<NbtElement> CODEC = Codec.PASSTHROUGH.comapFlatMap(
            (dynamic) -> {
                NbtElement nbtElement =
                        (NbtElement) dynamic.convert(NbtOps.INSTANCE).getValue();
                return DataResult.success(nbtElement == dynamic.getValue() ? nbtElement.copy() : nbtElement);
            },
            (nbt) -> {
                return new Dynamic<>(NbtOps.INSTANCE, nbt.copy());
            });
}
