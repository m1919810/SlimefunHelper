package me.matl114.hacks.modules.combat;

import java.awt.*;
import java.util.List;
import java.util.Objects;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.render.ProjectileESP;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

public class PearlFly extends BaseModule {
    public PearlFly() {
        super("PearlFly");
    }

    public final ModulePath combatUtils = makePath(Configs.COMBAT_CONFIG, "combat-utils");
    public final ModulePath pearl = combatUtils.add("pearl-fly");

    public final ModulePath pearlPhase = combatUtils.add("pearl-phase");
    public final FlagRef enablePearlPhase = flagBuilder(pearlPhase.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(pearlPhase.addHotkey(), new MultiKeyBind(), pearlPhase.addEnable())
            .build();

    public final FlagRef enableCrawl =
            flagBuilder(pearlPhase.add("enable-crawl")).build();

    public final FlagRef enableStand =
            flagBuilder(pearlPhase.add("enable-stand")).build();

    public final FlagRef autoCrawl = flagBuilder(pearlPhase.add("auto-crawl")).build();

    public final DoubleRef autoPearlActivateRange = doubleBuilder(pearlPhase.add("auto-activate-range"))
            .defaultValue(0.35)
            .build();

    public final FlagRef useWASDControl =
            flagBuilder(pearlPhase.add("use-wasd-control")).build();

    public final FlagRef enableJump = builder(pearlPhase.add("enable-jump-up"), Boolean.class)
            .defaultValue(true)
            .build();

    public final ModulePath pearlThrow = combatUtils.add("pearl-throw");

    public final KeyBindRef executePearlThrow = hotkey(pearlThrow.add("execute-throw"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::doPearlThrow))
            .build();

    public final FlagRef pearlAimRender =
            flagBuilder(pearlThrow.add("pearl-trace-render")).build();

    public final KeyBindRef executePearlHelper = toggleHotkey(
                    pearlThrow.add("pearl-trace-render-hotkey"),
                    new MultiKeyBind(),
                    pearlThrow.add("pearl-trace-render"))
            .build();

    public final NBTRef<WrapColor> color = builder(pearlThrow.add("color"), WrapColor.class)
            .defaultValue(new WrapColor((Color.ORANGE)))
            .build();

    public final FlagRef exactDirection = flagBuilder(pearlThrow.add("exact")).build();

    public final EnumRef<GhostHandMode> ghostHand = builder(pearl.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final FlagRef offhand = flagBuilder(pearl.add("offhand")).build();

    public final FlagRef swingHand =
            builder(pearl.add("swing-hand"), Boolean.class).defaultValue(true).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInputEvent);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
    }

    Vec2f pearlThrowRotation = null;

