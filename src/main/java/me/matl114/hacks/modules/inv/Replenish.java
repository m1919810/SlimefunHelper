package me.matl114.hacks.modules.inv;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.IntRef;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.collections.FlagEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public class Replenish extends BaseModule {
    public static Replenish INSTANCE;

    public Replenish() {
        super("Replenish");
        INSTANCE = this;
    }

    public final ModulePath root = makePath(Configs.INV_CONFIG, "auto-inv.replenish");
    public List<Vec3i> blockSeq = new ArrayList<>();

    public final IntRef searchRange = intBuilder(root.add("search-range"))
            .defaultValue(3)
            .validator(Configs.INT_POSITIVE)
            .updateListener(s -> blockSeq = MathUtils.create3DPointListInRange(s))
            .build();

    public Stream<Pair<BlockPos, BlockHitResult>> searchAvailableShulkerPosition() {
        BlockPos playerPos = mc.player.getBlockPos();
        Vec3d eyePos = mc.player.getEyePos();
        Direction playerLook = mc.player.getFacing();
        return blockSeq.stream()
                .map(playerPos::add)
                .map(s -> {
                    BlockState state = mc.world.getBlockState(s);
                    if (!state.isAir() && !state.isLiquid() && !state.isReplaceable()) {
                        return null;
                    }
                    List<FlagEntry<BlockHitResult>> placeHitResult =
                            InteractionTasks.getAllPlaceSupportingResult(eyePos, s, playerLook, false, false);
                    if (placeHitResult.isEmpty()) {
                        return null;
                    }
                    return placeHitResult.stream()
                            .filter(hitResult -> {
                                if (InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
                                    BlockState targetState = InteractUtils.getBlockPlacement(
                                            Blocks.SHULKER_BOX, mc.player, mc.world, hitResult.val());
                                    if (targetState != null) {
                                        // can open
                                        return InteractUtils.canShulkerOpen(mc.world, playerPos, targetState);
                                    } else {
                                        return false;
                                    }
                                } else {
                                    return false;
                                }
                            })
                            .findFirst()
                            .map(hit -> Pair.of(s, hit.val()))
                            .orElse(null);
                })
                .filter(Objects::nonNull);
    }

    public Stream<Pair<BlockPos, BlockHitResult>> searchAvailableChestLikePosition(boolean ender) {
        BlockPos playerPos = mc.player.getBlockPos();
        Vec3d eyePos = mc.player.getEyePos();
        Direction playerLook = mc.player.getFacing();
        return blockSeq.stream()
                .map(playerPos::add)
                .map(s -> {
                    BlockState state = mc.world.getBlockState(s);
                    if (!state.isAir() && !state.isLiquid() && !state.isReplaceable()) {
                        return null;
                    }
                    FlagEntry<BlockHitResult> hitResult =
                            InteractionTasks.getPlaceSupportingResult(eyePos, s, playerLook, false, false);
                    if (InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
                        BlockState expectedState = InteractUtils.getBlockPlacement(
                                ender ? Blocks.ENDER_CHEST : Blocks.CHEST, mc.player, mc.world, hitResult.val());
                        if (expectedState != null
                                && (ender
                                        ? InteractUtils.canEnderChestOpen(mc.world, s)
                                        : InteractUtils.canChestOpen(mc.world, s, expectedState))) {
                            return Pair.of(s, hitResult.val());
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull);
    }
}
