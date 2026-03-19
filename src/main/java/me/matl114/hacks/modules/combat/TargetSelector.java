package me.matl114.hacks.modules.combat;

import com.google.common.collect.ImmutableList;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;

public class TargetSelector extends BaseModule {
    public static final String[] ATTACK_WHITELISTED = {"attack", "whitelist"};
    public static final String[] ATTACK_PLAYER_FRIENDLIST = {"attack", "friends"};
    public static final String[] ATTACK_NAMED = {"attack", "att-named"};
    public static final String[] ATTACK_TEAMMATE = {"attack", "att-teammate"};
    public static final String[] ATTACK_HOSTILE = {"attack", "att-hostile"};
    public static final String[] COMBAT_OPPOSITE_ATTACK_MULTIPLY = {"attack", "opposite-attack-multiply"};
    public static final String[] COMBAT_PLAYER_ATTACK_MULTIPLY = {"attack", "player-attack-multiply"};

    public TargetSelector() {}

    public Set<EntityType<?>> types = new LinkedHashSet<>();

    public void parseEntityTypes(String regex) {
        Set<EntityType<?>> va = new LinkedHashSet<>();
        EntityUtils.parseEntityWhiteList(regex, va);
        types = va;
    }

    public final StringRef whiteListTypes = builder(Configs.COMBAT_CONFIG, ATTACK_WHITELISTED, StringRef.TYPE)
            .defaultValue("^(monster|!endermite|player)$")
            .validator(Configs.REGEX_VALIDATOR)
            .updateListener(this::parseEntityTypes)
            .build();

    public final StringRef friendNameRegex = builder(Configs.COMBAT_CONFIG, ATTACK_PLAYER_FRIENDLIST, StringRef.TYPE)
            .defaultValue("^(.*NPC.*|matl114)$")
            .validator(Configs.REGEX_VALIDATOR)
            .build();

    public final FlagRef attackNamedEntity = builder(Configs.COMBAT_CONFIG, ATTACK_NAMED, Boolean.class)
            .defaultValue(true)
            .build();

