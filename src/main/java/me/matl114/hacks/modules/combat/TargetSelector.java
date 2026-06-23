package me.matl114.hacks.modules.combat;

import com.google.common.collect.ImmutableList;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.DoubleStream;
import me.matl114.commands.MainCommand;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.entity.CameraEntity;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;

public class TargetSelector extends BaseModule {
    public static TargetSelector INSTANCE;
    public final ModulePath attack = makePath(Configs.COMBAT_CONFIG, "attack");

    public TargetSelector() {
        INSTANCE = this;
    }

    public Set<EntityType<?>> types = new LinkedHashSet<>();

    public void parseEntityTypes(String regex) {
        Set<EntityType<?>> va = new LinkedHashSet<>();
        EntityUtils.parseEntityWhiteList(regex, va);
        types = va;
    }

    public final FlagRef grimExpandEyeHeight =
            flagBuilder(attack.add("use-grim-expand-eye-height")).build();

    public final StringRef whiteListTypes = builder(attack.add("whitelist"), StringRef.TYPE)
            .defaultValue("^(monster|!endermite|player)$")
            .validator(Configs.REGEX_VALIDATOR)
            .updateListener(this::parseEntityTypes)
            .build();

    public final StringRef friendNameRegex = builder(attack.add("friends"), StringRef.TYPE)
            .defaultValue("^(.*NPC.*|matl114)$")
            .validator(Configs.REGEX_VALIDATOR)
            .build();

    public final ListRef friendList = builder(attack.add("friend-list"), ListRef.TYPE)
            .defaultValue(List.of())
            .build();

    public final KeyBindRef addFriend = hotkey(attack.add("add-friend-hotkey"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::onAddFriend))
            .build();

    public final FlagRef attackFriend =
            builder(attack.add("att-friend"), FlagRef.TYPE).defaultValue(true).build();

    public final FlagRef attackNamedEntity =
            builder(attack.add("att-named"), Boolean.class).defaultValue(true).build();

