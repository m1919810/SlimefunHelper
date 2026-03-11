package me.matl114.hacks.modules.combat;

import com.google.common.base.Preconditions;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.RenderUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class BowTp extends BaseModule {

    public static final String[] TP_ACCELERATE = {"bow-att", "bowtp-distance"};
    public static final String[] TARGETING_DISTANCE = {"bow-att", "bowtp-target-distance"};
    public static final String[] RENDER_TARGET = {"bow-att", "bowtp-render-target"};
    public static final String[] DELTA_Y = {"bow-att", "bowtp-start-delta-y"};
    public static final String[] ENABLE = {"bow-att", "bowtp-enable"};

    public static final String[] ENABLE_HOTKEY = {"bow-att", "bowtp-enable-hotkey"};

    public BowTp() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(Configs.COMBAT_CONFIG, ENABLE).build();
    public KeyBindRef keyBind = toggleHotkey(Configs.COMBAT_CONFIG, ENABLE_HOTKEY, new MultiKeyBind(), ENABLE)
            .build();

    public DoubleRef tpDistance = builder(Configs.COMBAT_CONFIG, TP_ACCELERATE, DoubleRef.TYPE)
            .defaultValue(80.0D)
            .build();

    public DoubleRef targetingDistance = builder(Configs.COMBAT_CONFIG, TARGETING_DISTANCE, DoubleRef.TYPE)
            .defaultValue(80.0D)
            .build();

    public FlagRef render = flagBuilder(Configs.COMBAT_CONFIG, RENDER_TARGET).build();

    public DoubleRef deltaY = builder(Configs.COMBAT_CONFIG, DELTA_Y, DoubleRef.TYPE)
            .defaultValue(0.0D)
            .validator(Configs.doubleRange(-1e-7, 100))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onBowAction, -999);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
    }

    public void onBowAction(Event<PlayerActionC2SPacket> packetEvent) {
        if (packetEvent.isCancelled()) return;
        var actionPacket = packetEvent.context();
        if (actionPacket.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM && enable.get() && canTp()) {
            if (mc.player != null && mc.player.getActiveItem().getItem() instanceof BowItem bowItem) {
                var targetSelector = CombatTasks.getTargetSelector();
                Entity entity = targetSelector.searchAttackEntity(
                        targetingDistance.get(), true, e -> targetSelector.checkWeapon(e, true));
                // rewrite tp system
                if (entity != null) {
                    Debug.chat(Text.literal("[Bow Attack] Aim at %s"
                                    .formatted(entity instanceof PlayerEntity ? "player " : "entity "))
                            .append(EntityUtils.getEntityDisplayable(entity))
                            .formatted(Formatting.GREEN));
                    onBowTpAttack(packetEvent, entity);
                } else {
                    return;
                }
            }
        }
    }

    public void onBowTpAttack(Event<PlayerActionC2SPacket> packetEvent, Entity target) {
        var player = mc.player;
        Deque<MovTasks.MovInfo> movementStack = new ArrayDeque<>();
        Deque<MovTasks.MovInfo> shouldMoveBackStack = new ArrayDeque<>();
        Vec3d currentStartPos = mc.player.getPos();
        float pitch = mc.player.getPitch();
        float yaw = mc.player.getYaw();
        movementStack.addLast(MovTasks.MovInfo.createNoUpdate(mc.player.getPos()));
        shouldMoveBackStack.addFirst(MovTasks.MovInfo.createNoUpdate(mc.player.getPos()));
        // mace hack、
        if (!processExactBowTp(player, target, movementStack, shouldMoveBackStack)) {
            return;
        }
        if (!processBowVClip(player, target, movementStack, shouldMoveBackStack)) {
            return;
        }

        // attacking creative player with mace at same height will cause falldamage calculate(caused by the shit code
        // below: we should resetHeight even if backStack.size() = 1
        //            Vec3d lastlyPos = movementStack.peekLast().vec3d();
        // final pos lies in attack range
        // remove final pos check because already checked
        // start execute
        var iter = movementStack.iterator();
        Preconditions.checkArgument(iter.hasNext());
        Vec3d vec3d1 = iter.next().vec3d();
        MovTasks.MovingContext movingContext = MovTasks.MovingContext.create(vec3d1);
        List<MovTasks.MovInfo> moveInfos = new ArrayList<>();
        iter.forEachRemaining(moveInfos::add);
        shouldMoveBackStack.removeFirst();
        int movingToBundleCnt = moveInfos.size();
        moveInfos.addAll(shouldMoveBackStack);
        //                MovTasks.scheduleFarawayMoveInternal(moveInfos, false, movingContext, false);
        // attack
        List<MovTasks.StepActionBundle> actionBundles =
                MovTasks.createMovingPacketsForMovSequence(movingContext, moveInfos, true, false);
        for (int i = 0; i < movingToBundleCnt; ++i) {
            actionBundles.get(i).run();
        }
        // processDuplicateAttack(player, target, moveInfos, movingContext, maceAttack);
        Listener.sendPacketNoEvents(packetEvent.context());
        for (int i = movingToBundleCnt; i < actionBundles.size(); ++i) {
            if (actionBundles.get(i).success) {
                actionBundles.get(i).run();

            } else {
                List<MovTasks.MovInfo> leftTasks = moveInfos.subList(i, moveInfos.size());
                Tasks.scheduleDelayed(
                        () -> {
                            MovTasks.scheduleFarawayMoveInternal(leftTasks, false, movingContext.resetTick(), true);
                        },
                        1);
                break;
            }
        }
        // force resync position to origin
        mc.player.setPosition(currentStartPos);
        mc.player.setPitch(pitch);
        mc.player.setYaw(yaw);
        // feature
        MovTasks.setupAutoResync();

        // check fall damage
        ClientPlayerAccess.of((ClientPlayerEntity) player).setForceNoFall(true);
        // in case that resync packet cause OnGround falldamage
        player.setOnGround(false);
        packetEvent.cancel();
        // next, can continue
    }

    private boolean processExactBowTp(
            PlayerEntity player,
            Entity target,
            Deque<MovTasks.MovInfo> movementStack,
            Deque<MovTasks.MovInfo> shouldMoveBackStack) {
        // how to manage exact attack and mace hack
        // fixed : can not tp to shulker inside
        // should teleport the player to the pos of target entity
        PositionPredict positionPredict = CombatTasks.getPositionPredict();
        double range = this.targetingDistance.get();
        Vec3d current = player.getPos();
        // feat : teleporting position should met the need of antishield
        Vec3d targetPos = positionPredict.getExactAttackPosition(target);

        if (targetPos != null) {
            // target pos should be higher
            targetPos = targetPos.add(0, getFireArrowPositionHeight(player, target), 0);
            // common atttack?
            if (RenderTasks.DEBUG_RENDER_COMBAT)
                RenderTasks.drawBox(player.dimensions.getBoxAt(targetPos), 150, Color.GREEN);
            List<Vec3d> tpSequence = MovTasks.generateTpSequence(current, targetPos, false, 1.5 * range, true);
            List<Vec3d> tpSequenceBack = MovTasks.generateTpSequence(targetPos, current, false, 1.5 * range, true);
            if ((tpSequence.size() == 2 || tpSequence.size() == 4)
                    && (tpSequenceBack.size() == 2 || tpSequenceBack.size() == 4)) {
                // correct tp sequence
                // try compact mace hack
                if (tpSequence.size() == 2) {
                    // can directly tp
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(1)));

                } else {
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(1)));
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(2)));
                    movementStack.addLast(MovTasks.MovInfo.createNotOnGround(tpSequence.get(3)));
                }
                int size = tpSequenceBack.size();

                for (int i = size - 2; i >= 0; --i) {
                    shouldMoveBackStack.addFirst(MovTasks.MovInfo.create(tpSequenceBack.get(i)));
                }
                return true;
            } else {
                Debug.chat("[Bow Attack] Can not reach the target");
            }
        }
        return false;
    }

    private boolean processBowVClip(
            PlayerEntity player,
            Entity target,
            Deque<MovTasks.MovInfo> movementStack,
            Deque<MovTasks.MovInfo> shouldMoveBackStack) {
        if (canTp()) {
            double maxMace = tpDistance.get();
            player.setOnGround(false);
            Vec3d playerPos = movementStack.peekLast().vec3d();
            double deltaY = target.getY() - playerPos.y;
            // error: down search returns negative value
            double height = MovTasks.searchFirstNoCollisionSpaceYHeight(
                    playerPos.add(0, maxMace, 0), 0, maxMace - 2 - deltaY, false);
            // +height
            double maceHeightMultiplier = maxMace + height;
            // the stack already contains the height.
            double minAvailableHeight = 0.0F;

            if (maceHeightMultiplier - minAvailableHeight > 0.0) {
                Debug.chat(Text.literal("[Bow Attack] Bow Attack Simulation: simulate height %.2f"
                                .formatted(maceHeightMultiplier))
                        .formatted(Formatting.GREEN));
                movementStack.addLast(MovTasks.MovInfo.createNoUpdate(playerPos.add(0, maceHeightMultiplier, 0)));
                // Debug.info("add", playerPos.add(0, maceHeightMultiplier,0));
                // create the movement with pitchYaw update
                movementStack.addLast(new MovTasks.MovInfo(
                        playerPos.add(0, minAvailableHeight, 0), null, false, new Vec2f(89, mc.player.getYaw())));

                return true;
            }
        }
        return false;
    }

    public void onRender(Event<MatrixStack> stackE) {
        var stack = stackE.context;
        if (enable.get() && mc.player != null && render.get()) {
            float tickDelta = (Float) stackE.extraArgs[0];
            if (mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof BowItem bow) {
                // filter bow, but keep shield
                RenderUtils.startDrawVirtual(stack);
                try {
                    var targetSelector = CombatTasks.getTargetSelector();
                    Entity entity = targetSelector.searchAttackEntity(
                            targetingDistance.get(), true, e -> targetSelector.checkWeapon(e, true));
                    if (entity != null) {
                        float dist = entity.distanceTo(mc.player);
                        float opacity = Math.min(0.6F, 0.10F + dist * 0.02F);
                        Box box = RenderUtils.getLerpedBox(entity, tickDelta);
                        RenderUtils.drawSolidBox(
                                stack, box.getMinPos(), box.getMaxPos(), ColorUtils.withAlpha(Color.GREEN, opacity));
                    }
                } finally {
                    RenderUtils.stopDrawVirtual(stack);
                }
            }
        }
    }

    private double getFireArrowPositionHeight(PlayerEntity player, Entity target) {
        // real height is owner.getEyeY() - 0.10000000149011612
        return target.getBoundingBox().getLengthY() - player.getEyeHeight(player.getPose()) + 0.11 + deltaY.get();
    }

    private boolean canTp() {
        return tpDistance.get() > 0.0D;
    }
}
