package me.matl114.hacks.modules.combat;

import com.mojang.datafixers.util.Pair;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.SlimefunHelper;
import me.matl114.accessors.access.PlayerInteractEntityC2SPacketAccess;
import me.matl114.accessors.hacks.PlayerInternalAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.EventContainer;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicContentWidget;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.move.ElytraFlight;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.*;
import me.matl114.hacks.utils.entity.PredictorImpl;
import me.matl114.hacks.utils.enums.PredictionMode;
import me.matl114.hacks.utils.move.ElytraOptimizeUtils;
import me.matl114.hacks.utils.move.FlightVelocity;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.algorithms.StateMachine;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VDrawContext;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.ApiStatus;

public class ElytraBot extends BaseModule {
    public final ModulePath combatBot = makePath(Configs.COMBAT_CONFIG, "combat-bot");
    public final ModulePath elytraBot = combatBot.add("elytra-bot");

    public static ElytraBot INSTANCE;

    public ElytraBot() {
        super("ElytraBot");
        bindFlag(enable);
        INSTANCE = this;
    }

    public final FlagRef enable = flagBuilder(elytraBot.add("enable")).build();

    public final KeyBindRef keyBind = moduleEntry(
                    elytraBot.add("hotkey"), new MultiKeyBind(), elytraBot.add("enable"), moduleMeta(() -> this.mode))
            .build();

    public final IntRef targetRange = intBuilder(elytraBot.add("range"))
            .defaultValue(80)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final EnumRef<Mode> mode =
            builder(elytraBot.add("mode"), Mode.class).defaultValue(Mode.FOLLOW).build();

