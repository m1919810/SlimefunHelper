package me.matl114.hacks.modules.combat;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.KineticWeaponComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class SpearEnhance extends BaseModule {
    public final ModulePath spearModule = makePath(Configs.COMBAT_CONFIG, "spear-module");

    public SpearEnhance() {}

    public final FlagRef spearAutoRestart =
            flagBuilder(spearModule.add("spear-auto-restart")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onPreTick);
    }

    public static boolean isUsingSpear(PlayerEntity player) {
        // todo consider viaversion
        return player != null
                && player.isUsingItem()
                && VItem.getInstance().isSpear(player.getActiveItem());
    }

    public static ItemStack getSpear() {
        return mc.player.getActiveItem();
    }

    public void onPreTick(Event<ClientPlayerEntity> tickEvent) {
        if (isUsingSpear(mc.player)) {
            ItemStack stack = getSpear();
            int maxKineticTime = getMaxKineticTime(stack);
            Hand hand = mc.player.getActiveHand();
            if (spearAutoRestart.get() && mc.player.getItemUseTime() > maxKineticTime) {
                mc.interactionManager.stopUsingItem(mc.player);
                mc.interactionManager.interactItem(mc.player, hand);
            }
        }
    }

    public boolean canSpearKineticAttack() {
        if (isUsingSpear()) {
            if (mc.player.getItemUseTime() < 8) {
                return false;
            }
            ItemStack stack = getSpear();
            int maxKineticTime = getMaxKineticTime(stack);
            return mc.player.getItemUseTime() < maxKineticTime;
        }
        return false;
    }

    public static int getMaxKineticTime(ItemStack stack) {
        if (!VItem.getInstance().isSpear(stack)) {
            return 0;
        } else {
            Item item = stack.getItem();
            float lastingSec;
            if (item == Items.DIAMOND_SWORD) {
                lastingSec = 10;
            } else if (item == Items.NETHERITE_SWORD) {
                lastingSec = 8.75f;
            } else if (item == Items.IRON_SWORD) {
                lastingSec = 11.25f;
            } else if (item == Items.STONE_SWORD || item == Items.GOLDEN_SWORD) {
                lastingSec = 13.75f;
            } else if (item == Items.WOODEN_SWORD) {
                lastingSec = 15f;
            } else {
                return 0;
            }
            return (int) lastingSec * 20;
        }
    }
}
