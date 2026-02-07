package me.matl114.utils.itemdb;

import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import me.matl114.utils.CustomItemStackBuilder;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VNbt;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.visitor.StringNbtWriter;

import javax.annotation.Nonnull;
import java.util.Objects;

//todo: check and use
public interface ItemStackData {
    public JsonElement getAsJson();

    public void resolveItemStack();

    public boolean isValid();

    public ItemStack getItemStack();

    public ItemStack getIcon();

    static ItemStack FAILURE = CustomItemStackBuilder.builder()
        .type(Items.BARRIER)
        .amount(1)
        .name("&c物品解析失败")
        .lore()
        .append("")
        .append("&7详细信息请检查日志")
        .endLore()
        .build();

    static ItemStack MISSING = CustomItemStackBuilder.builder()
        .type(Items.STRUCTURE_VOID)
        .amount(1)
        .name("&c物品索引缺失")
        .lore()
        .append("")
        .append("&7请修复item-database.json")
        .endLore()
        .build();

    public static ItemStack deserialize(JsonElement json){
        if(json.isJsonObject()){
            JsonObject jsonMap = json.getAsJsonObject();
            try{
                ItemStack stack = ItemStack.CODEC.decode(ItemStackUtils.registry().getOps(JsonOps.INSTANCE), jsonMap).getOrThrow().getFirst();
                return stack.isEmpty() ? ItemStack.EMPTY: stack;
            }catch (Throwable e){
                throw new JsonParseException(e);
            }
        }else{
            String jsonString = json.getAsString();
            try{
                NbtCompound nbtElement = (NbtCompound) VNbt.getInstance().readNbt(jsonString);
                ItemStack stack = ItemStack.CODEC.decode(ItemStackUtils.registry().getOps(NbtOps.INSTANCE), nbtElement).getOrThrow().getFirst();
                return stack.isEmpty()? ItemStack.EMPTY : stack;
            }catch (Throwable e){
                throw new NbtException(e.getMessage());
            }
        }
    }

    public static JsonElement serialize(ItemStack stack){
        if(stack.isEmpty()){
            return JsonNull.INSTANCE;
        }else{
            NbtCompound nbt = (NbtCompound) ItemStack.CODEC.encodeStart(ItemStackUtils.registry().getOps(NbtOps.INSTANCE), stack).getOrThrow();
            return new JsonPrimitive(VNbt.getInstance().writeNbt(nbt));
        }
    }


    public static ItemStackData wrapRaw(ItemStack stack){
        if(stack.isEmpty()){
            return EMPTY;
        }else{
            return new Wrapper(stack);
        }
    }

    public static ItemStackData wrapCopy(ItemStack stack){
        if(stack.isEmpty()){
            return EMPTY;
        }else {
            return new Wrapper(stack.copyWithCount(1));
        }
    }




    public static final ItemStackData EMPTY = new ItemStackData() {

        @Override
        public JsonElement getAsJson() {
            return JsonNull.INSTANCE;
        }

        @Override
        public void resolveItemStack() {

        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public ItemStack getItemStack() {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack getIcon() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || (obj instanceof ItemStackData data && data.isValid() && data.getItemStack().isEmpty());
        }
        private static final int EMPTY_HASHCODE = ItemStack.hashCode(ItemStack.EMPTY);
        @Override
        public int hashCode() {
            return EMPTY_HASHCODE;
        }
    };

    public static final class DataSource implements ItemStackData{
        JsonElement jsonRaw;
        ItemStack stack;
        boolean resolve = false;
        boolean valid = false;
        Integer cachedHash ;
        public DataSource(@Nonnull JsonElement jsonRaw){
            this.jsonRaw = jsonRaw;
        }

        @Override
        public JsonElement getAsJson() {
            return jsonRaw;
        }

        @Override
        public void resolveItemStack() {
            if(!resolve){
                resolve = true;
                try{
                    stack = ItemStackData.deserialize(this.jsonRaw).copyWithCount(1);
                    valid = true;
                }catch (Throwable e){
                    Debug.info("Error while resolving ItemStackData:", jsonRaw);
                    Debug.info(e);
                    valid = false;
                }
            }
        }

        @Override
        public boolean isValid() {
            resolveItemStack();
            return valid;
        }

        @Override
        public ItemStack getItemStack() {
            resolveItemStack();
            if(valid){
                return stack;
            }else {
                throw new UnsupportedOperationException("Invalid data");
            }
        }

        @Override
        public int hashCode() {
            if(cachedHash == null){
                resolveItemStack();
                if(valid){
                    cachedHash = ItemStack.hashCode(stack);
                }else{
                    cachedHash = jsonRaw.hashCode();
                }
            }
            return cachedHash;
        }

        @Override
        public ItemStack getIcon() {
            resolveItemStack();
            if(valid){
                return stack;
            }else {
                return FAILURE;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == this){
                return true;
            }else if(obj instanceof ItemStackData data){
                boolean v1 = valid;
                boolean v2 = data.isValid();
                if(v1 && v2){
                    return ItemStack.areItemsAndComponentsEqual(stack, data.getItemStack());
                }else if(!v1 && !v2){
                    return Objects.equals(jsonRaw, data.getAsJson());
                }else{
                    return false;
                }
            }else{
                return false;
            }
        }
    }

    public static final class Wrapper implements ItemStackData{
        ItemStack itemStack;
        Integer cachedHash;
        JsonElement cachedJson;
        public Wrapper(ItemStack itemStack){
            this.itemStack = itemStack;
        }

        @Override
        public JsonElement getAsJson() {
            if(cachedJson == null){
                try{
                    cachedJson = serialize(this.itemStack);
                }catch (Throwable e){
                    cachedJson = JsonNull.INSTANCE;
                }
            }
            return cachedJson;
        }

        @Override
        public void resolveItemStack() {
            //no
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public ItemStack getItemStack() {
            return itemStack;
        }

        @Override
        public ItemStack getIcon() {
            return itemStack;
        }

        @Override
        public int hashCode() {
            if(cachedHash == null){
                cachedHash = ItemStack.hashCode(itemStack);
            }
            return cachedHash;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == this){
                return true;
            }else if(obj instanceof ItemStackData data){
                if(data.isValid()){
                    return ItemStack.areItemsAndComponentsEqual(itemStack, data.getItemStack());
                }else {
                    return false;
                }
            }else{
                return false;
            }
        }

    }

    public static final record Missing(String customId) implements ItemStackData{

        @Override
        public JsonElement getAsJson() {
            return JsonNull.INSTANCE;
        }

        @Override
        public void resolveItemStack() {

        }

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public ItemStack getItemStack() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemStack getIcon() {
            return MISSING;
        }

        public boolean equals(Object obj){
            if(obj == this){
                return true;
            }else if(obj instanceof Missing ms){
                return ms.customId.equals(customId);
            }else if(obj instanceof ItemStackData md){
                return false;
            }return false;
        }
    }

    public Codec<ItemStackData> CODEC = Codec.PASSTHROUGH.xmap(
        (dynamic)->{
            JsonElement jsonElement = dynamic.convert(JsonOps.INSTANCE).getValue();
            if(jsonElement.isJsonNull()){
                return EMPTY;
            }else{
                return new DataSource(jsonElement);
            }
        },
        (data)->{
            JsonElement jsonElement = data.getAsJson();
            return new Dynamic<>(JsonOps.INSTANCE, jsonElement);
        }
    );


}
