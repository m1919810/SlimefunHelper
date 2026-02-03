package me.matl114.utils;

import net.minecraft.block.SpawnerBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector2d;
import org.spongepowered.include.com.google.common.collect.BiMap;
import org.spongepowered.include.com.google.common.collect.HashBiMap;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class EntityUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static void parseEntityWhiteList(String value, Set<EntityType<?>> collection){
        collection.clear();
        try{
            for(net.minecraft.entity.EntityType<?> entityType: Registries.ENTITY_TYPE){
                if(Pattern.matches(value,Registries.ENTITY_TYPE.getId(entityType).getPath())){
                    collection.add(entityType);
                }
            }
            if(Pattern.matches(value,"animal")){
                for(net.minecraft.entity.EntityType<?> entityType: Registries.ENTITY_TYPE){
                    if(entityType.getSpawnGroup()== SpawnGroup.CREATURE){
                        collection.add(entityType);
                    }
                }
            }
            if(Pattern.matches(value,"monster")){
                for(net.minecraft.entity.EntityType<?> entityType: Registries.ENTITY_TYPE){
                    if(entityType.getSpawnGroup()== SpawnGroup.MONSTER){
                        if(!(entityType== EntityType.ZOMBIFIED_PIGLIN)&&!(entityType== net.minecraft.entity.EntityType.ENDERMAN)){
                            collection.add(entityType);
                        }
                    }
                }
            }
            //feat: add spawn group flag
            for (SpawnGroup group: SpawnGroup.values()){
                if(group != SpawnGroup.MONSTER && Pattern.matches(value, group.getName())){
                    for(net.minecraft.entity.EntityType<?> entityType: Registries.ENTITY_TYPE){
                        if(entityType.getSpawnGroup()== group){
                            collection.add(entityType);

                        }
                    }
                }
            }
            if(Pattern.matches(value, "living_entity")){
                for (EntityType<?> entityType : Registries.ENTITY_TYPE){
                    if(entityType.getSpawnGroup() != SpawnGroup.MISC){
                        collection.add(entityType);
                    }
                }
            }
            var iter=collection.iterator();
            while(iter.hasNext()){
                EntityType<?> entityType=iter.next();
                if(Pattern.matches(value,"!"+Registries.ENTITY_TYPE.getId(entityType).getPath())){
                    iter.remove();
                }
            }
        }catch(Throwable valuePatternError){
            collection.clear();
        }
        //Debug.info("Using whitelist",set);

    }
    private static final BiMap<Item,EntityType<?>> ITEM2SPAWN_ENTITY = HashBiMap.create();
    static {
        for(Item item: Registries.ITEM){
            if(item instanceof SpawnEggItem egg){
                ITEM2SPAWN_ENTITY.put(item, egg.getEntityType(new ItemStack(item)));
            }
        }
    }
    public static EntityType<?> spawnEggToEntity(Item spawner){
        return ITEM2SPAWN_ENTITY.getOrDefault(spawner, null);
    }
    public static Item entityToSpawnEgg(EntityType<?> entityType){
        return ITEM2SPAWN_ENTITY.inverse().getOrDefault(entityType, null);
    }
    public static EntityType<?> getStoredEntityType(ItemStack stack){
        if(stack != null && stack.getItem() instanceof BlockItem block && block.getBlock() instanceof SpawnerBlock spawner && ItemStackUtils.hasInPatch(stack, DataComponentTypes.BLOCK_ENTITY_DATA)){
            NbtComponent component = ItemStackUtils.getInPatch(stack, DataComponentTypes.BLOCK_ENTITY_DATA);
            return getSpawnerEntityType(component.getNbt());
        }
        return null;
    }
    public static EntityType<?> getSpawnerEntityType(NbtCompound spawnerCompound){
        return spawnerCompound == null? null: Registries.ENTITY_TYPE.getOrEmpty(getSpawnedEntityId(spawnerCompound, "SpawnData")).orElse(null);
    }
    public static Identifier getSpawnedEntityId(NbtCompound nbt, String spawnDataKey) {
        if (nbt.contains(spawnDataKey, 10)) {
            if(nbt.get(spawnDataKey) instanceof NbtCompound cp1){
                if(cp1.get("entity") instanceof NbtCompound cp2){
                    if(cp2.get("id") instanceof NbtString nbt3){
                        String string = nbt3.asString();
                        if(string != null && !string.isEmpty()){
                            return Identifier.tryParse(string);
                        }
                    }
                }
            }

            return null;
        } else {

            return null;
        }
    }



    public static Vector2d getEntityLookXZ(Entity entity){
        float yaw = entity.getYaw();
        float pitch = entity.getPitch();
        float f = MathHelper.cos(-yaw * 0.017453292F - 3.1415927F);
        float g = MathHelper.sin(-yaw * 0.017453292F - 3.1415927F);
        float h = -MathHelper.cos(-pitch * 0.017453292F);
        return new Vector2d(g*h, f*h);
    }


    public static void setEntityRotation(Entity entity, Vec3d vec){
        vec = vec.normalize();

        entity.setPitch((float) Math.toDegrees( Math.asin(- vec.y)));
        entity.setYaw((float)Math.toDegrees(Math.atan2( -vec.x, vec.z)));
    }

    public static void setEntityRotationSafe(Entity entity, Vec3d vec){
        vec = vec.normalize();
        setEntityPitchSafe(entity, (float) Math.toDegrees( Math.asin(- vec.y)));


        float newYaw = (float)Math.toDegrees(Math.atan2( -vec.x, vec.z));
        setEntityYawSafe(entity, newYaw);
    }
    public static float getSafeYaw(Entity entity, float newYaw){
//        if(newYaw == -180.0 || newYaw == 180.0)return newYaw;
//        float oldYaw = entity.getYaw();
        float oldYaw = entity.getYaw();
        float diff = getSafeYawDiff(oldYaw, newYaw);
        return oldYaw + diff;
    }
    public static float getSafeYawDiff(float oldYaw, float newYaw){
        float diff = newYaw - oldYaw;
        return  (diff % 360.0F + 720.0F + 180.0F) % 360.0F - 180.0F; // 归一化到 [-180,180]

    }
    public static float getSafePitch(float newPitch){
        //fix: 当玩家低头的时候不要改成抬头
        return normalizePitch(newPitch);
    }
    public static float normalizeYaw(float yaw){
        if(yaw >-1E-5 && yaw < 360 + 1E-5){
            return yaw;
        }
        return ((yaw % 360.0F + 720.0F + 180.0F) %360.0F) - 180.0F;
    }
    public static float normalizePitch(float newPitch){
        if(newPitch < 90.0F + 1E-5 && newPitch > -90.0F - 1E-5)return newPitch;
        return  (newPitch % 180.0F + 720.0F + 90.0F) % 180.0F - 90.0F; // 归一化到 [-90, 90]
    }
    public static void setEntityYawSafe(Entity entity, float newYaw){
        newYaw = getSafeYaw(entity, newYaw);
        entity.setYaw(newYaw);
    }

    public static void setEntityPitchSafe(Entity entity, float newPitch){
        entity.setPitch(getSafePitch(newPitch));
    }

    public static Vec3d pitchYawToRotation(float pitch, float yaw){
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d((double)(i * j), (double)(-k), (double)(h * j));
    }


    public static Vec2f rotationToPitchYaw(Vec3d vec){
        return new Vec2f(rotationToPitch(vec),  rotationToYaw(vec));
    }
    public static float rotationToYaw(Vec3d vec){
        return (float)Math.toDegrees(Math.atan2( -vec.x, vec.z));
    }
    public static float rotationToPitch(Vec3d vec){
        return (float) Math.toDegrees( Math.asin(- vec.y));
    }

    public static double getProjectileGravity(Item item)
    {
        if(item instanceof RangedWeaponItem)
            return 0.05;

        if(item instanceof ThrowablePotionItem)
            return 0.4;

        if(item instanceof FishingRodItem)
            return 0.15;

        if(item instanceof TridentItem)
            return 0.015;

        return 0.03;
    }

    public static RaycastContext.FluidHandling getFluidHandling(Item item)
    {
        if(item instanceof FishingRodItem)
            return RaycastContext.FluidHandling.ANY;

        return RaycastContext.FluidHandling.NONE;
    }


    public static Vec3d rotateVec(Vec3d vec , float pitch, float yaw){
        Vec3d facing = vec.normalize();
        double len = vec.length();
        Vec2f py = rotationToPitchYaw(facing);
        Vec3d rotated = pitchYawToRotation(py.x  + pitch, py.y + yaw);
        return rotated.normalize().multiply(len);
    }


    public static Vec3d lookCoordTooAbsolutePos(Entity source, double x, double y, double z) {
        Vec2f vec2f = source.getRotationClient();
        Vec3d vec3d = source.;
        float f = MathHelper.cos((vec2f.y + 90.0F) * 0.017453292F);
        float g = MathHelper.sin((vec2f.y + 90.0F) * 0.017453292F);
        float h = MathHelper.cos(-vec2f.x * 0.017453292F);
        float i = MathHelper.sin(-vec2f.x * 0.017453292F);
        float j = MathHelper.cos((-vec2f.x + 90.0F) * 0.017453292F);
        float k = MathHelper.sin((-vec2f.x + 90.0F) * 0.017453292F);
        Vec3d vec3d2 = new Vec3d((double)(f * h), (double)i, (double)(g * h));
        Vec3d vec3d3 = new Vec3d((double)(f * j), (double)k, (double)(g * j));
        Vec3d vec3d4 = vec3d2.crossProduct(vec3d3).multiply(-1.0);
        double d = vec3d2.x * z + vec3d3.x * y + vec3d4.x * x;
        double e = vec3d2.y * z + vec3d3.y * y + vec3d4.y * x;
        double l = vec3d2.z * z + vec3d3.z * y + vec3d4.z * x;
        return new Vec3d(vec3d.x + d, vec3d.y + e, vec3d.z + l);
    }

    public static PlayerEntity getPlayerByName(String name){
        return MinecraftClient.getInstance().world.getPlayers().stream().filter(m->m.getNameForScoreboard().equals(name)).findFirst().orElse(null);
    }

    public static Stream<String> getWorldPlayerNames(boolean containSelf){
        return mc.world.getPlayers().stream().filter(i-> containSelf || i != mc.player).map(PlayerEntity::getNameForScoreboard);
    }



    public static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7) {
            return Vec3d.ZERO;
        } else {
            Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply((double)speed);
            float f = MathHelper.sin(yaw * 0.017453292F);
            float g = MathHelper.cos(yaw * 0.017453292F);
            return new Vec3d(vec3d.x * (double)g - vec3d.z * (double)f, vec3d.y, vec3d.z * (double)g + vec3d.x * (double)f);
        }
    }


    public static void smoothPlayerInputState(){

    }












    public static Text getEntityDisplayable(Entity target){
        return target instanceof PlayerEntity player? Text.literal(player.getNameForScoreboard()) : target.getDisplayName();
    }


}
