package me.matl114.utils.render;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UV {
    // 绘制顺序 (u0, v0) (u0, v1) (u1, v1) (u1, v0)
    float u0;
    float v0;
    float u1;
    float v1;
    public static final UV DEFAULT = new UV(0, 0, 1, 1);

    public float getU(int idx) {
        return (idx & 2) == 0 ? u0 : u1;
    }

    public float getV(int idx) {
        return ((idx - 1) & 2) == 0 ? v1 : v0;
    }
}
