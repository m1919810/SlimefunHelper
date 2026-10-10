package me.matl114.hacks.modules.move;

import java.util.*;
import java.util.function.Supplier;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.packets.PacketStorage;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.RayCastCalculator;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Tasks;
import me.matl114.utils.ApiMethod;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

public class RotationManager extends BaseModule implements LegalMovementManager.MovementModifier {
    public static RotationManager INSTANCE;

    private final List<RotationRequest> currentTickRequests = new ArrayList<>();
    RotationRequest currentTickRequest;
    private int queueTick = 0;
    static LegalMovementManager.DelegateMovementModifier instance;

    public RotationManager() {
        super("RotationManager");
        INSTANCE = this;
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getServerLeavePoint(), this::onServerLeave);
        registerListener(PacketManager.getPacketQueueOutEvent(), this::onQueuePacket);
    }

    @Override
    public int priority() {
        // Resolve queued interaction rotations after movement modifiers. This keeps
        // flight and combat modifiers from taking ownership of a queued request.
        return PRIORITY_HIGHEST;
    }

    @ApiMethod
    public boolean request(RotationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!isOccupied()) {
            currentTickRequests.add(request);
            return true;
        }
        return false;
    }

    public boolean isOccupied() {
        return currentTickRequests.stream().anyMatch(RotationRequest::occupyRotation);
    }

    private void onQueuePacket(Event<PacketStorage> event) {
        if (queueTick == Tasks.getTick()) {
            event.cancel();
        }
    }

    public void startQueuePacket() {
        queueTick = Tasks.getTick();
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> event) {

        LegalMovementManager manager = event.context();
        ClientPlayerEntity player = manager.playerStatus.entity;

        currentTickRequest = currentTickRequests.stream()
                .filter(RotationRequest::occupyRotation)
                .findFirst()
                .orElse(null);
        currentTickRequests.clear();
        if (ElytraExtra.INSTANCE != null && ElytraExtra.INSTANCE.hasFireworkVelocityOverrides()) {
            return;
        }
        if (currentTickRequest == null) {
            return;
        }
        // Resolve the initial rotation against the position used by this tick's movement.

        Vec2f rotation = currentTickRequest.tickRotationPre(player.getPos(), player);
        if (rotation == null) return;
        manager.pushImportantRotation(true, true);
        EntityUtils.setEntityPitchSafe(player, rotation.x);
        PlayerStateManager.setPlayerYawSafe(player, rotation.y);
        if (currentTickRequest.applyMoveFix()) manager.tryMarkForMoveFix();
        manager.markForResetRot();
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> event) {
        if (currentTickRequest == null) return;
        ClientPlayerEntity player = event.context().playerStatus.entity;
        if (ElytraExtra.INSTANCE != null && ElytraExtra.INSTANCE.hasFireworkVelocityOverrides()) return;
        Vec2f rotation = currentTickRequest.tickRotationPreMovementPacket(player.getPos(), player);
        if (rotation == null) return;
        if (player.isFallFlying() || player.isSwimming()) return;
        EntityUtils.setEntityPitchSafe(player, rotation.x);
    }

    @Override
    public void applyBeforeInputPacketModify(Event<LegalMovementManager> event) {
        if (currentTickRequest == null) return;
        ClientPlayerEntity player = event.context().playerStatus.entity;
        Vec2f rotation = currentTickRequest.tickRotationPreMovementPacket(player.getPos(), player);
        if (rotation == null) return;
        EntityUtils.setEntityPitchSafe(player, rotation.x);
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> event, boolean enabledThisTick) {
        RotationRequest request = currentTickRequest;
        currentTickRequest = null;
        if (request != null) {
            request.tickPost();
        }
        if (queueTick != 0) {
            queueTick = 0;
            PacketManager.flushOutBound();
        }
        return true;
    }

    private void onServerLeave(Event<Void> ignored) {
        clearRequests();
    }

    private void clearRequests() {
        currentTickRequests.clear();
        currentTickRequest = null;
    }

    public interface RotationRequest {
        default boolean occupyRotation() {
            return true;
        }

        @Nullable
        public Vec2f tickRotationPre(Vec3d pos, ClientPlayerEntity player);

        @Nullable
        default Vec2f tickRotationPreMovementPacket(Vec3d pos, ClientPlayerEntity player) {
            return null;
        }

        default void tickPost() {}

        default boolean applyMoveFix() {
            return true;
        }
    }

    public static final RotationRequest EMPTY = (pos, player) -> null;

    @Setter
    @Accessors(fluent = true, chain = true)
    public static class RotationRequestImpl<T> implements RotationRequest {
        private final RayCastCalculator<T> calculator;
        private final Supplier<T> targetSupplier;
        private final boolean preMovementAdjust;
        private Runnable preTick = () -> {};
        private Runnable postTick = () -> {};
        private boolean applyMoveFix = true;

        public RotationRequestImpl(
                RayCastCalculator<T> calculator, Supplier<T> targetSupplier, boolean preMovementAdjust) {
            this.calculator = Objects.requireNonNull(calculator, "calculator");
            this.targetSupplier = Objects.requireNonNull(targetSupplier, "targetSupplier");
            this.preMovementAdjust = preMovementAdjust;
        }

        @Override
        public boolean occupyRotation() {
            return targetSupplier.get() != null;
        }

        @Override
        public boolean applyMoveFix() {
            return applyMoveFix;
        }

        @MustBeInvokedByOverriders
        @Override
        public void tickPost() {
            if (postTick != null) postTick.run();
        }

        @MustBeInvokedByOverriders
        @Override
        public @Nullable Vec2f tickRotationPre(Vec3d pos, ClientPlayerEntity player) {
            if (preTick != null) preTick.run();
            T target = targetSupplier.get();
            if (target == null) return null;
            Vec3d direction = calculator.getRaycast(player.getPos(), target);
            return direction == null ? null : EntityUtils.rotationToPitchYaw(direction.normalize());
        }

        @Override
        public @Nullable Vec2f tickRotationPreMovementPacket(Vec3d pos, ClientPlayerEntity player) {
            if (!preMovementAdjust || player.isSwimming() || player.isTouchingWater() || player.isFallFlying())
                return null;
            T target = targetSupplier.get();
            if (target == null) return null;
            Box effectiveBox = calculator.getEffectiveBox(target);
            if (effectiveBox == null) return null;

            float fixedYaw = player.getYaw();
            float currentPitch = player.getPitch();
            if (calculator.canRayCast(pos, target, currentPitch, fixedYaw)) {
                return new Vec2f(currentPitch, fixedYaw);
            }

            Vec3d eyePos = pos.add(0, player.dimensions.eyeHeight(), 0);
            float centerPitch = EntityUtils.rotationToPitch(
                    effectiveBox.getCenter().subtract(eyePos).normalize());
            if (calculator.canRayCast(pos, target, centerPitch, fixedYaw)) {
                return new Vec2f(centerPitch, fixedYaw);
            }
            Box projectileBox = effectiveBox.withMinY(-1.0D).withMaxY(1.0D);
            Vec3d projectileYaw = EntityUtils.pitchYawToRotation(0, fixedYaw);
            var hitResult = projectileBox.raycast(
                    pos.withAxis(Direction.Axis.Y, 0), projectileYaw.multiply(calculator.getReachDistance(target)));
            if (hitResult.isPresent()) {
                Vec3d hitPoint = hitResult.get();
                var hitRay = calculator
                        .getPotentialEyeHeights(pos)
                        .map(s -> {
                            Vec3d magPoint = hitPoint.withAxis(
                                    Direction.Axis.Y, Math.clamp(s.getY(), effectiveBox.minY, effectiveBox.maxY));
                            double distance = magPoint.distanceTo(s);
                            return distance <= calculator.getReachDistance(target) ? magPoint.subtract(s) : null;
                        })
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
                if (hitRay != null) {
                    float pitch = EntityUtils.rotationToPitch(hitRay.normalize());
                    if (calculator.canRayCast(pos, target, pitch, fixedYaw)) {
                        return new Vec2f(pitch, fixedYaw);
                    }
                }
            }
            return null;
        }
    }

    private static final RayCastCalculator<Box> BOX_CALCULATOR = new RayCastCalculator<>() {
        @Override
        public Box getEffectiveBox(Box target) {
            return target;
        }

        @Override
        public double getReachDistance(Box val) {
            return 100.0D;
        }
    };

    public static RotationRequestImpl<Box> ofBox(Supplier<Box> boxSupplier) {
        return new RotationRequestImpl<>(BOX_CALCULATOR, boxSupplier, false);
    }

    public static DirectionRotationRequest ofDirection(Supplier<Vec2f> rotationSupplier) {
        return new DirectionRotationRequest(rotationSupplier);
    }

    public static final class DirectionRotationRequest implements RotationRequest {
        private final Supplier<Vec2f> rotationSupplier;
        private Runnable postTick = () -> {};

        private DirectionRotationRequest(Supplier<Vec2f> rotationSupplier) {
            this.rotationSupplier = Objects.requireNonNull(rotationSupplier, "rotationSupplier");
        }

        public DirectionRotationRequest postTick(Runnable callback) {
            postTick = callback == null ? () -> {} : callback;
            return this;
        }

        @Override
        public @Nullable Vec2f tickRotationPre(Vec3d pos, ClientPlayerEntity player) {
            return rotationSupplier.get();
        }

        @Override
        public void tickPost() {
            postTick.run();
        }
    }

    public static RotationRequestImpl<BlockPos> ofBlock(Supplier<BlockPos> posSupplier) {
        return new RotationRequestImpl<>(InteractExtra.INSTANCE, posSupplier, false) {
            @Override
            public @Nullable Vec2f tickRotationPre(Vec3d pos, ClientPlayerEntity player) {
                Vec2f vec2f = super.tickRotationPre(pos, player);
                if (vec2f == null) return null;
                return fluctuateBlockRaycast(posSupplier.get(), pos, vec2f, InteractExtra.INSTANCE);
            }
        };
    }

    public static RotationRequestImpl<Entity> ofEntity(Supplier<Entity> entitySupplier) {
        return new RotationRequestImpl<>(TargetSelector.INSTANCE, entitySupplier, true);
    }

    private static boolean lastAdd;

    private static Vec2f fluctuateBlockRaycast(
            BlockPos pos, Vec3d feet, Vec2f direct, RayCastCalculator<BlockPos> posRaycast) {
        if (pos == null) return direct;
        float pitch = quantize(direct.x);
        float yaw = quantize(direct.y);
        Vec2f candidate = new Vec2f(pitch, yaw);
        if (!posRaycast.canRayCast(feet, pos, candidate.x, candidate.y)) candidate = direct;
        yaw = candidate.y;
        int sign = lastAdd ? 1 : -1;
        lastAdd = !lastAdd;
        for (int i = 0; i < 8; i++) {
            Vec2f adjusted = new Vec2f(pitch, quantize(yaw + sign * 0.1F));
            if (posRaycast.canRayCast(feet, pos, adjusted.x, adjusted.y)) {
                candidate = adjusted;
                yaw = adjusted.y;
            }
        }
        return candidate;
    }

    private static float quantize(float angle) {
        return Math.round(angle * 10.0F) / 10.0F;
    }
}
