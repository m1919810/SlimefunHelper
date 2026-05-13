package me.matl114.hacks.modules.interact;

import java.awt.*;
import java.util.Locale;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.RenderListener;
import me.matl114.hacks.ACTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.MathUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

public class Airplace extends BaseModule {
    public static final String[] AIR_PLACE = {"interaction-tweaks", "air-place", "enable"};

    public static final String[] HOTKEY = {"interaction-tweaks", "air-place", "hotkey"};
    public static final String[] AIR_PLACE_RANGE = {"interaction-tweaks", "air-place", "range"};

    public static final String[] AIR_PLACE_RENDER = {"interaction-tweaks", "air-place", "render"};

    public static final String[] AIR_WALL = {"interaction-tweaks", "air-place", "mode"};

    public Airplace() {}

    public final FlagRef enable =
            flagBuilder(Configs.INTERACT_CONFIG, AIR_PLACE).build();

    public final KeyBindRef hotkey = toggleHotkey(Configs.INTERACT_CONFIG, HOTKEY, new MultiKeyBind(), AIR_PLACE)
            .build();

    public final DoubleRef range = builder(Configs.INTERACT_CONFIG, AIR_PLACE_RANGE, DoubleRef.TYPE)
            .defaultValue(5.0D)
            .validator(Configs.doubleRange(0, 10000))
            .build();

    public final FlagRef render =
            flagBuilder(Configs.INTERACT_CONFIG, AIR_PLACE_RENDER).build();
    // todo: add to switch mode
    public final EnumRef<AirPlaceMode> enableAirWall = builder(Configs.INTERACT_CONFIG, AIR_WALL, AirPlaceMode.class)
            .defaultValue(AirPlaceMode.VANILLA)
            .updateListener(s -> {
                onSwitch();
            })
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getItemUseAction(), this::onInteract);
        registerListener(Listener.getPreHandleInputEvents(), this::onInput);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRenderPos);
        registerListener(PacketManager.getPacketQueueEvent().getPacketReceiveChannel(), this::onPacketAcceptQueue);
        registerListener(Listener.getPostTick(), this::onPostTick);
        registerListener(PacketManager.getQueueShutdownEvent(), this::onShutdownQueue);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        clearCurrentAirWall();
    }

    public void onInteract(Event<HitResult> event) {
        if (!event.isCancelled() && enable.get()) {
            Hand hand = event.getArgs(0);
            ItemStack stack = mc.player.getStackInHand(hand);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                HitResult hitResult = event.context();
                if (hitResult.getType() == HitResult.Type.MISS) {
                    HitResult result = getCameraEntity().raycast(range.get(), 0, false);
                    if (result.getType() == HitResult.Type.MISS && result instanceof BlockHitResult block) {
                        switch (enableAirWall.get()) {
                            case VANILLA -> {
                                BlockHitResult newResult = new BlockHitResult(
                                        block.getPos(),
                                        block.getSide(),
                                        block.getBlockPos(),
                                        block.isInsideBlock(),
                                        block.isAgainstWorldBorder());
                                event.context(newResult);
                                return;
                            }
                            case GRIM_GHOST_BLOCK_WALL -> {
                                onGrimAirWall(block);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public void onShutdownQueue(Event<Void> event) {
        clearCurrentAirWall();
    }

    public void onSwitch() {
        clearCurrentAirWall();
    }

    public void clearCurrentAirWall() {
        targetPos = null;
        lastDelayTick = 0;
    }

    public void onGrimAirWall(BlockHitResult hitResult) {
        clearCurrentAirWall();
        if (ACTasks.getDisablerManager().isGrimSelfCheckDisabled()) {
            targetPos = hitResult.getBlockPos();
        } else {
            Debug.chat("[AirWall] 当前暂未禁用GrimSelfCheck,无法执行");
        }
    }

    BlockPos targetPos = null;
    int lastDelayTick = 0;

    public void flush() {
        lastDelayTick--;
        if (targetPos == null && lastDelayTick == 0) {
            PacketManager.flushInBound();
        } else {
            if (lastDelayTick > 3) lastDelayTick = 3;
            PacketManager.flushInBound((packetStorage -> {
                long timeMS = packetStorage.timestampMS();
                long currentMs = System.currentTimeMillis();
                if (currentMs > timeMS + 50L) {
                    return PacketManager.FlushAction.FLUSH;
                }
                return PacketManager.FlushAction.QUEUE;
            }));
        }
    }

    public void onPacketAcceptQueue(Event<Packet<?>> packet) {
        if (enable.get() && targetPos != null) {
            var pkt = packet.context;

            if (PacketManager.isAsyncOrNotTransactionS2CPacket(pkt)) {
                return;
            }
            lastDelayTick += 1;
            packet.cancel();
        }
    }

    public void onPostTick(Event<Void> event) {
        if ((lastDelayTick > 0)) {
            flush();
        }
    }

    public void onInput(Event<Void> event) {
        if (enable.get()
                && targetPos != null
                && mc.player.getStackInHand(Hand.MAIN_HAND).getItem() instanceof BlockItem block
                && block != Items.AIR
                && targetPos.toCenterPos().subtract(mc.player.getEyePos()).horizontalLengthSquared()
                        <= MathUtils.s2(mc.player.getBlockInteractionRange() + 1)) {
            for (var i = 1; i < 256; ++i) {
                BlockPos checkPos = targetPos.add(0, -i, 0);
                BlockState state = mc.world.getBlockState(checkPos);
                if (!state.isAir() && !state.isLiquid()) {
                    if (i == 1) targetPos = null;
                    var ppp = checkPos;
                    RenderTasks.drawBox(Box.from(new BlockBox(ppp)), 50, Color.MAGENTA);
                    mc.interactionManager.interactBlock(
                            mc.player,
                            Hand.MAIN_HAND,
                            new BlockHitResult(ppp.toBottomCenterPos().add(0, 1, 0), Direction.UP, ppp, false, false));
                    mc.player.swingHand(Hand.MAIN_HAND);
                    // work by magic
                    if (!PlayerInputUtils.of(mc.options).hasWASDMovement()) {
                        MovTasks.getFloatingUtils().setGrimFloatingTick(true);
                    }
                    return;
                }
            }
        } else {
            targetPos = null;
        }
    }

    public void onRenderPos(Event<MatrixStack> event) {
        if (enable.get()) {
            if (mc.player.getStackInHand(Hand.MAIN_HAND).isEmpty()
                    && mc.player.getStackInHand(Hand.OFF_HAND).isEmpty()) {
                return;
            }
            MatrixStack stack = event.context();
            if (mc.crosshairTarget.getType() == HitResult.Type.MISS) {
                HitResult result = getCameraEntity().raycast(range.get(), 0, false);
                if (result.getType() == HitResult.Type.MISS && result instanceof BlockHitResult block) {
                    RenderUtils.startDrawVirtual(stack);
                    try {
                        BlockPos pos = block.getBlockPos();
                        RenderUtils.drawOutlinedBox(stack, Vec3d.of(pos), Vec3d.of(pos.add(1, 1, 1)), Color.RED);
                    } finally {
                        RenderUtils.stopDrawVirtual(stack);
                    }
                }
            }
        }
    }

    public Entity getCameraEntity() {
        if (mc.getCameraEntity() != null) {
            return mc.getCameraEntity();
        }
        return mc.player;
    }

    public static enum AirPlaceMode implements ConfigEnum {
        VANILLA,
        GRIM_GHOST_BLOCK_WALL;

        @Override
        public Text getDisplay() {
            return Text.translatable("configenum.air-place-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }
}
