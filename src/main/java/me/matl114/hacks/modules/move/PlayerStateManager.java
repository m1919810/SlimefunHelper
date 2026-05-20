package me.matl114.hacks.modules.move;

import me.matl114.hacks.api.BaseModule;
import net.minecraft.util.math.Vec3d;

public class PlayerStateManager extends BaseModule {

    double startFallingY;
    double fallDistance;

    Vec3d lastKnownMovementSpeed;
    Vec3d lastAverageMovementSpeed;
    Vec3d lastSetBackPosition;



    boolean inWall;

    @Override
    public void registerAll() {
        super.registerAll();
    }

    public void onPlayerReset(){

    }

    public void onUpdate(){

    }

    public void onYLevelReset(double y){

    }
}
