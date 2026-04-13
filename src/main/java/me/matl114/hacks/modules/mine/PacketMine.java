package me.matl114.hacks.modules.mine;

import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.*;
import me.matl114.managers.config.*;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.MathUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class PacketMine extends BaseModule {
    public static final String[] MINE_ONEBLOCK_PACKET_MULTIPLE = {"mine-oneblock", "multiple-packets"};
    public static final String[] MINE_ONEBLOCK_PACKET_LAZY = {"mine-oneblock", "lazy-mode"};
    public static final String[] MINE_ONEBLOCK_PACKET_PICKAXE = {"mine-oneblock", "auto-pickaxe"};
    public static final String[] MINE_ONEBLOCK = {"mine-oneblock", "mine-oneblock"};
    public static final String[] MINE_ONEBLOCK_HOTKEY = {"mine-oneblock", "mine-oneblock-hotkey"};

    public PacketMine() {
        bindFlag(autoEnable);
    }

    public final FlagRef autoEnable =
            flagBuilder(Configs.MINE_CONFIG, MINE_ONEBLOCK).build();

    public final KeyBindRef hotkey = toggleHotkey(
                    Configs.MINE_CONFIG,
                    MINE_ONEBLOCK_HOTKEY,
                    new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_O),
                    MINE_ONEBLOCK)
            .build();

    public final IntRef multiplePackets = builder(Configs.MINE_CONFIG, Integer.class)
            .path(MINE_ONEBLOCK_PACKET_MULTIPLE)
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef considerAirState = flagBuilder(
                    Configs.MINE_CONFIG, makePath("mine-oneblock.consider-air-state"))
            .build();

    public final DoubleRef mineThreshold = builder(
                    Configs.MINE_CONFIG, makePath("mine-oneblock.mine-threshold"), DoubleRef.TYPE)
            .defaultValue(0.7)
            .validator(Configs.doubleRange(-0.0001F, 1.0001F))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
    }

    public void onTick(Event<ClientPlayerEntity> tickEvent) {
        if (isActive()) {
            if (mc.interactionManager != null && mc.player != null) {
                BlockPos pos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
                // todo: add predicted speed
                if (pos != null) {
                    double lenSq = new Box(pos).squaredMagnitude(mc.player.getEyePos());
                    if (lenSq <= MathUtils.s2(mc.player.getBlockInteractionRange() + 1)) {
                        for (int i = 0; i < multiplePackets.get(); ++i) {
                            BlockState blockState = mc.world.getBlockState(pos);
                            if (canMine(blockState)) {
                                Vec3d shouldFacing = pos.toCenterPos().subtract(mc.player.getEyePos());
                                Direction dir =
                                        Direction.getFacing(shouldFacing).getOpposite();
                                if (considerAirState.get()
                                        && PlayerInteractionAccess.of(mc.interactionManager)
                                                        .getCurrentMiningProgress(true)
                                                > 0.98F) {
                                    mc.interactionManager.breakBlock(pos);
                                    // mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
                                }
                                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                                PlayerInteractionAccess.of(mc.interactionManager)
                                        .sendStopBreakPacket(pos, dir);
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean canMine(BlockState state) {
        // do not mine liquid, that's a disaster
        // do not mine air, shit
        if (state.getBlock().getHardness() >= 0.0F && !state.isLiquid() && !state.isAir()) {
            if (mineThreshold.get() > 0) {
                var access = PlayerInteractionAccess.of(mc.interactionManager);
                return access.getCurrentMiningProgress(true) > Math.min(0.98, mineThreshold.get());
            }
            return true;
        } else {
            return false;
        }
    }
}
