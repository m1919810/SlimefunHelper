package me.matl114.utils;

import me.matl114.utils.UtilClass.Point;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.include.com.google.common.collect.BiMap;
import org.spongepowered.include.com.google.common.collect.HashBiMap;

import java.util.HashSet;
import java.util.regex.Pattern;

public class EntityUtils {
    public static void parseEntityWhiteList(String value, HashSet<EntityType<?>> collection){
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
            String string = nbt.getCompound(spawnDataKey).getCompound("entity").getString("id");
            if(string != null && !string.isEmpty()){
                return Identifier.tryParse(string);
            }
            return null;
        } else {

            return null;
        }
    }


    public static void setEntityRotation(Entity entity, Vec3d vec){
        vec = vec.normalize();

        entity.setPitch((float) Math.toDegrees( Math.asin(- vec.y)));
        entity.setYaw((float)Math.toDegrees(Math.atan2( -vec.x, vec.z)));
    }

    public static void setEntityRotationSafe(Entity entity, Vec3d vec){
        vec = vec.normalize();
        entity.setPitch((float) Math.toDegrees( Math.asin(- vec.y)));


        float newYaw = (float)Math.toDegrees(Math.atan2( -vec.x, vec.z));
        setEntityYawSafe(entity, newYaw);
    }
    public static void setEntityYawSafe(Entity entity, float newYaw){
        float oldYaw = entity.getYaw();
        if(newYaw > oldYaw + 180 ){
            newYaw = newYaw - 360;
        }else if(newYaw < oldYaw - 180){
            newYaw = newYaw + 360;
        }
        entity.setYaw(newYaw);
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
        return new Vec2f((float) Math.toDegrees( Math.asin(- vec.y)),(float)Math.toDegrees(Math.atan2( -vec.x, vec.z)));
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



}