    @ApiStatus.Experimental
    public final FlagRef teamMate = builder(attack.add("att-teammate"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef hostile =
            builder(attack.add("att-hostile"), Boolean.class).defaultValue(true).build();

    public final FlagRef invulnerable =
            flagBuilder(attack.add("att-invulnerable")).build();

    public final FlagRef multiplyBackward =
            flagBuilder(attack.add("opposite-attack-multiply")).build();

    public final DoubleRef multiplyPlayer = builder(attack.add("player-attack-multiply"), DoubleRef.TYPE)
            .defaultValue(0.0D)
            .build();

    public final FlagRef fakePlayerDetect =
            flagBuilder(attack.add("fake-player-and-npc-detect")).build();

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
        // todo: move whitelist
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerCommandBootstrap(this::onFriendCommandBootstrap);
    }

    private static final double[] FALL_FLYING_EYE_HEIGHTS = {0.4D, 1.62D, 1.27D};
    private static final double[] STANDING_EYE_HEIGHTS = {1.62D, 1.27D, 0.4D};

    public DoubleStream getPotentialEyeHeights() {
        if (grimExpandEyeHeight.get()) {
            double scale = mc.player.getScale();
            if (mc.player.isFallFlying() || mc.player.isUsingRiptide() || mc.player.isSwimming()) {
                return DoubleStream.concat(
                        Arrays.stream(FALL_FLYING_EYE_HEIGHTS).map(s -> s * scale),
                        DoubleStream.of(mc.player.dimensions.eyeHeight()));
            }
            return DoubleStream.concat(
                    Arrays.stream(STANDING_EYE_HEIGHTS).map(s -> s * scale),
                    DoubleStream.of(mc.player.dimensions.eyeHeight()));
        }
        return DoubleStream.of(mc.player.dimensions.eyeHeight());
    }

    public boolean isWithinAttackRange(Vec3d pos, Box box, double range) {
        if (box.squaredMagnitude(pos) > MathUtils.s2(range + 2 + mc.player.dimensions.eyeHeight())) {
            // filter all outofrange
            // optimize calculation
            return false;
        }
        return getPotentialEyeHeights()
                .mapToObj(s -> pos.add(0, s, 0))
                .anyMatch(ps -> box.squaredMagnitude(ps) < MathUtils.s2(range));
    }

    public Vec3d getBestAttackEyePos(Vec3d pos, Box box) {
        var poses = getPotentialEyeHeights().mapToObj(s -> pos.add(0, s, 0)).toList();
        Vec3d playerPos = pos.add(mc.player.getEyePos().subtract(mc.player.getPos()));
        double s2 = box.squaredMagnitude(playerPos);
        for (var pp : poses) {
            double s3 = box.squaredMagnitude(pp);
            if (s3 < s2) {
                s2 = s3;
                playerPos = pp;
            }
        }
        return playerPos;
    }

    public boolean onAddFriend() {
        if (mc.crosshairTarget.getType() == HitResult.Type.ENTITY
                && ((EntityHitResult) mc.crosshairTarget).getEntity() instanceof PlayerEntity player
                && player != mc.player) {
            addFriend(player.getNameForScoreboard());
        }
        return false;
    }

    public void addFriend(String friends) {
        List<String> friendList = this.friendList.get();
        if (friendList.contains(friends)) {
            Debug.chat(ChatUtils.stringToText("&c[Friends] &f你已经添加了 %s 为好友".formatted(friends)));
        } else {
            Debug.chat(ChatUtils.stringToText("&c[Friends] &f你成功添加了 %s 为好友".formatted(friends)));
            friendList = new ArrayList<>(friendList);
            friendList.add(friends);
            this.friendList.set(friendList);
        }
    }

    public void removeFriend(String friend) {
        List<String> friendList = this.friendList.get();
        if (friendList.contains(friend)) {
            Debug.chat(ChatUtils.stringToText("&c[Friends] &f你成功移除了 %s 好友".formatted(friend)));
            friendList = new ArrayList<>(friendList);
            friendList.remove(friend);
            this.friendList.set(friendList);
        } else {
            Debug.chat(ChatUtils.stringToText("&c[Friends] &f你暂未添加 %s 为好友".formatted(friend)));
        }
    }

    public boolean canAttack(Entity target) {
        if (mc.player == null) return false;
        if (target == null || target == mc.player || target instanceof CameraEntity) {
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

        if (!isNotFriend(target)) {
            return false;
        }

        if (!isNotTeamMate(target)) {
            return false;
        }
        if (!isNotInvulnerable(target)) {
            return false;
        }
        return true;
    }

    public boolean canAttackWithBow(Entity target) {
        return checkWeapon(target, true) && canAttack(target);
    }

    public boolean isInFriendList(PlayerEntity e) {
        List<String> list = friendList.get();
        if (list != null && list.contains(e.getNameForScoreboard())) {
            return true;
        }
        return false;
    }

    public boolean isNotFriend(Entity e) {
        if (e instanceof PlayerEntity pl) {
            String name = pl.getNameForScoreboard();
            String regex = friendNameRegex.get();
            if (regex != null && Pattern.matches(regex, name)) {
                // friend
                return false;
            }
            if (!attackFriend.get()) {
                if (isInFriendList(pl)) {
                    return false;
                }
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

    public boolean isNotTeamMate(Entity e) {
        if (teamMate.get()) {
            if (e instanceof PlayerEntity pl) {

                if (pl.getScoreboardTeam() != null && pl.getScoreboardTeam() == mc.player.getScoreboardTeam()) {
                    Team team = pl.getScoreboardTeam();
                    // 过滤友伤
                    if (!team.isFriendlyFireAllowed()) {
                        return false;
                    }
                }
                ItemStack chestPlate = pl.getEquippedStack(EquipmentSlot.CHEST);
                if (chestPlate.contains(DataComponentTypes.DYED_COLOR)) {
                    ItemStack ourPlate = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                    if (ourPlate.contains(DataComponentTypes.DYED_COLOR)) {
                        if (Objects.equals(
                                chestPlate.get(DataComponentTypes.DYED_COLOR),
                                ourPlate.get(DataComponentTypes.DYED_COLOR))) {
                            return false;
                        }
                    }
                }
                // more, consider colors of chestplates
                return true;
            }
        }
        return true;
    }

    public boolean isNotInvulnerable(Entity e) {
        if (!invulnerable.get()) {
            if (e instanceof PlayerEntity pl) {
                // login players are invulnerable
                if (pl.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) < 1e-6) {
                    return false;
                }
                // creative players are invulnerable
                if (pl.isCreative()) {
                    return false;
                }
                // wtf
                if (pl.isInvulnerable()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean passHostileCheck(Entity e) {
        if (hostile.get()) {
            if (e instanceof Angerable anger) {
                long time = anger.getAngerEndTime();
                long currentTime = e.getEntityWorld().getTime();
                return currentTime < time;
            }
            return false;
        } else {
            return false;
        }
    }

    public List<Entity> getAttackableEntities(double nearbyOverride) {
        return getAttackableEntities(nearbyOverride, 0, this::canAttack);
    }

    private List<Entity> getAttackableEntities(double nearbyOverride, int ticks, Predicate<Entity> predicate) {
        List<Entity> entities = new ArrayList<>();
        List<Entity> et = ImmutableList.copyOf(mc.world.getEntities());
        for (var e : et) {
            if (predicate.test(e) && isTargetInRange(e, nearbyOverride, ticks)) {
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

    public boolean isTargetInRange(Entity e, double nearby, int ticks) {
        if (mc.player == null) return false;
        nearby = Math.max(nearby, CombatExtra.INSTANCE.getAttackAtTargetRange(e));
        Vec3d predictedPlayerPos =
                mc.player.getPos().add(mc.player.getVelocity().multiply(mc.player.isFallFlying() ? ticks : 0));
        return isWithinAttackRange(predictedPlayerPos, e.getBoundingBox(), nearby);
    }

    public Entity searchAttackEntity(double nearby, boolean autoSelect) {
        return searchAttackEntity(nearby, autoSelect, 0);
    }

    public Entity searchAttackEntity(double nearby, boolean autoSelect, int tickPredict) {
        return searchAttackEntity(nearby, autoSelect, tickPredict, null);
    }

    public Entity searchAttackEntity(double nearby, boolean autoSelect, Predicate<Entity> predicate) {
        return searchAttackEntity(nearby, autoSelect, 0, predicate);
    }

    public Entity searchAttackEntity(double nearby, boolean autoSelect, int tickPredict, Predicate<Entity> predicate) {
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
        List<Entity> targets = getAttackableEntities(nearby, tickPredict, combinedPredicate);
        // Debug.info(pos);
        // fixed: if player is targeting a faraway entity, then it should be privileged
        // fixed: should not target entity at back of me, because some anticheat place fake players to test killarua;
        // use weighted value
        targets.sort(Comparator.comparingDouble(e -> {
            return getEntityWeight(e, mc.player);
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

    private double getEntityWeight(Entity e, PlayerEntity player) {
        Vec3d vec3d = player.getEyePos();
        Vec3d eye = player.getRotationVector().normalize();
        var pos = e.getPos().subtract(vec3d).normalize(); // .dotProduct(eye))
        double horizontalMultiply = (pos.x * eye.x + pos.z * eye.z);
        if (multiplyBackward.get()) {
            if (horizontalMultiply >= 0) {
                return -((horizontalMultiply / ((e.getPos().subtract(vec3d).horizontalLength() + 1E-10)))
                        + (e instanceof PlayerEntity ? multiplyPlayer.get() : 0.0D));
            } else {
                // rotate
                double horizontalNormalize = horizontalMultiply / (pos.length() * eye.length() + 1E-10);
                return -(horizontalNormalize + (e instanceof PlayerEntity ? multiplyPlayer.get() : 0.0D));
            }
        } else {
            return -(Math.abs(
                            (horizontalMultiply) / ((e.getPos().subtract(vec3d).horizontalLength() + 1E-10)))
                    + (e instanceof PlayerEntity ? multiplyPlayer.get() : 0.0D));
        }
    }

    //    private double withMultiply(Entity e, double v) {
    //        return Math.abs(v)
    //                - ((v < 0.0) ? multiplyBackward.get() : 0.0D)
    //                + (e instanceof PlayerEntity ? multiplyPlayer.get() : 0.0D);
    //    }

    private static boolean canPlayerDirectlySee(Entity entity) {
        // 横向距离小于300
        return entity.getPos().subtract(mc.player.getPos()).horizontalLengthSquared() < 90000
                && !RaycastUtils.raycastAnySolidBlock(mc.player, mc.player.getEyePos(), entity.getEyePos());
    }

    private void onFriendCommandBootstrap(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.mainBuilder().name("friends_command").build();
        {
            main.subBuilder(SubCommand.treeBuilder())
                    .name("friends")
                    .post(m -> m.subBuilder(SubCommand.taskBuilder())
                            .name("list")
                            .helper("显示好友列表")
                            .post(e -> e.executor(CommandContext.run(() -> {
                                Debug.chat(Text.literal("== 当前好友列表 ==").formatted(Formatting.GREEN));
                                for (var re : this.friendList.get()) {
                                    Debug.chat(re);
                                }
                            })))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("add")
                            .helper("添加好友")
                            .arg(me.matl114.utils.commands.params.SimpleCommandArgs.argumentBuilder()
                                    .name("name")
                                    .tabSupplier(WorldUtils::getPlayerListNames)
                                    .build())
                            .post(e -> e.executor(CommandContext.run(
                                    (Consumer<ArgumentInputStream>) (arg) -> this.addFriend(arg.nextNonnullString()))))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("remove")
                            .helper("移除好友")
                            .arg(SimpleCommandArgs.argumentBuilder()
                                    .name("name")
                                    .tabSupplier(() -> this.friendList.get().stream())
                                    .build())
                            .post(e -> e.executor(CommandContext.run((arg) -> {
                                this.removeFriend(arg.nextNonnullString());
                            })))
                            .complete())
                    .complete();
        }
    }
}
