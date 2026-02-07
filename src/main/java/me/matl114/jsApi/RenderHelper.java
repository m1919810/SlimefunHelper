package me.matl114.jsApi;

import me.matl114.hacks.RenderTasks;
import me.matl114.utils.ApiMethod;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.List;

@ApiMethod
public class RenderHelper {
    public static Color color(int x, int y, int z) {
        return new Color(x, y, z);
    }

    public static RenderTasks.TaskBuilder builder(){
        return new RenderTasks.TaskBuilder();
    }

    public static RenderTasks.RenderObject createBox(double x, double y, double z, double x1, double y1, double z1, Color color){
        return createBox(new Vec3d(x, y, z), new Vec3d(x1, y1, z1), color);
    }
    public static RenderTasks.RenderObject createBox(Object vec3d1, Object vec3d2, Color color){
        return new RenderTasks.BoxObject(DataHelper.createVec(vec3d1), DataHelper.createVec(vec3d2), color);
    }

    public static RenderTasks.RenderObject createBoxFrame(Object vec3d1, Object vec3d2, Color color){
        return new RenderTasks.BoxOutlineObject(DataHelper.createVec(vec3d1), DataHelper.createVec(vec3d2), color);
    }

    public static RenderTasks.RenderObject createLineToTarget(Object vec3d1, Color color){
        return new RenderTasks.LineToTargetObject(DataHelper.createVec(vec3d1), color);
    }

    public static RenderTasks.RenderObject createEntityBox(Object vec3d1, Color color){
        return new RenderTasks.EntityBoxObject(JsHelper.unwrap(vec3d1, Entity.class), color);
    }

    public static RenderTasks.RenderObject createEntityBoxFrame(Object vec3d1, Color color){
        return new RenderTasks.EntityBoxOutlineObject(JsHelper.unwrap(vec3d1, Entity.class), color);
    }

    public static RenderTasks.RenderObject createLineToEntity(Object vec3d1, Color color){
        return new RenderTasks.LineToEntityObject(JsHelper.unwrap(vec3d1, Entity.class), color);
    }

    public static RenderTasks.RenderObject createLine(Object vec3d1, Object vec3d2, Color color){
        return new RenderTasks.LineObject(DataHelper.createVec(vec3d1), DataHelper.createVec(vec3d2)).color(color);
    }

    public static RenderTasks.RenderObject createMultiLine(List vec3d1, Color color){
        return new RenderTasks.MultiLineObject(vec3d1.stream().map(DataHelper::createVec).toList(), color);
    }

    public static RenderTasks.RenderObject createQuad(Object vec3d1, Object vec3d2, Object vec3d3, Object vec3d4, Color color){
        return new RenderTasks.QuadObject(DataHelper.createVec(vec3d1), DataHelper.createVec(vec3d2), DataHelper.createVec(vec3d3), DataHelper.createVec(vec3d4), color);
    }

}
