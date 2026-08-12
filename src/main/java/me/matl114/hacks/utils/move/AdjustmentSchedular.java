package me.matl114.hacks.utils.move;

import java.util.List;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.events.Event;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.survival.SchedularSettings;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

@Accessors(fluent = true, chain = true)
public class AdjustmentSchedular {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final double EDGE_STEP = 1E-2;
    private static final double LINE_LENGTH = 0.8;

    @Setter
    @Getter
    double extraRange = 0;

    @Setter
    @Getter
    Supplier<Vec3d> center;

    @Setter
    @Getter
    boolean opposite = false;

    @Setter
    @Getter
    double adjustRange = 1.5F;

    @Setter
    @Getter
    double availableRange = 0.05;

    ClientPlayerEntity player;
    RenderCollector<List<Vec3d>> renderCollector = RenderCollectors.createLinesCollector();

    public AdjustmentSchedular() {}

    public void tickAdjustment(ClientPlayerEntity player) {
        renderCollector.clear();
        this.player = player;
        if (player == null || center == null) {
            return;
        }

        if (PathingSchedular.isCurrentPathing()) {
            return;
        }

        Vec3d target = center.get();
        if (target == null) {
            return;
        }

        Vec3d playerCenter = player.getPos();
        renderCollector.submit(
                List.of(player.getPos(), target),
                SchedularSettings.INSTANCE.colorLines.get().withAlpha(255));
        if (MathUtils.isInBox(playerCenter, target, availableRange)) {

            return;
        }

        double range = adjustRange + extraRange;
        if (!MathUtils.isInBox(playerCenter.subtract(target), range)) {
            return;
        }

        Vec3d direction = opposite ? playerCenter.subtract(target) : target.subtract(playerCenter);
        Vec3d flatDirection = new Vec3d(direction.x, 0, direction.z);
        if (flatDirection.lengthSquared() < 1.0E-8) {
            return;
        }

        int collisionCount = countAdjacentXZCollisions(player, flatDirection);
        PlayerInputManager.Modifier modifier;
        Vec3d renderDirection;
        if (collisionCount >= 2) {
            modifier = null;
            renderDirection = Vec3d.ZERO;
        } else if (collisionCount == 1) {
            Vec2f pitchYaw = EntityUtils.rotationToPitchYaw(target.subtract(player.getPos()));
            renderDirection = flatDirection.normalize();
            modifier = PlayerInputManager.Modifier.empty(0)
                    .forward(true)
                    .backward(false)
                    .left(false)
                    .right(false)
                    .pitch(pitchYaw.x)
                    .yaw(pitchYaw.y);
        } else {
            renderDirection = flatDirection.normalize();
            modifier = buildWasdModifier(player, renderDirection);
        }
        if (modifier != null) {
            PlayerInputManager.INSTANCE.addInputModifier(modifier, 1);
        }
    }

    private int countAdjacentXZCollisions(ClientPlayerEntity player, Vec3d direction) {
        Vec3d step = direction.normalize().multiply(EDGE_STEP);
        Box movedBox = player.getBoundingBox().offset(step);
        BlockPos basePos = player.getBlockPos();
        int minY = (int) Math.floor(movedBox.minY);
        int maxY = (int) Math.ceil(movedBox.maxY) - 1;
        int collisions = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                boolean collided = false;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(basePos.getX() + dx, y, basePos.getZ() + dz);
                    VoxelShape shape = mc.world.getBlockState(pos).getCollisionShape(mc.world, pos);
                    if (shape.isEmpty()) {
                        continue;
                    }

                    for (Box box : shape.getBoundingBoxes()) {
                        if (movedBox.intersects(box.offset(pos))) {
                            collided = true;
                            break;
                        }
                    }
                    if (collided) {
                        break;
                    }
                }

                if (collided) {
                    collisions++;
                    if (collisions >= 2) {
                        return collisions;
                    }
                }
            }
        }
        return collisions;
    }

    private PlayerInputManager.Modifier buildWasdModifier(ClientPlayerEntity player, Vec3d direction) {
        double yawRad = Math.toRadians(player.getYaw());
        double forwardAmount = -direction.x * Math.sin(yawRad) + direction.z * Math.cos(yawRad);
        double sidewaysAmount = direction.x * Math.cos(yawRad) + direction.z * Math.sin(yawRad);

        PlayerInputManager.Modifier modifier = PlayerInputManager.Modifier.empty(0)
                .forward(forwardAmount > 1.0E-3)
                .backward(forwardAmount < -1.0E-3)
                .left(sidewaysAmount > 1.0E-3)
                .right(sidewaysAmount < -1.0E-3);

        if (!modifier.forward() && !modifier.backward() && !modifier.left() && !modifier.right()) {
            modifier = modifier.forward(true);
        }
        return modifier;
    }

    public void renderAdjustment(Event<MatrixStack> event) {
        if (!SchedularSettings.INSTANCE.enableRender.get()) {
            return;
        }
        RenderUtils.startDrawVirtual(event.context);
        try {
            renderCollector.render3D(event.context);
        } finally {
            RenderUtils.stopDrawVirtual(event.context);
        }
    }
}
