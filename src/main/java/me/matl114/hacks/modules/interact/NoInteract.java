package me.matl114.hacks.modules.interact;

import java.awt.*;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.EventContainer;
import me.matl114.events.impl.UseItemOnBlock;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

public class NoInteract extends BaseModule {
    public NoInteract() {
        super("NoInteract");
        bindFlag(enable);
    }

    public final ModulePath noInteract = makePath(Configs.INTERACT_CONFIG, "interaction-tweaks.no-interact");
    public final FlagRef enable = flagBuilder(noInteract.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(noInteract.addHotkey(), new MultiKeyBind(), noInteract.addEnable())
            .build();

    public final NBTRef<EntrySet<Block>> noInteractBlocks = builder(
                    noInteract.add("no-interact-block"), EntrySet.<Block>parameter())
            .defaultValue(new EntrySet<>(new Regex("^(.*chest|.*pot)$"), Registries.BLOCK))
            .build();

    public final NBTRef<EntrySet<Item>> noInteractIgnoreItems = builder(
                    noInteract.add("no-interact-ignore-item"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(new Regex("^()$"), Registries.ITEM))
            .build();

    public final FlagRef vanillaOnly = builder(noInteract.add("no-interact-vanilla-only"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef autoDisableSameTickOffHand =
            flagBuilder(noInteract.add("auto-disable-same-tick-offhand")).build();

    public final FlagRef sneakIfInsta =
            flagBuilder(noInteract.add("sneak-if-may-instant")).build();

    public final FlagRef autoCorrect =
            flagBuilder(noInteract.add("auto-correct-placement")).build();

    public final EnumRef<Configs.LegalInteractMode> correctMode = builder(
                    noInteract.add("auto-correct-mode"), Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.NONE)
            .build();

    public final FlagRef correctAirPlace =
            flagBuilder(noInteract.add("auto-correct-air-place")).build();

    public final FlagRef correctState =
            flagBuilder(noInteract.add("auto-correct-state")).build();

    public final FlagRef swingHand = builder(noInteract.add("swing-hand"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef render =
            flagBuilder(noInteract.add("render-fail-interact")).build();

    public final NBTRef<WrapColor> color = builder(noInteract.add("render-fail-interact-color"), WrapColor.class)
            .defaultValue(new WrapColor((Color.RED)))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPrePlayerUseItemAtBlock(), this::onPreInteractBlock);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3d);
    }

    int lastCancelMainHandVanillaInputTick = 0;
    RenderCollector<Box> failInteractPlace = RenderCollectors.createBoxCollector(true, false, false);

    int lastStartRenderFailPlace = 0;

    public void onPreInteractBlock(Event<UseItemOnBlock> event) {
        if (enable.get() && (!vanillaOnly.get() || InteractManager.INSTANCE.duringVanillaInput)) {
            Hand hand = event.context.hand();
            ItemStack stack = mc.player.getStackInHand(hand);
            BlockHitResult hitResult = event.context.hitResult();
            if (autoDisableSameTickOffHand.get()
                    && hand == Hand.OFF_HAND
                    && InteractManager.INSTANCE.duringVanillaInput
                    && Tasks.getTick() == lastCancelMainHandVanillaInputTick) {
                event.cancel();
                event.context.actionResult(ActionResult.PASS);
                return;
            }
            if (hitResult != null
                    && !stack.isEmpty()
                    && !noInteractIgnoreItems.get().test(stack.getItem())) {
                BlockPos interactAtPos = hitResult.getBlockPos();
                BlockState state = mc.world.getBlockState(interactAtPos);
                if (!noInteractBlocks.get().test(state.getBlock())) return;
                boolean mayInteractAccept =
                        InteractUtils.isInteractAcceptable(mc.world, mc.player, interactAtPos, state, stack);
                if (!InteractUtils.canInteractAndPlace(mc.player, mayInteractAccept)) {
                    onFailOriginalInteract(interactAtPos, state);
                    if (stack.getItem() instanceof BlockItem) {
                        if (sneakIfInsta.get() && ViaFabricPlusHooks.isSupportInstaSneak() && !mc.player.isSneaking()) {
                            PlayerInputUtils.of(mc.player)
                                    .sneak(true)
                                    .sendPlayerSneakUpdatePacket()
                                    .applyInput(mc.player);
                            ClientPlayerAccess.of(mc.player).setLastSneakFlag(true);

                            return;
                        }
                        if (autoCorrect.get()) {
                            BlockPos targetPos = InteractUtils.getCurrentPlacePos(mc.player, hitResult);
                            BlockState targetState = InteractUtils.getBlockPlacement(mc.player, hand, stack, hitResult);
                            if (targetState != null) {
                                BlockHitResult hitResultOverride = handleMayAutoCorrect(hitResult);
                                if (hitResultOverride != null) {
                                    if (correctState.get()) {

                                        BlockRotate.INSTANCE.addTempStateSchematic(targetPos, targetState);
                                    }
                                    InteractionTasks.handlePlaceMode(
                                            correctMode.get(), hitResultOverride, hand, swingHand.get());
                                    event.cancel();
                                    event.context.actionResult(ActionResult.SUCCESS);
                                    return;
                                }
                            }
                        }
                    }

                    event.cancel();
                    event.context.actionResult(ActionResult.PASS);
                    if (InteractManager.INSTANCE.duringVanillaInput && hand == Hand.MAIN_HAND) {
                        lastCancelMainHandVanillaInputTick = Tasks.getTick();
                    }
                }
            }
        }
    }

    public void onFailOriginalInteract(BlockPos interactAt, BlockState state) {
        if (render.get()) {
            VoxelShape stateShape = state.getCollisionShape(mc.world, interactAt);
            failInteractPlace.clear();
            lastStartRenderFailPlace = Tasks.getTick();
            if (!stateShape.isEmpty()) {
                for (var box : stateShape.getBoundingBoxes()) {
                    failInteractPlace.submit(box.offset(interactAt), color.get().withAlpha(255));
                }
            }
        }
    }

    public void onRender3d(Event<MatrixStack> stackEvent) {
        if (enable.get() && render.get()) {
            if (Tasks.getTick() > lastStartRenderFailPlace + 200) {
                failInteractPlace.clear();
                ;
                return;
            }
            RenderUtils.startDrawVirtual(stackEvent.context);
            try {
                failInteractPlace.render3D(stackEvent.context);
            } finally {
                RenderUtils.stopDrawVirtual(stackEvent.context);
            }
        }
    }

    public BlockHitResult handleMayAutoCorrect(BlockHitResult hitResult) {
        BlockPos placePosition = InteractUtils.getCurrentPlacePos(mc.player, hitResult);
        var newHitResult = InteractionTasks.getPlaceSupportingResult(
                placePosition, correctAirPlace.get(), !correctMode.get().isLegal());
        return (newHitResult != null && !newHitResult.flag()) ? newHitResult.val() : null;
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        correctMode.set(Configs.LegalInteractMode.getFromPreset(event.context.getValue()));
        correctAirPlace.set(!event.context.getValue().hasAC());
    }
}
