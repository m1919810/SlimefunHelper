package me.matl114.bukkit;

import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    public void rebuildDirtyProperties() {}

    UUID uniqueId;
    String name;
    URL skinUrl;

    @Getter
    Multimap<String, Property> properties = LinkedHashMultimap.create();

    public BukkitPlayerProfile(UUID uniqueId, String name) {
        this.uniqueId = uniqueId;
        this.name = name;
    }

    public static BukkitPlayerProfile deserialize(Map<String, Object> map) {
        UUID uniqueId;
        String uuidString = (String) map.get("uniqueId");
        if (uuidString == null) uniqueId = null;
        else uniqueId = UUID.fromString(uuidString);

        String name = (String) map.get("name");

        // This also validates the deserialized unique id and name (ensures that not both are null):
        BukkitPlayerProfile profile = new BukkitPlayerProfile(uniqueId, name);
        // Debug.info("playerProfile instance created");
        try {
            if (map.containsKey("properties")) {
                for (Object propertyData : (List<?>) map.get("properties")) {
                    Preconditions.checkArgument(
                            propertyData instanceof Map, "Propertu data (%s) is not a valid Map", propertyData);
                    Property property = deserializeProperty((Map<?, ?>) propertyData);
                    profile.properties.put((String) ((Map<?, ?>) propertyData).get("name"), property);
                }
            }
        } catch (Throwable e) {
            Debug.info("error in properties deserialization");
            // Debug.info("playerProfile deserialization failed,more information provided");
            // e.printStackTrace();
            // throw  e;
        }
        // Debug.info("playerProfile deserialization finished");
        return profile;
    }

    public String toString() {
        return new StringBuilder("{uid: ")
                .append(uniqueId)
                .append(",name: ")
                .append(name)
                .append(",properties: ")
                .append(properties.isEmpty() ? "empty" : properties.toString())
                .append("}")
                .toString();
    }

    static final String PROPERTY_NAME = "textures";
    private static final String MINECRAFT_HOST = "textures.minecraft.net";
    private static final String MINECRAFT_PATH = "/TEXTURE/";

    public static Property encodeUrlToProperty(URL skinUrl, PlayerTextures.SkinModel model, URL cape) {
        JsonObject propertyData = new JsonObject();
        if (skinUrl != null) {
            JsonObject texturesMap = getOrCreateObject(propertyData, "textures");
            JsonObject skinTexture = getOrCreateObject(texturesMap, MinecraftProfileTexture.Type.SKIN.name());
            skinTexture.addProperty("url", skinUrl.toExternalForm());

            // Special case: If the skin model is classic (i.e. default), omit it.
            // Assert: skinModel != null
            if (model != PlayerTextures.SkinModel.CLASSIC) {
                JsonObject metadata = getOrCreateObject(skinTexture, "metadata");
                metadata.addProperty("model", model.name().toLowerCase(Locale.ROOT));
            }
        }

        if (cape != null) {
            JsonObject texturesMap = getOrCreateObject(propertyData, "textures");
            JsonObject skinTexture = getOrCreateObject(texturesMap, MinecraftProfileTexture.Type.CAPE.name());
            skinTexture.addProperty("url", cape.toExternalForm());
        }
        String encodedTexturesData =
                BukkitPlayerTextures.encodePropertyValue(propertyData, BukkitPlayerTextures.JsonFormatter.COMPACT);
        return new Property(PROPERTY_NAME, encodedTexturesData);
    }

    public void setSkinUrl(URL skinUrl, PlayerTextures.SkinModel model, URL cape) {
        this.skinUrl = skinUrl;
        if (skinUrl == null && cape == null) {
            this.properties.removeAll(PROPERTY_NAME); //  removeProperty(CraftPlayerTextures.PROPERTY_NAME);
            return;
        } else {
            Property property = encodeUrlToProperty(skinUrl, model, cape);
            this.properties.removeAll(PROPERTY_NAME);
            this.properties.put(PROPERTY_NAME, property);
        }
    }

    public void addGameProfile(ItemStack stack) {
        ItemStackUtils.setOrRemoveChange(stack, DataComponentTypes.PROFILE, createGameProfile());
    }

    public PropertyMap createPropertyMap() {
        PropertyMap map = new PropertyMap();
        map.putAll(this.properties);
        return map;
    }

    public ProfileComponent createGameProfile() {
        PropertyMap map = new PropertyMap();
        map.putAll(this.properties);
        return new ProfileComponent(
                Optional.ofNullable(StringHelper.isEmpty(this.name) ? (String) null : this.name),
                Optional.ofNullable(this.uniqueId),
                map);
    }

    public static Property deserializeProperty(@Nonnull Map<?, ?> map) {
        String name = (String) map.get("name");
        String value = (String) map.get("value");
        String signature = (String) map.get("signature");
        return new Property(name, value, signature);
    }

    public static Map<String, Object> serializeProperty(@Nonnull Property property) {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            map.put("name", property.name());
        } catch (Throwable e) {

        }
        map.put("value", property.value());
        if (property.hasSignature()) {
            map.put("signature", property.signature());
        }
        return map;
    }

    @AllArgsConstructor
    public static class PlayerSkin {
        //        UUID uuid;
        //        String base64skinTexture;
        //        URL url;
        @Getter
        BukkitPlayerProfile profile;

        public PlayerSkin(UUID uniqueId, String name, URL url) {
            profile = new BukkitPlayerProfile(uniqueId, name);
            profile.setSkinUrl(url, PlayerTextures.SkinModel.CLASSIC, null);
        }

        @javax.annotation.Nullable
        private static String decodeBase64(@Nonnull String encoded) {
            try {
                return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null; // Invalid input
            }
        }

        @javax.annotation.Nullable
        public static JsonObject decodePropertyValue(@Nonnull String encodedPropertyValue) {
            String json = decodeBase64(encodedPropertyValue);
            if (json == null) return null;
            try {
                JsonElement jsonElement = JsonParser.parseString(json);
                if (!jsonElement.isJsonObject()) return null;
                return jsonElement.getAsJsonObject();
            } catch (JsonParseException e) {
                return null; // Invalid input
            }
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
        String url = jsonObject
                .getAsJsonObject("textures")
                .getAsJsonObject("SKIN")
                .get("url")
                .getAsString();

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

    public static URL fromHashToUrl(String hash) {
        String url = "http://textures.minecraft.net/texture/" + hash;
        URL skinUrl;
        try {
            skinUrl = URI.create(url).toURL();
        } catch (MalformedURLException var6) {
            MalformedURLException e = var6;
            throw new RuntimeException(e);
        }
        return skinUrl;
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

            private SkinModel() {}
        }
    }

    @javax.annotation.Nullable
    public static JsonObject getObjectOrNull(@Nonnull JsonObject parent, @Nonnull String key) {
        JsonElement element = parent.get(key);
        return (element instanceof JsonObject) ? (JsonObject) element : null;
    }

    @Nonnull
    public static JsonObject getOrCreateObject(@Nonnull JsonObject parent, @Nonnull String key) {
        JsonObject jsonObject = getObjectOrNull(parent, key);
        if (jsonObject == null) {
            jsonObject = new JsonObject();
            parent.add(key, jsonObject);
        }
        return jsonObject;
    }
}
