package me.matl114.hacks.utils.render;

import me.matl114.versioned.api.VRender;
import net.minecraft.util.math.Vec3d;

public class RenderElements {
    private static final int POSITION_FLAG = VRender.createTextPositionFlag(0, 1);

    public static record Text(net.minecraft.text.Text text, Vec3d position, int offSetFlag, float scale) {
        public Text(net.minecraft.text.Text text, Vec3d position) {
            this(text, position, POSITION_FLAG, 1.0F);
        }

        public Text(net.minecraft.text.Text text, Vec3d position, float scale) {
            this(text, position, POSITION_FLAG, scale);
        }
    }
}
