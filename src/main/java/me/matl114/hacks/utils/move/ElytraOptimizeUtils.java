package me.matl114.hacks.utils.move;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public class ElytraOptimizeUtils {
    public static Vec3d calculateBestPullupSpeed(Vec3d vec3d) {
        double horizontal = vec3d.horizontalLength();
        if (horizontal < 1E-6) {
            vec3d = vec3d.withAxis(Direction.Axis.X, 5);
            horizontal = vec3d.horizontalLength();
        }
        double pitchDeg = 54.5;
        double pitchRad = Math.toRadians(pitchDeg);
        // 使用 -tan(pitch) 来抵消符号，或者直接用 tan(54.5)
        double newY = horizontal * Math.tan(pitchRad);
        // 等价写法：double newY = horizontal * Math.tan(Math.toRadians(54.5));

        // 4. 返回新的向量（保持 x 和 z 不变，仅替换 y）
        return vec3d.withAxis(Direction.Axis.Y, newY);
    }
}
