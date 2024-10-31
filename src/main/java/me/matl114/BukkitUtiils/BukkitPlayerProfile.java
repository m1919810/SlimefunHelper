package me.matl114.BukkitUtiils;

import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import me.matl114.Access.PropertyAccess;
import me.matl114.SlimefunUtils.Debug;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Colors;
import net.minecraft.util.StringHelper;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.profile.PlayerProfile;

import javax.annotation.Nonnull;
import java.util.*;

public class BukkitPlayerProfile implements ConfigurationSerializable {
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (uniqueId != null) {
            map.put("uniqueId", uniqueId.toString());
        }
        if (name != null) {
            map.put("name", name);
        }
        rebuildDirtyProperties();
        if (!properties.isEmpty()) {
            List<Object> propertiesData = new ArrayList<>();
            properties.forEach((propertyName, property) -> {
                propertiesData.add(serializeProperty(property));
            });
            map.put("properties", propertiesData);
        }
        return map;
    }
    public void rebuildDirtyProperties(){

    }
    UUID uniqueId;
    String name;
    Multimap<String,PropertyMock> properties = LinkedHashMultimap.create();
    public BukkitPlayerProfile(UUID uniqueId, String name) {
        this.uniqueId = uniqueId;
        this.name = name;
    }
    public static BukkitPlayerProfile deserialize(Map<String, Object> map) {
        UUID uniqueId;
        String uuidString = (String) map.get("uniqueId");
        if (uuidString == null) uniqueId=null;
        else uniqueId = UUID.fromString(uuidString);

        String name =(String) map.get("name");

        // This also validates the deserialized unique id and name (ensures that not both are null):
        BukkitPlayerProfile profile = new BukkitPlayerProfile(uniqueId, name);
        //Debug.info("playerProfile instance created");
        try{
            if (map.containsKey("properties")) {
                for (Object propertyData : (List<?>) map.get("properties")) {
                    Preconditions.checkArgument(propertyData instanceof Map, "Propertu data (%s) is not a valid Map", propertyData);
                    PropertyMock property = deserializeProperty((Map<?,?>) propertyData);
                    profile.properties.put((String) ((Map<?,?>)propertyData).get("name"), property);
                }
            }
        }catch(Throwable e){
            Debug.info("error in properties deserialization");
            //Debug.info("playerProfile deserialization failed,more information provided");
            //e.printStackTrace();
            //throw  e;
        }
        //Debug.info("playerProfile deserialization finished");
        return profile;
    }
    public String toString(){
        return new StringBuilder("{uid: ").append(uniqueId).append(",name: ").append(name).append(",properties: ").append(properties.isEmpty()?"empty":properties.toString()).append("}").toString();

    }
    public void addGameProfile(ItemStack stack) {
        writeGameProfile(stack.getOrCreateSubNbt("SkullOwner"));
    }
    public NbtCompound writeGameProfile(NbtCompound var0) {

        if (!StringHelper.isEmpty(name)) {
            var0.putString("Name", this.name);
        }

        if (uniqueId != null) {
            var0.putUuid("Id", uniqueId);
        }

        if (!this.properties.isEmpty()) {
            NbtCompound var2 = new NbtCompound();
            Iterator var3 = this.properties.keySet().iterator();

            while(var3.hasNext()) {
                String var4 = (String)var3.next();
                NbtList var5 = new NbtList();

                NbtCompound var8;
                for(Iterator var6 = properties.get(var4).iterator(); var6.hasNext(); var5.add(var8)) {
                    PropertyMock var7 = (PropertyMock) var6.next();
                    var8 = new NbtCompound();

                    var8.putString("Value", var7.getValue());
                    if (var7.hasSignature()) {
                        var8.putString("Signature", var7.getSignature());
                    }
                }

                var2.put(var4, var5);
            }

            var0.put("Properties", var2);
        }

        return var0;
    }

    public static PropertyMock deserializeProperty(@Nonnull Map<?, ?> map) {
        String name = (String) map.get("name");
        String value = (String) map.get("value");
        String signature = (String) map.get("signature");
        return new PropertyMock(name, value, signature);
    }
    public static Map<String, Object> serializeProperty(@Nonnull PropertyMock property) {
        Map<String, Object> map = new LinkedHashMap<>();
        try{
            map.put("name", property.getName());
        }catch(Throwable e){

        }
        map.put("value", property.getValue());
        if (property.hasSignature()) {
            map.put("signature", property.getSignature());
        }
        return map;
    }
}
