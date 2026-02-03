package me.matl114.utils.impl.world;

import lombok.Getter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AlignedFace {
    @Getter
    Vec3d from;
    @Getter
    Vec3d to;
    @Getter(lazy = true)
    private final Vec3d dimensions = new Vec3d(to.x - from.x, to.y - from.y, to.z - from.z);
//    public Vec3d getDimensions(){
//        return ;
//    }
    @Getter(lazy = true)
    private final double area = calculateArea(getDimensions());

    @Getter(lazy = true)
    private final Vec3d center = from.add(to).multiply(0.5);

    private double calculateArea(Vec3d dims){
        return (dims.x * dims.y + dims.y * dims.z + dims.x * dims.z) * 2.0;
    }

    public AlignedFace(Vec3d from, Vec3d to){
        this.from = new Vec3d(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z));
        this.to = new Vec3d(Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z));

    }

    public AlignedFace truncateY(double minY) {

        return new AlignedFace(
            new Vec3d(this.from.x, Math.max( this.from.y, minY), this.from.z),
            new Vec3d(this.to.x, Math.max( this.to.y, minY), this.to.z)
        );
    }

    public boolean isEmpty(){
        return MathHelper.approximatelyEquals(this.getArea(), 0.0F);
    }

}
