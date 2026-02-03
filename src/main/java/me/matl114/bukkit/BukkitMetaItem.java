package me.matl114.bukkit;

import com.google.common.base.Preconditions;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BukkitMetaItem implements Cloneable , ConfigurationSerializable {
    protected Item item;
    protected HashMap<String,Object> attributes=new HashMap<>();
    public BukkitMetaItem(Item material) {
        item=material;
        attributes=new HashMap<>();
    }
    public boolean hasDisplayName(){
        return attributes.containsKey("display-name");
    }

    @NotNull
     public String getDisplayName(){
        return attributes.get("display-name").toString();
    }



    public boolean hasLore(){
        return attributes.containsKey("lore");
    }

    @Nullable
    public List<String> getLore(){
        return (List<String>) attributes.get("lore");
    }



    public boolean hasCustomModelData(){
        return this.attributes.containsKey("custom-model-data");
    }

    public int getCustomModelData(){
        Object value = this.attributes.get("custom-model-data");
        if (value instanceof Integer)return (Integer)value;
        else if(value instanceof BukkitSerializationMock.UnknownSerialization unknown1_21_4){
            try{
                if(unknown1_21_4.value.containsKey("floats")){
                    List floats = (List) unknown1_21_4.value.get("floats");
                    return floats.isEmpty() ? 0 : (floats.get(0) instanceof Number unm? unm.intValue() : 0);
                }else {
                    return 0;
                }
            }catch (Throwable e){
                return 0;
            }
        }
        else return 0;
      //  return  (Integer) this.attributes.getOrDefault("custom-model-data",0);
    }



    public boolean hasEnchants(){
        return !getEnchants().isEmpty();
    }



    @NotNull
    public Map getEnchants(){
        return (Map) attributes.getOrDefault("enchants",new HashMap<>());
    }



    @ApiStatus.Internal
    public void setVersion(int var1){

    }

    @NotNull
    public BukkitMetaItem clone(){
        BukkitMetaItem var1=null;
        try{
            var1=(BukkitMetaItem) super.clone();
        }catch (Throwable e){
            e.printStackTrace();
        }
        var1.item=item;
        var1.attributes= (HashMap)attributes.clone();
        return var1;
    }

    public Map<String, Object> serialize() {
        throw new AssertionError();
    }
    public static BukkitMetaItem deserialize(@NotNull Map<String, Object> map) throws Throwable{
        Preconditions.checkArgument(map != null, "Cannot deserialize null map");
        BukkitMetaItem bmi= new BukkitMetaItem(Items.AIR);
        try {
            bmi.attributes.putAll(map);
            return bmi;
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }
    public String toString(){
        return new StringBuilder("Bukkit Meta:{").append(attributes.toString()).append("}").toString();
    }
    public BukkitPersistentDataContainer getPersistentDataContainer(){
        if(this.attributes.containsKey("PublicBukkitValues")){
            Object nbtMap =  this.attributes.get("PublicBukkitValues");
            if (nbtMap != null) {
                BukkitPersistentDataContainer container= new BukkitPersistentDataContainer();
                container.putData((NbtCompound)BukkitConfigDeserializor.deserializeObject(nbtMap));
                return container;
            }
        }
        return null;
    }
}
