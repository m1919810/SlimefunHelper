package me.matl114.hacks.modules.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class AutoWeb extends BaseModule {
    private static final BlockState WEB_STATE = Blocks.COBWEB.getDefaultState();

    public AutoWeb() {
        super("AutoWeb");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.COMBAT_CONFIG, "combat-utils.auto-web");
    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final DoubleRef interactRange =
            doubleBuilder(root.add("interact-range")).defaultValue(4.5).build();

    public final EnumRef<Configs.LegalInteractMode> mode = builder(root.add("mode"), Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.NONE)
            .build();

    public final FlagRef airplace = flagBuilder(root.add("air-place")).build();

    public final FlagRef selfWeb = flagBuilder(root.add("self-web")).build();
    public final FlagRef selfWebOnlySlow =
            flagBuilder(root.add("self-web-only-slow")).build();

    public final FlagRef otherWeb = flagBuilder(root.add("other-web")).build();

    // 当对方不在地面 是否考虑它的头部（player.getBlockPos.up
    public final FlagRef ceiling = flagBuilder(root.add("ceiling")).build();

    public final FlagRef notifySupply =
            builder(root.add("notify-supply"), Boolean.class).defaultValue(true).build();

    public final FlagRef swingHand =
            builder(root.add("swing-hand"), Boolean.class).defaultValue(true).build();

    private final TimerExecutor noSupplyTimer = new TimerExecutor();

    @Override
    public void onEnableModule() {
        super.onEnableModule();
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPreset);
    }

    private void onPreInputEvent(Event<Void> event) {
        if (checkNull() || !enable.get()) {
            return;
        }
        BlockHitResult option = searchPlaceOption();
        if (option == null) {
            return;
        }
        IndexEntry<ItemStack> web = supplyWeb();
        if (web == null) {
            noSupplyTimer.run(100, () -> {
                if (notifySupply.get()) {
                    logI18N("message.module.auto-web.no-item");
                }
            });
            return;
        }
        placeWeb(web, option);
    }

    private void onPreset(Event<EventContainer<ModulePreset>> event) {
        mode.set(Configs.LegalInteractMode.getFromPreset(event.context.getValue()));
    }

    private IndexEntry<ItemStack> supplyWeb() {
        return InventoryUtils.findPlayerItem(stack -> stack.isOf(Items.COBWEB), true, false);
    }

    private boolean placeWeb(IndexEntry<ItemStack> web, BlockHitResult hitResult) {
        Runnable callback = InvExtra.INSTANCE.swapInventoryIndexToHand(web.index());
        if (callback == null) {
            return false;
        }
        InteractionTasks.handlePlaceMode(mode.get(), hitResult, Hand.MAIN_HAND, swingHand.get());
        callback.run();
        return true;
    }

    private BlockHitResult searchPlaceOption() {
        if (selfWeb.get() && (!selfWebOnlySlow.get() || mc.player.hasStatusEffect(StatusEffects.SLOWNESS))) {
            BlockPos pos = mc.player.getBlockPos();
            if (mc.world.getBlockState(pos) != WEB_STATE && !PlayerStateManager.INSTANCE.lastInWeb) {
                FlagEntry<BlockHitResult> hitResult = createWebHitResult(mc.player, pos);
                if (hitResult != null) {
                    return hitResult.val();
                }
            }
        }
        if (otherWeb.get()) {
            List<PlayerEntity> targets = getTargets();
            if (targets.isEmpty()) {
                return null;
            }

            for (PlayerEntity target : targets) {
                for (BlockPos pos : collectTargetPositions(target)) {
                    FlagEntry<BlockHitResult> hitResult = createWebHitResult(target, pos);
                    if (hitResult == null) {
                        continue;
                    }
                    return hitResult.val();
                }
            }
        }
        return null;
    }

    private List<PlayerEntity> getTargets() {
        if (TargetSelector.INSTANCE == null) {
            return List.of();
        }
        return TargetSelector.INSTANCE.getAttackableEntities(interactRange.get()).stream()
                .filter(PlayerEntity.class::isInstance)
                .map(PlayerEntity.class::cast)
                .filter(EntityUtils::isEntityValid)
                .filter(player -> player != mc.player)
                .sorted(Comparator.comparingDouble(mc.player::squaredDistanceTo))
                .toList();
    }

    private List<BlockPos> collectTargetPositions(PlayerEntity target) {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        Box box = target.getBoundingBox();
        positions.addAll(MathUtils.getOccupiedBlockPositions(box.withMaxY(box.minY + 0.5D)));
        if (ceiling.get() && !target.isOnGround()) {
            int minTargetY = target.getBlockY();
            MathUtils.getOccupiedBlockPositions(box.stretch(0.0D, 0.75D, 0.0D)).stream()
                    .filter(pos -> pos.getY() > minTargetY)
                    .forEach(positions::add);
        }
        List<BlockPos> result = new ArrayList<>(positions.size());
        positions.stream()
                .map(BlockPos::toImmutable)
                .sorted(Comparator.comparingDouble(s -> new Box(s).squaredMagnitude(mc.player.getEyePos())))
                .forEach(result::add);
        return result;
    }

    private FlagEntry<BlockHitResult> createWebHitResult(PlayerEntity target, BlockPos pos) {
        if (!isValidWebPos(target, pos)) {
            return null;
        }
        FlagEntry<BlockHitResult> hitResult = InteractionTasks.createSpecificStateHitResult(
                pos, WEB_STATE, airplace.get(), !mode.get().isLegal());
        if (!InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
            return null;
        }
        if (!InteractExtra.INSTANCE.isWithinInteractRange(
                mc.player.getPos(), hitResult.val().getBlockPos())) {
            return null;
        }
        return hitResult;
    }

    private boolean isValidWebPos(PlayerEntity target, BlockPos pos) {
        if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos)) {
            return false;
        }
        BlockState state = mc.world.getBlockState(pos);
        if (state.isOf(Blocks.COBWEB)) {
            return false;
        }
        if (!state.isAir() && !state.isLiquid() && !state.isReplaceable()) {
            return false;
        }
        if (!WEB_STATE.canPlaceAt(mc.world, pos)) {
            return false;
        }
        return true;
    }

    private boolean shouldIgnoreWebCollision(Entity entity, PlayerEntity target) {
        return entity == target || (selfWeb.get() && entity == mc.player);
    }

    private boolean isPlayerAlreadyWebbed() {
        return collectTargetPositions(mc.player).stream()
                .anyMatch(pos -> mc.world.getBlockState(pos).isOf(Blocks.COBWEB));
    }
}
