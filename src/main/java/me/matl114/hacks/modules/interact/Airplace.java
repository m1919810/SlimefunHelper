package me.matl114.hacks.modules.interact;

import java.awt.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.RenderUtils;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class Airplace extends BaseModule {
    public static final String[] AIR_PLACE = {"interaction-tweaks", "air-place", "enable"};
    public static final String[] AIR_PLACE_RANGE = {"interaction-tweaks", "air-place", "range"};

    public static final String[] AIR_PLACE_RENDER = {"interaction-tweaks", "air-place", "render"};

    public Airplace() {}

    public final FlagRef enable =
            flagBuilder(Configs.INTERACT_CONFIG, AIR_PLACE).build();

    public final DoubleRef range = builder(Configs.INTERACT_CONFIG, AIR_PLACE_RANGE, DoubleRef.TYPE)
            .defaultValue(5.0D)
            .validator(Configs.doubleRange(0, 10000))
            .build();

    public final FlagRef render =
            flagBuilder(Configs.INTERACT_CONFIG, AIR_PLACE_RENDER).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getItemUseAction(), this::onInteract);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRenderPos);
    }

    public void onInteract(Event<HitResult> event) {
        if (!event.isCancelled() && enable.get()) {
            Hand hand = event.getArgs(0);
            ItemStack stack = mc.player.getStackInHand(hand);
            if (!stack.isEmpty()) {
                HitResult hitResult = event.context();
                if (hitResult.getType() == HitResult.Type.MISS) {
                    HitResult result = getCameraEntity().raycast(range.get(), 0, false);
                    if (result.getType() == HitResult.Type.MISS && result instanceof BlockHitResult block) {
                        BlockHitResult newResult = new BlockHitResult(
                                block.getPos(),
                                block.getSide(),
                                block.getBlockPos(),
                                block.isInsideBlock(),
                                block.isAgainstWorldBorder());
                        event.context(newResult);
                    }
                }
            }
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
}
