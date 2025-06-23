package me.matl114.bukkitUtiils;

import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.utils.Debug;
import me.matl114.utils.JsonUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.StringHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
    URL skinUrl;
    @Getter
    Multimap<String,Property> properties = LinkedHashMultimap.create();
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
                    Property property = deserializeProperty((Map<?,?>) propertyData);
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
    static final String PROPERTY_NAME = "textures";
    private static final String MINECRAFT_HOST = "textures.minecraft.net";
    private static final String MINECRAFT_PATH = "/texture/";
    public void setSkinUrl(URL skinUrl, PlayerTextures.SkinModel model, URL cape) {
        this.skinUrl = skinUrl;
        if( skinUrl == null && cape ==  null){
            this.properties.removeAll(PROPERTY_NAME) ;//  removeProperty(CraftPlayerTextures.PROPERTY_NAME);
            return;
        }else {
            JsonObject propertyData = new JsonObject();
            if( skinUrl !=null){
                JsonObject texturesMap = JsonUtils.getOrCreateObject(propertyData, "textures");
                JsonObject skinTexture = JsonUtils.getOrCreateObject(texturesMap, MinecraftProfileTexture.Type.SKIN.name());
                skinTexture.addProperty("url", skinUrl.toExternalForm());

                // Special case: If the skin model is classic (i.e. default), omit it.
                // Assert: skinModel != null
                if (model != PlayerTextures.SkinModel.CLASSIC) {
                    JsonObject metadata = JsonUtils.getOrCreateObject(skinTexture, "metadata");
                    metadata.addProperty("model", model.name().toLowerCase(Locale.ROOT));
                }
            }

            if (cape != null) {
                JsonObject texturesMap = JsonUtils.getOrCreateObject(propertyData, "textures");
                JsonObject skinTexture = JsonUtils.getOrCreateObject(texturesMap, MinecraftProfileTexture.Type.CAPE.name());
                skinTexture.addProperty("url", cape.toExternalForm());
            }
            String encodedTexturesData = BukkitPlayerTextures. encodePropertyValue(propertyData, BukkitPlayerTextures.JsonFormatter.COMPACT);
            Property property = new Property(PROPERTY_NAME, encodedTexturesData);
            this.properties.removeAll(PROPERTY_NAME);
            this.properties.put(PROPERTY_NAME, property);
        }

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
                    Property var7 = (Property) var6.next();
                    var8 = new NbtCompound();

                    var8.putString("Value", var7.value());
                    if (var7.hasSignature()) {
                        var8.putString("Signature", var7.signature());
                    }
                }

                var2.put(var4, var5);
            }

            var0.put("Properties", var2);
        }

        return var0;
    }

    public static Property deserializeProperty(@Nonnull Map<?, ?> map) {
        String name = (String) map.get("name");
        String value = (String) map.get("value");
        String signature = (String) map.get("signature");
        return new Property(name, value, signature);
    }
    public static Map<String, Object> serializeProperty(@Nonnull Property property) {
        Map<String, Object> map = new LinkedHashMap<>();
        try{
            map.put("name", property.name());
        }catch(Throwable e){

        }
        map.put("value", property.value());
        if (property.hasSignature()) {
            map.put("signature", property.signature());
        }
        return map;
    }
    @AllArgsConstructor
    public static class PlayerSkin{
//        UUID uuid;
//        String base64skinTexture;
//        URL url;
        @Getter
        BukkitPlayerProfile profile;
        public PlayerSkin(UUID uniqueId, String name, URL url) {
            profile = new BukkitPlayerProfile(uniqueId, name);
            profile.setSkinUrl(url, PlayerTextures.SkinModel.CLASSIC, null);
        }
    }
    @ParametersAreNonnullByDefault
    @Nonnull
    public static PlayerSkin fromBase64(UUID uuid, String base64skinTexture, URL url) {
        return new PlayerSkin(uuid, base64skinTexture, url);
    }

    /** @deprecated */
    @Deprecated
    @ParametersAreNonnullByDefault
    @Nonnull
    public static PlayerSkin fromBase64(UUID uuid, String base64skinTexture) {
        String base64decode = new String(Base64.getDecoder().decode(base64skinTexture));
        JsonObject jsonObject = (new JsonParser()).parse(base64decode).getAsJsonObject();
        String url = jsonObject.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();

        URL skinUrl;
        try {
            skinUrl = URI.create(url).toURL();
        } catch (MalformedURLException var7) {
            MalformedURLException e = var7;
            throw new RuntimeException(e);
        }

        return new PlayerSkin(uuid, base64skinTexture, skinUrl);
    }

    @ParametersAreNonnullByDefault
    @Nonnull
    public static PlayerSkin fromBase64(String base64skinTexture) {
        UUID uuid = UUID.nameUUIDFromBytes(base64skinTexture.getBytes(StandardCharsets.UTF_8));
        return fromBase64(uuid, base64skinTexture);
    }

    @ParametersAreNonnullByDefault
    @Nonnull
    public static PlayerSkin fromURL(UUID uuid, String url) {
        String value = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        String base64skinTexture = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

        URL skinUrl;
        try {
            skinUrl = URI.create(url).toURL();
        } catch (MalformedURLException var6) {
            MalformedURLException e = var6;
            throw new RuntimeException(e);
        }

        return fromBase64(uuid, base64skinTexture, skinUrl);
    }

    @ParametersAreNonnullByDefault
    @Nonnull
    public static PlayerSkin fromURL(String url) {
        UUID uuid = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
        return fromURL(uuid, url);
    }

    @ParametersAreNonnullByDefault
    @Nonnull
    public static PlayerSkin fromHashCode(UUID uuid, String hashCode) {
        return fromURL(uuid, "http://textures.minecraft.net/texture/" + hashCode);
    }

    @ParametersAreNonnullByDefault
    @Nonnull
    public static PlayerSkin fromHashCode(String hashCode) {
        UUID uuid = UUID.nameUUIDFromBytes(hashCode.getBytes(StandardCharsets.UTF_8));
        return fromHashCode(uuid, hashCode);
    }
    public interface PlayerTextures {
        boolean isEmpty();

        void clear();

        @Nullable
        URL getSkin();

        void setSkin(@Nullable URL var1);

        void setSkin(@Nullable URL var1, @Nullable PlayerTextures.SkinModel var2);

        @NotNull
        PlayerTextures.SkinModel getSkinModel();

        @Nullable
        URL getCape();

        void setCape(@Nullable URL var1);

        long getTimestamp();

        boolean isSigned();

        public static enum SkinModel {
            CLASSIC,
            SLIM;

            private SkinModel() {
            }
        }
    }

}
