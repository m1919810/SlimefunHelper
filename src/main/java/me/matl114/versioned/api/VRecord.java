package me.matl114.versioned.api;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import java.util.UUID;

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
}
