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
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.task.MovementManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.RayCastCalculator;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.hacks.utils.move.Rotation;
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
        MovementManager.registerMovementPipeline(this);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getServerLeavePoint(), this::clearRequests);
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

    public void flushQueuePackets() {
        if (queueTick != 0 || currentInQueue) {
            PacketManager.flushOutBound(() -> {
                queueTick = 0;
                currentInQueue = false;
            });
        }
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

        Rotation rotation = currentTickRequest.tickRotationPre(player.getPos(), player);
        if (rotation == null || !rotation.hasRotate()) return;
        manager.pushImportantRotation(rotation.hasPitch(), rotation.hasYaw());
        if (rotation.hasPitch()) EntityUtils.setEntityPitchSafe(player, rotation.getPitch());
        if (rotation.hasYaw()) PlayerStateManager.setPlayerYawSafe(player, rotation.getYaw());
        if (currentTickRequest.applyMoveFix()) manager.tryMarkForMoveFix();
        manager.markForResetRot();
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> event) {
        if (currentTickRequest == null) return;
        ClientPlayerEntity player = event.context().playerStatus.entity;
        if (ElytraExtra.INSTANCE != null && ElytraExtra.INSTANCE.hasFireworkVelocityOverrides()) return;
        Rotation rotation = currentTickRequest.tickRotationPreMovementPacket(player.getPos(), player);
        if (rotation == null || !rotation.hasPitch()) return;
        if (player.isFallFlying() || player.isSwimming()) return;
        EntityUtils.setEntityPitchSafe(player, rotation.getPitch());
    }

    @Override
    public void applyBeforeInputPacketModify(Event<LegalMovementManager> event) {
        if (currentTickRequest == null) return;
        ClientPlayerEntity player = event.context().playerStatus.entity;
        Rotation rotation = currentTickRequest.tickRotationPreMovementPacket(player.getPos(), player);
        if (rotation == null || !rotation.hasPitch()) return;
        EntityUtils.setEntityPitchSafe(player, rotation.getPitch());
    }

    @Override
    public void postModify(Event<LegalMovementManager> event) {
        RotationRequest request = currentTickRequest;
        currentTickRequest = null;
        if (request != null) {
            request.tickPost();
        }
        flushQueuePackets();
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
        public Rotation tickRotationPre(Vec3d pos, ClientPlayerEntity player);

        @Nullable
        default Rotation tickRotationPreMovementPacket(Vec3d pos, ClientPlayerEntity player) {
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
        public @Nullable Rotation tickRotationPre(Vec3d pos, ClientPlayerEntity player) {
            if (preTick != null) preTick.run();
            return calculateRotationPre(pos, player);
        }

        protected abstract @Nullable Rotation calculateRotationPre(Vec3d pos, ClientPlayerEntity player);
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
        protected @Nullable Rotation calculateRotationPre(Vec3d pos, ClientPlayerEntity player) {
            T target = targetSupplier.get();
            if (target == null) return null;
            Vec3d direction = calculator.getRaycast(player.getPos(), target);
            return direction == null ? null : Rotation.from(direction.normalize());
        }

        @Override
        public @Nullable Rotation tickRotationPreMovementPacket(Vec3d pos, ClientPlayerEntity player) {
            if (!preMovementAdjust() || player.isSwimming() || player.isTouchingWater() || player.isFallFlying())
                return null;
            T target = targetSupplier.get();
            if (target == null) return null;
            Box effectiveBox = calculator.getEffectiveBox(target);
            if (effectiveBox == null) return null;

            float fixedYaw = player.getYaw();
            float currentPitch = player.getPitch();
            if (calculator.canRayCast(pos, target, currentPitch, fixedYaw)) {
                return Rotation.from(currentPitch, fixedYaw);
            }

            Vec3d eyePos = pos.add(0, player.dimensions.eyeHeight(), 0);
            float centerPitch = EntityUtils.rotationToPitch(
                    effectiveBox.getCenter().subtract(eyePos).normalize());
            if (calculator.canRayCast(pos, target, centerPitch, fixedYaw)) {
                return Rotation.from(centerPitch, fixedYaw);
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
                        return Rotation.from(pitch, fixedYaw);
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

    public static LookDirectionRequest ofDirection(Supplier<Rotation> rotationSupplier) {
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

    public static final class LookDirectionRequest extends RotationRequestImpl<Rotation> {
        private final Function<ClientPlayerEntity, Rotation> rotationSupplier;

        public LookDirectionRequest() {
            this(Optional.empty(), Optional.empty());
        }

        public LookDirectionRequest(Supplier<Rotation> rotationSupplier) {
            this.rotationSupplier = (pl) -> rotationSupplier.get();
        }

        public LookDirectionRequest(Optional<Float> pitch, Optional<Float> yaw) {
            this.rotationSupplier = (pl) -> {
                if (pitch.isEmpty() && yaw.isEmpty()) return null;
                return new Rotation(pitch.orElse(null), yaw.orElse(null));
            };
        }

        @Override
        protected @Nullable Rotation calculateRotationPre(Vec3d pos, ClientPlayerEntity player) {
            return rotationSupplier == null ? null : rotationSupplier.apply(player);
        }
    }

    public static BoxRaycastRequest<BlockPos> ofBlock(Supplier<BlockPos> posSupplier) {
        return new BoxRaycastRequest<>(InteractExtra.INSTANCE, posSupplier) {
            @Override
            protected @Nullable Rotation calculateRotationPre(Vec3d pos, ClientPlayerEntity player) {
                Rotation rotation = super.calculateRotationPre(pos, player);
                if (rotation == null) return null;
                return fluctuateBlockRaycast(posSupplier.get(), pos, rotation, InteractExtra.INSTANCE);
            }
        };
    }

    public static BoxRaycastRequest<Entity> ofEntity(Supplier<Entity> entitySupplier) {
        BoxRaycastRequest<Entity> request = new BoxRaycastRequest<>(TargetSelector.INSTANCE, entitySupplier);
        request.preMovementAdjust(true);
        return request;
    }

    private static boolean lastAdd;

    private static Rotation fluctuateBlockRaycast(
            BlockPos pos, Vec3d feet, Rotation direct, RayCastCalculator<BlockPos> posRaycast) {
        if (pos == null || !direct.hasPitch() || !direct.hasYaw()) return direct;
        float pitch = quantize(direct.getPitch());
        float yaw = quantize(direct.getYaw());
        Rotation candidate = Rotation.from(pitch, yaw);
        if (!posRaycast.canRayCast(feet, pos, candidate.getPitch(), candidate.getYaw())) candidate = direct;
        yaw = candidate.getYaw();
        int sign = lastAdd ? 1 : -1;
        lastAdd = !lastAdd;
        for (int i = 0; i < 8; i++) {
            Rotation adjusted = Rotation.from(pitch, quantize(yaw + sign * 0.1F));
            if (posRaycast.canRayCast(feet, pos, adjusted.getPitch(), adjusted.getYaw())) {
                candidate = adjusted;
                yaw = adjusted.getYaw();
            }
        }
        return candidate;
    }

    private static float quantize(float angle) {
        return Math.round(angle * 10.0F) / 10.0F;
    }
}