    public final FlagRef playerOnly = builder(elytraBot.add("player-only"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final FlagRef autoControl =
            flagBuilder(elytraBot.add("auto-control-elytra")).build();

    public final FlagRef dynamicTarget =
            flagBuilder(elytraBot.add("dynamic-target")).build();

    public final FlagRef onlyWhenNoWASD =
            flagBuilder(elytraBot.add("only-when-no-wasd")).build();

    public final DoubleRef combatMaceRange = builder(elytraBot.add("combat-range"), DoubleRef.TYPE)
            .defaultValue(10.0D)
            .build();

    public final DoubleRef combatSpearRange = builder(elytraBot.add("combat-spear-range"), DoubleRef.TYPE)
            .defaultValue(11.0D)
            .build();

    public final FlagRef followFriend = flagBuilder(elytraBot.add("follower-follow-friend"))
            .show(() -> mode.get().isIn(Mode.FOLLOW))
            .build();

    public final NBTRef<OptionalPrimitive<Double>> followOnGroundHeight = builder(
                    elytraBot.add("follow-on-ground-height-extra"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(true, NBTTypes.DOUBLE_TYPE, 2.0D))
            .show(() -> mode.get().isNotIn(Mode.SPEAR_ARUA))
            .build();

    public final FlagRef maceUsePredictor = flagBuilder(elytraBot.add("mace-pull-up-use-predictor"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef maceTryFollowUsePredictor = flagBuilder(elytraBot.add("mace-use-predictor-to-judge-follow"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef logSpearHit = flagBuilder(elytraBot.add("log-spear-hit"))
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA))
            .build();

    public final FlagRef spearUsePredictor = flagBuilder(elytraBot.add("spear-use-predictor"))
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA))
            .build();

    public final FlagRef render = flagBuilder(elytraBot.add("render")).build();

    public final KeyBindRef switchMode = hotkey(elytraBot.add("switch-hotkey"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::onSwitch))
            .build();

    private final ModulePath attackMace = elytraBot.add("mace-attack-settings");

    {
        portConfigs(elytraBot, attackMace, "mace-combat-use-extra-attack");
        portConfigs(elytraBot, attackMace, "mace-post-attack");
        portConfigs(elytraBot, attackMace, "mace-combat-consider-use");
    }

    public final FlagRef maceAttackUseSimple = builder(attackMace.add("mace-combat-use-extra-attack"), Boolean.class)
            .defaultValue(true)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef appendPostAttack = builder(attackMace.add("mace-post-attack"), Boolean.class)
            .defaultValue(false)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef maceAttackConsiderUse = flagBuilder(attackMace.add("mace-combat-consider-use"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef maceFix = flagBuilder(attackMace.add("mace-fix"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final IntRef macePreSwapDelay = intBuilder(attackMace.add("mace-pre-swap-delay"))
            .defaultValue(2)
            .validator(Configs.INT_NONNEGATIVE)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef macePreSwapDistancePerTick = doubleBuilder(attackMace.add("mace-pre-swap-distance-per-tick"))
            .defaultValue(0.0D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final IntRef maceSwapMinDelay = intBuilder(attackMace.add("mace-swap-min-delay"))
            .defaultValue(2)
            .validator(Configs.INT_NONNEGATIVE)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    private final ModulePath maceHeightAndChase = elytraBot.add("mace-height-and-pull-up");

    {
        portConfigs(elytraBot, maceHeightAndChase, "mace-height");
        portConfigs(elytraBot, maceHeightAndChase, "mace-height-ground");
        portConfigs(elytraBot, maceHeightAndChase, "mace-max-extra-pull-up-tick");
        portConfigs(elytraBot.add("combat-smooth-flight"), maceHeightAndChase.add("combat-smooth-flight-pullup"));
        portConfigs(
                elytraBot.add("combat-smooth-flight-argument-1"),
                maceHeightAndChase.add("combat-smooth-flight-circle-extra-range"));
        portConfigs(
                elytraBot.add("combat-smooth-flight-argument-1-1"),
                maceHeightAndChase.add("combat-smooth-flight-out-pull-ratio"));
        portConfigs(
                elytraBot.add("combat-smooth-flight-argument-1-6"),
                maceHeightAndChase.add("combat-smooth-flight-out-pull-ratio-enable-distance"));
        portConfigs(
                elytraBot.add("combat-smooth-flight-argument-1-5"),
                maceHeightAndChase.add("combat-smooth-flight-out-pull-ratio-use-horizontal-distance"));
        portConfigs(
                elytraBot.add("combat-smooth-flight-argument-1-4"),
                maceHeightAndChase.add("combat-smooth-flight-dynamic-circle-extra-range"));
        portConfigs(
                elytraBot.add("combat-smooth-flight-2"), maceHeightAndChase.add("combat-smooth-flight-pullup-attack"));
        portConfigs(
                elytraBot.add("combat-smooth-flight-argument-2-1"),
                maceHeightAndChase.add("combat-smooth-flight-pull-attack-max-horizontal"));
        portConfigs(
                elytraBot.add("combat-smooth-flight-argument-2-2"),
                maceHeightAndChase.add("combat-smooth-flight-pull-attack-max-relative-y"));
    }

    public final DoubleRef maceHeight = builder(maceHeightAndChase.add("mace-height"), DoubleRef.TYPE)
            .defaultValue(10.0D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef maceHeightGround = builder(maceHeightAndChase.add("mace-height-ground"), DoubleRef.TYPE)
            .defaultValue(10.0D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final IntRef maceRemainPullUpTick = builder(
                    maceHeightAndChase.add("mace-max-extra-pull-up-tick"), IntRef.TYPE)
            .defaultValue(20)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef maceChaseFollowYBias = flagBuilder(
                    maceHeightAndChase.add("combat-mace-chase-follow-use-y-bias"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef maceChaseFollowBiasStartHorizontalRange = doubleBuilder(
                    maceHeightAndChase.add("combat-mace-chase-follow-use-y-bias-range"))
            .defaultValue(15.0D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef maceChaseFollowBiasExtraHeightPerBlock = doubleBuilder(
                    maceHeightAndChase.add("combat-mace-chase-follow-use-y-bias-extra-height-per-block"))
            .defaultValue(0.6)
            .build();

    public final FlagRef combatSmoothFlightPullup = flagBuilder(maceHeightAndChase.add("combat-smooth-flight-pullup"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef combatSmoothCircleExtraRange = doubleBuilder(
                    maceHeightAndChase.add("combat-smooth-flight-circle-extra-range"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .defaultValue(1.0D)
            .build();

    public final NBTRef<OptionalPrimitive<Vec3>> combatSmoothCircleDynamicRangeByHeight = builder(
                    maceHeightAndChase.add("combat-smooth-flight-dynamic-circle-extra-range"),
                    OptionalPrimitive.type(Vec3.class))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.VEC3_TYPE, new Vec3(10, 0.3, 20)))
            .build();

    public final DoubleRef combatSmoothOutPullRatio = doubleBuilder(
                    maceHeightAndChase.add("combat-smooth-flight-out-pull-ratio"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .defaultValue(1.0D)
            .build();

    public final DoubleRef combatSmoothOutPullRatioEnableDistance = doubleBuilder(
                    maceHeightAndChase.add("combat-smooth-flight-out-pull-ratio-enable-distance"))
            .defaultValue(16.0D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef combatSmoothOutPullRatioUseHorizontalDistance = flagBuilder(
                    maceHeightAndChase.add("combat-smooth-flight-out-pull-ratio-use-horizontal-distance"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef combatSmoothFlightPullupAttack = flagBuilder(
                    maceHeightAndChase.add("combat-smooth-flight-pullup-attack"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef combatSmoothFlightPullupAttackMaxHorizontal = doubleBuilder(
                    maceHeightAndChase.add("combat-smooth-flight-pull-attack-max-horizontal"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .defaultValue(10.0D)
            .build();

    public final DoubleRef combatSmoothFlightPullupAttackMinRelativeY = doubleBuilder(
                    maceHeightAndChase.add("combat-smooth-flight-pull-attack-min-relative-y"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .defaultValue(0.0D)
            .build();

    public final DoubleRef combatSmoothFlightPullupAttackMaxRelativeY = doubleBuilder(
                    maceHeightAndChase.add("combat-smooth-flight-pull-attack-max-relative-y"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .defaultValue(10.0D)
            .build();

    public final ModulePath maceFollow = elytraBot.add("mace-follow-and-attack");

    {
        portConfigs(elytraBot, maceFollow, "mace-max-follow-height");
        portConfigs(elytraBot, maceFollow, "mace-y-level-lerp");
    }

    public final DoubleRef maceFollowMinHeight = doubleBuilder(maceFollow.add("mace-max-follow-height"))
            .defaultValue(1.5D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef maceYLevelWeight = doubleBuilder(maceFollow.add("mace-y-level-lerp"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .defaultValue(0.0)
            .build();

    public final DoubleRef chooseDownTargetDistance = builder(
                    maceFollow.add("choose-down-target-distance"), DoubleRef.TYPE)
            .defaultValue(3.0D)
            .build();

    public final FlagRef followDownPosWhenLow = builder(maceFollow.add("follow-lerp-pos-when-low"), Boolean.class)
            .defaultValue(false)
            .build();

    public final DoubleRef minPitchDownwards = doubleBuilder(maceFollow.add("follow-min-pitch-deg"))
            .defaultValue(15.0D)
            .build();

    public final FlagRef maceFollowUsePredictorLine =
            flagBuilder(maceFollow.add("predict-line")).build();

    public final DoubleRef maceFollowStartPredictDistance = doubleBuilder(maceFollow.add("start-predict-distance"))
            .defaultValue(10.0D)
            .build();

    public final DoubleRef maceFollowPredictTicksPerDistance = doubleBuilder(
                    maceFollow.add("predict-ticks-per-distance"))
            .defaultValue(0.5D)
            .build();

    public final DoubleRef maceFollowMaxPredictionTicks = doubleBuilder(maceFollow.add("max-prediction-ticks"))
            .defaultValue(3.0D)
            .build();

    public final FlagRef continuePullupIfCannotChaseTarget = flagBuilder(
                    maceFollow.add("continue-pull-up-if-can-not-target-prediction"))
            .build();

    // todo:
    public final FlagRef followAntiCenter =
            flagBuilder(maceFollow.add("follow-anti-center")).build();

    public final ModulePath angleOptimize = elytraBot.add("angle-optimizing-flight");

    {
        portConfigs(elytraBot.add("combat-angle-optimize"), angleOptimize.add("combat-angle-optimize-pullup"));
        portConfigs(elytraBot, angleOptimize, "combat-angle-optimize-follow");
        portConfigs(elytraBot, angleOptimize, "combat-angle-optimize-range");
        portConfigs(
                elytraBot.add("combat-angle-optimize-radical"),
                angleOptimize.add("combat-angle-optimize-radical-pullup"));
        portConfigs(elytraBot, angleOptimize, "combat-angle-optimize-radical-pull-up-optimize-range");
        portConfigs(
                elytraBot.add("combat-angle-optimize-radical-follow"),
                angleOptimize.add("combat-angle-optimize-follow-pitch-limit"));
        portConfigs(
                elytraBot.add("combat-pull-up-angle-optimize"),
                angleOptimize.add("combat-angle-optimize-pullup-persistent-direction"));
    }

    public final FlagRef angleOptimizePullUp = builder(angleOptimize.add("combat-angle-optimize-pullup"), Boolean.class)
            .defaultValue(false)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA)
                    && ElytraExtra.INSTANCE.autoRescale.get()
                    && ElytraFlight.INSTANCE.useAutoRescale.get())
            .build();

    public final FlagRef angleOptimizeAxisXZPullupOut = builder(
                    angleOptimize.add("combat-angle-optimize-xz-pullup-out"), Boolean.class)
            .defaultValue(true)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA)
                    && ElytraExtra.INSTANCE.autoRescale.get()
                    && ElytraFlight.INSTANCE.useAutoRescale.get())
            .build();

    public final DoubleRef angleOptimizeAxisXZOutRange = doubleBuilder(
                    angleOptimize.add("combat-angle-optimize-xz-pullup-out-range"))
            .defaultValue(30.0D)
            .build();

    public final FlagRef angleOptimizeAxisXZPulluIn = builder(
                    angleOptimize.add("combat-angle-optimize-xz-pullup-in"), Boolean.class)
            .defaultValue(false)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA)
                    && ElytraExtra.INSTANCE.autoRescale.get()
                    && ElytraFlight.INSTANCE.useAutoRescale.get())
            .build();

    public final DoubleRef angleOptimizeAxisXZInRange = doubleBuilder(
                    angleOptimize.add("combat-angle-optimize-xz-pullup-in-range"))
            .defaultValue(12.0D)
            .build();
    // 这个傻逼玩意， 代表的是 激进的拉升优化

    public final FlagRef angleOptimizeRadicalPullup = flagBuilder(
                    angleOptimize.add("combat-angle-optimize-radical-pullup"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA)
                    && ElytraExtra.INSTANCE.autoRescale.get()
                    && ElytraFlight.INSTANCE.useAutoRescale.get()
                    && ElytraExtra.INSTANCE
                            .autoRescaleAl
                            .get()
                            .isIn(ElytraExtra.Al.V2, ElytraExtra.Al.V3, ElytraExtra.Al.V4))
            .build();
    // 直接往外拉

    public final NBTRef<OptionalPrimitive<Double>> angleOptimizePullRange = builder(
                    angleOptimize.add("combat-angle-optimize-radical-pull-up-optimize-range"),
                    OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 20.0D))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA)
                    && ElytraExtra.INSTANCE.autoRescale.get()
                    && ElytraFlight.INSTANCE.useAutoRescale.get()
                    && ElytraExtra.INSTANCE
                            .autoRescaleAl
                            .get()
                            .isIn(ElytraExtra.Al.V2, ElytraExtra.Al.V3, ElytraExtra.Al.V4))
            .build();

    public final FlagRef angleOptimizeFollow = builder(angleOptimize.add("combat-angle-optimize-follow"), Boolean.class)
            .defaultValue(false)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA)
                    && ElytraExtra.INSTANCE.autoRescale.get()
                    && ElytraFlight.INSTANCE.useAutoRescale.get())
            .build();
    public final NBTRef<LabelVec3> angleOptimizeFollowRange = builder(
                    angleOptimize.add("combat-angle-optimize-range"), LabelVec3.class)
            .defaultValue(new LabelVec3(
                    "widget.elytra-bot.angle.normal-flight",
                    "widget.elytra-bot.angle.spear-flight",
                    "widget.elytra-bot.angle.anti-spear-flight",
                    new Vec3(4.0, 4.0, 4.0)))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA)
                    && ElytraExtra.INSTANCE.autoRescale.get()
                    && ElytraFlight.INSTANCE.useAutoRescale.get())
            .build();

    // 这个傻逼玩意 代表的是激进的追击角度优化是否有角度限制（在垂直向下的时候禁用角度优化）
    public final NBTRef<OptionalPrimitive<Double>> angleOptimizeFollowPitchLimit = builder(
                    angleOptimize.add("combat-angle-optimize-follow-pitch-limit"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(true, NBTTypes.DOUBLE_TYPE, 75.0D))
            .validator(s -> s.getValue() >= 0.0D && s.getValue() <= 90.0D)
            .show(() -> mode.get().isIn(Mode.MACE_ARUA)
                    && ElytraExtra.INSTANCE.autoRescale.get()
                    && ElytraFlight.INSTANCE.useAutoRescale.get())
            .build();

    // 这个傻逼玩意。在拉高的时候会来回摆。非常的炫酷(何意味

    public final NBTRef<OptionalPrimitive<Double>> pullupPersistentDirection = builder(
                    angleOptimize.add("combat-angle-optimize-pullup-persistent-direction"),
                    OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 6.0D))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA)
                    && ElytraExtra.INSTANCE.autoRescale.get()
                    && ElytraFlight.INSTANCE.useAutoRescale.get())
            .build();

    public final ModulePath antiSpear = elytraBot.add("anti-spear-settings");

    {
        portConfigs(elytraBot, antiSpear, "fly-anti-spear");
        portConfigs(elytraBot, antiSpear, "fly-anti-spear-when-pull-up");
        portConfigs(elytraBot, antiSpear, "fly-anti-spear-disable-when-using-spear");
        portConfigs(elytraBot, antiSpear, "fly-anti-spear-after-angle");
        portConfigs(elytraBot, antiSpear, "fly-anti-spear-use-spear-reset-when-follow");
    }

    public final NBTRef<OptionalPrimitive<Double>> flyAntiSpear = builder(
                    antiSpear.add("fly-anti-spear"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(true, NBTTypes.DOUBLE_TYPE, 1.0D))
            .show(() ->
                    mode.get().isNotIn(Mode.SPEAR_ARUA) || (mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV2.get()))
            .build();

    public final DoubleRef flyAntiSpearRange = doubleBuilder(antiSpear.add("fly-anti-spear-range"))
            .defaultValue(9.0D)
            .build();

    public final NBTRef<OptionalPrimitive<Double>> flyAntiSpearWhenPullup = builder(
                    antiSpear.add("fly-anti-spear-when-pull-up"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 1.0D))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final DoubleRef flyAntiSpearPullupRange = doubleBuilder(antiSpear.add("fly-anti-spear-pull-up-range"))
            .defaultValue(9.0D)
            .build();

    @ApiStatus.Experimental
    public final FlagRef flyAntiSpearDisableWhenSpear = flagBuilder(
                    antiSpear.add("fly-anti-spear-disable-when-using-spear"))
            .show(() -> (mode.get().isNotIn(Mode.SPEAR_ARUA)
                    || (mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV2.get())))
            .experimental()
            .build();

    public final FlagRef flyAntiSpearOnlyWhenPredictedHit = flagBuilder(
                    antiSpear.add("fly-anti-spear-use-predictor-to-predict-hit"))
            .build();

    public final FlagRef flyAntiSpearPullupWhenPredictHit = flagBuilder(
                    antiSpear.add("fly-anti-spear-pull-up-when-predict-hit"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef flyAntiSpearRevertFlyWhenPredictHit = flagBuilder(
                    antiSpear.add("fly-anti-spear-revert-direction-when-predict-hit"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef flyAntiSpearRotateFlyWhenPredictHit = flagBuilder(
                    antiSpear.add("fly-anti-spear-rotate-direction-when-predict-hit"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final FlagRef flyAntiSpearAfterAngle = builder(antiSpear.add("fly-anti-spear-after-angle"), Boolean.class)
            .defaultValue(true)
            .show(() -> (mode.get().isNotIn(Mode.SPEAR_ARUA)
                    || (mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV2.get())))
            .build();

    public final FlagRef flyAntiSpearUseSpearResetWhenFollow = flagBuilder(
                    antiSpear.add("fly-anti-spear-use-spear-reset-when-follow"))
            .show(() -> mode.get().isIn(Mode.MACE_ARUA))
            .build();

    public final ModulePath spearVer2 = elytraBot.add("spear-arua-v2");

    @ApiStatus.Experimental
    public final FlagRef spearTestV2 = builder(spearVer2.add("spear-v2"), Boolean.class)
            .defaultValue(false)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA)
                    && ElytraExtra.INSTANCE.autoRescale.get()
                    && ElytraFlight.INSTANCE.useAutoRescale.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final FlagRef spearAngleOptimize = builder(spearVer2.add("spear-angle-optimize"), Boolean.class)
            .defaultValue(false)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA)
                    && ElytraExtra.INSTANCE.autoRescale.get()
                    && ElytraFlight.INSTANCE.useAutoRescale.get()
                    && spearTestV2.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final NBTRef<Vec2> spearV2HeightRange = builder(spearVer2.add("spear-v2-height-range"), Vec2.class)
            .defaultValue(new Vec2(0.0, 4.0D))
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV2.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final DoubleRef spearV2PositionLerp = doubleBuilder(spearVer2.add("spear-v2-position-lerp"))
            .defaultValue(1.0D)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV2.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final DoubleRef spearV2AdjustPositionHorizontal = doubleBuilder(
                    spearVer2.add("spear-v2-adjust-pos-horizontal-amount"))
            .defaultValue(0.0D)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV2.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final DoubleRef spearV2PreAttackRange = doubleBuilder(spearVer2.add("spear-v2-pre-attack-range"))
            .defaultValue(9.0D)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV2.get())
            .experimental()
            .build();

    public final ModulePath spearVer3 = elytraBot.add("spear-arua-v3");

    @ApiStatus.Experimental
    public final FlagRef spearTestV3 = builder(spearVer3.add("spear-v3"), Boolean.class)
            .defaultValue(false)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA))
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final DoubleRef spearV3PullUpHeight = doubleBuilder(spearVer3.add("spear-v3-pull-up-height"))
            .defaultValue(8.0D)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV3.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final DoubleRef spearV3OutwardRange = doubleBuilder(spearVer3.add("spear-v3-outward-range"))
            .defaultValue(12.0D)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV3.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final DoubleRef spearV3OutwardSpeed = doubleBuilder(spearVer3.add("spear-v3-outward-speed"))
            .defaultValue(10.0D)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV3.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final DoubleRef spearV3DiveDistance = doubleBuilder(spearVer3.add("spear-v3-dive-distance"))
            .defaultValue(20.0D)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV3.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final NBTRef<OptionalPrimitive<Double>> spearV3PullUpFallDistance = builder(
                    spearVer3.add("spear-v3-pull-up-fall-distance"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 1.5D))
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV3.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final FlagRef spearV3UseMaceAttack = flagBuilder(spearVer3.add("spear-v3-use-mace-attack"))
            .defaultValue(true)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV3.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final NBTRef<OptionalPrimitive<Double>> spearV3MaceFallDistance = builder(
                    spearVer3.add("spear-v3-mace-fall-distance"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 1.5D))
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV3.get())
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final IntRef spearV3MaceAttackCooldown = builder(spearVer3.add("spear-v3-mace-attack-cooldown"), IntRef.TYPE)
            .defaultValue(5)
            .show(() -> mode.get().isIn(Mode.SPEAR_ARUA) && this.spearTestV3.get())
            .experimental()
            .build();

    public void onSwitch() {
        mode.next();
        logI18N("message.module.elytra-bot.mode-switch", mode.get().getDisplay());
    }

    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreTick(), this::onPreTick);
        registerListener(Listener.getCustomListener().getChannel(FlightVelocity.class), this::onElytraChase);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class), this::onAttack);
        registerListener(Listener.getPreHandleInputEvents(), this::onInputEvent);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onEntityStatus);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
        if (SlimefunHelper.DEV_ENV) {
            registerListener(RenderListener.getRender2DEvent(), this::onDebugRender);
        }
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onEntityDamage);
        registerListener(Listener.getEntityPostTickListener().getChannel(EntityType.PLAYER), this::onPostPlayerMove);
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        var widget = createTitle("widget.attack.attack.use-argument", 0, dblank, dx, dy);
        acceptor.accept(new DynamicContentWidget<>(
                () -> {
                    return mode.get().isIn(Mode.MACE_ARUA) ? widget : null;
                },
                0,
                0));
    }

    Entity target;

    @Nullable
    AbstractBotBehaviour currentBehaviour;

    final AbstractBotBehaviour defaultBehaviour = new Follower().setBase(this);
    final AbstractBotBehaviour maceArua = new MaceArua().setBase(this);
    final AbstractBotBehaviour maceAruaGround = new MaceAuraGround().setBase(this);
    final AbstractBotBehaviour spearArua = new SpearAura().setBase(this);
    final AbstractBotBehaviour spearV2 = new SpearAruaV2().setBase(this);
    final AbstractBotBehaviour spearV3 = new SpearAruaV3().setBase(this);

    public boolean canControlFlight() {
        return enable.get()
                && autoControl.get()
                && currentBehaviour != null
                && currentBehaviour.movementDirection != null
                && currentBehaviour.movementDirection.lengthSquared() > 1E-9;
    }

    Entity lastTarget;
    TargetAction currentAction;
    boolean currentInCombatRange;
    boolean currentOnGround;
    double speedMultiplier = 1.0D;
    boolean heightLimitEnvironment = false;

    public boolean isNoPullUpEnvironment() {
        return heightLimitEnvironment || currentOnGround;
    }

    public boolean isTargetUsingSpear() {
        return target instanceof PlayerEntity otherShit && SpearEnhance.isUsingSpear(otherShit);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public AbstractBotBehaviour getBehaviour() {
        if (enable.get()) {
            return switch (mode.get()) {
                case FOLLOW -> defaultBehaviour;
                case MACE_ARUA -> isNoPullUpEnvironment() ? maceAruaGround : maceArua;
                case SPEAR_ARUA -> spearTestV3.get() ? spearV3 : spearArua;
            };
        } else {
            return null;
        }
    }

    boolean currentArmorGliding = false;

    public void onPreTick(Event<Void> event) {
        var lastBehaviour = currentBehaviour;
        currentBehaviour = getBehaviour();
        if (currentBehaviour != lastBehaviour) {
            if (lastBehaviour != null) {
                lastBehaviour.onDisable();
            }
            if (currentBehaviour != null) {
                currentBehaviour.onEnable();
            }
        }
        if (checkNull()) return;
        currentArmorGliding = ElytraExtra.INSTANCE.isCurrentArmorGliding();
        if (currentBehaviour != null) {
            heightLimitEnvironment = mc.world.getDimension().hasCeiling()
                    && mc.player.getY()
                            < mc.world.getDimension().minY()
                                    + mc.world.getDimension().logicalHeight();
            refreshTarget();
            updateTargetAction();
            currentBehaviour.onUpdate();
        }
    }

    public void onPostPlayerMove(Event<PlayerEntity> event) {
        if (event.context == mc.player && currentBehaviour != null) {
            currentBehaviour.onPostPlayerTick();
        }
    }

    public double combatRange() {
        return (target instanceof PlayerEntity pl && SpearEnhance.isUsingSpear(pl))
                ? combatSpearRange.get()
                : combatMaceRange.get();
    }

    public void updateTargetAction() {
        if (target != lastTarget) {
            lastTarget = target;
            currentAction = null;
        }
        if (target != null) {

            // initialize pos
            double combatRange = combatRange();
            currentInCombatRange = TargetSelector.INSTANCE.isWithinAttackRange(
                    mc.player.getPos(), target.getBoundingBox(), combatRange);
            currentOnGround = target.isOnGround() || CollisionUtil.isEntitySupported(target);
            if (target instanceof PlayerEntity pl) {
                // speed < 1, we can easily handle this speed
                if (currentOnGround) {
                    currentAction = TargetAction.SLOW_SPEED;
                } else {
                    List<PredictorImpl.KnownPosition> knownPositions =
                            ((PlayerInternalAccess) target).getPredictorImpl().getLastKnownPositions(3);
                    if (knownPositions.size() < 2) {
                        // 数据不足，默认行为（可改为 TOWARDS 或不做处理）
                        currentAction = TargetAction.CIRCLING;
                    } else {
                        int currentTick = Tasks.getTick();
                        // 1. 最早的点（索引0）是否在10 tick之前
                        PredictorImpl.KnownPosition oldest = knownPositions.get(0);
                        if (currentTick - oldest.tick() > 20) {
                            currentAction = TargetAction.AFK;
                        } else {
                            // 相邻点距离检查
                            Vec3d pos0 = oldest.vec3d();
                            Vec3d pos1 = knownPositions.get(1).vec3d();
                            Vec3d pos2 = knownPositions.get(2).vec3d();

                            double dist01 = pos0.distanceTo(pos1);
                            double dist12 = pos1.distanceTo(pos2);
                            if (dist01 < 1E-6 && dist12 < 1E-6) {
                                currentAction = TargetAction.AFK;
                            }
                            // 2. 若相邻两点距离小于1.5，判定为SLOW_SPEED
                            else if (dist01 < 0.75 && dist12 < 0.75) {
                                currentAction = TargetAction.SLOW_SPEED;
                            } else if (knownPositions.size() >= 3) {
                                // 3. 计算向量 ab 和 bc 的夹角
                                Vec3d ab = pos1.subtract(pos0);
                                Vec3d bc = pos2.subtract(pos1);
                                double dot = ab.dotProduct(bc);
                                double magAB = ab.length();
                                double magBC = bc.length();
                                double angleRad = Math.acos(Math.min(1.0, Math.max(-1.0, dot / (magAB * magBC))));
                                double angleDeg = Math.toDegrees(angleRad);

                                if (angleDeg < 60.0) {
                                    // 方向变化小，判断朝向玩家还是远离玩家
                                    Vec3d playerPos = mc.player.getPos();
                                    // 使用从最新点(pos2)指向玩家的向量
                                    Vec3d toPlayer = playerPos.subtract(pos2);
                                    // 如果 bc 方向（移动方向）与指向玩家的方向夹角小于90度，视为向玩家靠近
                                    double moveDot = bc.normalize().dotProduct(toPlayer.normalize());
                                    if (moveDot > 0) {
                                        currentAction = TargetAction.TOWARDS; // 向我们来
                                    } else {
                                        currentAction = TargetAction.ESCAPING; // 离我们去
                                    }
                                } else {
                                    currentAction = TargetAction.CIRCLING;
                                }
                            } else {
                                // 点不足3个，默认行为
                                currentAction = TargetAction.TOWARDS;
                            }
                        }
                    }
                }
            } else {
                currentAction = TargetAction.SLOW_SPEED;
            }
        }
    }

    public void onRender(Event<MatrixStack> event) {
        if (enable.get() && render.get()) {
            RenderUtils.startDrawVirtual(event.context);
            try {
                MatrixStack stack = event.context;
                if (currentBehaviour != null) {
                    Vec3d targetRender = currentBehaviour.movementDirection.add(mc.player.getPos());
                    if (targetRender != null) {
                        RenderUtils.drawOutlinedBox(
                                stack,
                                targetRender.add(RenderTasks.FROM),
                                targetRender.add(RenderTasks.TO),
                                Color.MAGENTA);
                    }
                }

            } finally {
                RenderUtils.stopDrawVirtual(event.context);
            }
        }
    }

    public void onDebugRender(Event<VDrawContext> eventVDraw) {
        if (enable.get() && render.get() && currentBehaviour != null && target != null) {
            var vdraw = eventVDraw.context;
            vdraw.getMatrices().pushMatrix();
            vdraw.getMatrices().translate(200, 200);
            vdraw.drawText(
                    mc.textRenderer,
                    "Action: %s, Combating: %s".formatted(currentAction, String.valueOf(currentInCombatRange)),
                    0,
                    0,
                    -1,
                    true);
            vdraw.getMatrices().popMatrix();
        }
    }

    public void refreshTarget() {
        if (!EntityUtils.isEntityValid(target)
                || target.getPos().squaredDistanceTo(mc.player.getPos()) > targetRange.get()) {
            target = null;
        }
        if (target == null || dynamicTarget.get()) {
            target = currentBehaviour != null ? currentBehaviour.searchTarget() : null;
        }
    }

    //    private boolean isConsideredAsAttackableEntity(Entity entity) {
    //        if (!(entity instanceof PlayerEntity) && playerOnly.get()) {
    //            return false;
    //        }
    //        var raycastResult = RaycastUtils.raycastSolidBlockResult(mc.player, mc.player.getPos(), entity.getPos());
    //        if (raycastResult == null || raycastResult.getType() == HitResult.Type.MISS) {
    //            return true;
    //        }
    //        Vec3d hitPoint = raycastResult.getPos();
    //        return TargetSelector.INSTANCE.isWithinAttackRange(
    //                hitPoint, entity.getBoundingBox(), CombatExtra.INSTANCE.getAttackAtTargetRange(entity));
    //    }

    public void onElytraChase(Event<EventContainer<FlightVelocity>> event) {
        if (enable.get()) {
            AbstractBotBehaviour behaviour = currentBehaviour;
            if (behaviour != null) {
                if (onlyWhenNoWASD.get()) {
                    PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
                    if (input.hasMovementControl()) {
                        behaviour.onPauseControl();
                        return;
                    }
                }
                behaviour.onElytra(event);
            }
        }
    }

    public void onAttack(Event<PlayerInteractEntityC2SPacket> attack) {
        if (enable.get()
                && currentBehaviour != null
                && PlayerInteractEntityC2SPacketAccess.of(attack.context).isAttack()) {
            Entity entity = mc.world.getEntityById(
                    PlayerInteractEntityC2SPacketAccess.of(attack.context).getEntityId());
            if (entity != null) {
                currentBehaviour.onAttack(entity);
            }
        }
    }

    public void onInputEvent(Event<Void> event) {
        if (enable.get()) {
            AbstractBotBehaviour behaviour = currentBehaviour;
            if (behaviour != null) {
                behaviour.onInputEvent(event);
            }
        }
    }

    public void onEntityStatus(Event<EntityStatusS2CPacket> statusS2CPacketEvent) {
        var statusS2CPacket = statusS2CPacketEvent.context;
        if (currentBehaviour instanceof HitListener sp
                && mc.player != null
                && mc.world != null
                && enable.get()
                && statusS2CPacket.getEntity(mc.world) == mc.player
                && statusS2CPacket.getStatus() == VDataFlag.ENTITY_STATUS_KINETIC_ATTACK) {
            if (logSpearHit.get() && mode.get().isIn(Mode.SPEAR_ARUA)) {
                logI18N("message.module.elytra-bot.spear-hit");
            }
            sp.onHit(HitListener.HIT_SPEAR);
        }
    }

    public void onEntityDamage(Event<EntityDamageS2CPacket> e) {
        if (checkNull()) return;
        if (currentBehaviour instanceof HitListener sp
                && enable.get()
                && e.context.sourceCauseId() == mc.player.getId()
                && mc.world.getEntityById(e.context.entityId()) == target) {
            var source = e.context.sourceType().getKey().orElse(null);
            if (DamageUtils.isType(source, "mace_smash")) {
                // we trigger a mace smash
                sp.onHit(HitListener.HIT_MACE);
                return;
            }
            sp.onHit(HitListener.HIT_ATTACK);
        }
    }

    public static interface HitListener {
        static int HIT_ATTACK = 0;
        static int HIT_MACE = 1;
        static int HIT_SPEAR = 2;

        public void onHit(int type);
    }

    @Setter
    @Accessors(chain = true)
    public abstract static class AbstractBotBehaviour {
        // todo： add target anaylsis

        ElytraBot base;
        Vec3d movementDirection = Vec3d.ZERO;
        // todo: update target considering blocks , can we async calculate to let
        // use pitch search

        // todo: calculate reachable, if entity can reach reach distance
        public void onElytra(Event<EventContainer<FlightVelocity>> event) {
            if (movementDirection != null && movementDirection.lengthSquared() > 1E-9) {
                Vec3d targetVec = movementDirection;
                double targetVecVelocity = targetVec.length();
                double min =
                        Math.min(targetVecVelocity, event.context.getValue().maxVelocity() * base.speedMultiplier);
                targetVec = targetVec.normalize().multiply(min);
                event.context.getValue().velocity(targetVec);
            }
        }

        protected Vec3d calculateTargetDirection(Vec3d predictorPos) {
            if (!ElytraExtra.INSTANCE.autoRescale.get()
                    || ElytraExtra.INSTANCE.autoRescaleAl.get().isIn(ElytraExtra.Al.V1, ElytraExtra.Al.V2)) {
                //
                if (SpearEnhance.isUsingSpear(mc.player)) {
                    return (predictorPos
                                    .add(0, base.target.getEyeHeight(base.target.getPose()), 0)
                                    .subtract(mc.player.getEyePos()))
                            .normalize();
                } else {
                    return predictorPos.subtract(mc.player.getPos()).normalize();
                }
            } else {
                Vec3d legacy;
                Vec3d forward;
                if (SpearEnhance.isUsingSpear(mc.player)) {
                    Vec3d targetPos = predictorPos.add(0, base.target.getEyeHeight(base.target.getPose()), 0);
                    legacy = targetPos.subtract(mc.player.getEyePos());
                    forward = ElytraOptimizeUtils.calculateLookTowardsTargetV3Direction(legacy, 1.7);
                } else {
                    legacy = predictorPos.subtract(mc.player.getPos());
                    forward = ElytraOptimizeUtils.calculateTowardsTargetV3Direction(legacy, 1.7);
                }
                if (legacy.dotProduct(forward) < 0) {
                    return legacy;
                } else {
                    return forward;
                }
            }
        }

        protected Vec3d simulateMovement(Vec3d movement) {
            if (!ElytraExtra.INSTANCE.autoRescale.get()
                    || ElytraExtra.INSTANCE.autoRescaleAl.get().isIn(ElytraExtra.Al.V1, ElytraExtra.Al.V2)) {
                return movement.normalize().multiply(1.7);
            } else {
                var pitchYaw = EntityUtils.rotationToPitchYaw(movement.normalize());
                return ElytraOptimizeUtils.simulateAxisLimitSpeed(
                        movement.normalize().multiply(1.7), pitchYaw.x, pitchYaw.y);
            }
        }

        public void onPostPlayerTick() {}

        public Entity searchTarget() {
            return CombatTasks.getTargetSelector()
                    .searchAttackEntity(
                            base.targetRange.get(),
                            true,
                            base.playerOnly.get() ? (e) -> e instanceof PlayerEntity : null);
        }

        public synchronized void onUpdate() {
            base.speedMultiplier = 1.0D;
        }

        public void onInputEvent(Event<Void> eventInput) {}

        public synchronized void onAttack(Entity entity) {}

        public abstract void onEnable();

        public abstract void onDisable();

        public void onPauseControl() {}

        protected boolean willUseAntiSpear(OptionalPrimitive<Double> op, double distance, Vec3d predictorPos) {
            return (op.isPresent()
                    && (!(base.flyAntiSpearDisableWhenSpear.get() && SpearEnhance.isUsingSpear(mc.player)))
                    && Math.abs(op.getValue()) > 1E-6
                    && base.isTargetUsingSpear()
                    && mc.player.getPos().squaredDistanceTo(predictorPos) < MathUtils.s2(distance)
                    && (!base.flyAntiSpearOnlyWhenPredictedHit.get() || predictMayHitAntiSpear(predictorPos)));
        }

        protected boolean predictMayHitAntiSpear(Vec3d predictor) {
            Box originalBox = mc.player.getBoundingBox();
            Vec3d newVec3d = simulateMovement(movementDirection);
            Box currentBox = originalBox.offset(newVec3d).expand(0.3, 0.3, 0.3);
            Vec3d originalVec = base.target.getPos();
            Vec3d predictorMovement = predictor.subtract(originalVec).normalize();
            Vec3d test = predictorMovement.multiply(12);
            if (currentBox.raycast(originalVec, predictor).isEmpty()
                    && currentBox.raycast(predictor, predictor.add(test)).isEmpty()) {
                return false;
            }
            return true;
        }

        protected void antiSpear(OptionalPrimitive<Double> op) {
            ;
            Vec3d originalLookHorizontal = movementDirection.withAxis(Direction.Axis.Y, 0);
            if (originalLookHorizontal.lengthSquared() < 1E-2) {
                //
                double range = 3;
                if (mc.player.getPos().subtract(base.target.getPos()).horizontalLength() < range) {
                    movementDirection = movementDirection.withAxis(Direction.Axis.X, 5);
                    originalLookHorizontal = movementDirection.withAxis(Direction.Axis.Y, 0);
                } else {
                    // pulling up, do not antispear
                    return;
                }
            }
            Vec3d vertical = new Vec3d(0, 1, 0);
            Vec3d side = vertical.crossProduct(originalLookHorizontal).normalize();
            Vec3d origin = movementDirection.normalize();

            Vec3d multiply = side.multiply(op.getValue());
            movementDirection = origin.add(multiply).normalize().multiply(10);
        }
    }

    public static class Follower extends AbstractBotBehaviour {
        // todo: add in-hole behaviour, add hole-esp related, add landing

        @Override
        public Entity searchTarget() {
            return CombatTasks.getTargetSelector().searchAttack(base.targetRange.get(), true, 0, this::canBeAttack);
        }

        public boolean canBeAttack(Entity entity) {
            if (entity instanceof PlayerEntity player
                    && player != mc.player
                    && base.followFriend.get()
                    && !TargetSelector.INSTANCE.isNotFriend(player)) {
                return true;
            } else {
                return TargetSelector.INSTANCE.canAttack(entity)
                        && (!base.playerOnly.get() || entity instanceof PlayerEntity);
            }
        }

        @Override
        public synchronized void onUpdate() {
            super.onUpdate();
            if (base.target != null) {
                movementDirection = base.target.getPos().subtract(mc.player.getPos());
                if (base.currentOnGround) {
                    var op = base.followOnGroundHeight.get();
                    if (op.isPresent()) {
                        movementDirection = movementDirection.add(0, op.getValue(), 0);
                    }
                }
            } else {
                movementDirection = Vec3d.ZERO;
            }
        }

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}
    }

    public abstract static class AbstractMaceBehaviour extends AbstractBotBehaviour implements HitListener {
        static final int STATE_PULL_UP = 1;
        static final int STATE_FOLLOW = 2;
        static final int STATE_WAIT_ATTACK = 3;
        static final int STATE_NONE = 0;
        public StateMachine stateMachine;
        int startWaitAttack = -1;
        double maxHeightInAttack = Double.MIN_VALUE;
        double startPullUp = Double.MIN_VALUE;
        int startPullUpTick = 0;

        public AbstractMaceBehaviour() {
            stateMachine = new StateMachine(
                    STATE_NONE,
                    this::onCondition,
                    this::onStateNone,
                    this::onStatePullUp,
                    this::onStateFollow,
                    this::onStateWaitAttack);
            stateMachine.registerListener(STATE_WAIT_ATTACK, this::onStartWaitAttack);
            stateMachine.registerListener(STATE_PULL_UP, this::onStartPullUp);
        }

        public int onCondition(StateMachine machine, int state) {
            if (base.target == null) {
                machine.markForEndState();
                movementDirection = Vec3d.ZERO;
                return STATE_NONE;
            }
            return state;
        }

        public int onStateNone(StateMachine machine) {
            if (base.target != null) {
                if (PlayerStateManager.INSTANCE.fallDistance > 4
                        && mc.player.getPos().getY() > base.target.getPos().getY() + 4.0) {
                    return STATE_FOLLOW;
                }
                return STATE_PULL_UP;
            }
            movementDirection = Vec3d.ZERO;
            machine.markForEndState();
            return STATE_NONE;
        }

        public abstract int onStatePullUp(StateMachine machine);

        public abstract int onStateFollow(StateMachine machine);
        // compat delay attack shit, add cd,
        public int onStateWaitAttack(StateMachine machine) {

            if (base.currentAction != TargetAction.AFK && base.currentAction != TargetAction.SLOW_SPEED) {
                machine.markForEndState();
                return STATE_PULL_UP;
            }
            if (++startWaitAttack > 1) {
                if (base.currentOnGround) {
                    return STATE_NONE;
                } else {
                    return STATE_FOLLOW;
                }
            }
            machine.markForEndState();
            // stay!
            Vec3d targetPos = base.maceUsePredictor.get()
                    ? PositionPredict.INSTANCE
                            .attackPredictArgument
                            .get()
                            .predict(base.target)
                            .withAxis(Direction.Axis.Y, base.target.getY())
                    : base.target.getPos();
            double lerpY = base.maceYLevelWeight.get();
            double yLerp = targetPos.y * lerpY + base.target.getY() * (1.0D - lerpY);
            targetPos = targetPos.withAxis(Direction.Axis.Y, yLerp);
            setTargetToPlayer(targetPos);
            return STATE_WAIT_ATTACK;
        }

        protected abstract void setTargetToPlayerUpper(Vec3d predictor, double height);

        protected abstract void setTargetToPlayer(Vec3d targetPos);

        boolean thisTickStartFallFlying;

        @Override
        public synchronized void onUpdate() {
            super.onUpdate();
            this.thisTickStartFallFlying = ElytraExtra.INSTANCE.isThisTickArmoGlideMovementServerSideGlide();
            if (mc.player.isFallFlying() || mc.player.getAbilities().flying) {
                maxHeightInAttack = Math.max(maxHeightInAttack, mc.player.getY());
                stateMachine.step();
                // Debug.info("State", stateMachine.getState(), "height", PlayerStateManager.INSTANCE.fallDistance);
                // todo: consider cooldown, do not attack too fast
                if (attackFlag) {
                    if (base.target != null) {
                        // anti shield
                        boolean simpleAttack = shouldAttackSimple();
                        boolean maceAttack = canAttackMace();
                        if (simpleAttack) {
                            // can not deal mace attack anyway
                            Attack.AttackSettings settings = CombatTasks.getAttack()
                                    .createAttackSettings()
                                    .withMaceSwap(false);
                            if (shouldUseAntiShield()) {
                                settings = settings.withAntiShieldSwap(true);
                            }
                            CombatTasks.getAttack().attackEntity(base.target, settings);
                        }
                        if (maceAttack) {
                            Attack.AttackSettings settings =
                                    CombatTasks.getAttack().createAttackSettings();
                            CombatTasks.getAttack()
                                    .attackEntity(
                                            base.target,
                                            settings.withMaceSwap(true)
                                                    .withInvSwap(false)
                                                    .withAntiShieldSwap(false));
                            lastMaceAttackTick = Tasks.getTick();
                            Debug.debug("[ElytraBot] Do mace attack here", Tasks.getTick());
                        }
                    }
                    maxHeightInAttack = mc.player.getY();
                    attackFlag = false;
                }
            } else {
                stateMachine.setState(STATE_NONE);
                movementDirection = Vec3d.ZERO;
            }
            lastFallDistance = PlayerStateManager.INSTANCE.fallDistance;
        }

        @Override
        public void onPostPlayerTick() {
            super.onPostPlayerTick();
            if (base.target != null
                    && base.appendPostAttack.get()
                    && !thisTickStartFallFlying
                    && TargetSelector.INSTANCE.isWithinAttackRange(mc.player.getPos(), base.target)
                    && canAttackMace()
                    && shouldAttackMace()) {
                Attack.AttackSettings settings = CombatTasks.getAttack().createAttackSettings();
                CombatTasks.getAttack()
                        .attackEntity(base.target, settings.withMaceSwap(true).withInvSwap(false));
            }
        }

        public void onStartWaitAttack(boolean on) {
            startWaitAttack = 0;
        }

        public void onStartPullUp(boolean on) {
            if (on) {
                startPullUp = mc.player.getY();
                startPullUpTick = Tasks.getTick();
            } else {
                startPullUp = Double.MIN_VALUE;
                startPullUpTick = 0;
            }
        }

        public boolean shouldUseAntiShield() {
            return Attack.shouldUseAntiShield(base.target);
        }

        public boolean shouldPullUpEating() {
            return base.maceAttackConsiderUse.get()
                    && CombatTasks.getAttackAura().checkEating();
        }

        public boolean shouldAttackSimple() {
            boolean useAntiShield = shouldUseAntiShield();
            if (useAntiShield) return true;
            if (VItem.getInstance().isSpear(mc.player.getActiveItem())) return false;
            if (!base.maceAttackUseSimple.get()) return false;
            if ((mc.player.getAttackCooldownProgress(0.5F) > 0.95F)) {
                if (base.maceAttackConsiderUse.get()
                        && CombatTasks.getAttackAura().checkUsing()) {
                    return false;
                }
                // auto mace, do not hit twice
                if (Attack.INSTANCE.willUseMaceAttack(false)) {
                    return false;
                }
                return true;
            }
            return false;
        }

        public boolean shouldAttackMace() {
            boolean cooldown =
                    (lastMaceAttackSuccessTick < Tasks.getTick()) || Attack.INSTANCE.willUseMaceAttack(false);
            return cooldown && PlayerStateManager.INSTANCE.fallDistance > 1.5D;
        }

        public boolean canAttackMace() {
            boolean cooldown = (lastMaceAttackSuccessTick < Tasks.getTick());
            return Attack.INSTANCE.willUseMaceAttack(true)
                    && (cooldown || PlayerStateManager.INSTANCE.fallDistance > 1.5);
        }

        public synchronized void onPauseControl() {
            stateMachine.setState(STATE_NONE);
        }

        boolean attackFlag = false;
        int lastMaceAttackTick;
        int lastMaceAttackSuccessTick;
        double lastFallDistance;

        public boolean tryPreSwapArmorForAttack() {
            if (shouldAttackMace()
                    && base.maceFix.get()
                    && mc.player.isFallFlying()
                    && !base.currentArmorGliding
                    && !ElytraExtra.INSTANCE.hasPendingFallFlyingReset()
                    && Tasks.getTick() - PlayerStateManager.INSTANCE.lastStartGlidingTick
                            >= base.maceSwapMinDelay.get()) {
                Vec3d predictedPosition = PositionPredict.INSTANCE
                        .attackPredictArgument
                        .get()
                        .predictWithExtraTicks(base.target, base.macePreSwapDelay.get());
                double range = CombatTasks.getCombatExtra().getAttackAtTargetRange(base.target)
                        + base.macePreSwapDistancePerTick.get() * base.macePreSwapDelay.get();
                if (TargetSelector.INSTANCE.isWithinAttackRange(
                        mc.player.getPos(), base.target.dimensions.getBoxAt(predictedPosition), range)) {
                    return ElytraExtra.INSTANCE.requestManualArmorSwapAndResetFallFlying();
                }
            }
            return false;
        }

        public void scheduleAttack() {
            attackFlag = true;
        }
        // todo: check mace swap

        @Override
        public void onEnable() {
            stateMachine.setState(STATE_NONE);
        }

        @Override
        public void onDisable() {}

        @Override
        public synchronized void onHit(int type) {
            // pull up only when after mace attack to miss
            if (lastMaceAttackTick > Tasks.getTick() - 5 && stateMachine.getState() != STATE_PULL_UP) {
                lastMaceAttackSuccessTick = Tasks.getTick();
                stateMachine.setState(STATE_PULL_UP);
            }
        }

        @Override
        public abstract void onAttack(Entity entity);
    }

    public static class MaceArua extends AbstractMaceBehaviour implements HitListener {

        public int onStatePullUp(StateMachine machine) {
            Vec3d testMovement = new Vec3d(0, 0.1, 0);
            Vec3d simulation = MovTasks.simulateMovement(mc.player, mc.player.getPos(), testMovement, true);
            boolean simulationHead = simulation.squaredDistanceTo(testMovement) > 1E-4;
            boolean shouldForcePullUp = shouldPullUpEating();
            Vec3d predictor = base.maceUsePredictor.get()
                    ? PositionPredict.INSTANCE.attackPredictArgument.get().predict(base.target)
                    : base.target.getPos();
            double pullupHeight = base.maceHeight.get();
            if (base.maceChaseFollowYBias.get()) {
                double horizontalRange = mc.player.getPos().subtract(predictor).horizontalLength();
                if (base.maceChaseFollowBiasStartHorizontalRange.get() < horizontalRange) {
                    pullupHeight += base.maceChaseFollowBiasExtraHeightPerBlock.get()
                            * (horizontalRange - base.maceChaseFollowBiasStartHorizontalRange.get());
                }
            }
            if (shouldForcePullUp) {
                setTargetToEat(predictor, simulationHead, pullupHeight);
                machine.markForEndState();
                return STATE_PULL_UP;
            }
            if (simulationHead) {
                // can not pull up
                return STATE_FOLLOW;
            } else {
                {
                    double thresholdY = base.maceTryFollowUsePredictor.get() ? predictor.getY() : base.target.getY();

                    double targetY = thresholdY + pullupHeight;

                    // check pulling time
                    boolean mayFollow = (mc.player.getY() >= targetY)
                            || (mc.player.getY() > thresholdY
                                    && startPullUpTick != 0
                                    && Tasks.getTick()
                                            > base.maceRemainPullUpTick.get() + startPullUpTick + pullupHeight);

                    if (!mayFollow) {
                        setTargetToPlayerUpper(predictor, pullupHeight);
                        machine.markForEndState();
                        return STATE_PULL_UP;
                    } else {
                        return STATE_FOLLOW;
                    }
                }
            }
        }

        boolean currentTargetUpFly = false;

        public int onStateFollow(StateMachine machine) {
            if (PlayerStateManager.INSTANCE.fallDistance < 1E-6 && lastFallDistance > 1E-6) {
                // we trigger falldistance reset during chase
                return STATE_PULL_UP;
            }
            // already reach the target
            if (shouldPullUpEating()) {
                return STATE_PULL_UP;
            }
            tryPreSwapArmorForAttack();
            Vec3d targetPos = base.maceUsePredictor.get()
                    ? PositionPredict.INSTANCE.attackPredictArgument.get().predict(base.target)
                    : base.target.getPos();

            boolean mayAttack = shouldAttackSimple() || shouldAttackMace();
            boolean targetInRange = TargetSelector.INSTANCE.isWithinAttackRange(
                    mc.player.getPos(),
                    base.target.getBoundingBox(),
                    CombatTasks.getCombatExtra().getAttackAtTargetRange(base.target));
            // 限制高度 但是对面是往上飞的 不需要
            if (mayAttack && targetInRange) {
                setTargetToPlayer(targetPos);
                scheduleAttack();
                machine.markForEndState();

                return STATE_WAIT_ATTACK;
            } else {
                Vec3d testMovement = new Vec3d(0, -0.1, 0);
                Vec3d simulation = MovTasks.simulateMovement(mc.player, mc.player.getPos(), testMovement, true);
                boolean simulationFeet = simulation.squaredDistanceTo(testMovement) > 1E-4;
                // do not follow because no enough height and other people will overhead us
                boolean pullUp = simulationFeet;
                double minimalHeight = base.maceFollowMinHeight.get();
                // shit
                if (!pullUp && base.target.getY() > mc.player.getY() + minimalHeight) {
                    pullUp = true;
                }
                if (pullUp) {
                    if (targetInRange) {
                        setTargetToPlayer(targetPos);
                        scheduleAttack();
                        machine.markForEndState();
                        return STATE_WAIT_ATTACK;
                    } else {
                        return STATE_PULL_UP;
                    }
                }
                setTargetToPlayer(targetPos);
                if (movementDirection.y >= 0) {
                    return STATE_PULL_UP;
                }
            }
            if (base.flyAntiSpearUseSpearResetWhenFollow.get()) {
                SpearEnhance.INSTANCE.setForceSpearReset(true);
            }
            machine.markForEndState();
            return STATE_FOLLOW;
        }

        private void setTargetToEat(Vec3d predictor, boolean headSimulation, double height) {
            if (headSimulation) {
                Vec3d vec3d = base.target.getPos().subtract(mc.player.getPos());
                if (vec3d.horizontalLength() > base.combatRange()) {
                    setTargetToPlayerUpper(predictor, height);
                } else {
                    movementDirection =
                            new Vec3d(-vec3d.x, 0, -vec3d.z).normalize().multiply(10);
                }

            } else {
                setTargetToPlayerUpper(predictor, height);
            }
        }

        protected void setTargetToPlayerUpper(Vec3d predictor, double height) {

            Vec3d movement = null;
            boolean onGroundSupport = base.currentOnGround;
            boolean executeSmoothHideFlight = false;
            boolean antiSpear = willUseAntiSpear(
                            base.flyAntiSpearWhenPullup.get(), base.flyAntiSpearPullupRange.get(), predictor)
                    && (mc.player.getY() > predictor.getY()
                            || mc.player.getPos().subtract(predictor).horizontalLength() < base.combatSpearRange.get());
            boolean yLow = predictor.getY() >= mc.player.getY();
            if (base.combatSmoothFlightPullup.get() && !onGroundSupport) {
                double combatRange = base.combatMaceRange.get();
                if (base.combatSmoothCircleDynamicRangeByHeight.get().isPresent()) {
                    Vec3 arguments =
                            base.combatSmoothCircleDynamicRangeByHeight.get().getValue();
                    double predictorYL = predictor.y - mc.player.getY();
                    if (predictorYL > arguments.x()) {
                        combatRange += Math.min(predictorYL - arguments.x(), arguments.z()) * arguments.y();
                    }
                }
                if (yLow) {
                    Vec3d center = base.target.dimensions.getBoxAt(predictor).getCenter();
                    {
                        double radius = combatRange + base.combatSmoothCircleExtraRange.get();
                        Pair<Vec3d, Vec3d> tangents =
                                MathUtils.getTangentWithSameXZ(center, radius, mc.player.getEyePos());
                        Vec3d vec3d = tangents.getFirst();
                        Vec3d vec3d2 = tangents.getSecond();
                        Vec3d vec3d3 = vec3d.y < vec3d2.y ? vec3d2 : vec3d;
                        // go upper not horizontal
                        vec3d3 = vec3d3.add(0, 1E-2, 0);
                        if (Math.abs(base.combatSmoothOutPullRatio.get()) > 1E-6
                                && (base.combatSmoothOutPullRatioUseHorizontalDistance.get()
                                                ? center.subtract(mc.player.getEyePos())
                                                        .horizontalLengthSquared()
                                                : center.squaredDistanceTo(mc.player.getEyePos()))
                                        < MathUtils.s2(base.combatSmoothOutPullRatioEnableDistance.get())) {
                            // 垂线
                            vec3d3 = vec3d3.normalize();
                            Vec3d delta = mc.player.getEyePos().subtract(center);
                            Vec3d horizontalMul = new Vec3d(delta.x, 0, delta.z)
                                    .normalize()
                                    .multiply(base.combatSmoothOutPullRatio.get());
                            vec3d3 = vec3d3.add(horizontalMul).normalize();
                        }
                        if (vec3d3.y > 0) {
                            movement = vec3d3.normalize().multiply(10);
                        }
                        executeSmoothHideFlight =
                                center.squaredDistanceTo(mc.player.getEyePos()) < MathUtils.s2(radius);
                    }
                }
            }
            if (movement == null && base.combatSmoothFlightPullupAttack.get() && !onGroundSupport) {
                Vec3d direction = ElytraExtra.INSTANCE.autoRescale.get()
                        ? predictor.subtract(mc.player.getPos()).normalize().multiply(10)
                        : Vec3d.ZERO;
                if (predictor.subtract(mc.player.getPos()).horizontalLength()
                        < base.combatSmoothFlightPullupAttackMaxHorizontal.get()) {
                    if (predictor.getY() + base.combatSmoothFlightPullupAttackMinRelativeY.get() > mc.player.getY()) {
                        movement = new Vec3d(-direction.x, 10, -direction.z);
                    } else if (predictor.getY() + base.combatSmoothFlightPullupAttackMaxRelativeY.get()
                            > mc.player.getY()) {
                        movement = new Vec3d(direction.x, 10, direction.z);
                    }
                }
            }
            if (movement == null) {
                // normal pull up
                movement = predictor
                        .withAxis(Direction.Axis.Y, (predictor.getY() + (height)))
                        .subtract(mc.player.getPos());
                if (movement.length() < 5) {
                    movement = movement.normalize().multiply(5);
                }
            }
            if (base.pullupPersistentDirection.get().isPresent() && !onGroundSupport && !executeSmoothHideFlight) {
                double horizontalDistance = movement.horizontalLength();
                if (horizontalDistance > 1E-1) {
                    Vec3d lastMovement = PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed;
                    Vec3d lastHorizontal = lastMovement.withAxis(Direction.Axis.Y, 0);
                    double distance = base.pullupPersistentDirection.get().getValue();
                    if (distance > horizontalDistance) {
                        if (lastHorizontal.dotProduct(movement) < 0) {
                            movement = movement.multiply(-1, 1, -1);
                        }
                    }
                }
            }
            movementDirection = movement;

            if (!base.flyAntiSpearAfterAngle.get() && antiSpear) {
                antiSpear(base.flyAntiSpearWhenPullup.get());
            }
            if (base.angleOptimizePullUp.get()) {
                if (movementDirection.y > 1E-6) {
                    double horizontal = mc.player.getPos().subtract(predictor).horizontalLength();
                    Vec3d targetVec = mc.player.getPos().subtract(predictor).withAxis(Direction.Axis.Y, 0);
                    boolean out = movementDirection.dotProduct(targetVec) > 0;

                    if (base.angleOptimizeRadicalPullup.get()
                            && !out
                            && yLow
                            && base.angleOptimizePullRange.get().isPresent()
                            && horizontal < base.angleOptimizePullRange.get().getValue()) {
                        Vec3d horizontalDelta = mc.player
                                .getPos()
                                .subtract(predictor)
                                .withAxis(Direction.Axis.Y, 0)
                                .normalize()
                                .multiply(6);
                        movementDirection = horizontalDelta.withAxis(Direction.Axis.Y, movementDirection.y);
                    } else {
                        double horizontal2 = Math.max(Math.abs(movementDirection.x), Math.abs(movementDirection.z));
                        movementDirection = movementDirection.withAxis(Direction.Axis.Y, horizontal2);
                    }
                    if ((out ? base.angleOptimizeAxisXZPullupOut.get() : base.angleOptimizeAxisXZPulluIn.get())
                            && (horizontal
                                    < (out
                                            ? base.angleOptimizeAxisXZOutRange.get()
                                            : base.angleOptimizeAxisXZInRange.get()))) {
                        double horizontal2 = Math.max(Math.abs(movementDirection.x), Math.abs(movementDirection.z));
                        double sgnX = MathUtils.sgn(movementDirection.x);
                        double sgnZ = MathUtils.sgn(movementDirection.z);
                        movementDirection = new Vec3d(sgnX * horizontal2, movementDirection.y, sgnZ * horizontal2);
                    }
                    movementDirection = ElytraOptimizeUtils.calculateBestPullupSpeed(movementDirection);
                }
            }
            double len = movementDirection.length();
            if (len < 5) {
                movementDirection = movementDirection.normalize().multiply(5);
            }
            if (base.flyAntiSpearAfterAngle.get() && antiSpear) {
                antiSpear(base.flyAntiSpearWhenPullup.get());
            }
            if (base.flyAntiSpearRevertFlyWhenPredictHit.get()
                    && base.target instanceof PlayerEntity pl
                    && SpearEnhance.isUsingSpear(pl)
                    && predictMayHitAntiSpear(predictor)) {
                movementDirection = new Vec3d(-movementDirection.x, movementDirection.y, -movementDirection.z);
            }
            if (base.flyAntiSpearRotateFlyWhenPredictHit.get()
                    && base.target instanceof PlayerEntity pl
                    && SpearEnhance.isUsingSpear(pl)
                    && predictMayHitAntiSpear(predictor)) {
                movementDirection = new Vec3d(movementDirection.z, movementDirection.y, -movementDirection.x);
            }
        }

        protected void setTargetToPlayer(Vec3d targetPos) {
            double lerpY = base.maceYLevelWeight.get();
            double baseY = base.target.getY();
            currentTargetUpFly = baseY + 0.5 < targetPos.y;
            double yLerp = currentTargetUpFly ? (targetPos.y * lerpY + baseY * (1.0D - lerpY)) : targetPos.y;
            if (base.flyAntiSpearUseSpearResetWhenFollow.get()) {
                SpearEnhance.INSTANCE.setForceSpearReset(true);
            }
            direction_calculate:
            {
                Predicate<Vec3d> conditionMovement = (vec3) -> {
                    if (vec3.y >= 0) {
                        return false;
                    }
                    float pitch = EntityUtils.rotationToPitch(vec3.normalize());
                    if (pitch < base.minPitchDownwards.get()) {
                        Vec3d predictorMovement = simulateMovement(vec3);
                        if (!TargetSelector.INSTANCE.isWithinAttackRange(
                                mc.player.getPos().add(predictorMovement), base.target)) {
                            return false;
                        }
                    }
                    return true;
                };
                if (base.maceFollowUsePredictorLine.get()) {
                    double distance = mc.player.getPos().distanceTo(targetPos);
                    if (distance > base.maceFollowStartPredictDistance.get()) {
                        double predictionTicks = Math.min(
                                (distance - base.maceFollowStartPredictDistance.get())
                                        * base.maceFollowPredictTicksPerDistance.get(),
                                base.maceFollowMaxPredictionTicks.get());
                        Vec3d predictionPosition = PositionPredict.INSTANCE
                                .attackPredictArgument
                                .get()
                                .predictWithExtraTicks(base.target, predictionTicks);
                        Vec3d untrustedDirection = calculateTargetDirection(predictionPosition);
                        if (untrustedDirection.y < 0 && conditionMovement.test(untrustedDirection)) {
                            movementDirection = untrustedDirection;
                            break direction_calculate;
                        } else {
                            if (base.continuePullupIfCannotChaseTarget.get()) {
                                break direction_calculate;
                            }
                        }
                    }
                }
                boolean near = targetPos.squaredDistanceTo(mc.player.getPos())
                        < MathUtils.s2(base.chooseDownTargetDistance.get());
                if (!near) {
                    movementDirection = calculateTargetDirection(targetPos);
                    if (conditionMovement.test(movementDirection)) {
                        break direction_calculate;
                    } else {
                        movementDirection = movementDirection.withAxis(Direction.Axis.Y, 0);
                    }
                }
                if (near || (movementDirection.y >= 0 && base.followDownPosWhenLow.get())) {
                    movementDirection = calculateTargetDirection(targetPos.withAxis(Direction.Axis.Y, baseY));
                    if (conditionMovement.test(movementDirection)) {
                        break direction_calculate;
                    } else {
                        movementDirection = movementDirection.withAxis(Direction.Axis.Y, 0);
                    }
                }
            }

            boolean onGroundSupport = base.currentOnGround;
            if (onGroundSupport) {
                // handle on ground target\
                var op = base.followOnGroundHeight.get();
                if (op.isPresent()) {
                    movementDirection = movementDirection.add(0, op.getValue(), 0);
                }
            } else {
                // use real value, because predictors may predict wrong values
                if (yLerp - base.maceFollowMinHeight.get() > mc.player.getY()) {
                    // to nothing modify
                    movementDirection = movementDirection.withAxis(Direction.Axis.Y, 0);
                    return;
                    // 我没招了。这还是尽快重开吧
                }
            }
            if (movementDirection.y >= 0) {
                return;
            }
            boolean antiSpear = willUseAntiSpear(base.flyAntiSpear.get(), base.flyAntiSpearRange.get(), targetPos);
            // only optimize when target offground
            if (!base.flyAntiSpearAfterAngle.get() && antiSpear) {
                antiSpear(base.flyAntiSpear.get());
            }
            if (!onGroundSupport && base.angleOptimizeFollow.get()) {
                if (movementDirection.y < -1E-6) {
                    var distancePair = base.angleOptimizeFollowRange.get();
                    double distance;
                    if (SpearEnhance.isUsingSpear(mc.player)) {
                        distance = distancePair.y();
                    } else if (antiSpear) {
                        distance = distancePair.z();
                    } else {
                        distance = distancePair.x();
                    }
                    double horizontal = mc.player.getPos().subtract(targetPos).length();
                    if (horizontal > distance) {
                        double horizontal2 = Math.max(Math.abs(movementDirection.x), Math.abs(movementDirection.z));
                        if (horizontal2 > 0.1) {
                            // rescale
                            if (!base.angleOptimizeFollowPitchLimit.get().isPresent()
                                    || Math.abs(movementDirection.y)
                                            < movementDirection.horizontalLength()
                                                    * Math.tan(Math.toRadians(base.angleOptimizeFollowPitchLimit
                                                            .get()
                                                            .getValue()))) {
                                if (Math.abs(movementDirection.y) > horizontal2) {
                                    movementDirection = movementDirection.withAxis(Direction.Axis.Y, -horizontal2);
                                }
                                movementDirection =
                                        ElytraOptimizeUtils.calculateBestDownForwardSpeed(movementDirection, true);
                            }
                        }
                    }
                }
            }
            double len = movementDirection.length();
            if (len < 5) {
                movementDirection = movementDirection.normalize().multiply(5);
            }
            if (base.flyAntiSpearAfterAngle.get() && antiSpear) {
                antiSpear(base.flyAntiSpear.get());
            }
            if (base.flyAntiSpearPullupWhenPredictHit.get()
                    && base.target instanceof PlayerEntity pl
                    && SpearEnhance.isUsingSpear(pl)
                    && predictMayHitAntiSpear(targetPos)) {
                movementDirection = movementDirection.multiply(-1, -1, -1);
            }
        }

        @Override
        public synchronized void onUpdate() {
            currentTargetUpFly = false;
            super.onUpdate();
        }

        @Override
        public synchronized void onAttack(Entity entity) {
            if ((mc.player.isFallFlying() || mc.player.getAbilities().flying)
                    && stateMachine.getState() != STATE_PULL_UP) {
                // just in few ticks for the attack(current tick)
                // pull up only when after mace attack to miss
                if (lastMaceAttackTick >= Tasks.getTick() - 1 && currentTargetUpFly) {
                    stateMachine.setState(STATE_PULL_UP);
                    stateMachine.step();
                } else {
                    stateMachine.setState(STATE_FOLLOW);
                }
            }
        }
    }

    public static class MaceAuraGround extends AbstractMaceBehaviour implements HitListener {
        private void setTargetToEat(Vec3d predictor, boolean headSimulation, double height) {
            if (headSimulation) {
                Vec3d vec3d = base.target.getPos().subtract(mc.player.getPos());
                if (vec3d.horizontalLength() > base.combatRange()) {
                    setTargetToPlayerUpper(predictor, height);
                } else {
                    movementDirection =
                            new Vec3d(-vec3d.x, 0, -vec3d.z).normalize().multiply(10);
                }

            } else {
                setTargetToPlayerUpper(predictor, height);
            }
        }

        @Override
        public int onStatePullUp(StateMachine machine) {
            boolean shouldForcePullUp = shouldPullUpEating();
            boolean shouldFollow;
            Vec3d testMovement = new Vec3d(0, 0.1, 0);
            Vec3d simulation = MovTasks.simulateMovement(mc.player, mc.player.getPos(), testMovement, true);
            Vec3d predictor = base.maceUsePredictor.get()
                    ? PositionPredict.INSTANCE.attackPredictArgument.get().predict(base.target)
                    : base.target.getPos();
            boolean simulationHead = simulation.squaredDistanceTo(testMovement) > 1E-4;
            double pullupHeight = base.maceHeightGround.get();
            if (base.maceChaseFollowYBias.get()) {
                double horizontalRange = mc.player.getPos().subtract(predictor).horizontalLength();
                if (base.maceChaseFollowBiasStartHorizontalRange.get() < horizontalRange) {
                    pullupHeight += base.maceChaseFollowBiasExtraHeightPerBlock.get()
                            * (horizontalRange - base.maceChaseFollowBiasStartHorizontalRange.get());
                }
            }
            double targetY = base.target.getY() + pullupHeight;
            // check pulling time

            boolean mayFollow = (mc.player.getY() >= targetY)
                    || (mc.player.getY() > base.target.getY()
                            && startPullUpTick != 0
                            && Tasks.getTick() > base.maceRemainPullUpTick.get() + startPullUpTick + pullupHeight);
            shouldFollow = simulationHead || mayFollow;
            if (shouldFollow) {
                if (!shouldForcePullUp) {
                    return STATE_FOLLOW;
                } else {
                    setTargetToEat(base.target.getPos(), simulationHead, pullupHeight);
                    machine.markForEndState();
                    return STATE_PULL_UP;
                }
            } else {

                setTargetToPlayerUpper(predictor, pullupHeight);
                machine.markForEndState();
                return STATE_PULL_UP;
            }
        }

        @Override
        public int onStateFollow(StateMachine machine) {
            tryPreSwapArmorForAttack();
            if (PlayerStateManager.INSTANCE.fallDistance < 1E-6 && lastFallDistance > 1E-6) {
                // we trigger falldistance reset during chase
                return STATE_PULL_UP;
            }
            if (shouldPullUpEating()) {
                return STATE_PULL_UP;
            }
            Vec3d targetPos = base.maceUsePredictor.get()
                    ? PositionPredict.INSTANCE.attackPredictArgument.get().predict(base.target)
                    : base.target.getPos();
            boolean targetInRange = TargetSelector.INSTANCE.isWithinAttackRange(
                    mc.player.getPos(),
                    base.target.getBoundingBox(),
                    CombatTasks.getCombatExtra().getAttackAtTargetRange(base.target));

            if (base.currentOnGround) {
                if (targetInRange) {
                    setTargetToPlayer(base.target.getPos());
                    scheduleAttack();
                    machine.markForEndState();
                    return STATE_WAIT_ATTACK;
                } else {
                    setTargetToPlayer(base.target.getPos());
                    Vec3d testMovement = movementDirection.normalize().multiply(0.1);
                    Vec3d simulation = MovTasks.simulateMovement(mc.player, mc.player.getPos(), testMovement, true);
                    boolean simulationFeet = simulation.squaredDistanceTo(testMovement) > 1E-4;
                    if (simulationFeet) {
                        // reset movement
                        movementDirection = Vec3d.ZERO;
                        return STATE_PULL_UP;
                    } else {
                        machine.markForEndState();
                        return STATE_FOLLOW;
                    }
                }
            } else {
                Vec3d testVelocity = new Vec3d(0, 1, 0);
                Vec3d simulation = MovTasks.simulateMovement(base.target, targetPos, testVelocity, false);
                boolean targetHeadSimulation = simulation.squaredDistanceTo(testVelocity) > 1E-4;
                if (targetHeadSimulation) {
                    Vec3d target = targetPos.add(0, -1.62 - 2.8 + simulation.length(), 0);
                    movementDirection = target.subtract(mc.player.getPos());
                    if (movementDirection.y > -1 && targetInRange) {
                        scheduleAttack();
                        machine.markForEndState();
                        return movementDirection.y > 0 ? STATE_PULL_UP : STATE_FOLLOW;
                    } else if (movementDirection.y > 0) {
                        return STATE_PULL_UP;
                    } else {
                        machine.markForEndState();
                        return STATE_FOLLOW;
                    }
                } else {
                    if (targetInRange) {
                        setTargetToPlayer(base.target.getPos());
                        scheduleAttack();
                        machine.markForEndState();
                        return STATE_FOLLOW;
                    } else {
                        setTargetToPlayer(targetPos);
                        if (movementDirection.y > 0) {
                            movementDirection = Vec3d.ZERO;
                            return STATE_PULL_UP;
                        } else {
                            machine.markForEndState();
                            return STATE_FOLLOW;
                        }
                    }
                }
            }
        }

        @Override
        protected void setTargetToPlayerUpper(Vec3d predictor, double height) {
            if (base.currentOnGround) {
                if (predictor.y > mc.player.getY()) {
                    movementDirection = predictor
                            .withAxis(Direction.Axis.Y, (predictor.getY() + height))
                            .subtract(mc.player.getPos());
                } else {
                    Vec3d delta = mc.player.getPos().subtract(predictor);
                    double horizontal = delta.horizontalLength();
                    if (horizontal < height && delta.y > horizontal) {
                        movementDirection = delta;
                    } else {
                        Vec3d targetPos = delta.withAxis(Direction.Axis.Y, 0)
                                .normalize()
                                .multiply(height)
                                .withAxis(Direction.Axis.Y, height);
                        movementDirection = targetPos.subtract(delta);
                    }
                }
            } else {
                Vec3d testVelocity = new Vec3d(0, 1, 0);
                Vec3d simulation = MovTasks.simulateMovement(base.target, predictor, testVelocity, false);
                boolean targetHeadSimulation = simulation.squaredDistanceTo(testVelocity) > 1E-4;
                // no space above target
                if (targetHeadSimulation) {
                    movementDirection = predictor.add(testVelocity).subtract(mc.player.getPos());
                } else {
                    Vec3d movement = null;
                    boolean yLow = predictor.getY() >= mc.player.getY();
                    if (base.combatSmoothFlightPullup.get()) {
                        double combatRange = base.combatMaceRange.get();
                        if (base.combatSmoothCircleDynamicRangeByHeight.get().isPresent()) {
                            Vec3 arguments = base.combatSmoothCircleDynamicRangeByHeight
                                    .get()
                                    .getValue();
                            double predictorYL = predictor.y - mc.player.getY();
                            if (predictorYL > arguments.x()) {
                                combatRange += Math.min(predictorYL - arguments.x(), arguments.z()) * arguments.y();
                            }
                        }
                        combatRange = combatRange / 2;
                        if (yLow) {
                            Vec3d center =
                                    base.target.dimensions.getBoxAt(predictor).getCenter();
                            double radius = combatRange + base.combatSmoothCircleExtraRange.get();
                            Pair<Vec3d, Vec3d> tangents =
                                    MathUtils.getTangentWithSameXZ(center, radius, mc.player.getEyePos());
                            Vec3d vec3d = tangents.getFirst();
                            Vec3d vec3d2 = tangents.getSecond();
                            Vec3d vec3d3 = vec3d.y < vec3d2.y ? vec3d2 : vec3d;
                            // go upper not horizontal
                            vec3d3 = vec3d3.add(0, 1E-2, 0);
                            if (Math.abs(base.combatSmoothOutPullRatio.get()) > 1E-6
                                    && ((base.combatSmoothOutPullRatioUseHorizontalDistance.get()
                                                    ? center.subtract(mc.player.getEyePos())
                                                            .horizontalLengthSquared()
                                                    : center.squaredDistanceTo(mc.player.getEyePos()))
                                            < MathUtils.s2(base.combatSmoothOutPullRatioEnableDistance.get()))) {
                                // 垂线
                                vec3d3 = vec3d3.normalize();
                                Vec3d delta = mc.player.getEyePos().subtract(center);
                                Vec3d horizontalMul = new Vec3d(delta.x, 0, delta.z)
                                        .normalize()
                                        .multiply(base.combatSmoothOutPullRatio.get());
                                vec3d3 = vec3d3.add(horizontalMul).normalize();
                            }
                            if (vec3d3.y > 0) {
                                movement = vec3d3.normalize().multiply(10);
                            }
                        }
                    }
                    if (movement == null) {
                        movement = predictor
                                .withAxis(Direction.Axis.Y, (predictor.getY() + (height)))
                                .subtract(mc.player.getPos());
                        if (movement.length() < 5) {
                            movement = movement.normalize().multiply(5);
                        }
                    }
                    movementDirection = movement;
                }
            }
            if (movementDirection.length() < 5) {
                movementDirection = movementDirection.normalize().multiply(5);
            }
        }

        @Override
        protected void setTargetToPlayer(Vec3d targetPos) {
            Vec3d playerPos = mc.player.getPos();
            Vec3d fallbackTargetPos;
            if (base.currentOnGround) {
                fallbackTargetPos =
                        targetPos.add(0, base.followOnGroundHeight.get().orElse(0.5), 0);
                // in case of landing
                // ElytraExtra.INSTANCE.autoTakeoff();
            } else {
                fallbackTargetPos = targetPos;
                if (base.flyAntiSpearUseSpearResetWhenFollow.get()) {
                    SpearEnhance.INSTANCE.setForceSpearReset(true);
                }
            }

            boolean found = false;
            if (base.currentOnGround) {
                double attackAtTargetRange = CombatTasks.getCombatExtra().getAttackAtTargetRange(base.target);
                Vec3d targetEyePos = base.target.getEyePos();
                BlockHitResult raycastResult = mc.world.raycast(new RaycastContext(
                        mc.player.getEyePos(),
                        targetEyePos,
                        net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE,
                        mc.player));
                if (raycastResult != null
                        && raycastResult.getType() != HitResult.Type.MISS
                        && raycastResult.getPos().squaredDistanceTo(targetEyePos)
                                <= MathUtils.s2(2.0D * attackAtTargetRange)) {
                    BlockPos raycastPos = net.minecraft.util.math.BlockPos.ofFloored(raycastResult.getPos());
                    int searchRadius = Math.max(1, (int) Math.ceil(attackAtTargetRange));
                    List<BlockPos> searchPoses = new ArrayList<>();
                    for (int x = raycastPos.getX() - searchRadius; x <= raycastPos.getX() + searchRadius; x++) {
                        for (int y = raycastPos.getY() - searchRadius; y <= raycastPos.getY() + searchRadius; y++) {
                            for (int z = raycastPos.getZ() - searchRadius; z <= raycastPos.getZ() + searchRadius; z++) {
                                searchPoses.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                    searchPoses.sort(
                            Comparator.comparingDouble(pos -> pos.toCenterPos().squaredDistanceTo(targetEyePos)));
                    for (BlockPos pos : searchPoses) {
                        Vec3d centerPos = pos.toCenterPos();
                        if (!TargetSelector.INSTANCE.isWithinAttackRange(
                                centerPos, base.target.getBoundingBox(), attackAtTargetRange + 0.5D)) {
                            continue;
                        }
                        if (mc.world
                                        .raycast(new RaycastContext(
                                                playerPos,
                                                centerPos,
                                                RaycastContext.ShapeType.COLLIDER,
                                                RaycastContext.FluidHandling.NONE,
                                                mc.player))
                                        .getType()
                                != HitResult.Type.MISS) {
                            continue;
                        }
                        Box box = mc.player.dimensions.getBoxAt(centerPos);
                        if (!mc.world.isSpaceEmpty(box)) {
                            continue;
                        }
                        movementDirection = centerPos.subtract(playerPos);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                movementDirection =
                        calculateTargetDirection(fallbackTargetPos); // fallbackTargetPos.subtract(playerPos);
            }
            if (movementDirection.length() < 5 && movementDirection.length() > 1E-6) {
                movementDirection = movementDirection.normalize().multiply(5);
            }
        }

        @Override
        public void onAttack(Entity entity) {
            if ((mc.player.isFallFlying() || mc.player.getAbilities().flying)
                    && stateMachine.getState() != STATE_FOLLOW) {
                // just in few ticks for the attack(current tick)
                // pull up only when after mace attack to miss
                stateMachine.setState(STATE_FOLLOW);
            }
        }
    }

    public static class SpearAura extends AbstractBotBehaviour implements HitListener {
        static final int STATE_NONE = 0;
        static final int STATE_FOLLOW = 1;
        static final int STATE_NEAR_FOLLOW = 2;
        static final int STATE_PULL_OVER = 3;
        int nearFollowTimer;
        int pullOverTimer;
        StateMachine stateMachine;

        //        @Override
        //        public Entity searchTarget() {
        //            return CombatTasks.getTargetSelector()
        //                    .searchAttackEntity(base.targetRange.get(), true, TargetSelector::canPlayerDirectlySee);
        //        }

        public SpearAura() {
            this.stateMachine = new StateMachine(
                    STATE_NONE,
                    this::onStateUpdate,
                    this::onStateNone,
                    this::onStateFollow,
                    this::onStateNearFollow,
                    this::onStatePullOver);
            this.stateMachine.registerListener(STATE_NEAR_FOLLOW, this::onSwitchToNearFollow);
            this.stateMachine.registerListener(STATE_PULL_OVER, this::onSwitchToPullOver);
        }

        public int onStateUpdate(StateMachine machine, int state) {
            if (base.target == null) {
                return STATE_NONE;
            }
            return state;
        }

        public int onStateNone(StateMachine machine) {
            if (base.target != null) {
                if (!VItem.getInstance().isSpear(mc.player.getMainHandStack())
                        && !VItem.getInstance().isSpear(mc.player.getOffHandStack())) {
                    base.logI18N("message.module.elytra-bot.no-spear");
                }
                return STATE_FOLLOW;
            }
            machine.markForEndState();
            movementDirection = Vec3d.ZERO;
            return STATE_NONE;
        }

        private Vec3d getTargetPosition() {
            Vec3d predictedPosition = base.spearUsePredictor.get()
                    ? PositionPredict.INSTANCE
                            .getPredictor(base.target)
                            .predict(2, PredictionMode.PREDICTOR_NV.ordinal(), 10)
                    : base.target.getPos();
            Vec3d delta = predictedPosition.subtract(base.target.getPos());
            if (base.currentOnGround) {
                return base.target.getEyePos().add(delta);
            }

            return base.target.getBoundingBox().getCenter().add(delta);
        }

        public int onStateFollow(StateMachine machine) {
            Vec3d targetPos = getTargetPosition();
            if (mc.player.getEyePos().squaredDistanceTo(targetPos) < MathUtils.s2(getActiveRange())) {
                return STATE_NEAR_FOLLOW;
            }
            machine.markForEndState();
            /// compute their
            Vec3d originalLook = targetPos.subtract(mc.player.getEyePos());
            if (originalLook.length() < 6) {
                originalLook = originalLook.normalize().multiply(6);
            }
            if (canAdjustMovement()) {
                if (adjustMovementForSpear((PlayerEntity) base.target, originalLook, false)) {
                    return STATE_FOLLOW;
                }
            }
            movementDirection = originalLook;
            return STATE_FOLLOW;
        }

        private boolean canAdjustMovement() {
            return false && base.isTargetUsingSpear();
        }

        private boolean adjustMovementForSpear(PlayerEntity otherShit, Vec3d originalLook, boolean expand) {
            // shit not work
            // handle their shit ass spear

            // filter run away
            //            if (otherShit.getRotationVector().dotProduct(mc.player.getPos().subtract(otherShit.getPos()))
            // < 0) {
            //                return false;
            //            }
            if (otherShit.squaredDistanceTo(mc.player.getPos()) < MathUtils.s2(getActiveRange() * 2)) {
                if (Tasks.getTick() % 5 < 2) {
                    return moveAdjust(originalLook);
                } else {
                    return movementPredictAdjust(originalLook);
                }
            }

            Box ourBox = expand ? mc.player.getBoundingBox().expand(0.85, 0.85, 0.85) : mc.player.getBoundingBox();
            Vec3d theirKnownMovement = PositionPredict.INSTANCE.predictKnownMovement(otherShit);
            Vec3d theirPredictedPos = PositionPredict.INSTANCE
                    .spearPredictArgument
                    .get()
                    .predict(otherShit); // predictFlyingPosition(otherShit, 2, 6);
            Vec3d facing = otherShit.getRotationVector();
            Debug.debug("Spear judgement", theirKnownMovement, theirPredictedPos, mc.player.getPos());
            double reachD = theirKnownMovement.dotProduct(facing);
            Vec3d theirPredictedEyePos = theirPredictedPos.add(0, otherShit.getEyeHeight(otherShit.getPose()), 0);
            Vec3d raycastStart = theirPredictedEyePos.add(facing.multiply(getMinRange()));
            Vec3d raycastEnd = theirPredictedEyePos.add(facing.multiply(getActiveRange() + reachD));
            if (ourBox.raycast(raycastStart, raycastEnd).isPresent()) {
                return moveAdjust(originalLook);
            }
            return false;
            // do spear raytrace
        }

        private boolean moveAdjust(Vec3d originalLook) {
            //
            Debug.debug("Judget may hit");
            Vec3d originalLookHorizontal = originalLook.getHorizontal();
            Vec3d vertical = new Vec3d(0, 1, 0);
            Vec3d side = vertical.crossProduct(originalLookHorizontal);
            Vec3d revertDirection =
                    side.normalize().multiply(originalLookHorizontal.length()).add(0, originalLookHorizontal.y, 0);
            Vec3d testVector = revertDirection.normalize().multiply(0.5);
            Vec3d simulate = MovTasks.simulateMovement(mc.player, mc.player.getPos(), testVector, false);
            if (simulate.squaredDistanceTo(testVector) < 0.1) {
                movementDirection = revertDirection;
                Debug.debug("JudgeA", movementDirection);
                RenderTasks.drawBoxMov(
                        mc.player.getBoundingBox(), revertDirection.normalize().multiply(1.7), 50, Color.BLUE);
                return true;
            } else {
                revertDirection = revertDirection.negate();
                testVector = testVector.negate();
                simulate = MovTasks.simulateMovement(mc.player, mc.player.getPos(), testVector, false);
                if (simulate.squaredDistanceTo(testVector) < 0.1) {
                    movementDirection = revertDirection;
                    Debug.debug("JudgeB", movementDirection);
                    RenderTasks.drawBoxMov(
                            mc.player.getBoundingBox(),
                            revertDirection.normalize().multiply(1.7),
                            50,
                            Color.BLUE);
                    return true;
                }
            }
            return false;
        }

        private boolean movementPredictAdjust(Vec3d originalLook) {
            return false;
        }

        public int onStateNearFollow(StateMachine machine) {
            if ((base.currentInCombatRange && base.currentAction == TargetAction.CIRCLING)
                    || base.currentAction == TargetAction.TOWARDS
                    || base.currentAction == TargetAction.AFK) {
                if (!SpearEnhance.canSpearKineticAttack()) {
                    return STATE_PULL_OVER;
                }
            }
            Vec3d targetPosition = getTargetPosition();
            if (mc.player.getEyePos().squaredDistanceTo(targetPosition) > MathUtils.s2(getActiveRange())) {
                return STATE_FOLLOW;
            } else {
                //                else if (++nearFollowTimer > getMaxAttackPeriod()) {
                //                    // catch up
                //                    state = STATE_PULL_OVER;
                //                    nearFollowTimer = 0;
                //                }
                machine.markForEndState();
                Vec3d look = targetPosition.subtract(mc.player.getEyePos());
                if (look.length() < 6) {
                    look = look.normalize().multiply(6);
                }
                if (canAdjustMovement()) {
                    if (adjustMovementForSpear((PlayerEntity) base.target, look, false)) {
                        return STATE_NEAR_FOLLOW;
                    }
                }
                Vec3d lookHorizontal = look.withAxis(Direction.Axis.Y, 0);
                Vec3d lastMoveHorizontal =
                        PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed.withAxis(Direction.Axis.Y, 0);
                double dotValue = lookHorizontal.dotProduct(lastMoveHorizontal);
                if (dotValue < 0) {
                    look = look.negate();
                }
                movementDirection = look;
                return STATE_NEAR_FOLLOW;
            }
        }
        // todo: howto when combating
        public int onStatePullOver(StateMachine machine) {
            Vec3d targetPosition;
            if (++pullOverTimer > getCooldown()) {
                return STATE_FOLLOW;
            } else {
                targetPosition = getTargetPosition();
                // calculate left time
                if (base.currentAction != null) {
                    if (base.currentAction == TargetAction.AFK
                            || base.currentAction == TargetAction.SLOW_SPEED
                            || (base.currentInCombatRange && base.currentAction != TargetAction.ESCAPING)) {
                        // stable
                        int leftTicks = getCooldown() - pullOverTimer;
                        double canChaseDistance = Math.max(0.0D, 1.0D * (leftTicks));
                        if (mc.player.getEyePos().squaredDistanceTo(targetPosition) > MathUtils.s2(canChaseDistance)) {
                            return STATE_FOLLOW;
                        }
                    } else if (base.currentAction == TargetAction.ESCAPING) {
                        // chasing
                        // do not too close,
                        double canChaseDistance = getMinRange();
                        if (mc.player.getEyePos().squaredDistanceTo(targetPosition) > MathUtils.s2(canChaseDistance)) {
                            return STATE_FOLLOW;
                        }
                    } else {
                        // meeting
                        // escape their attack range, can hit
                        double canChaseDistance = getActiveRange();
                        if (mc.player.getEyePos().squaredDistanceTo(targetPosition) > MathUtils.s2(canChaseDistance)) {
                            return STATE_FOLLOW;
                        }
                    }
                }
            }
            machine.markForEndState();
            Vec3d look = targetPosition.subtract(mc.player.getEyePos());
            if (look.length() < 6) {
                look = look.normalize().multiply(6);
            }
            if (canAdjustMovement()) {
                if (adjustMovementForSpear((PlayerEntity) base.target, look, true)) {
                    return STATE_PULL_OVER;
                }
            }
            if (base.currentOnGround) {
                if (look.lengthSquared() < getMinRange()) {
                    movementDirection = look.negate().add(0, 1, 0);
                } else {
                    movementDirection = look.negate();
                }
            } else {
                movementDirection = look.negate();
                movementDirection = movementDirection.withAxis(Direction.Axis.Y, Math.abs(movementDirection.y));
            }
            //            Vec3d lookHorizontal = movementDirection.withAxis(Direction.Axis.Y, 0);
            //            Vec3d lastMoveHorizontal =
            //                PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed.withAxis(Direction.Axis.Y, 0);
            //            double dotValue = lookHorizontal.dotProduct(lastMoveHorizontal);
            //            if (dotValue < 0) {
            //                movementDirection = movementDirection.negate();
            //                movementDirection = movementDirection.withAxis(Direction.Axis.Y,
            // Math.abs(movementDirection.y));
            //            }
            return STATE_PULL_OVER;
        }

        public void onSwitchToNearFollow(boolean isOn) {
            nearFollowTimer = 0;
        }

        public void onSwitchToPullOver(boolean isOn) {
            pullOverTimer = 0;
        }

        public double getActiveRange() {

            return 8.0D;
        }

        public double getMinRange() {
            return 1.0D;
        }

        public int getCooldown() {
            return 6;
        }

        @Override
        public synchronized void onUpdate() {
            super.onUpdate();
            if (mc.player.isFallFlying() || mc.player.getAbilities().flying) {
                stateMachine.step();
            } else {
                movementDirection = Vec3d.ZERO;
                stateMachine.setState(STATE_NONE);
            }
        }

        @Override
        public void onEnable() {
            stateMachine.setState(STATE_NONE);
            pullOverTimer = 0;
            nearFollowTimer = 0;
        }

        @Override
        public void onDisable() {}

        @Override
        public synchronized void onHit(int spear) {
            if (spear == HIT_SPEAR) {

                nearFollowTimer = 0;
                stateMachine.setState(STATE_PULL_OVER);
                pullOverTimer = 0;
            }
        }
    }

    public static class SpearAruaV2 extends AbstractBotBehaviour implements HitListener {
        public static final int STATE_NONE = 0;
        public static final int STATE_FOLLOW = 1;
        public static final int STATE_ATTACK_PATTERN = 2;
        public static final int STATE_ADJUST_POSITION = 3;

        public int followTimer = 0;
        public int attackTimer = 0;
        public int adjustTimer = 0;
        public final StateMachine machine;
        Vec3d predictor = Vec3d.ZERO;

        public double getMinRange() {
            return 2.0D;
        }

        public double getMinHeightRequire() {
            return base.spearV2HeightRange.get().x();
        }

        public double getMaxHeightRequire() {
            return base.spearV2HeightRange.get().y();
        }

        public double getPositionLerp() {
            return base.spearV2PositionLerp.get();
        }

        public double getAdjustingMovement() {
            return base.spearV2AdjustPositionHorizontal.get();
        }

        public int getAdjustingTime() {
            return 10;
        }

        public SpearAruaV2() {
            machine = new StateMachine(
                    STATE_NONE,
                    this::onStateUpdate,
                    this::onStateNone,
                    this::onStateFollow,
                    this::onStateAttackPattern,
                    this::onStateAdjust);
            machine.registerListener(STATE_FOLLOW, this::onSwitchFollow);
            machine.registerListener(STATE_ATTACK_PATTERN, this::onSwitchAttackPattern);
            machine.registerListener(STATE_ADJUST_POSITION, this::onSwitchAdjustPosition);
        }

        public int onStateUpdate(StateMachine machine, int state) {
            if (base.target == null) {
                predictor = Vec3d.ZERO;
                return STATE_NONE;
            }
            predictor = base.spearUsePredictor.get()
                    ? PositionPredict.INSTANCE.spearPredictArgument.get().predict(base.target)
                    : base.target.getPos();
            return state;
        }

        public int onStateNone(StateMachine machine) {
            if (base.target != null) {
                if (!VItem.getInstance().isSpear(mc.player.getMainHandStack())
                        && !VItem.getInstance().isSpear(mc.player.getOffHandStack())) {
                    base.logI18N("message.module.elytra-bot.no-spear");
                }
                return STATE_FOLLOW;
            }
            machine.markForEndState();
            movementDirection = Vec3d.ZERO;
            return STATE_NONE;
        }

        public int onStateFollow(StateMachine machine) {
            Vec3d currentPos = mc.player.getPos();
            Debug.info("Current state follow", Tasks.getTick());
            if (currentPos.squaredDistanceTo(predictor) < MathUtils.s2(getAttackThreshold())) {
                return STATE_ATTACK_PATTERN;
            }
            if (base.currentOnGround) {
                Vec3d vec3d = predictor.subtract(mc.player.getPos());
                if (vec3d.length() < 6) {
                    vec3d = vec3d.normalize().multiply(6);
                }
                movementDirection = vec3d;
                machine.markForEndState();
                return STATE_FOLLOW;
            } else {
                Vec3d vec3d = predictor;
                double yHeight = (mc.player.getPos().y < predictor.y + getMinHeightRequire())
                        ? (predictor.y + getMaxHeightRequire())
                        : ((mc.player.getPos().y > predictor.y + getMaxHeightRequire())
                                ? (predictor.y + getMaxHeightRequire())
                                : predictor.y + getMinHeightRequire());
                Vec3d vec3d2 = vec3d.withAxis(Direction.Axis.Y, yHeight).subtract(mc.player.getPos());
                // pull up first
                if (vec3d.horizontalLength() < base.combatMaceRange.get()
                        && Math.abs(vec3d.y) > base.combatMaceRange.get()) {
                    vec3d2 = new Vec3d(0, vec3d2.y, 0);
                }
                if (vec3d2.length() < 6) {
                    vec3d2 = vec3d2.normalize().multiply(6);
                }
                movementDirection = vec3d2;
                if (!base.flyAntiSpearAfterAngle.get() && willUseAntiSpear(base.flyAntiSpear.get(), 14, predictor)) {
                    antiSpear(base.flyAntiSpear.get());
                }
                if (base.spearAngleOptimize.get()) {
                    double horizontal2 = Math.max(Math.abs(movementDirection.x), Math.abs(movementDirection.z));
                    if (horizontal2 > 0.1 && Math.abs(movementDirection.y) > horizontal2) {
                        // rescale
                        double sgnY = MathUtils.sgn(movementDirection.y, 1E-2);
                        movementDirection = movementDirection.withAxis(Direction.Axis.Y, sgnY * horizontal2);
                    }
                }
                if (base.flyAntiSpearAfterAngle.get() && willUseAntiSpear(base.flyAntiSpear.get(), 14, predictor)) {
                    antiSpear(base.flyAntiSpear.get());
                }
                Debug.chat("Current Follow");
                machine.markForEndState();
                return STATE_FOLLOW;
            }
        }

        public int onStateAttackPattern(StateMachine machine) {
            if (mc.player.getPos().squaredDistanceTo(predictor) < MathUtils.s2(getMinRange())) {
                return STATE_ADJUST_POSITION;
            }
            if (mc.player.getPos().squaredDistanceTo(predictor) > MathUtils.s2(getAttackThreshold() + 2.0D)) {
                return STATE_FOLLOW;
            }

            double lerp = getPositionLerp();
            Vec3d pos = base.target.getPos();
            Vec3d targetPos = predictor.multiply(lerp).add(pos.multiply(1 - lerp));
            Vec3d targetCenter = targetPos.add(0, base.target.getBoundingBox().getLengthY() / 2, 0);
            Vec3d playerPos = mc.player.getEyePos();
            Vec3d towards = targetCenter.subtract(playerPos);
            if (towards.length() < 6) {
                towards = towards.normalize().multiply(6);
            }
            movementDirection = towards;
            Debug.chat("Current Attack");
            machine.markForEndState();
            return STATE_ATTACK_PATTERN;
        }

        public int onStateAdjust(StateMachine machine) {
            if (++adjustTimer > getAdjustingTime()) {
                return STATE_FOLLOW;
            }
            if (mc.player.getPos().squaredDistanceTo(predictor) > MathUtils.s2(getAttackThreshold() + 2.0D)) {
                return STATE_FOLLOW;
            }
            if (base.currentOnGround) {
                Vec3d vec3d = predictor.subtract(mc.player.getPos());
                double y = Math.max(Math.abs(vec3d.x), Math.abs(vec3d.z));
                Vec3d vec3d2 = vec3d.negate().withAxis(Direction.Axis.Y, y);
                if (vec3d2.length() < 6) {
                    vec3d2 = vec3d.normalize().multiply(6);
                }
                movementDirection = vec3d2;
                machine.markForEndState();
                return STATE_ADJUST_POSITION;
            } else {
                Vec3d vec3d = predictor.subtract(mc.player.getPos());
                Vec3d vec3d1 = new Vec3d(vec3d.x, 0, vec3d.z);
                Vec3d vec3dNormalize = vec3d1;
                if (vec3dNormalize.lengthSquared() == 0) {
                    // ? in case of stuck here
                    vec3dNormalize = new Vec3d(1, 0, 0);
                }
                vec3dNormalize = vec3dNormalize.normalize();
                Vec3d vec3dHorizontal =
                        MathUtils.getVerticalWithSameY(vec3dNormalize).normalize();
                double tan = getAdjustingMovement();
                Vec3d direction1 = vec3dNormalize
                        .add(vec3dHorizontal.multiply(tan))
                        .normalize()
                        .multiply(vec3d1.length());
                Vec3d direction2 = vec3dNormalize
                        .add(vec3dHorizontal.multiply(-tan))
                        .normalize()
                        .multiply(vec3d1.length());
                Vec3d finalDirection;
                Vec3d lastMovement = PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed;
                Vec3d lastKnownMovementHorizontal = lastMovement.withAxis(Direction.Axis.Y, 0);
                if (base.currentAction == TargetAction.ESCAPING) {
                    double dot1 = lastKnownMovementHorizontal.dotProduct(direction1);
                    double dot2 = lastKnownMovementHorizontal.dotProduct(direction2);
                    Vec3d selectDirection;
                    if (Math.abs(dot1) > Math.abs(dot2)) {
                        selectDirection = direction1;
                        if (dot1 < 0) {
                            selectDirection = selectDirection.negate();
                        }
                    } else {
                        selectDirection = direction2;
                        if (dot2 < 0) {
                            selectDirection = selectDirection.negate();
                        }
                    }
                    finalDirection = selectDirection;
                } else {
                    direction1 = direction1.negate();
                    direction2 = direction2.negate();
                    double dot1 = lastKnownMovementHorizontal.dotProduct(direction1);
                    double dot2 = lastKnownMovementHorizontal.dotProduct(direction2);
                    Vec3d selectDirection;
                    if (dot1 > dot2) {
                        selectDirection = direction1;
                    } else {
                        selectDirection = direction2;
                    }
                    finalDirection = selectDirection;
                }
                finalDirection = finalDirection
                        .normalize()
                        .multiply(vec3d1.length())
                        .withAxis(Direction.Axis.Y, Math.abs(vec3d.y))
                        .normalize()
                        .multiply(6.0);
                movementDirection = finalDirection;
                if (!base.flyAntiSpearAfterAngle.get() && willUseAntiSpear(base.flyAntiSpear.get(), 14, predictor)) {
                    antiSpear(base.flyAntiSpear.get());
                }
                if (base.spearAngleOptimize.get()) {
                    double horizontal2 = Math.max(Math.abs(movementDirection.x), Math.abs(movementDirection.z));
                    if (horizontal2 > 0.1 && Math.abs(movementDirection.y) > horizontal2) {
                        // rescale
                        double sgnY = MathUtils.sgn(movementDirection.y, 1E-2);
                        movementDirection = movementDirection.withAxis(Direction.Axis.Y, sgnY * horizontal2);
                    }
                }
                if (base.flyAntiSpearAfterAngle.get() && willUseAntiSpear(base.flyAntiSpear.get(), 14, predictor)) {
                    antiSpear(base.flyAntiSpear.get());
                }
                Debug.chat("Current Adjust");
                machine.markForEndState();
                return STATE_ADJUST_POSITION;
            }
        }

        public void onSwitchFollow(boolean bl) {}

        public void onSwitchAttackPattern(boolean bl) {}

        public void onSwitchAdjustPosition(boolean bl) {
            adjustTimer = 0;
        }

        public double getAttackThreshold() {
            return base.spearV2PreAttackRange.get();
        }

        public synchronized void onUpdate() {
            super.onUpdate();
            if (mc.player.isFallFlying() || mc.player.getAbilities().flying) {
                machine.step();
            } else {
                movementDirection = Vec3d.ZERO;
                machine.setState(STATE_NONE);
            }
        }

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}

        @Override
        public synchronized void onHit(int spear) {
            if (spear == HIT_SPEAR) {
                if (machine.getState() == STATE_ATTACK_PATTERN) {
                    machine.setState(STATE_ADJUST_POSITION);
                }
            }
        }
    }

    public static class SpearAruaV3 extends AbstractBotBehaviour implements HitListener {
        public static final int STATE_NONE = 0;
        public static final int STATE_PULL_UP = 1;
        public static final int STATE_DIVE = 2;

        public final StateMachine machine;
        Vec3d predictor = Vec3d.ZERO;
        int lastMaceAttackTick = Integer.MIN_VALUE;

        public SpearAruaV3() {
            machine = new StateMachine(
                    STATE_NONE, this::onStateUpdate, this::onStateNone, this::onStatePullUp, this::onStateDive);
        }

        public int onStateUpdate(StateMachine machine, int state) {
            if (base.target == null) {
                predictor = Vec3d.ZERO;
                movementDirection = Vec3d.ZERO;
                machine.markForEndState();
                return STATE_NONE;
            }
            predictor = base.spearUsePredictor.get()
                    ? PositionPredict.INSTANCE.spearPredictArgument.get().predict(base.target)
                    : base.target.getPos();
            return state;
        }

        public int onStateNone(StateMachine machine) {
            if (base.target == null) {
                movementDirection = Vec3d.ZERO;
                machine.markForEndState();
                return STATE_NONE;
            }
            if (!VItem.getInstance().isSpear(mc.player.getMainHandStack())
                    && !VItem.getInstance().isSpear(mc.player.getOffHandStack())) {
                base.logI18N("message.module.elytra-bot.no-spear");
            }
            return STATE_PULL_UP;
        }

        public int onStatePullUp(StateMachine machine) {
            Vec3d playerPos = mc.player.getPos();
            Vec3d delta = predictor.subtract(playerPos);
            double horizontal = delta.horizontalLength();
            double height = delta.y;

            if (height > 0.0D) {
                if (horizontal <= base.spearV3OutwardRange.get()) {
                    movementDirection = calculateOutwardMovement(delta);
                } else {
                    movementDirection = calculatePullUpMovement();
                }
                machine.markForEndState();
                return STATE_PULL_UP;
            }

            double fallDistance = PlayerStateManager.INSTANCE.fallDistance;
            if (fallDistance < base.spearV3PullUpFallDistance.get().orElse(1.5D)) {
                movementDirection = calculatePullUpMovement();
                machine.markForEndState();
                return STATE_PULL_UP;
            }

            movementDirection = calculateDiveMovement();
            machine.markForEndState();
            return STATE_DIVE;
        }

        public int onStateDive(StateMachine machine) {
            if (base.target == null) {
                machine.markForEndState();
                return STATE_NONE;
            }
            if (mc.player.getY() < predictor.getY() + base.spearV3PullUpHeight.get()
                    && PlayerStateManager.INSTANCE.fallDistance
                            < base.spearV3PullUpFallDistance.get().orElse(1.5D)) {
                return STATE_PULL_UP;
            }
            movementDirection = calculateDiveMovement();
            machine.markForEndState();
            return STATE_DIVE;
        }

        private Vec3d calculateOutwardMovement(Vec3d delta) {
            Vec3d outward = new Vec3d(-delta.x, 0.0D, -delta.z);
            if (outward.lengthSquared() < 1E-6) {
                outward = PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed.withAxis(Direction.Axis.Y, 0);
            }
            if (outward.lengthSquared() < 1E-6) {
                outward = new Vec3d(1.0D, 0.0D, 0.0D);
            }
            return outward.normalize().multiply(base.spearV3OutwardSpeed.get());
        }

        private Vec3d calculatePullUpMovement() {
            Vec3d targetPosition = predictor
                    .withAxis(Direction.Axis.Y, predictor.getY() + base.spearV3PullUpHeight.get())
                    .subtract(mc.player.getPos());
            if (targetPosition.length() < 5.0D) {
                targetPosition = targetPosition.normalize().multiply(5.0D);
            }
            return targetPosition;
        }

        private Vec3d calculateDiveMovement() {
            Vec3d targetPosition =
                    base.target.dimensions.getBoxAt(predictor).getCenter().subtract(mc.player.getEyePos());
            if (targetPosition.length() > base.spearV3DiveDistance.get()) {
                return ElytraOptimizeUtils.calculateBestDownwardSpeed(targetPosition);
            }
            if (targetPosition.length() < 5.0D && targetPosition.lengthSquared() > 1E-6) {
                return targetPosition.normalize().multiply(5.0D);
            }
            return targetPosition;
        }

        private boolean canAttackWithMace() {
            if (!base.spearV3UseMaceAttack.get()
                    || Tasks.getTick() < lastMaceAttackTick + base.spearV3MaceAttackCooldown.get()) {
                return false;
            }
            if (!Attack.INSTANCE.willUseMaceAttack(true)) {
                return false;
            }
            if (PlayerStateManager.INSTANCE.fallDistance
                    <= base.spearV3MaceFallDistance.get().orElse(1.5D)) {
                return false;
            }
            return TargetSelector.INSTANCE.isWithinAttackRange(
                    mc.player.getPos(),
                    base.target.getBoundingBox(),
                    CombatTasks.getCombatExtra().getAttackAtTargetRange(base.target));
        }

        private boolean tryMaceAttack() {
            if (base.target == null || !canAttackWithMace()) {
                return false;
            }
            Attack.AttackSettings settings = CombatTasks.getAttack()
                    .createAttackSettings()
                    .withMaceSwap(true)
                    .withInvSwap(false)
                    .withAntiShieldSwap(false);
            CombatTasks.getAttack().attackEntity(base.target, settings);
            lastMaceAttackTick = Tasks.getTick();
            machine.setState(STATE_PULL_UP);
            machine.step();
            return true;
        }

        @Override
        public synchronized void onUpdate() {
            super.onUpdate();
            if (!(mc.player.isFallFlying() || mc.player.getAbilities().flying)) {
                movementDirection = Vec3d.ZERO;
                machine.setState(STATE_NONE);
                return;
            }
            machine.step();
            tryMaceAttack();
        }

        @Override
        public void onEnable() {
            machine.setState(STATE_NONE);
            predictor = Vec3d.ZERO;
            lastMaceAttackTick = Integer.MIN_VALUE;
        }

        @Override
        public void onDisable() {
            movementDirection = Vec3d.ZERO;
        }

        @Override
        public synchronized void onHit(int type) {
            if (type == HIT_SPEAR) {
                machine.setState(STATE_PULL_UP);
            }
            if (type == HIT_MACE) {
                machine.setState(STATE_PULL_UP);
            }
        }

        @Override
        public synchronized void onAttack(Entity entity) {
            if (entity == base.target && (mc.player.isFallFlying() || mc.player.getAbilities().flying)) {
                machine.setState(STATE_PULL_UP);
            }
        }
    }

    public static class WeaponArua extends AbstractBotBehaviour {

        @Override
        public void onUpdate() {}

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}
    }

    public static class LandingControl extends AbstractBotBehaviour {

        @Override
        public void onUpdate() {}

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}
    }

    public enum TargetAction {
        ESCAPING,
        TOWARDS,
        CIRCLING,
        SLOW_SPEED,
        AFK;
    }

    public enum Mode implements ConfigEnum {
        FOLLOW,
        MACE_ARUA,
        SPEAR_ARUA;

        @Override
        public String getConfigEnumType() {
            return "elytra_bot_mode";
        }
    }
}
