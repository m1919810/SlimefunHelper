package me.matl114.hacks.modules.interact;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.DoubleStream;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.utils.MathUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class InteractExtra extends BaseModule {
    public final ModulePath interactFix = makePath(Configs.INTERACT_CONFIG, "interact-fix");
    public static InteractExtra INSTANCE;

    public InteractExtra() {
        INSTANCE = this;
    }

    public final FlagRef grimExpandEyeHeight =
            flagBuilder(interactFix.add("use-grim-expand-eye-height")).build();

    public final FlagRef noCooldown =
            flagBuilder(interactFix.add("no-cool-down")).build();

    public final IntRef noCooldownValue =
            intBuilder(interactFix.add("cool-down-rewrite")).defaultValue(4).build();

    public final FlagRef rideUse = builder(interactFix.add("allow-ride-interact"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final FlagRef holdUse = builder(interactFix.add("hold-use"), FlagRef.TYPE)
            .defaultValue(false)
            .build();

    public final IntRef holdUseStartTick =
            intBuilder(interactFix.add("hold-use-start-tick")).defaultValue(4).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getUseItemCooldownReset(), this::onCooldown);
    }

    private final double[] FALL_FLYING_EYE_HEIGHTS = {0.4D, 1.62D, 1.27D};
    private final double[] STANDING_EYE_HEIGHTS = {1.62D, 1.27D, 0.4D};

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

    public boolean isWithinInteractRange(Vec3d pos, BlockPos bp, double range) {
        return isWithinInteractRange(pos, new Box(bp), range);
    }

    public boolean isWithinInteractRange(Vec3d pos, Box box, double range) {
        if (box.squaredMagnitude(pos) > MathUtils.s2(range + 2 + mc.player.dimensions.eyeHeight())) {
            // filter all outofrange
            // optimize calculation
            return false;
        }
        return getPotentialEyeHeights()
                .mapToObj(s -> pos.add(0, s, 0))
                .anyMatch(ps -> box.squaredMagnitude(ps) < MathUtils.s2(range));
    }

    public Vec3d getBestInteractEyePos(Vec3d pos, BlockPos box) {
        return getBestInteractEyePos(pos, new Box(box));
    }

    public Vec3d getBestInteractEyePos(Vec3d pos, Box box) {
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

    public Optional<Vec3d> getBestInteractEyePos(Vec3d pos, BlockPos box, double range) {
        return getBestInteractEyePos(pos, new Box(box), range);
    }

    public Optional<Vec3d> getBestInteractEyePos(Vec3d pos, Box box, double range) {
        if (box.squaredMagnitude(pos) > MathUtils.s2(range + 3 + mc.player.dimensions.eyeHeight())) {
            // filter all outofrange
            // optimize calculation
            return Optional.empty();
        }
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
        if (MathUtils.s2(range) >= s2) {
            return Optional.of(playerPos);
        } else {
            return Optional.empty();
        }
    }

    public void onCooldown(Event<Integer> event) {
        if (noCooldown.get() && noCooldownValue.get() >= 0) {
            event.context(noCooldownValue.get());
        }
    }
}
