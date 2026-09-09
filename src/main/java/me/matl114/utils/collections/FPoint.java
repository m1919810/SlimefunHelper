package me.matl114.utils.collections;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class FPoint {
    public double x;
    public double y;

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }
}