    public void onInputEvent(Event<Void> event) {
        if (checkNull()) return;
        if (enablePearlPhase.get()) {
            var pose = mc.player.getPose();
            if (pose == EntityPose.SWIMMING) {
                if (!enableCrawl.get()) {
                    return;
                }
            } else {
                if (!enableStand.get()) {
                    return;
                }
            }
            if (mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL)) {
                return;
            }
            Direction direction = mc.player.getHorizontalFacing();
            Vec3d pos = mc.player.getBlockPos().toCenterPos();
            Vec3d ppos = mc.player.getPos();
            BlockPos pbpos = mc.player.getBlockPos();

            BlockPos searchPos;
            if (useWASDControl.get()) {
                var input = PlayerInputUtils.of(mc.options);
                Direction right = direction.rotateYCounterclockwise();
                searchPos = pbpos.add(direction.getVector().multiply(input.forwardSpeed()))
                        .add(right.getVector().multiply(input.sidewaysSpeed()));
                if (enableJump.get() && input.upwardSpeed() > 0) {
                    searchPos = searchPos.offset(Direction.UP, input.upwardSpeed());
                }
                if (!Objects.equals(pbpos, searchPos) && doPearlUse(pbpos, searchPos)) {
                    enablePearlPhase.set(false);
                    return;
                }
            } else {
                if (MathUtils.isInXZRange(pos, ppos, 0.5 - autoPearlActivateRange.get())) {
                    return;
                }
                Direction search = direction;
                Direction result = direction;
                double min = Double.MAX_VALUE;
                do {
                    BlockPos test = pbpos.offset(search);
                    BlockState state = mc.world.getBlockState(test);

                    if (MathUtils.isInXZRange(ppos, test.toCenterPos(), 0.5 + autoPearlActivateRange.get())) {
                        double sqd = test.getSquaredDistance(ppos);
                        if (sqd < min) {
                            result = search;
                            min = sqd;
                        }
                    }

                    search = search.rotateYClockwise();
                } while (search != direction);
                if (min == Double.MAX_VALUE) {
                    return;
                }
                search = result;
                searchPos = pbpos.offset(search, 1);

                BlockState state = mc.world.getBlockState(searchPos);
                if (!state.isAir() && !state.isLiquid()) {
                    if (doPearlUse(pbpos, searchPos)) {
                        enablePearlPhase.set(false);
                        return;
                    }
                }
                if (autoCrawl.get() && pose != EntityPose.SWIMMING) {
                    BlockState state2 = mc.world.getBlockState(searchPos.offset(Direction.UP));
                    if (!state2.isAir() && !state2.isLiquid()) {
                        if (doPearlUse(pbpos, searchPos)) {
                            enablePearlPhase.set(false);
                            return;
                        }
                    }
                }
            }
        }
        if (pearlThrowRotation != null && !mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL)) {
            usePearl(EntityUtils.pitchYawToRotation(pearlThrowRotation.x, pearlThrowRotation.y));
            pearlThrowRotation = null;
        }
        lineCollector.clear();
        hitBoxCollector.clear();
        if (pearlAimRender.get()) {
            Vec2f direction;
            Vec3d eye = mc.player.getEyePos();
            if (exactDirection.get()) {
                Vec3d rayCastStart = RenderUtils.getCameraPos();
                Vec3d look = RenderUtils.getCameraLookVec(0.0F);
                var hit = RaycastUtils.raycastOnlyBlockCollisions(rayCastStart, rayCastStart.add(look.multiply(128)));
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    Vec3d hitPoint = hit.getPos();
                    var red = CombatTasks.calculatePitchYawPredict(1.48F, Vec3d.ZERO, hitPoint.subtract(eye));
                    if (Float.isNaN(red.x)
                            || Float.isInfinite(red.x)
                            || Float.isNaN(red.y)
                            || Float.isInfinite(red.y)) {
                        return;
                    } else {
                        direction = red;
                    }
                } else {
                    return;
                }
            } else {
                direction = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
            }
            Vec3d rot = EntityUtils.pitchYawToRotation(direction.x, direction.y);
            var predictor = new ProjectileESP.ArrowPredictor(
                    eye.subtract(0, 0.11, 0), rot.normalize().multiply(1.48F), mc.player);
            var result = predictor.predictLineWithHitResult(400);
            if (result != null) {
                lineCollector.submit(result.getFirst(), color.get().withAlpha(255));
                HitResult hitPose = result.getSecond();
                if (hitPose != null) {
                    var hitPos = hitPose.getPos();
                    hitBoxCollector.submit(
                            new Box(hitPos.add(RenderTasks.SMALL_FROM), hitPos.add(RenderTasks.SMALL_TO)),
                            color.get().withAlpha(64));
                }
            }
        }
    }

    RenderCollector<List<Vec3d>> lineCollector = RenderCollectors.createLinesCollector();
    RenderCollector<Box> hitBoxCollector = RenderCollectors.createBoxCollector(true, true, false);

    public void doPearlThrow() {
        if (checkNull()) return;
        if (exactDirection.get()) {
            Vec3d rayCastStart = RenderUtils.getCameraPos();
            Vec3d look = RenderUtils.getCameraLookVec(0.0F);
            var hit = RaycastUtils.raycastOnlyBlockCollisions(rayCastStart, rayCastStart.add(look.multiply(128)));
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                Vec3d hitPoint = hit.getPos();
                var red = CombatTasks.calculatePitchYawPredict(
                        1.48F, Vec3d.ZERO, hitPoint.subtract(mc.player.getEyePos()));
                if (Float.isNaN(red.x) || Float.isInfinite(red.x) || Float.isNaN(red.y) || Float.isInfinite(red.y)) {
                    logI18NSub("Pearl", "message.module.pearl-fly.can-not-reach-target");
                } else {
                    pearlThrowRotation = red;
                }
            } else {
                logI18NSub("Pearl", "message.module.pearl-fly.no-target");
            }
        } else {
            pearlThrowRotation = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        }
    }

    public boolean doPearlUse(BlockPos originPos, BlockPos pos) {
        EntityPose pose = mc.player.getPose();
        Vec3d look;
        if (pose == EntityPose.SWIMMING) {
            look = pos.toCenterPos().subtract(mc.player.getEyePos());
        } else {
            look = pos.toCenterPos().add(originPos.toCenterPos()).multiply(0.5).subtract(mc.player.getEyePos());
        }
        return usePearl(look);
    }

    public boolean usePearl(Vec3d look) {
        look = look.normalize();
        var re = InventoryUtils.findPlayerItem((ss) -> ss.getItem() == Items.ENDER_PEARL, true, false);
        if (re == null) {
            logI18NSub("Pearl", "message.module.pearl-fly.no-pearl");
            return true;
        }

        boolean offHand = offhand.get() || re.index() == 40;
        Runnable runnable = InvExtra.INSTANCE.swapItemToHand(re.index(), offHand, ghostHand.get());
        if (runnable == null) return false;
        InteractionTasks.interactItem(offHand ? Hand.OFF_HAND : Hand.MAIN_HAND, look, true, swingHand.get());
        runnable.run();
        return true;
    }

    public void onRender3D(Event<Render3D> event) {
        if (checkNull())
            ;
        if (pearlAimRender.get()) {
            RenderUtils.startDrawVirtual(event.context.stack());
            try {
                lineCollector.render3D(event.context.stack());
                hitBoxCollector.render3D(event.context.stack());
            } finally {
                RenderUtils.stopDrawVirtual(event.context.stack());
            }
        }
    }
}
