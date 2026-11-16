package me.matl114.hacks.modules.combat;

import java.util.Map;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.AttributeUtils;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.RegistryUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class SpearSwap extends BaseModule {
    public final ModulePath root =
            makePath(Configs.COMBAT_CONFIG, "spear-module").add("spear-swap");

    public SpearSwap() {
        super("SpearSwap");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey = hotkey(root.addHotkey(), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::submitTask))
            .build();

    public final FlagRef selectCharge =
            builder(root.add("select-charge"), Boolean.class).defaultValue(true).build();

    public final EnumRef<GhostHandMode> ghostHandMode = builder(root.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    private boolean pendingTask;
    private Runnable restoreCallback;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        clearTask();
    }

    private boolean submitTask() {
        if (!enable.get() || checkNull() || mc.interactionManager == null || findBestLungeSpear() == null) {
            return false;
        }
        pendingTask = true;
        return true;
    }

    public void onPreInputEvent() {
        if (checkNull() || mc.interactionManager == null || !enable.get()) {
            clearTask();
            return;
        }

        if (!pendingTask) return;
        if (mc.player.getHungerManager().getSaturationLevel() <= 0.0F) {
            clearTask();
            return;
        }
        IndexEntry<ItemStack> best = findBestLungeSpear();
        if (best == null) {
            clearTask();
            return;
        }

        if (getAttackProgress(best.val()) < 1.0F) {
            if (!selectCharge.get()) return;
            if (restoreCallback == null) {
                IndexEntry<ItemStack> fastest = findBestChargingSpear();
                if (fastest == null) {
                    clearTask();
                    return;
                }
                restoreCallback = InvExtra.INSTANCE.swapItemToHand(fastest.index(), false, ghostHandMode.get());
                if (restoreCallback == null) clearTask();
            }
            return;
        }

        if (stabWithBestSpear(best)) {
            pendingTask = false;
            restoreCallback = null;
        } else {
            clearTask();
        }
    }

    private void clearTask() {
        pendingTask = false;
        restoreSwap();
    }

    private boolean isPiercingWeapon(ItemStack stack) {
        return stack.contains(DataComponentTypes.PIERCING_WEAPON)
                || VItem.getInstance().isSpear(stack);
    }

    private float getAttackProgress(ItemStack stack) {
        double attackSpeed = AttributeUtils.getAttributeWith(mc.player, Map.of(EquipmentSlot.MAINHAND, stack))
                .getValue(EntityAttributes.ATTACK_SPEED);
        if (attackSpeed <= 0.0D) return 0.0F;
        float perTick = (float) (1.0D / attackSpeed * 20.0D);
        int strength = Tasks.getTick() - PlayerStateManager.INSTANCE.lastAttackStrengthResetTick;
        return MathHelper.clamp(((float) strength + 0.5F) / perTick, 0.0F, 1.0F);
    }

    private IndexEntry<ItemStack> findBestChargingSpear() {
        return InventoryUtils.findBestPlayerItem(
                stack -> isPiercingWeapon(stack) ? (double) getAttackProgress(stack) : null,
                ghostHandMode.get().getSearchSize(false),
                false,
                false);
    }

    private IndexEntry<ItemStack> findBestLungeSpear() {
        RegistryEntry<Enchantment> lunge =
                RegistryUtils.getRegistryEntry(mc.world.getRegistryManager(), Enchantments.LUNGE);
        return InventoryUtils.findBestPlayerItem(
                stack -> isPiercingWeapon(stack)
                        ? 2.0D * (lunge == null ? 0 : EnchantmentHelper.getLevel(lunge, stack))
                                + getAttackProgress(stack)
                        : null,
                ghostHandMode.get().getSearchSize(false),
                false,
                false);
    }

    private boolean stabWithBestSpear(IndexEntry<ItemStack> best) {
        Runnable chargeRestore = restoreCallback;
        Runnable attackRestore = InvExtra.INSTANCE.swapItemToHand(best.index(), false, ghostHandMode.get());
        if (attackRestore == null) return false;
        restoreCallback = chargeRestore == null
                ? attackRestore
                : () -> {
                    attackRestore.run();
                    chargeRestore.run();
                };

        ItemStack spear = mc.player.getMainHandStack();
        if (!isPiercingWeapon(spear) || mc.interactionManager.isFlyingLocked()) {
            return false;
        }
        if (ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(21, 9)) {
            if (SpearEnhance.INSTANCE.onPiercing(() -> 0)) return true;
            return false;
        }

        PiercingWeaponComponent piercing = spear.get(DataComponentTypes.PIERCING_WEAPON);
        if (piercing == null) {
            return false;
        }
        mc.interactionManager.attackWithPiercingWeapon(piercing);
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private void restoreSwap() {
        Runnable callback = restoreCallback;
        restoreCallback = null;
        if (callback != null && !checkNull()) {
            callback.run();
        }
    }
}
