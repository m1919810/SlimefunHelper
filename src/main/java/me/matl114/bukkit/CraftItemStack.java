package me.matl114.bukkit;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VItem;
import me.matl114.versioned.api.VNbt;
import net.minecraft.SharedConstants;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentMapImpl;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;

import java.util.*;

public final class CraftItemStack extends BukkitItemStack {
    Map<String, String> compoundTag;
    String item;
    int count;
    int version;
    int dataVersion;
    ItemStack display;
    public CraftItemStack(String item, int count, Map<String, String> compoundTag, int dataVersion, int version) {
        this.item = item;
        this.count = count;
        this.dataVersion = dataVersion;
        this.compoundTag = compoundTag;
        this.display = buildDisplay0(compoundTag);
    }

    public static final Codec<ComponentChanges> CODEC_DISPLAY_CHANGES =
        Codec.<ComponentChanges>of(
            ComponentChanges.CODEC,
            Codec.<String, Dynamic<?>>unboundedMap(Codec.STRING, Codec.PASSTHROUGH)
                .map(s -> {
                    if (s.isEmpty()) {
                        return ComponentChanges.EMPTY;
                    } else {
                        Reference2ObjectMap<ComponentType<?>, Optional<?>> reference2ObjectMap = new Reference2ObjectArrayMap<>(s.size());

                        for (Map.Entry<String, Dynamic<?>> entry : s.entrySet()) {
                            String string = entry.getKey();
                            String realKey ;
                            boolean removal = false;
                            if(string.startsWith("!")){
                                realKey = string.substring(1);
                                removal = true;
                            }else {
                                realKey = string;
                            }
                            ComponentType<?> type = Registries.DATA_COMPONENT_TYPE.get(Identifier.tryParse(realKey));
                            if(type != null) {
                                if(removal){
                                    reference2ObjectMap.put(type, Optional.empty());
                                }else {
                                    Codec<?> codec = type.getCodecOrThrow();
                                    codec = VItem.getInstance().getVersionCompatCodecs().getOrDefault(type, codec);
                                    var dataResult = codec.decode(entry.getValue());
                                    if(dataResult.isSuccess()){
                                        reference2ObjectMap.put(type, dataResult.result().map(Pair::getFirst));
                                    }
                                }
                            }
                        }
                        return new ComponentChanges(reference2ObjectMap);
                    }

                })
        );
    private ItemStack buildDisplay0(Map<String, String> tag){
        if (!Objects.equals(item, "minecraft:air")) {
            try{
                Item item = Registries.ITEM.get(Identifier.tryParse(this.item));
                // if not air then it is unknown item
                item = item == Items.AIR ? Items.BARRIER : item;
                ItemStack stack = new ItemStack(item, count);
                NbtCompound tagCompound = new NbtCompound();
                for (var entry : tag.entrySet()) {
                    try{
                        final NbtElement componentTag = VNbt.getInstance().readNbtNoRegistry(entry.getValue());
                        tagCompound.put(entry.getKey(), componentTag);
                    }catch (Throwable ignoreFormatError){
                    }
                }
                ComponentChanges displayChanges = CODEC_DISPLAY_CHANGES.decode(ItemStackUtils.registry().getOps(NbtOps.INSTANCE), tagCompound).result().map(Pair::getFirst).orElse(ComponentChanges.EMPTY);
                stack.applyChanges(displayChanges);

                return stack;
            }catch (Throwable throwable){
                return new ItemStack(Items.BARRIER);
            }

        }else return ItemStack.EMPTY;
    }

    public ItemStack buildDisplay(){
        return display.copy();
    }

    public static CraftItemStack deserializeModern(Map<String, Object> args) {
        final int version = args.getOrDefault("schema_version", 1) instanceof Number val ? val.intValue() : -1;
        //from paper1.21.10
        String id = "minecraft:air"; int cnt = 0; Map<String, String> compoundTag = Map.of();int dataversion = 0;
        for (var entry : args.entrySet()) {
            switch (entry.getKey()) {
                case "id" -> {
                    id = (String) entry.getValue();
                }
                case "count" -> {
                    cnt = ((Number) entry.getValue()).intValue();
                }
                case "components" -> {

                    if (entry.getValue() instanceof Map map0) {
                        compoundTag = map0;
                    }  else {
                        throw new IllegalArgumentException("components must be a Map");
                    }

                }
                case "DataVersion" -> {
                    dataversion = ((Number) entry.getValue()).intValue();
                }
                default -> {
                    // Ignore
                }
            }
        }
        return new CraftItemStack(id, cnt, compoundTag, dataversion, version);
    }



    public Map<String, Object> serialize() {
        final Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("id", this.item);
        ret.put("count", this.count);
        ret.put("components", this.compoundTag);
        ret.put("DataVersion", this.dataVersion);
        ret.put("schema_version", this.version);
        return ret;
    }
}
