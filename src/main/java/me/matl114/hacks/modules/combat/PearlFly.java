package me.matl114.hacks.modules.combat;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.entity.EntityMovementStatus;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class PearlFly extends BaseModule {
    public PearlFly() {}

    public final ModulePath combatUtils = makePath(Configs.COMBAT_CONFIG, "combat-utils");
    public final ModulePath pearl = combatUtils.add("pearl-fly");
    public final FlagRef enable = flagBuilder(pearl.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(pearl.addHotkey(), new MultiKeyBind(), pearl.addEnable())
            .build();

    public final FlagRef enableCrawl = flagBuilder(pearl.add("enable-crawl")).build();

    public final FlagRef enableStand = flagBuilder(pearl.add("enable-stand")).build();

    public final FlagRef autoCrawl = flagBuilder(pearl.add("auto-crawl")).build();

    public final FlagRef offhand = flagBuilder(pearl.add("offhand")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInputEvent);
    }

    public void onInputEvent(Event<Void> event) {
        if (checkNull()) return;
        if (enable.get()) {
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
            if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))) {
                return;
            }
            Direction direction = mc.player.getHorizontalFacing();
            Vec3d pos = mc.player.getBlockPos().toCenterPos();
            Vec3d ppos = mc.player.getPos();
            if (MathUtils.isInXZRange(pos, ppos, 0.19)) {
                return;
            }
            Direction search = direction;

            do {
                Vec3d searchPos = pos.offset(search, 1);
                if (MathUtils.isInXZRange(ppos, searchPos, 0.5 + 0.31)) {
                    BlockState state =
                            mc.world.getBlockState(mc.player.getBlockPos().offset(search));
                    if (!state.isAir() && !state.isLiquid()) {
                        if (doPearlUse(search, mc.player.getBlockPos().offset(search))) {
                            enable.set(false);
                            return;
                        }
                    }
                    if (autoCrawl.get() && pose != EntityPose.SWIMMING) {
                        BlockState state2 = mc.world.getBlockState(
                                mc.player.getBlockPos().offset(search).offset(Direction.UP));
                        if (!state2.isAir() && !state2.isLiquid()) {
                            if (doPearlUse(search, mc.player.getBlockPos().offset(search))) {
                                enable.set(false);
                                return;
                            }
                        }
                    }
                }

                search = search.rotateYClockwise();
            } while (search != direction);
        }
    }

    public boolean doPearlUse(Direction direction, BlockPos pos) {
        EntityPose pose = mc.player.getPose();
        Vec3d look;
        if (pose == EntityPose.SWIMMING) {
            look = pos.toCenterPos().subtract(mc.player.getEyePos());
        } else {
            look = pos.toBottomCenterPos().offset(direction.getOpposite(), 0.5).subtract(mc.player.getEyePos());
        }
        return usePearl(look);
    }

    public boolean usePearl(Vec3d look) {
        var re = InventoryUtils.findPlayerItem((ss) -> ss.getItem() == Items.ENDER_PEARL, true, false);
        if (re == null) {
            Debug.chat(ChatUtils.stringToText("&c[Pearl] &f没有珍珠了"));
            return true;
        }

        boolean offHand = offhand.get() || re.index() == 40;
        Runnable runnable = offHand
                ? InvExtra.INSTANCE.swapInventoryIndexToOffhand(re.index())
                : InvExtra.INSTANCE.swapInventoryIndexToHand(re.index());
        if (runnable == null) return false;
        var status = new EntityMovementStatus<>(mc.player);

        EntityUtils.setEntityRotationSafe(mc.player, look);
        Hand hand = offHand ? Hand.OFF_HAND : Hand.MAIN_HAND;
        mc.interactionManager.interactItem(mc.player, hand);
        status.restoreRotation();
        runnable.run();
        return true;
    }
}
