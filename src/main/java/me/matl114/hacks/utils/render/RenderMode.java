package me.matl114.hacks.utils.render;

import me.matl114.managers.config.ConfigEnum;

public enum RenderMode implements ConfigEnum {
    RENDER_2D,
    RENDER_3D;

    @Override
    public String getConfigEnumType() {
        return "render_mode";
    }
}
