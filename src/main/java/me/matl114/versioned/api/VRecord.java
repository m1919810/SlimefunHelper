package me.matl114.versioned.api;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import java.util.UUID;
import net.minecraft.component.type.ProfileComponent;

public interface VRecord {
    public static UUID getId(GameProfile profile) {
        return profile.id();
    }

    public static String getName(GameProfile profile) {
        return profile.name();
    }

    public static PropertyMap getProperties(GameProfile profile) {
        return profile.properties();
    }

    public static UUID getGameProfileId(ProfileComponent profileComponent) {
        return profileComponent.getGameProfile().id();
    }

    public static String getGameProfileName(ProfileComponent profileComponent) {
        return profileComponent.getGameProfile().name();
    }

    public static PropertyMap getGameProfileProperties(ProfileComponent profileComponent) {
        return profileComponent.getGameProfile().properties();
    }

    public static ProfileComponent staticProfile(UUID uuid, String name, PropertyMap properties) {

        return ProfileComponent.ofStatic(new GameProfile(uuid, name, properties));
    }

    public static ProfileComponent withProperty(ProfileComponent component, PropertyMap properties) {
        return ProfileComponent.ofStatic(new GameProfile(
                component.getGameProfile().id(), component.getGameProfile().name(), properties));
    }
}
