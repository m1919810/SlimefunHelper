package me.matl114.hacks.modules.interact;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.BlockBreak;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.PacketMine;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.modules.move.RotationManager;
import me.matl114.hacks.modules.task.DisablerManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.InteractUtils;
import me.matl114.hacks.utils.PlayerInputUtils;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.enums.LegalInteractMode;
import me.matl114.hacks.utils.tasks.StateExecutor;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class SelfTrap extends BaseModule {
    public static SelfTrap INSTANCE;

    public SelfTrap() {
        super("SelfTrap");
        bindFlag(enable);
        INSTANCE = this;
    }

    public final ModulePath selfTrap = makePath(Configs.INTERACT_CONFIG, "place-utils.self-trap");

    public final FlagRef enable = flagBuilder(selfTrap.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(selfTrap.addHotkey(), new MultiKeyBind(), selfTrap.addEnable())
            .build();

    public final FlagRef offhand = flagBuilder(selfTrap.add("offhand")).build();

    public final IntRef delay = builder(selfTrap.add("delay"), IntRef.TYPE)
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef multiply = builder(selfTrap.add("multiply"), IntRef.TYPE)
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final EnumRef<LegalInteractMode> mode = builder(selfTrap.add("mode"), LegalInteractMode.class)
            .defaultValue(LegalInteractMode.NONE)
            .build();

    public final FlagRef airplace = flagBuilder(selfTrap.add("air-place")).build();

    public final FlagRef placeLower =
            flagBuilder(selfTrap.add("place-lower"), true).build();

    public final NBTRef<OptionalPrimitive<Integer>> scaffold = builder(
                    selfTrap.add("lower"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(true, NBTTypes.INT_TYPE, 1))
            .build();

    public final FlagRef onlyGround = flagBuilder(selfTrap.add("only-ground")).build();

    public final FlagRef autoCenter = flagBuilder(selfTrap.add("auto-center")).build();

    public final FlagRef autoSneak = flagBuilder(selfTrap.add("auto-sneak")).build();

    public final FlagRef useWhiteList =
            flagBuilder(selfTrap.add("use-white-list")).build();

    public final NBTRef<EntrySet<Item>> whiteList = builder(selfTrap.add("white-list"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(Registries.ITEM, List.of(Items.OBSIDIAN)))
            .build();

    public final FlagRef onlyBlastResistance = builder(selfTrap.add("only-blast-resistance"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef eatingAbort = builder(selfTrap.add("using-item-abort"), Boolean.class)
            .defaultValue(false)
            .build();

    public final EnumRef<GhostHandMode> ghostHand = builder(selfTrap.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final FlagRef swingHand = builder(selfTrap.add("swing-hand"), Boolean.class)
            .defaultValue(true)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInput);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(PacketMine.getPacketMineAction().getChannel(BlockBreak.Stage.PRE), this::onPrePacketMine);
    }

    int delayTicks;
    boolean needSneak;
    StateExecutor needPlaceState = new StateExecutor();

    public void onPrePacketMine(Event<BlockBreak> eventPre) {
        if (enable.get()
                && !eventPre.isCancelled()
                && eventPre.canCancel()
                && eventPre.context().stage() == BlockBreak.Stage.PRE) {
            BlockPos pos = eventPre.context().blockPos();
            if (getTargetingPos().contains(pos)) {
                eventPre.cancel();
            }
        }
    }

    BlockPos lastSurround;
    BlockPos lastCenter;

    public void onInput() {
        if (checkNull() || !enable.get()) {
            return;
        }
        boolean triggerCenterFix = lastCenter != null;
        boolean wasSneaking = mc.player.isSneaking();
        if (++delayTicks >= delay.get()) {
            if (!eatingAbort.get() || !mc.player.isUsingItem()) {
                if (checkSelfTrap()) {
                    if (autoCenter.get() && mc.player.getPose() != EntityPose.SWIMMING) {
                        triggerCenterFix = true;
                    }
                    delayTicks = 0;
                    needPlaceState.state(true);
                } else {
                    needPlaceState.state(false);
                }
            } else {
                needPlaceState.state(false);
            }
        }

        if (needSneak) {
            needSneak = false;
            if (autoSneak.get()) {
                if (ViaFabricPlusHooks.isSupportInstaSneak()) {
                    if (mc.player.isSneaking() != wasSneaking) {
                        PlayerInputUtils.of(mc.player)
                                .sneak(wasSneaking)
                                .sendPlayerSneakUpdatePacket()
                                .applyInput(mc.player);
                    }
                } else {
                    PlayerInputManager.INSTANCE.addSneakModifier(0, true, Math.max(delay.get() - 1, 0), 1);
                }
            }
        }
        if (triggerCenterFix && mc.player.isOnGround() && !mc.player.isFallFlying()) {
            if (lastCenter == null || !MathUtils.isInBox(lastCenter.toCenterPos(), mc.player.getPos(), 0.75)) {
                lastCenter = lastSurround == null ? PlayerStateManager.INSTANCE.lastVelocityAffectingPos : lastSurround;
            }
            var blockPos = lastCenter;
            boolean fixed = mc.player.getX() - blockPos.getX() - 0.5 <= 0.2
                    && mc.player.getX() - blockPos.getX() - 0.5 >= -0.2
                    && mc.player.getZ() - blockPos.getZ() - 0.5 <= 0.2
                    && mc.player.getZ() - 0.5 - blockPos.getZ() >= -0.2;
            if (!fixed) {
                PlayerInputUtils.Input inputUtils = PlayerInputUtils.of(mc.options);
                if (!inputUtils.hasMovement()) {
                    Vec3d lookHorizontal = blockPos.toCenterPos().subtract(mc.player.getPos());
                    float yaw = EntityUtils.rotationToYaw(lookHorizontal);
                    RotationManager.INSTANCE.push(RotationManager.ofDirection(Optional.empty(), Optional.of(yaw))
                            .preTick((rotation, player, matchRaycast) -> {
                                PlayerInputUtils.Input movement = PlayerInputUtils.tryCorrectMovementInput(
                                        new PlayerInputUtils.Input(true, false, false, false), yaw, player.getYaw());
                                PlayerInputManager.INSTANCE.addInputModifier(
                                        PlayerInputManager.Modifier.empty(0)
                                                .forward(movement.forward())
                                                .backward(movement.backward())
                                                .left(movement.left())
                                                .right(movement.right()),
                                        0);
                            })
                            .applyMoveFix(false)
                            .priority(0));
                }
            } else {
                lastCenter = null;
            }
        }
    }

    public Set<BlockPos> getTargetingPos() {
        Box playerBox = mc.player.getBoundingBox();
        int feetY = (int) Math.floor(playerBox.minY);
        Set<BlockPos> occupiedFeet = new LinkedHashSet<>();

        int minX = (int) Math.floor(playerBox.minX);
        int maxX = (int) Math.ceil(playerBox.maxX) - 1;
        int minZ = (int) Math.floor(playerBox.minZ);
        int maxZ = (int) Math.ceil(playerBox.maxZ) - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                occupiedFeet.add(new BlockPos(x, feetY, z));
            }
        }

        Set<BlockPos> result = new LinkedHashSet<>();
        if (placeLower.get()) {
            for (BlockPos occupied : occupiedFeet) {
                result.add(occupied.down());
            }
        }
        Set<BlockPos> scaffoldPoses = new LinkedHashSet<>();
        for (Direction direction : MathUtils.HORIZONTALS) {
            for (BlockPos occupied : occupiedFeet) {
                BlockPos candidate = occupied.offset(direction);
                if (!occupiedFeet.contains(candidate)) {
                    result.add(candidate);
                    if (scaffold.get().isPresent()) {
                        BlockState state = mc.world.getBlockState(candidate);
                        if (state.isAir() || state.isLiquid()) {
                            for (var i = 1; i <= scaffold.get().getValue(); ++i) {
                                BlockPos candidateDown = candidate.offset(Direction.DOWN, i);
                                BlockState currentState = mc.world.getBlockState(candidateDown);
                                if (!occupiedFeet.contains(candidateDown)) {
                                    if (currentState.isAir() || currentState.isLiquid()) {
                                        scaffoldPoses.add(candidateDown);
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        result.addAll(scaffoldPoses);
        return result;
    }

    public boolean checkSelfTrap() {
        if (onlyGround.get() && !mc.player.isOnGround() && !CollisionUtil.isEntitySupported(mc.player, 1.5D)) {
            return false;
        }
        lastSurround = PlayerStateManager.INSTANCE.lastVelocityAffectingPos;

        boolean legal = mode.get().isLegal();
        int maxPlace =
                DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled(mode.get().canMultiRotPlace())
                        ? multiply.get()
                        : 1;
        int placeCount = 0;
        Runnable inventoryCallback = null;
        boolean offhandOk = offhand.get();

        for (BlockPos target : getTargetingPos()) {
            BlockState state = mc.world.getBlockState(target);
            if (!state.isAir() && !state.isReplaceable()) {
                continue;
            }

            var hitResult = InteractionTasks.getPlaceSupportingResult(target, airplace.get(), !legal);
            if (hitResult == null) {
                continue;
            }

            if (hitResult.flag()) {
                if (!needSneak
                        && autoSneak.get()
                        && ViaFabricPlusHooks.isSupportInstaSneak()
                        && !mc.player.isSneaking()) {
                    PlayerInputUtils.of(mc.player)
                            .sneak(true)
                            .sendPlayerSneakUpdatePacket()
                            .applyInput(mc.player);
                }
                needSneak = true;
            }

            if (!InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
                continue;
            }

            if (placeCount == 0) {
                IndexEntry<ItemStack> supply = supplyBlocks();
                if (supply == null) {
                    break;
                }
                if (!InteractionTasks.canHandleInteract(mode.get(), hitResult.val())) {
                    break;
                }
                maxPlace = Math.min(maxPlace, supply.val().getCount());
                offhandOk |= supply.index() == 40;
                inventoryCallback = InvExtra.INSTANCE.swapItemToHand(supply.index(), offhandOk, ghostHand.get());
                if (inventoryCallback == null) {
                    break;
                }
            }
            boolean completed = InteractionTasks.handlePlaceMode(
                    mode.get(), hitResult.val(), offhandOk ? Hand.OFF_HAND : Hand.MAIN_HAND, swingHand.get());
            placeCount++;
            if (!completed || placeCount >= maxPlace) {
                break;
            }
        }

        if (inventoryCallback != null) {
            InteractionTasks.finishInteractGhostHand(mode.get(), inventoryCallback);
        }
        return placeCount > 0;
    }

    public IndexEntry<ItemStack> supplyBlocks() {
        return InventoryUtils.findBestPlayerItem(
                item -> {
                    if (!(item.getItem() instanceof BlockItem blockItem)) {
                        return null;
                    }
                    if (useWhiteList.get() && !whiteList.get().test(blockItem)) {
                        return null;
                    }
                    if (onlyBlastResistance.get() && blockItem.getBlock().getBlastResistance() < 600) {
                        return null;
                    }
                    return (double) blockItem.getBlock().getBlastResistance()
                            + (blockItem == Items.OBSIDIAN ? 1E8 : 0)
                            + (blockItem.getBlock() instanceof BlockWithEntity ? -1E8 : 0);
                },
                ghostHand.get().getSearchSize(offhand.get()),
                false,
                false);
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        ModulePreset preset = event.context.getValue();
        ghostHand.set(preset.getGhostHandMode(ghostHand.get()));
        if (preset.isAirplacePreset()) airplace.set(preset.getAirplace(airplace.get()));
        if (preset.isSwingPlacePreset()) swingHand.set(preset.getSwingPlace(swingHand.get()));
        if (preset.isMultitaskPreset()) eatingAbort.set(preset.getEatingAbort(eatingAbort.get()));
        if (!preset.isOptionPreset()) {
            mode.set(LegalInteractMode.getFromPreset(preset));
            airplace.set(!preset.hasAC());
        }
    }
}
