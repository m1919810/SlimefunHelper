package me.matl114.hacks.modules.move;

import java.util.*;
import java.util.function.Function;
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
    private boolean currentInQueue = false;
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
        if (isOccupied()) {
            return false;
        }
        currentTickRequests.add(request);
        return true;
    }

    public boolean isOccupied() {
        return currentTickRequests.stream().anyMatch(request -> request.priority() == Integer.MIN_VALUE);
    }

    private void onQueuePacket(Event<PacketStorage.Send> event) {
        if (queueTick < Tasks.getTick() - 5) {
            currentInQueue = false;
        }
        if (currentInQueue) {
            event.cancel();
        }
    }

    public void startQueuePacket() {
        queueTick = Tasks.getTick();
        PacketManager.schedule(() -> {
            queueTick = Tasks.getTick();
            currentInQueue = true;
        });
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> event) {

        LegalMovementManager manager = event.context();
        ClientPlayerEntity player = manager.playerStatus.entity;

        currentTickRequest = currentTickRequests.stream()
                .min(Comparator.comparingInt(RotationRequest::priority))
                .orElse(null);
        currentTickRequests.clear();
        if (ElytraExtra.INSTANCE.hasFireworkVelocityOverrides()) {
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
        if (queueTick != 0 || currentInQueue) {
            PacketManager.flushOutBound(() -> {
                queueTick = 0;
                currentInQueue = false;
            });
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
        default int priority() {
            return Integer.MIN_VALUE;
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
    public abstract static class RotationRequestImpl<T> implements RotationRequest {
        private Runnable preTick = () -> {};
        private Runnable postTick = () -> {};
        private boolean preMovementAdjust;
        private boolean applyMoveFix = true;
        private int priority = Integer.MIN_VALUE;

        @Override
        public int priority() {
            return priority;
        }

        public boolean preMovementAdjust() {
            return preMovementAdjust;
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
            return calculateRotationPre(pos, player);
        }

        protected abstract @Nullable Vec2f calculateRotationPre(Vec3d pos, ClientPlayerEntity player);
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    public static class BoxRaycastRequest<T> extends RotationRequestImpl<T> {
        private final RayCastCalculator<T> calculator;
        private final Supplier<T> targetSupplier;

        public BoxRaycastRequest(RayCastCalculator<T> calculator, Supplier<T> targetSupplier) {
            this.calculator = Objects.requireNonNull(calculator, "calculator");
            this.targetSupplier = Objects.requireNonNull(targetSupplier, "targetSupplier");
        }

        public BoxRaycastRequest(
                RayCastCalculator<T> calculator, Supplier<T> targetSupplier, boolean preMovementAdjust) {
            this(calculator, targetSupplier);
            preMovementAdjust(preMovementAdjust);
        }

        @MustBeInvokedByOverriders
        @Override
        protected @Nullable Vec2f calculateRotationPre(Vec3d pos, ClientPlayerEntity player) {
            T target = targetSupplier.get();
            if (target == null) return null;
            Vec3d direction = calculator.getRaycast(player.getPos(), target);
            return direction == null ? null : EntityUtils.rotationToPitchYaw(direction.normalize());
        }

        @Override
        public @Nullable Vec2f tickRotationPreMovementPacket(Vec3d pos, ClientPlayerEntity player) {
            if (!preMovementAdjust() || player.isSwimming() || player.isTouchingWater() || player.isFallFlying())
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

    public static BoxRaycastRequest<Box> ofBox(Supplier<Box> boxSupplier) {
        return new BoxRaycastRequest<>(BOX_CALCULATOR, boxSupplier);
    }

    public static LookDirectionRequest ofDirection(Supplier<Vec2f> rotationSupplier) {
        return new LookDirectionRequest(rotationSupplier);
    }

    public static LookDirectionRequest ofDirection(Optional<Float> pitch, Optional<Float> yaw) {
        return new LookDirectionRequest(pitch, yaw);
    }

    public static LookDirectionRequest ofDirection(float pitch, float yaw) {
        return new LookDirectionRequest(Optional.of(pitch), Optional.of(yaw));
    }

    public static LookDirectionRequest ofLookDirection(Optional<Float> pitch, Optional<Float> yaw) {
        return new LookDirectionRequest(pitch, yaw);
    }

    public static LookDirectionRequest ofLookDirection(float pitch, float yaw) {
        return new LookDirectionRequest(Optional.of(pitch), Optional.of(yaw));
    }

    public static final class LookDirectionRequest extends RotationRequestImpl<Vec2f> {
        private final Function<ClientPlayerEntity, Vec2f> rotationSupplier;

        public LookDirectionRequest() {
            this(Optional.empty(), Optional.empty());
        }

        public LookDirectionRequest(Supplier<Vec2f> rotationSupplier) {
            this.rotationSupplier = (pl) -> rotationSupplier.get();
        }

        public LookDirectionRequest(Optional<Float> pitch, Optional<Float> yaw) {
            this.rotationSupplier = (pl) -> {
                if (pitch.isEmpty() && yaw.isEmpty()) return null;
                return new Vec2f(pitch.orElseGet(pl::getPitch), yaw.orElseGet(pl::getYaw));
            };
        }

        @Override
        protected @Nullable Vec2f calculateRotationPre(Vec3d pos, ClientPlayerEntity player) {
            return rotationSupplier == null ? null : rotationSupplier.apply(player);
        }
    }

    public static BoxRaycastRequest<BlockPos> ofBlock(Supplier<BlockPos> posSupplier) {
        return new BoxRaycastRequest<>(InteractExtra.INSTANCE, posSupplier) {
            @Override
            protected @Nullable Vec2f calculateRotationPre(Vec3d pos, ClientPlayerEntity player) {
                Vec2f vec2f = super.calculateRotationPre(pos, player);
                if (vec2f == null) return null;
                return fluctuateBlockRaycast(posSupplier.get(), pos, vec2f, InteractExtra.INSTANCE);
            }
        };
    }

    public static BoxRaycastRequest<Entity> ofEntity(Supplier<Entity> entitySupplier) {
        BoxRaycastRequest<Entity> request = new BoxRaycastRequest<>(TargetSelector.INSTANCE, entitySupplier);
        request.preMovementAdjust(true);
        return request;
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
