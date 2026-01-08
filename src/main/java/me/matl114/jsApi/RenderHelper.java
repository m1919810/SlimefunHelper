package me.matl114.jsApi;

import me.matl114.hackUtils.RenderTasks;
import me.matl114.utils.ApiMethod;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.awt.*;

@ApiMethod
public class RenderHelper {
    public static Color color(int x, int y, int z) {
        return new Color(x, y, z);
    }

    public static void renderBlock(int x, int y, int z, int tick, boolean line, boolean solid){
        renderBox(x, y, z, x + 1, y + 1, z + 1, tick, line, solid);
    }

    public static void renderBlock(int x, int y, int z, int tick, boolean line, boolean solid, Color color){
        renderBox(x, y, z, x + 1, y + 1, z + 1, tick, line, solid, color, Color.RED);
    }

    public static void renderBlock(int x, int y, int z, int tick, boolean line, boolean solid, Color color, Color lineColor){
        renderBox(x, y, z, x + 1, y + 1, z + 1, tick, line, solid, color, lineColor);
    }

    public static void renderEntity(Object jsEntity , int tick, boolean line, boolean solid){
        renderEntity(jsEntity, tick, line, solid, Color.GREEN, Color.RED);
    }
    public static void renderEntity(Object jsEntity , int tick, boolean line, boolean solid, Color color){
        renderEntity(jsEntity, tick, line, solid, color, Color.RED  );
    }

    public static void renderEntity(Object jsEntity , int tick, boolean line, boolean solid, Color color, Color lineColor){
        Entity entity = JsHelper.unwrap(jsEntity, Entity.class);
        JsHelper.runOnMainThread(()->{
            RenderTasks.registerVirtualRenderTask(solid? new RenderTasks.EntityRenderingTask(entity, tick, color): new RenderTasks.EntityOutlineRenderingTask(
                entity, tick, color
            ));
            if(line){
                RenderTasks.registerVirtualRenderTask(
                    new RenderTasks.LineToEntityRenderingTask(entity, tick, lineColor)
                );
            }
        });

    }

    public static void renderBox(double x, double y, double z, double x1, double y1, double z1, int tick, boolean line, boolean solid){
        renderBox(x, y, z, x1, y1, z1, tick, line, solid, Color.GREEN, Color.RED);
    }

    public static void renderBox(double x, double y, double z, double x1, double y1, double z1, int tick, boolean line, boolean solid, Color color  , Color lineColor){
        JsHelper.runOnMainThread(()->{
            RenderTasks.registerVirtualRenderTask(
                solid? new RenderTasks.BoxRenderingTask(new Vec3d(x, y, z), new Vec3d(x1, y1, z1) , tick, color) : new RenderTasks.BoxOutlineRenderingTask(new Vec3d(x, y, z), new Vec3d(x1, y1, z1) , tick, color)

            );
            if(line){
                RenderTasks.registerVirtualRenderTask(
                    new RenderTasks.LineToTargetRenderingTask(new Vec3d(x + x1 , y + y1, z + z1).multiply(0.5), tick, lineColor)
                );
            }
        });



    }
}
