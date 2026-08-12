package me.matl114.hacks.modules.combat;

import java.util.*;
import java.util.function.Predicate;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.mine.MineExtra;
import me.matl114.hacks.modules.mine.PacketMine;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.MathUtils;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.*;

public class AutoCity extends BaseModule {
    public static AutoCity INSTANCE;
    public final ModulePath combatUtils = makePath(Configs.COMBAT_CONFIG, "combat-utils");
    public final ModulePath autoCity = combatUtils.add("auto-city");

    public AutoCity() {
        super("AutoCity");
        INSTANCE = this;
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(autoCity.add("enable")).build();

    public final KeyBindRef hotkey = moduleEntry(autoCity.add("hotkey"), new MultiKeyBind(), autoCity.add("enable"))
            .build();

    // 是否优先考虑目标脚下/卡脚位置的黑曜石。
    public final FlagRef burrow = builder(autoCity.add("burrow-first"), Boolean.class)
            .defaultValue(true)
            .build();

    // 是否考虑目标脸侧可直接打开缺口的位置。
    public final FlagRef head = flagBuilder(autoCity.add("head-target")).build();

    // 是否允许向下扩展搜索目标下方可挖位置。
    public final FlagRef down = flagBuilder(autoCity.add("down")).build();

    // 是否启用周围一圈的 surround 位置搜索。
    public final FlagRef surround =
            builder(autoCity.add("surround"), Boolean.class).defaultValue(true).build();

    public final FlagRef doubleMineFace =
            flagBuilder(autoCity.add("double-mine-face")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInputEvent);
        registerListener(PacketMine.getPrePacketMine(), this::onPrePacketMine);
    }

    PlayerEntity targetEntity;
    BlockPos targetPos;
    boolean pendingSwitchPos;

    public void refreshTarget() {
        double range = InteractExtra.INSTANCE.getBlockReachDistance() + 2;
        if (!EntityUtils.isEntityValid(targetEntity)
                || targetEntity.getBoundingBox().squaredMagnitude(mc.player.getEyePos()) > MathUtils.s2(range)) {
            targetEntity = null;
            targetPos = null;
        }
        if (targetEntity == null) {
            Entity en =
                    CombatTasks.getTargetSelector().searchAttackEntity(range, true, (pl) -> pl instanceof PlayerEntity);
            if (en instanceof PlayerEntity pl && pl != mc.player) {
                targetEntity = pl;
            }
        }
    }

    public void onInputEvent(Event<Void> event) {
        if (enable.get()) {
            pendingSwitchPos = false;
            refreshTarget();
            if (targetEntity != null) {
                // consider cooldown

                onMine();
            }
        }
    }

    private void onPrePacketMine(Event<PacketMine.Pre> event) {
        if (enable.get() && !event.isCancelled() && pendingSwitchPos) {
            event.cancel();
        }
    }

