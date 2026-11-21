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
    private final List<RotationRequest> currentTickActiveRequests = new ArrayList<>();
    private final Set<RotationRequest> currentTickRunningRequests = new HashSet<>();
    private Rotation currentTickRotation = Rotation.EMPTY;
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

    /**
     * INT_MIN 优先级的请求建议通过此方法提交：后续请求会检查与已接纳请求的角度冲突，
     * 因而提交时通过的请求不会彼此冲突。
     */
    @ApiMethod
    public boolean request(RotationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!canRequest(request)) {
            return false;
        }
        currentTickRequests.add(request);
        currentTickRequests.sort(Comparator.comparingInt(RotationRequest::priority));
        return true;
    }

    /**
     * 高于 INT_MIN 的优先级建议通过此方法提交：跳过提交时的冲突检查，
     * 不要求最终命中射线，并在调度时遵循 INT_MIN 请求确定的角度。
     */
    public void push(RotationRequest request) {
        currentTickRequests.add(Objects.requireNonNull(request, "request"));
        currentTickRequests.sort(Comparator.comparingInt(RotationRequest::priority));
    }

    public boolean canRequest(RotationRequest request) {
        Objects.requireNonNull(request, "request");
        List<RotationRequest> relevant = new ArrayList<>();
        for (RotationRequest existing : currentTickRequests) {
            if (existing.priority() <= request.priority()) relevant.add(existing);
        }
        if (relevant.isEmpty()) return true;
        if (request == EMPTY || relevant.contains(EMPTY)) return false;
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;
        Vec3d pos = player.getPos();
        relevant.add(request);
        List<RotationRequest> accepted = new ArrayList<>();
        combineRotations(relevant, pos, player, accepted);
        return !accepted.isEmpty() && accepted.get(accepted.size() - 1) == request;
    }

    private static Rotation mergeRotation(Rotation current, Rotation next) {
        return new Rotation(
                current.hasPitch() ? current.pitch() : next.pitch(), current.hasYaw() ? current.yaw() : next.yaw());
    }

    private static boolean acceptsRotation(
            List<RotationRequest> requests, Vec3d pos, ClientPlayerEntity player, Rotation rotation) {
        for (RotationRequest request : requests) {
            if (request != EMPTY && !request.canUseRotation(pos, player, rotation)) return false;
        }
        return true;
    }

    private static Rotation combineRotations(
            List<RotationRequest> requests, Vec3d pos, ClientPlayerEntity player, List<RotationRequest> accepted) {
        Rotation rotation = Rotation.EMPTY;
        List<RotationRequest> prioritized = new ArrayList<>(requests);
        prioritized.sort(Comparator.comparingInt(RotationRequest::priority));
        for (RotationRequest request : prioritized) {
            Rotation candidate = mergeRotation(rotation, request.peekRotation(rotation, player));
            if (request != EMPTY
                    && (!request.canUseRotation(pos, player, candidate)
                            || !acceptsRotation(accepted, pos, player, candidate))) continue;
            accepted.add(request);
            rotation = candidate;
        }
        return rotation;
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

        Vec3d pos = player.getPos();
        List<RotationRequest> accepted = new ArrayList<>();
        Rotation expected = combineRotations(currentTickRequests, pos, player, accepted);
        currentTickRotation = expected;
        currentTickActiveRequests.clear();
        currentTickActiveRequests.addAll(currentTickRequests);
        currentTickRequests.clear();
        currentTickRunningRequests.clear();
        for (RotationRequest request : accepted) {
            if (request == EMPTY || request.canUseRotation(pos, player, expected))
                currentTickRunningRequests.add(request);
        }
        float previousPitch = player.getPitch();
        float previousYaw = player.getYaw();
        if (expected.hasPitch()) EntityUtils.setEntityPitchSafe(player, expected.getPitch());
        if (expected.hasYaw()) PlayerStateManager.setPlayerYawSafe(player, expected.getYaw());
        boolean pitchChanged = player.getPitch() != previousPitch;
        boolean yawChanged = player.getYaw() != previousYaw;
        manager.pushImportantRotation(expected.hasPitch() || pitchChanged, expected.hasYaw() || yawChanged);
        if (pitchChanged || yawChanged) {

            manager.markForResetRot();
        }
        if (currentTickRunningRequests.stream().anyMatch(RotationRequest::applyMoveFix)) manager.tryMarkForMoveFix();
        for (RotationRequest request : currentTickActiveRequests) {
            request.preTick(currentTickRotation, player, currentTickRunningRequests.contains(request));
        }
    }

    @Override
    public void postModify(Event<LegalMovementManager> event) {
        ClientPlayerEntity player = event.context().playerStatus.entity;
        for (RotationRequest request : currentTickActiveRequests)
            request.postTick(currentTickRotation, player, currentTickRunningRequests.contains(request));
        currentTickActiveRequests.clear();
        currentTickRotation = Rotation.EMPTY;
        currentTickRunningRequests.clear();
        flushQueuePackets();
    }

    private void clearRequests() {
        currentTickRequests.clear();
        currentTickActiveRequests.clear();
        currentTickRunningRequests.clear();
        currentTickRotation = Rotation.EMPTY;
    }

    public interface RotationRequest {
        default int priority() {
            return Integer.MIN_VALUE;
        }

        default Rotation peekRotation(Rotation currentModification, ClientPlayerEntity player) {
            return Rotation.EMPTY;
        }

        default void preTick(Rotation rotation, ClientPlayerEntity player, boolean matchRaycast) {}

        default boolean canUseRotation(Vec3d pos, ClientPlayerEntity player, Rotation rotation) {
            return false;
        }

        default void postTick(Rotation rotation, ClientPlayerEntity player, boolean matchRaycast) {}

        default boolean applyMoveFix() {
            return false;
        }
    }

    public static final RotationRequest EMPTY = new RotationRequest() {};

    @FunctionalInterface
    public interface TickTask {
        void run(Rotation rotation, ClientPlayerEntity player, boolean matchRaycast);
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    public abstract static class RotationRequestImpl<T> implements RotationRequest {
        private TickTask preTick;
        private TickTask postTick;
        private boolean applyMoveFix;
        private int priority = Integer.MIN_VALUE;

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean applyMoveFix() {
            return applyMoveFix;
        }

        @MustBeInvokedByOverriders
        @Override
        public void postTick(Rotation rotation, ClientPlayerEntity player, boolean matchRaycast) {
            if (postTick != null) postTick.run(rotation, player, matchRaycast);
        }

        @MustBeInvokedByOverriders
        @Override
        public void preTick(Rotation rotation, ClientPlayerEntity player, boolean matchRaycast) {
            if (preTick != null) preTick.run(rotation, player, matchRaycast);
        }

        public RotationRequestImpl<T> preTickTask(Runnable task) {
            this.preTick = (rotation, player, matchRaycast) -> task.run();
            return this;
        }

        public RotationRequestImpl<T> postTickTask(Runnable task) {
            this.postTick = (rotation, player, matchRaycast) -> task.run();
            return this;
        }

        @Override
        public Rotation peekRotation(Rotation currentModification, ClientPlayerEntity player) {
            Rotation rotation = calculateRotationPre(player, currentModification);
            return rotation == null ? Rotation.EMPTY : rotation;
        }

        protected abstract @Nullable Rotation calculateRotationPre(
                ClientPlayerEntity player, Rotation currentModification);
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

        @MustBeInvokedByOverriders
        @Override
        protected @Nullable Rotation calculateRotationPre(ClientPlayerEntity player, Rotation currentModification) {
            T target = targetSupplier.get();
            if (target == null) return null;
            Vec3d pos = player.getPos();
            if (!currentModification.hasRotate()) {
                Vec3d direction = calculator.getRaycast(pos, target);
                return direction == null ? null : Rotation.from(direction.normalize());
            }
            float pitch = currentModification.getPitch(player.getPitch());
            float yaw = currentModification.getYaw(player.getYaw());
            if (calculator.canRayCast(pos, target, pitch, yaw)) return Rotation.from(pitch, yaw);
            if (currentModification.hasPitch() && currentModification.hasYaw()) return null;
            float base = currentModification.hasPitch() ? yaw : pitch;
            int steps = currentModification.hasPitch() ? 720 : 360;
            for (int i = 1; i <= steps; i++) {
                float offset = i * 0.25F;
                for (int sign : new int[] {-1, 1}) {
                    float adjusted = base + sign * offset;
                    float candidatePitch = currentModification.hasPitch() ? pitch : adjusted;
                    float candidateYaw = currentModification.hasPitch() ? adjusted : yaw;
                    if (calculator.canRayCast(pos, target, candidatePitch, candidateYaw))
                        return Rotation.from(candidatePitch, candidateYaw);
                }
            }
            return null;
        }

        @Override
        public boolean canUseRotation(Vec3d pos, ClientPlayerEntity player, Rotation rotation) {
            T target = targetSupplier.get();
            return target != null
                    && calculator.canRayCast(
                            pos, target, rotation.getPitch(player.getPitch()), rotation.getYaw(player.getYaw()));
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

    public static LookDirectionRequest ofRotation(Supplier<Rotation> rotationSupplier) {
        return new LookDirectionRequest(player -> rotationSupplier.get());
    }

    public static LookDirectionRequest ofDirection(Rotation rotation) {
        return new LookDirectionRequest(player -> rotation);
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

        private LookDirectionRequest(Function<ClientPlayerEntity, Rotation> rotationSupplier) {
            this.rotationSupplier = rotationSupplier;
        }

        public LookDirectionRequest(Optional<Float> pitch, Optional<Float> yaw) {
            this.rotationSupplier = (pl) -> {
                if (pitch.isEmpty() && yaw.isEmpty()) return null;
                return new Rotation(pitch.orElse(null), yaw.orElse(null));
            };
        }

        @Override
        protected @Nullable Rotation calculateRotationPre(ClientPlayerEntity player, Rotation currentModification) {
            return rotationSupplier == null ? null : rotationSupplier.apply(player);
        }

        @Override
        public boolean canUseRotation(Vec3d pos, ClientPlayerEntity player, Rotation rotation) {
            Rotation desired = calculateRotationPre(player, Rotation.EMPTY);
            if (desired == null) return false;
            return (!desired.hasPitch()
                            || Math.abs(MathHelper.wrapDegrees(
                                            desired.getPitch() - rotation.getPitch(player.getPitch())))
                                    < 0.001F)
                    && (!desired.hasYaw()
                            || Math.abs(MathHelper.wrapDegrees(desired.getYaw() - rotation.getYaw(player.getYaw())))
                                    < 0.001F);
        }
    }

    public static BoxRaycastRequest<BlockPos> ofBlock(Supplier<BlockPos> posSupplier) {
        return new BoxRaycastRequest<>(InteractExtra.INSTANCE, posSupplier) {
            @Override
            protected @Nullable Rotation calculateRotationPre(ClientPlayerEntity player, Rotation currentModification) {
                Vec3d pos = player.getPos();
                Rotation rotation = super.calculateRotationPre(player, currentModification);
                if (rotation == null) return null;
                if (currentModification.hasRotate()) return rotation;
                return fluctuateBlockRaycast(posSupplier.get(), pos, rotation, InteractExtra.INSTANCE);
            }

            @Override
            public void preTick(Rotation rotation, ClientPlayerEntity player, boolean matchRaycast) {
                super.preTick(rotation, player, matchRaycast);
                if (matchRaycast) lastAdd = !lastAdd;
            }
        };
    }

    public static BoxRaycastRequest<Entity> ofEntity(Supplier<Entity> entitySupplier) {
        return new BoxRaycastRequest<>(TargetSelector.INSTANCE, entitySupplier);
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
