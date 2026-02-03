package me.matl114.utils.impl.itemdb;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.matl114.utils.impl.codecs.NullCodec;
import net.minecraft.item.ItemStack;

public record ItemStackDataWithAmount(ItemStackData stackReference, int count) {
    public static final ItemStackDataWithAmount EMPTY = new ItemStackDataWithAmount(ItemStackData.EMPTY, 0);

    public static ItemStackDataWithAmount of(ItemStack itemStack) {
        if (itemStack.isEmpty()) return EMPTY;
        return new ItemStackDataWithAmount(ItemStackData.wrapCopy(itemStack), itemStack.getCount());
    }

    public ItemStackDataWithAmount asCount(int v){
        return new ItemStackDataWithAmount(stackReference, v);
    }

    public ItemStack getAsItemStack(){
        return stackReference.getIcon().copyWithCount(count);
    }

    public ItemStack getAsPrototype(){
        return stackReference.getIcon();
    }


    public static Codec<ItemStackDataWithAmount> createCodecOf(Codec<ItemStackData> itemStackData){
        Codec<ItemStackDataWithAmount> codec = RecordCodecBuilder.create((instance)->instance.group(
            itemStackData.fieldOf("typeid").forGetter(ItemStackDataWithAmount::stackReference),
            Codec.INT.fieldOf("amount").forGetter(ItemStackDataWithAmount::count)
        ).apply(instance, ItemStackDataWithAmount::new));
        return new NullCodec<>(codec, s -> s.stackReference == ItemStackData.EMPTY, EMPTY);
    }


}