    private void onMine() {
        Box box = targetEntity.getBoundingBox();
        Set<BlockPos> outerPoses = new LinkedHashSet<>();
        Set<BlockPos> selfPoses = new LinkedHashSet<>();
        Vec3d pos = mc.player.getEyePos();
        double range = InteractExtra.INSTANCE.getBlockReachDistance();
        Predicate<BlockPos> filter = (np) -> MathUtils.getBlockBox(np).squaredMagnitude(pos) <= MathUtils.s2(range);
        selfPoses.addAll(MathUtils.getOccupiedBlockPositions(box).stream()
                .sorted(Comparator.comparingInt(Vec3i::getY))
                .toList());

        Comparator<BlockPos> blockPosComparator =
                Comparator.comparingDouble(v -> MathUtils.getBlockBox(v).squaredMagnitude(pos));
        Box heightTest = box;
        if (head.get()) {
            heightTest = box.stretch(0, 0.75, 0);
            MathUtils.getOccupiedBlockPositions(heightTest).stream()
                    .filter(filter)
                    .sorted(blockPosComparator)
                    .forEach(outerPoses::add);
        }
        if (down.get()) {
            heightTest = box.stretch(0, -0.75, 0);
            MathUtils.getOccupiedBlockPositions(heightTest).stream()
                    .filter(filter)
                    .sorted(blockPosComparator)
                    .forEach(outerPoses::add);
        }
        if (surround.get()) {
            Set<BlockPos> surround = new HashSet<>();
            surround.addAll(
                    MathUtils.getOccupiedBlockPositions(box.expand(0.99, 0, 0).withMaxY(box.minY + 0.5)));
            surround.addAll(
                    MathUtils.getOccupiedBlockPositions(box.expand(0, 0, 0.99).withMaxY(box.minY + 0.5)));
            surround.stream().filter(filter).sorted(blockPosComparator).forEach(outerPoses::add);
        }

        outerPoses.removeAll(selfPoses);
        PlayerInteractionAccess access = PlayerInteractionAccess.of(mc.interactionManager);
        BlockPos currentPos = access.getCurrentMiningPos();
        BlockPos nowCurrentFailMinePos = access.getCurrentFailBreakPos();
        // not mining
        boolean working = false;
        boolean switchPosition;
        if (burrow.get()) {
            if (MovTasks.isCollidingWithEnvironment(targetEntity)) {
                switchPosition = (!selfPoses.contains(currentPos)
                                && (nowCurrentFailMinePos == null || !selfPoses.contains(nowCurrentFailMinePos)))
                        || !outerPoses.contains(currentPos);
            } else {
                switchPosition = !outerPoses.contains(currentPos);
            }
        } else {
            switchPosition = !outerPoses.contains(currentPos);
        }
        if (switchPosition) {
            if (MineExtra.INSTANCE.isVanillaMineCooldownComplete(1)) {
                pendingSwitchPos = false;
                Set<BlockPos> surroundPos = new HashSet<>();
                if (InteractionTasks.getAutoSurround().enable.get()) {
                    surroundPos.addAll(InteractionTasks.getAutoSurround().getTargetingPos());
                }
                outerPoses.removeAll(surroundPos);
                List<BlockPos> selfPosList = selfPoses.stream().toList();
                List<BlockPos> outerPosList = outerPoses.stream().toList();
                BlockPos currentMinePos = null;
                BlockPos currentFailMinePos = null;
                boolean canFailMine = access.isFailBreakEmpty();
                find_mine_schedule:
                {
                    // process selfPos first
                    for (var bp : selfPosList) {
                        BlockState bs = mc.world.getBlockState(bp);
                        if (!bs.isAir() && !bs.isLiquid() && PacketMine.INSTANCE.isMineable(bs)) {
                            if (Objects.equals(bp, nowCurrentFailMinePos)) {
                                continue;
                            }
                            if (currentFailMinePos == null && canFailMine && doubleMineFace.get()) {
                                currentFailMinePos = bp;
                                continue;
                            }
                            currentMinePos = bp;
                            break find_mine_schedule;
                        }
                    }
                    for (var bp : outerPosList) {
                        BlockState bs = mc.world.getBlockState(bp);
                        if (!bs.isAir() && !bs.isLiquid() && PacketMine.INSTANCE.isMineable(bs)) {
                            currentMinePos = bp;
                            break find_mine_schedule;
                        }
                    }
                }
                if (currentFailMinePos != null) {
                    // abort current
                    if (currentMinePos != null) {
                        if (!Objects.equals(currentPos, currentFailMinePos)) {
                            access.sendStartBreakPacket(currentFailMinePos);
                        }
                        access.sendFailBreakCurrentPos(null);
                    } else {
                        currentMinePos = currentFailMinePos;
                        currentFailMinePos = null;
                    }
                }
                if (currentMinePos != null) {
                    working = true;
                    if (!Objects.equals(currentPos, currentMinePos)) {
                        access.sendStartBreakPacket(currentMinePos);
                        // to avoid instant break not changing mining pos
                        if (Objects.equals(access.getCurrentMiningPos(), currentMinePos)) {
                            access.sendAbortBreakPacket();
                        }
                    }
                }
            } else {
                pendingSwitchPos = true;
            }
        } else {
            pendingSwitchPos = false;
            working = true;
        }
        if (working) {
            PacketMine.INSTANCE.tickMine();
        }
    }
}
