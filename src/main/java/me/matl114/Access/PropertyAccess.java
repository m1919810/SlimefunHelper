package me.matl114.Access;

import com.mojang.authlib.properties.Property;

public interface PropertyAccess {
    public String safeGetName();
    public String safeGetValue();
    public String safeGetSiginature();
    public boolean reallyHasSignature();
    static PropertyAccess of(Property property) {
        return (PropertyAccess) property;
    }
}
