package me.matl114.utils;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.*;
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
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Vector2d;
import org.spongepowered.include.com.google.common.collect.BiMap;
import org.spongepowered.include.com.google.common.collect.HashBiMap;

public class EntityUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void parseEntityWhiteList(String value, Set<EntityType<?>> collection) {
        collection.clear();
        try {
            for (net.minecraft.entity.EntityType<?> entityType : Registries.ENTITY_TYPE) {
                if (Pattern.matches(
                        value, Registries.ENTITY_TYPE.getId(entityType).getPath())) {
                    collection.add(entityType);
                }
            }
            if (Pattern.matches(value, "animal")) {
                for (net.minecraft.entity.EntityType<?> entityType : Registries.ENTITY_TYPE) {
                    if (entityType.getSpawnGroup() == SpawnGroup.CREATURE) {
                        collection.add(entityType);
                    }
                }
            }
            if (Pattern.matches(value, "monster")) {
                for (net.minecraft.entity.EntityType<?> entityType : Registries.ENTITY_TYPE) {
                    if (entityType.getSpawnGroup() == SpawnGroup.MONSTER) {
                        if (!(entityType == EntityType.ZOMBIFIED_PIGLIN)
                                && !(entityType == net.minecraft.entity.EntityType.ENDERMAN)) {
                            collection.add(entityType);
                        }
                    }
                }
            }
            // feat: add spawn group flag
            for (SpawnGroup group : SpawnGroup.values()) {
                if (group != SpawnGroup.MONSTER && Pattern.matches(value, group.getName())) {
                    for (net.minecraft.entity.EntityType<?> entityType : Registries.ENTITY_TYPE) {
                        if (entityType.getSpawnGroup() == group) {
                            collection.add(entityType);
                        }
                    }
                }
            }
            if (Pattern.matches(value, "living_entity")) {
                for (EntityType<?> entityType : Registries.ENTITY_TYPE) {
                    if (entityType.getSpawnGroup() != SpawnGroup.MISC) {
                        collection.add(entityType);
                    }
                }
            }
            var iter = collection.iterator();
            while (iter.hasNext()) {
                EntityType<?> entityType = iter.next();
                if (Pattern.matches(
                        value, "!" + Registries.ENTITY_TYPE.getId(entityType).getPath())) {
                    iter.remove();
                }
            }
        } catch (Throwable valuePatternError) {
            collection.clear();
        }
        // Debug.info("Using whitelist",set);

    }

    private static final BiMap<Item, EntityType<?>> ITEM2SPAWN_ENTITY = HashBiMap.create();

    static {
        for (Item item : Registries.ITEM) {
            if (item instanceof SpawnEggItem egg) {
                ITEM2SPAWN_ENTITY.put(item, egg.getEntityType(null, new ItemStack(item)));
            }
        }
    }

    public static EntityType<?> spawnEggToEntity(Item spawner) {
        return ITEM2SPAWN_ENTITY.getOrDefault(spawner, null);
    }

    public static Item entityToSpawnEgg(EntityType<?> entityType) {
        return ITEM2SPAWN_ENTITY.inverse().getOrDefault(entityType, null);
    }

    public static EntityType<?> getStoredEntityType(ItemStack stack) {
        if (stack != null
                && stack.getItem() instanceof BlockItem block
                && block.getBlock() instanceof SpawnerBlock spawner
                && ItemStackUtils.hasInPatch(stack, DataComponentTypes.BLOCK_ENTITY_DATA)) {
            NbtComponent component = ItemStackUtils.getInPatch(stack, DataComponentTypes.BLOCK_ENTITY_DATA);
            return getSpawnerEntityType(component.getNbt());
        }
        return null;
    }

    public static EntityType<?> getSpawnerEntityType(NbtCompound spawnerCompound) {
        return spawnerCompound == null
                ? null
                : Registries.ENTITY_TYPE
                        .getOrEmpty(getSpawnedEntityId(spawnerCompound, "SpawnData"))
                        .orElse(null);
    }

    public static Identifier getSpawnedEntityId(NbtCompound nbt, String spawnDataKey) {
        if (nbt.contains(spawnDataKey)) {
            if (nbt.get(spawnDataKey) instanceof NbtCompound cp1) {
                if (cp1.get("entity") instanceof NbtCompound cp2) {
                    if (cp2.get("id") instanceof NbtString nbt3) {
                        String string = nbt3.asString();
                        if (string != null && !string.isEmpty()) {
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

    public static Vector2d getEntityLookXZ(Entity entity) {
        float yaw = entity.getYaw();
        float pitch = entity.getPitch();
        float f = MathHelper.cos(-yaw * 0.017453292F - 3.1415927F);
        float g = MathHelper.sin(-yaw * 0.017453292F - 3.1415927F);
        float h = -MathHelper.cos(-pitch * 0.017453292F);
        return new Vector2d(g * h, f * h);
    }

    public static void setEntityRotation(Entity entity, Vec3d vec) {
        vec = vec.normalize();

        entity.setPitch((float) Math.toDegrees(Math.asin(-vec.y)));
        entity.setYaw((float) Math.toDegrees(Math.atan2(-vec.x, vec.z)));
    }

    public static void setEntityYawSafe(Entity entity, Vec2f vec2f) {
        setEntityYawSafe(entity, (float) Math.toDegrees(Math.atan2(-vec2f.x, vec2f.y)));
    }

    public static void setEntityRotationSafe(Entity entity, Vec3d vec) {
        vec = vec.normalize();
        setEntityPitchSafe(entity, (float) Math.toDegrees(Math.asin(-vec.y)));

        float newYaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
        setEntityYawSafe(entity, newYaw);
    }

    public static float getSafeYaw(Entity entity, float newYaw) {
        //        if(newYaw == -180.0 || newYaw == 180.0)return newYaw;
        //        float oldYaw = entity.getYaw();
        float oldYaw = entity.getYaw();
        return getSafeYaw(oldYaw, newYaw);
    }

    public static float getSafeYaw(float oldYaw, float newYaw) {
        //        if(newYaw == -180.0 || newYaw == 180.0)return newYaw;
        //        float oldYaw = entity.getYaw();
        float diff = getSafeYawDiff(oldYaw, newYaw);
        return oldYaw + diff;
    }

    public static float getSafeYawDiff(float oldYaw, float newYaw) {
        float diff = newYaw - oldYaw;
        return (diff % 360.0F + 720.0F + 180.0F) % 360.0F - 180.0F; // 归一化到 [-180,180]
    }

    public static float getSafePitch(float newPitch) {
        // fix: 当玩家低头的时候不要改成抬头
        return normalizePitch(newPitch);
    }

    public static float normalizeYaw(float yaw) {
        if (yaw > -1E-5 && yaw < 360 + 1E-5) {
            return yaw;
        }
        return ((yaw % 360.0F + 720.0F + 180.0F) % 360.0F) - 180.0F;
    }

    public static float normalizePitch(float newPitch) {
        if (newPitch < 90.0F + 1E-5 && newPitch > -90.0F - 1E-5) return newPitch;
        return (newPitch % 180.0F + 720.0F + 90.0F) % 180.0F - 90.0F; // 归一化到 [-90, 90]
    }

    public static void setEntityYawSafe(Entity entity, float newYaw) {
        newYaw = getSafeYaw(entity, newYaw);
        entity.setYaw(newYaw);
    }

    public static void setEntityPitchSafe(Entity entity, float newPitch) {
        entity.setPitch(getSafePitch(newPitch));
    }

    public static Vec3d pitchYawToRotation(float pitch, float yaw) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d((double) (i * j), (double) (-k), (double) (h * j));
    }

    public static Vec2f rotationToPitchYaw(Vec3d vec) {
        return new Vec2f(rotationToPitch(vec), rotationToYaw(vec));
    }

    public static Vec2f directionToPitchYaw(Direction direction) {
        switch (direction) {
            case DOWN:
                return new Vec2f(89.9F, 0);
            case UP:
                return new Vec2f(-89.9F, 0);
            case NORTH:
                return new Vec2f(0, 180);
            case SOUTH:
                return new Vec2f(0, 0);
            case WEST:
                return new Vec2f(0, 90);
            case EAST:
                return new Vec2f(0, -90);
            default:
                throw new IllegalArgumentException("Unknown direction: " + direction);
        }
    }

    public static float rotationToYaw(Vec3d vec) {
        return (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
    }

    public static float rotationToYaw(Direction direction) {
        switch (direction) {
            case SOUTH:
                return 0.0F;
            case WEST:
                return 90.0F;
            case NORTH:
                return 180.0F;
            case EAST:
                return -90.0F;
            default:
                // 对于UP/DOWN，返回0或任意值，但通常不会调用
                return 0.0F;
        }
    }

    public static float rotationToPitch(Vec3d vec) {
        return (float) Math.toDegrees(Math.asin(-vec.y));
    }

    public static Direction pitchYawToDirection(Vec2f pitchYaw) {
        float pitch = pitchYaw.x;
        float yaw = pitchYaw.y;

        double radPitch = Math.toRadians(pitch);
        double radYaw = Math.toRadians(yaw);

        double cosPitch = Math.cos(radPitch);
        double sinPitch = Math.sin(radPitch);
        double cosYaw = Math.cos(radYaw);
        double sinYaw = Math.sin(radYaw);

        double x = -cosPitch * sinYaw;
        double y = -sinPitch;
        double z = cosPitch * cosYaw;

        double x2 = x * x;
        double y2 = y * y;
        double z2 = z * z;

        if (x2 > y2 && x2 > z2) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        } else if (y2 > x2 && y2 > z2) {
            return y > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    public static Direction yawToHorizontalDirection(float yaw) {
        // 将角度偏移 45°，使边界落在整数点上
        float shifted = yaw + 45;
        // 归一化到 [0, 360)
        float norm = shifted % 360;
        if (norm < 0) norm += 360;
        int quarter = (int) (norm / 90);
        switch (quarter) {
            case 0:
                return Direction.SOUTH; // 原偏移后 0-90 -> 原 -45~45 -> 南
            case 1:
                return Direction.WEST; // 90-180 -> 45~135 -> 西
            case 2:
                return Direction.NORTH; // 180-270 -> 135~225 -> 北
            default:
                return Direction.EAST; // 270-360 -> 225~315 -> 东
        }
    }

    public static boolean isRotationDifferent(float lastPitch, float pitch, float lastYaw, float yaw) {
        return Math.abs(pitch - lastPitch) > 1e-2 || Math.abs(EntityUtils.getSafeYawDiff(lastYaw, yaw)) > 1e-2;
    }

    public static double getProjectileGravity(Item item) {
        if (item instanceof RangedWeaponItem) return 0.05;

        if (item instanceof ThrowablePotionItem) return 0.4;

        if (item instanceof FishingRodItem) return 0.15;

        if (item instanceof TridentItem) return 0.015;

        return 0.03;
    }

    public static RaycastContext.FluidHandling getFluidHandling(Item item) {
        if (item instanceof FishingRodItem) return RaycastContext.FluidHandling.ANY;

        return RaycastContext.FluidHandling.NONE;
    }

    public static Vec3d rotateVec(Vec3d vec, float pitch, float yaw) {
        Vec3d facing = vec.normalize();
        double len = vec.length();
        Vec2f py = rotationToPitchYaw(facing);
        Vec3d rotated = pitchYawToRotation(py.x + pitch, py.y + yaw);
        return rotated.normalize().multiply(len);
    }

    public static Vec3d lookCoordToAbsolutePos(Entity source, double x, double y, double z) {
        Vec2f vec2f = source.getRotationClient();
        Vec3d vec3d = source.getPos();

        float f = MathHelper.cos((vec2f.y + 90.0F) * 0.017453292F);

        float g = MathHelper.sin((vec2f.y + 90.0F) * 0.017453292F);

        float h = MathHelper.cos(-vec2f.x * 0.017453292F);

        float i = MathHelper.sin(-vec2f.x * 0.017453292F);
        float j = MathHelper.cos((-vec2f.x + 90.0F) * 0.017453292F);
        float k = MathHelper.sin((-vec2f.x + 90.0F) * 0.017453292F);
        Vec3d vec3d2 = new Vec3d((double) (f * h), (double) i, (double) (g * h));
        Vec3d vec3d3 = new Vec3d((double) (f * j), (double) k, (double) (g * j));
        Vec3d vec3d4 = vec3d2.crossProduct(vec3d3).multiply(-1.0);
        double d = vec3d2.x * z + vec3d3.x * y + vec3d4.x * x;
        double e = vec3d2.y * z + vec3d3.y * y + vec3d4.y * x;
        double l = vec3d2.z * z + vec3d3.z * y + vec3d4.z * x;
        return new Vec3d(vec3d.x + d, vec3d.y + e, vec3d.z + l);
    }

    public static Vec3d lookCoordToPos(float pitch, float yaw, double x, double y, double z) {
        Vec2f vec2f = new Vec2f(pitch, yaw);

        float f = MathHelper.cos((vec2f.y + 90.0F) * 0.017453292F);

        float g = MathHelper.sin((vec2f.y + 90.0F) * 0.017453292F);

        float h = MathHelper.cos(-vec2f.x * 0.017453292F);

        float i = MathHelper.sin(-vec2f.x * 0.017453292F);
        float j = MathHelper.cos((-vec2f.x + 90.0F) * 0.017453292F);
        float k = MathHelper.sin((-vec2f.x + 90.0F) * 0.017453292F);
        Vec3d vec3d2 = new Vec3d((double) (f * h), (double) i, (double) (g * h));
        Vec3d vec3d3 = new Vec3d((double) (f * j), (double) k, (double) (g * j));
        Vec3d vec3d4 = vec3d2.crossProduct(vec3d3).multiply(-1.0);
        double d = vec3d2.x * z + vec3d3.x * y + vec3d4.x * x;
        double e = vec3d2.y * z + vec3d3.y * y + vec3d4.y * x;
        double l = vec3d2.z * z + vec3d3.z * y + vec3d4.z * x;
        return new Vec3d(d, e, l);
    }

    public static PlayerEntity getPlayerByName(String name) {
        return MinecraftClient.getInstance().world.getPlayers().stream()
                .filter(m -> m.getNameForScoreboard().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static Stream<String> getWorldPlayerNames(boolean containSelf) {
        return mc.world.getPlayers().stream()
                .filter(i -> containSelf || i != mc.player)
                .map(PlayerEntity::getNameForScoreboard);
    }

    public static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7) {
            return Vec3d.ZERO;
        } else {
            Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply((double) speed);
            float f = MathHelper.sin(yaw * 0.017453292F);
            float g = MathHelper.cos(yaw * 0.017453292F);
            return new Vec3d(
                    vec3d.x * (double) g - vec3d.z * (double) f, vec3d.y, vec3d.z * (double) g + vec3d.x * (double) f);
        }
    }

    public static void smoothPlayerInputState() {}

    public static Text getEntityDisplayable(Entity target) {
        return target instanceof PlayerEntity player
                ? Text.literal(player.getNameForScoreboard())
                : target.getDisplayName();
    }

    public static double sqrtSpeed(Vec3d vec) {
        return Math.sqrt(vec.x * vec.x + vec.z * vec.z);
    }

    public static Vec3d withStrafe(Vec3d self, double speed, double strength, PlayerInputUtils.Input input, float yaw) {
        // 输入无效（无移动输入）时水平速度清零
        if (input != null && !input.hasWASDMovement()) {
            return new Vec3d(0.0, self.y, 0.0);
        }

        // 保留部分原有水平速度
        double prevX = self.x * (1.0 - strength);
        double prevZ = self.z * (1.0 - strength);
        double useSpeed = speed * strength;

        // 根据 yaw 计算新方向的单位向量，并叠加原速度
        double angle = Math.toRadians(yaw);
        double x = -Math.sin(angle) * useSpeed + prevX;
        double z = Math.cos(angle) * useSpeed + prevZ;

        return new Vec3d(x, self.y, z);
    }

    public static float getMovementDirectionOfInput(float facingYaw, PlayerInputUtils.Input input) {
        boolean forwards = input.forward() && !input.backward();
        boolean backwards = input.backward() && !input.forward();
        boolean left = input.left() && !input.right();
        boolean right = input.right() && !input.left();

        float actualYaw = facingYaw;
        float forward = 1.0f;

        if (backwards) {
            actualYaw += 180f;
            forward = -0.5f;
        } else if (forwards) {
            forward = 0.5f;
        }

        if (left) {
            actualYaw -= 90f * forward;
        }
        if (right) {
            actualYaw += 90f * forward;
        }

        return MathHelper.wrapDegrees(actualYaw);
    }

    public static final double SQRT_SPEED = Math.sqrt(0.0825);

    public static Vec3d withStrafe(Vec3d self, double speed) {

        return withStrafe(self, speed, 1.0D);
    }

    /**
     * 指定 speed 和 strength
     */
    public static Vec3d withStrafe(Vec3d self, double speed, double strength) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        PlayerInputUtils.Input input = PlayerInputUtils.of(player.input);
        float yaw = getMovementDirectionOfInput(player.getYaw(), input);
        return withStrafe(self, speed, strength, input, yaw);
    }
}
