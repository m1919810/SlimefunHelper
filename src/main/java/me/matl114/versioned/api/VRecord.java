package me.matl114.versioned.api;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.component.type.ProfileComponent;

public interface VRecord {
    public static UUID getId(GameProfile profile) {
        return profile.getId();
    }

    public static String getName(GameProfile profile) {
        return profile.getName();
    }

    public static PropertyMap getProperties(GameProfile profile) {
        return profile.getProperties();
    }

    public static UUID getGameProfileId(ProfileComponent profileComponent) {
        return profileComponent.gameProfile().getId();
    }

    public static String getGameProfileName(ProfileComponent profileComponent) {
        return profileComponent.gameProfile().getName();
    }

    public static PropertyMap getGameProfileProperties(ProfileComponent profileComponent) {
        return profileComponent.gameProfile().getProperties();
    }

    public static ProfileComponent staticProfile(UUID uuid, String name, PropertyMap properties) {

        return new ProfileComponent(Optional.ofNullable(name), Optional.ofNullable(uuid), properties);
    }

    public static ProfileComponent withProperty(ProfileComponent component, PropertyMap properties) {
        return new ProfileComponent(component.name(), component.id(), properties);
    }

    public static PropertyMap createProperty(Multimap<String, Property> ppt) {
        return new PropertyMap(LinkedHashMultimap.create(ppt));
    }

    public static PropertyMap createProperty() {
        return new PropertyMap(LinkedHashMultimap.create());
    }
}