    @ApiStatus.Experimental
    public final FlagRef teamMate = builder(Configs.COMBAT_CONFIG, ATTACK_TEAMMATE, Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef hostile = builder(Configs.COMBAT_CONFIG, ATTACK_HOSTILE, Boolean.class)
            .defaultValue(true)
            .build();

    public final DoubleRef multiplyBackward = builder(
                    Configs.COMBAT_CONFIG, COMBAT_OPPOSITE_ATTACK_MULTIPLY, DoubleRef.TYPE)
            .defaultValue(114514.0D)
            .build();

    public final DoubleRef multiplyPlayer = builder(
                    Configs.COMBAT_CONFIG, COMBAT_PLAYER_ATTACK_MULTIPLY, DoubleRef.TYPE)
            .defaultValue(0.0D)
            .build();

    {
        if (Configs.COMBAT_CONFIG.get("att-bot", "whitelist") instanceof StringRef stringRef
                && stringRef.get() != null) {
            whiteListTypes.set(stringRef.get());
            Configs.COMBAT_CONFIG.setValueNoNew(null, "att-bot", "whitelist");
        }
        if (Configs.COMBAT_CONFIG.get("att-bot", "friends") instanceof StringRef stringRef && stringRef.get() != null) {
            friendNameRegex.set(stringRef.get());
            Configs.COMBAT_CONFIG.setValueNoNew(null, "att-bot", "friends");
        }
    }

    public boolean canAttack(Entity target) {
        if (mc.player == null) return false;
        if (target == null || target == mc.player) {
            return false;
        }
        if (target instanceof LivingEntity lv && lv.getHealth() <= 0) {
            return false;
        }
        if (!types.contains(target.getType())) {
            if (!passHostileCheck(target)) {
                return false;
            }
            // if it is not friendly to us, and attackHostile is enabled, then we can still attack them
        }

        if (!passNameCheck(target)) {
            return false;
        }

        if (!passTeamCheck(target)) {
            return false;
        }
        return true;
    }

    public boolean canAttackWithBow(Entity target) {
        return checkWeapon(target, true) && canAttack(target);
    }

    private boolean passNameCheck(Entity e) {
        if (e instanceof PlayerEntity pl) {
            String name = pl.getNameForScoreboard();
            String regex = friendNameRegex.get();
            if (regex != null && Pattern.matches(regex, name)) {
                // friend
                return false;
            }
            return true;
        } else {
            if (e.hasCustomName()) {
                if (attackNamedEntity.get()) {
                    String regex = friendNameRegex.get();
                    if (regex != null && Pattern.matches(regex, ChatUtils.textToString(e.getCustomName()))) {
                        return false;
                    }
                    return true;
                } else {
                    return false;
                }
            } else {
                return true;
            }
        }
    }

    private boolean passTeamCheck(Entity e) {
        if (teamMate.get()) {
            if (e instanceof PlayerEntity pl) {

                if (pl.getScoreboardTeam() != null && pl.getScoreboardTeam() == mc.player.getScoreboardTeam()) {
                    Team team = pl.getScoreboardTeam();
                    // 过滤友伤
                    if (!team.isFriendlyFireAllowed()) {
                        return false;
                    }
                }
                // more, consider colors of chestplates
                return true;
            }
        }
        return true;
    }

    private boolean passHostileCheck(Entity e) {
        if (hostile.get()) {
            if (e instanceof BeeEntity bee) {
                if (bee.getAngerTime() > 0) {
                    return true;
                }
            } else if (e instanceof EndermanEntity entity) {
                if (entity.isAngry()) {
                    return true;
                }
            } else if (e instanceof WolfEntity ironGolem) {
                if (ironGolem.getAngerTime() > 0) {
                    return true;
                }
            } else if (e instanceof ZombifiedPiglinEntity piglin) {
                // any rules
            }
            return false;
        } else {
            return false;
        }
    }

    public List<Entity> getAttackableEntities(double nearby) {
        return getAttackableEntities(nearby, this::canAttack);
    }

    private List<Entity> getAttackableEntities(double nearby, Predicate<Entity> predicate) {
        List<Entity> entities = new ArrayList<>();
        List<Entity> et = ImmutableList.copyOf(mc.world.getEntities());
        for (var e : et) {
            if (isTargetInRange(e, nearby) && predicate.test(e)) {
                entities.add(e);
            }
        }
        return entities;
    }

    public boolean checkWeapon(Entity e, boolean commonBow) {
        return (!commonBow || (!(e instanceof EndermanEntity) && !(e instanceof ShulkerEntity)));
    }

    public List<Entity> getAimableEntities(boolean commonBow) {
        return getAimableEntities(commonBow ? this::canAttackWithBow : this::canAttack);
    }

    public List<Entity> getAimableEntities(Predicate<Entity> predicate) {
        List<Entity> entities = new ArrayList<>();
        List<Entity> et = ImmutableList.copyOf(mc.world.getEntities());
        for (var e : et) {
            // 普通弹射物， 无法攻击末影人和贝壳， 过滤掉
            if (predicate.test(e) && canPlayerDirectlySee(e)) {
                entities.add(e);
            }
        }
        return entities;
    }

    public boolean isTargetInRange(Entity e, double nearby) {
        if (mc.player == null) return false;
        return e.getBoundingBox().squaredMagnitude(mc.player.getEyePos()) < MathUtils.s2(nearby);
    }

    public Entity searchAttackEntity(double nearby, boolean autoSelect) {
        return searchAttackEntity(nearby, autoSelect, null);
    }

    public Entity searchAttackEntity(double nearby, boolean autoSelect, Predicate<Entity> predicate) {
        if (mc.player == null) return null;
        // when tp reach, also attack the targeted entity first
        Predicate<Entity> combinedPredicate =
                predicate != null ? (e) -> canAttack(e) && predicate.test(e) : this::canAttack;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity entityCheck = ((EntityHitResult) mc.crosshairTarget).getEntity();
            // fix: check attackable when not auto
            if (!autoSelect || combinedPredicate.test(entityCheck)) {
                return entityCheck;
            }
        }
        HitResult result = RaycastUtils.createEntityOnlyCrossHairResult(mc.player, nearby, 1.0F, combinedPredicate);
        if (result != null && result.getType() == HitResult.Type.ENTITY) {
            // focusing entity， attack
            // should respect whitelist
            if (combinedPredicate.test(((EntityHitResult) result).getEntity())) {
                //                    mc.interactionManager.attackEntity(mc.player,
                // ((EntityHitResult)result).getEntity());
                //                    mc.player.swingHand(Hand.MAIN_HAND);
                //                    handleShieldPredict(mc.player.getPitch(), mc.player.getYaw());
                return ((EntityHitResult) result).getEntity();
            }
        }
        // this use mc.crosshairTarget; if hand ...
        if (!autoSelect
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK
                && CombatTasks.notSuitableForAttack(mc.player.getMainHandStack())) {
            // stop if player only want to mine a block
            return null;
        }
        List<Entity> targets = getAttackableEntities(nearby, combinedPredicate);
        // Debug.info(pos);
        // fixed: if player is targeting a faraway entity, then it should be privileged
        // fixed: should not target entity at back of me, because some anticheat place fake players to test killarua;
        // use weighted value
        Vec3d vec3d = mc.player.getEyePos();
        Vec3d eye = mc.player.getRotationVector().normalize();
        targets.sort(Comparator.comparingDouble(e -> {
            var pos = e.getPos().subtract(vec3d).normalize(); // .dotProduct(eye))
            return (-withMultiply(
                    e,
                    (pos.x * eye.x + pos.z * eye.z)
                            / (e.getPos().subtract(vec3d).horizontalLength() + 1E-10)));
        }));
        if (!targets.isEmpty()) {
            return targets.get(0);
        }
        return null;
    }

