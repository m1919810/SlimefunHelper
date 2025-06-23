package me.matl114.bukkitUtiils;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public class BukkitPlayerTextures {
    static final String PROPERTY_NAME = "textures";
    private static final String MINECRAFT_HOST = "textures.minecraft.net";
    private static final String MINECRAFT_PATH = "/texture/";

    private static void validateTextureUrl(@Nullable URL url) {
        // Null represents an unset texture and is therefore valid.
        if (url == null) return;

        Preconditions.checkArgument(url.getHost().equals(MINECRAFT_HOST), "Expected host '%s' but got '%s'", MINECRAFT_HOST, url.getHost());
        Preconditions.checkArgument(url.getPath().startsWith(MINECRAFT_PATH), "Expected path starting with '%s' but got '%s", MINECRAFT_PATH, url.getPath());
    }

    @Nullable
    private static URL parseUrl(@Nullable String urlString) {
        if (urlString == null) return null;
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    @Nullable
    private static BukkitPlayerProfile.PlayerTextures.SkinModel parseSkinModel(@Nullable String skinModelName) {
        if (skinModelName == null) return null;
        try {
            return BukkitPlayerProfile.PlayerTextures.SkinModel.valueOf(skinModelName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    // The textures data is loaded lazily:
    private boolean loaded = false;
    private JsonObject data; // Immutable contents (only read)
    private long timestamp;
    private BukkitPlayerProfile profile;
    // Lazily decoded textures data that can subsequently be overwritten:
    private URL skin;
    private BukkitPlayerProfile.PlayerTextures.SkinModel skinModel = BukkitPlayerProfile.PlayerTextures.SkinModel.CLASSIC;
    private URL cape;

    // Dirty: Indicates a change that requires a rebuild of the property.
    // This also indicates an invalidation of any previously present textures data that is specific to official
    // GameProfiles, such as the property signature, timestamp, profileId, playerName, etc.: Any modifications by
    // plugins that affect the textures property immediately invalidate all attributes that are specific to official
    // GameProfiles (even if these modifications are later reverted).
    private boolean dirty = false;
    public boolean isEmpty() {
        return (this.skin == null) && (this.cape == null);
    }
//    public void rebuildPropertyIfDirty() {
//        if (!this.dirty) return;
//        // Assert: loaded
//        this.dirty = false;
//
//        if (this.isEmpty()) {
//            this.profile.getProperties().removeAll(PROPERTY_NAME) ;//  removeProperty(CraftPlayerTextures.PROPERTY_NAME);
//            return;
//        }
//
//        // This produces a new textures property that does not contain any attributes that are specific to official
//        // GameProfiles (such as the property signature, timestamp, profileId, playerName, etc.).
//        // Information on the format of the textures property:
//        // * https://minecraft.wiki/w/Head#Item_data
//        // * https://wiki.vg/Mojang_API#UUID_to_Profile_and_Skin.2FCape
//        // The order of Json object elements is important.
//        JsonObject propertyData = new JsonObject();
//
//        if (this.skin != null) {
//            JsonObject texturesMap = JsonUtils.getOrCreateObject(propertyData, "textures");
//            JsonObject skinTexture = JsonUtils.getOrCreateObject(texturesMap, MinecraftProfileTexture.Type.SKIN.name());
//            skinTexture.addProperty("url", this.skin.toExternalForm());
//
//            // Special case: If the skin model is classic (i.e. default), omit it.
//            // Assert: skinModel != null
//            if (this.skinModel != PlayerTextures.SkinModel.CLASSIC) {
//                JsonObject metadata = JsonUtils.getOrCreateObject(skinTexture, "metadata");
//                metadata.addProperty("model", this.skinModel.name().toLowerCase(Locale.ROOT));
//            }
//        }
//
//        if (this.cape != null) {
//            JsonObject texturesMap = JsonUtils.getOrCreateObject(propertyData, "textures");
//            JsonObject skinTexture = JsonUtils.getOrCreateObject(texturesMap, MinecraftProfileTexture.Type.CAPE.name());
//            skinTexture.addProperty("url", this.cape.toExternalForm());
//        }
//
//        this.data = propertyData;
//
//        // We use the compact formatter here since this is more likely to match the output of existing popular tools
//        // that also create profiles with custom textures:
//        String encodedTexturesData = encodePropertyValue(propertyData, JsonFormatter.COMPACT);
//        Property property = new Property(PROPERTY_NAME, encodedTexturesData);
//        this.profile.getProperties().removeAll(PROPERTY_NAME);
//        this.profile.getProperties().put(PROPERTY_NAME, property);
//    }
    public static String encodePropertyValue(@Nonnull JsonObject propertyValue, @Nonnull JsonFormatter formatter) {
        String json = formatter.format(propertyValue);
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
    public interface JsonFormatter {

        /**
         * A {@link JsonFormatter} that uses a compact formatting style.
         */
        public static final JsonFormatter COMPACT = new JsonFormatter() {

            private final Gson gson = new GsonBuilder().create();

            @Override
            public String format(JsonElement jsonElement) {
                return this.gson.toJson(jsonElement);
            }
        };

        public String format(JsonElement jsonElement);
    }

}
