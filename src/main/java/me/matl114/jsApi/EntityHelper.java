package me.matl114.jsApi;

import java.util.Locale;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.utils.ApiMethod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;

@ApiMethod
public class EntityHelper {
    public static int getEntityId(Entity entity) {
        return entity.getId();
    }

    public static boolean getEntityFlag(Entity entity, int flag) {
        return EntityAccess.of(entity).getDataFlag(flag);
    }

    public static void setEntityFlag(Entity entity, int flag, boolean value) {
        EntityAccess.of(entity).setDataFlag(flag, value);
    }

    public static EntityPose getEntityPose(Entity entity) {
        return entity.getPose();
    }

    public static void setEntityPose(Entity entity, String pose) {
        entity.setPose(EntityPose.valueOf(pose.toUpperCase(Locale.ROOT)));
    }

    public static Vec3d getEntityVelocity(Entity entity) {
        return entity.getVelocity();
    }

    public static void setEntityVelocity(Entity entity, Vec3d velocity) {
        entity.setVelocity(velocity);
    }

    public static EntityType getEntityType(Entity entity) {
        return entity.getType();
    }

    public static String getEntityTypeName(EntityType entityType) {
        return EntityType.getId(entityType).toString();
    }

    public static EntityType getEntityTypeByName(String name) {
        return EntityType.get(name).orElse(null);
    }
}