    public Entity searchAimableEntity(boolean commonBow) {
        return searchAimableEntity(commonBow, null);
    }

    public Entity searchAimableEntity(boolean commonBow, Predicate<Entity> predicate) {
        if (mc.player == null) return null;
        // when tp reach, also attack the targeted entity first
        Predicate<Entity> originPredicate = commonBow ? this::canAttackWithBow : this::canAttack;
        Predicate<Entity> combinedPredicate =
                predicate != null ? (e) -> originPredicate.test(e) && predicate.test(e) : originPredicate;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
            if (combinedPredicate.test(entity)) {
                return entity;
            }
        }
        // 25格子之内的瞄准

        HitResult result = RaycastUtils.createEntityOnlyCrossHairResult(mc.player, 25, 1.0F, combinedPredicate);
        if (result != null && result.getType() == HitResult.Type.ENTITY) {
            // focusing entity， attack
            // should respect whitelist
            return ((EntityHitResult) result).getEntity();
        }
        List<Entity> targets = getAimableEntities(combinedPredicate);
        // filter raycast
        // 考虑夹角
        Vec3d vec3d = mc.player.getEyePos();
        Vec3d playerRotation = mc.player.getRotationVector().normalize();
        // 通过
        if (targets.isEmpty()) return null;
        // 通过视角偏差
        targets.sort(Comparator.comparingDouble(
                e -> -e.getEyePos().subtract(vec3d).normalize().dotProduct(playerRotation)));
        // Debug.info(pos);
        return targets.get(0);
    }

    private double withMultiply(Entity e, double v) {
        return Math.abs(v)
                - ((v < 0.0) ? multiplyBackward.get() : 0.0D)
                + (e instanceof PlayerEntity ? multiplyPlayer.get() : 0.0D);
    }

    private static boolean canPlayerDirectlySee(Entity entity) {
        // 横向距离小于300
        return entity.getPos().subtract(mc.player.getPos()).horizontalLengthSquared() < 90000
                && !RaycastUtils.raycastAnySolidBlock(mc.player, mc.player.getEyePos(), entity.getEyePos());
    }
}
