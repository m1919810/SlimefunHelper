package me.matl114.hacks.modules.interact;

import java.util.*;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.RayCastCalculator;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.utils.MathUtils;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.*;
import org.joml.Vector2i;

public class InteractExtra extends BaseModule implements RayCastCalculator<BlockPos> {
    public final ModulePath interactFix = makePath(Configs.INTERACT_CONFIG, "interact-fix");
    public static InteractExtra INSTANCE;

    public InteractExtra() {
        super("InteractExtra");
        INSTANCE = this;
    }

    public List<Vec3i> blocksAround = new ArrayList<>();

    public List<Vector2i> platesAround = new ArrayList<>();

    public double lastRange;

    public List<Vec3i> getBlocksAround() {
        if (mc.player != null) {
            refreshInteractionRange(InteractExtra.INSTANCE.getBlockReachDistance());
        }
        return Collections.unmodifiableList(blocksAround);
    }

    public List<Vector2i> getPlatesAround() {
        if (mc.player != null) {
            refreshInteractionRange(InteractExtra.INSTANCE.getBlockReachDistance());
        }
        return Collections.unmodifiableList(platesAround);
    }

    public void refreshInteractionRange(double val) {
        if (mc.player != null) {
            if (lastRange != val) {
                // update interaction range lazily
                lastRange = val;
                List<Vec3i> points = new ArrayList<>();
                int range = (int) lastRange;
                for (int x = -range; x <= range; x++) {
                    for (int y = -range; y <= range; y++) {
                        for (int z = -range; z <= range; z++) {
                            points.add(new Vec3i(x, y, z));
                        }
                    }
                }
                points.sort(Comparator.comparingDouble(
                        v -> v.getX() * v.getX() + v.getY() * v.getY() + v.getZ() * v.getZ()));
                blocksAround = points;
                List<Vector2i> plates = new ArrayList<>();
                for (int x = -range; x <= range; x++) {
                    for (int y = -range; y <= range; y++) {
                        plates.add(new Vector2i(x, y));
                    }
                }
                plates.sort(Comparator.comparingDouble(v -> v.x * v.x + v.y * v.y));
                platesAround = plates;
            }
        }
    }

    public final FlagRef grimExpandEyeHeight =
            flagBuilder(interactFix.add("use-grim-expand-eye-height")).build();

    public final DoubleRef reachDistance = builder(interactFix.add("reach-distance"), Double.class)
            .defaultValue(0.0)
            .build();

    public final DoubleRef ridingReachDistance = builder(interactFix.add("riding-reach-distance"), DoubleRef.TYPE)
            .defaultValue(1.0D)
            .build();

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

    public final IntRef holdUseStartTick = intBuilder(interactFix.add("hold-use-start-tick"))
            .defaultValue(4)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final FlagRef clientUsingStateFix = builder(interactFix.add("client-using-state-fix"), Boolean.class)
            .defaultValue(true)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getUseItemCooldownReset(), this::onCooldown);
    }

    public double getBlockReachDistance() {
        return mc.player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE)
                + (mc.player.hasVehicle() ? ridingReachDistance.get() : reachDistance.get());
    }

    @Override
    public Box getEffectiveBox(BlockPos target) {
        return new Box(target);
    }

    @Override
    public Vec3d getRaycast(Vec3d pos, BlockPos target) {
        Box box = getEffectiveBox(target);
        double range = getBlockReachDistance();
        Vec3d fallbackEye = pos.add(0, mc.player.dimensions.eyeHeight(), 0);
        return getPotentialEyeHeights(pos)
                .filter(eyePos -> box.squaredMagnitude(eyePos) < MathUtils.s2(range))
                .map(eyePos -> raycastDirection(box, eyePos, range))
                .findFirst()
                .orElseGet(() -> raycastDirection(box, fallbackEye, range));
    }

    @Override
    public boolean canRayCast(Vec3d pos, BlockPos target, float pitch, float yaw) {
        Box box = getEffectiveBox(target);
        double range = getBlockReachDistance();
        Vec3d direction = EntityUtils.pitchYawToRotation(pitch, yaw).normalize();
        return getPotentialEyeHeights(pos).anyMatch(eyePos -> {
            return box.squaredMagnitude(eyePos) < MathUtils.s2(range)
                    && box.raycast(eyePos, eyePos.add(direction.multiply(range)))
                            .isPresent();
        });
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

    public Stream<Vec3d> getPotentialEyeHeights(Vec3d playerPos) {
        return getPotentialEyeHeights().mapToObj(s -> playerPos.add(0, s, 0));
    }

    public boolean isWithinInteractRange(Vec3d pos, BlockPos bp) {
        return isWithinInteractRange(pos, bp, getBlockReachDistance());
    }

    public boolean isWithinInteractRange(Vec3d pos, Box bp) {
        return isWithinInteractRange(pos, bp, getBlockReachDistance());
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
        return getPotentialEyeHeights(pos).anyMatch(ps -> box.squaredMagnitude(ps) < MathUtils.s2(range));
    }

    public void onCooldown(Event<Integer> event) {
        if (noCooldown.get() && noCooldownValue.get() >= 0) {
            event.context(noCooldownValue.get());
        }
    }
}
