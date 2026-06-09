package me.matl114.hacks.modules.interact;

import com.google.common.util.concurrent.Runnables;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.registry.Registries;

public class AutoEat extends BaseModule {
    public final ModulePath interactionTweaks = makePath(Configs.INTERACT_CONFIG, "interaction-tweaks");
    public final ModulePath autoEat = interactionTweaks.add("auto-eat");

    public AutoEat() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(autoEat.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(autoEat.addHotkey(), new MultiKeyBind(), autoEat.addEnable())
            .build();

    public final FlagRef log = builder(autoEat.add("log"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef enableHealth = builder(autoEat.add("enable-health"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef enableHunger = builder(autoEat.add("enable-hunger"), Boolean.class)
            .defaultValue(true)
            .build();

    public final DoubleRef healthLevel = doubleBuilder(autoEat.add("health-level"))
            .defaultValue(16.0D)
            .validator(Configs.doubleRange(0.0D, 20.0D))
            .build();

    public final IntRef hungerLevel = intBuilder(autoEat.add("hunger-level"))
            .defaultValue(16)
            .validator(Configs.intRange(0, 20))
            .build();

    public final FlagRef noEnemy = builder(autoEat.add("no-enemy"), Boolean.class)
            .defaultValue(true)
            .build();

    public final DoubleRef noEnemyDistance = doubleBuilder(autoEat.add("no-enemy-distance"))
            .defaultValue(8.0D)
            .validator(Configs.doubleRange(0.0D, 64.0D))
            .show(noEnemy::get)
            .build();

    public final NBTRef<RegistryRegex<Item>> whiteListItem = builder(
                    autoEat.add("white-list-item"), NBTType.<RegistryRegex<Item>>parameter(RegistryRegex.class))
            .defaultValue(new RegistryRegex<>(new Regex("^(golden_apple|enchanted_golden_apple|potion|cooked_beef|cooked_porkchop|baked_potato|bread|cooked_chicken|cooked_mutton|cooked_rabbit|cooked_cod|cooked_salmon|beetroot_soup|rabbit_stew|mushroom_stew|suspicious_stew|pumpkin_pie|melon_slice|carrot|golden_carrot)$"), Registries.ITEM))
            .build();

    public final FlagRef fireworkFix = builder(autoEat.add("firework-fix"), Boolean.class)
            .defaultValue(true)
            .build();

    private float lastHealth = -1.0F;
    private boolean eating;
    private Runnable restoreCallback = Runnables.doNothing();
    private int eatingSlot = -1;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        stopEating();
        lastHealth = -1.0F;
    }

    public void onTick(Event<ClientPlayerEntity> event) {
        ClientPlayerEntity player = event.context;
        if (!enable.get() || player == null || mc.world == null || mc.interactionManager == null) {
            stopEating();
            lastHealth = player == null ? -1.0F : player.getHealth();
            return;
        }
        if (shouldBlockForState(player)) {
            stopEating();
            lastHealth = player.getHealth();
            return;
        }
        boolean hurtPriority = isHealthDropPriority(player);
        if (!shouldStartEat(player, hurtPriority)) {
            stopEating();
            lastHealth = player.getHealth();
            return;
        }
        IndexEntry<ItemStack> candidate = selectBestFood(hurtPriority);
        if (candidate == null) {
            stopEating();
            lastHealth = player.getHealth();
            return;
        }
        startOrContinueEating(candidate);
        lastHealth = player.getHealth();
    }

    private boolean shouldBlockForState(ClientPlayerEntity player) {
        if (player.isUsingItem()) {
            ItemStack active = player.getActiveItem();
            if (eating && isFoodCandidate(active)) {
                return false;
            }
            return true;
        }
        if (player.isFallFlying() && fireworkFix.get()) {
            return true;
        }
        if (noEnemy.get() && hasEnemyNearby()) {
            return true;
        }
        return false;
    }

    private boolean shouldStartEat(ClientPlayerEntity player, boolean hurtPriority) {
        boolean healthNeed = enableHealth.get() && player.getHealth() <= healthLevel.get();
        boolean hungerNeed = enableHunger.get() && player.getHungerManager().getFoodLevel() <= hungerLevel.get();
        return hurtPriority || healthNeed || hungerNeed;
    }

    private boolean isHealthDropPriority(ClientPlayerEntity player) {
        if (!enableHealth.get()) {
            return false;
        }
        if (lastHealth < 0.0F) {
            return false;
        }
        return player.getHealth() < lastHealth;
    }

    private boolean hasEnemyNearby() {
        double distance = noEnemyDistance.get();
        double distanceSq = MathUtils.s2(distance);
        for (Entity entity : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(distance))) {
            if (entity instanceof HostileEntity && entity.isAlive()) {
                if (entity.squaredDistanceTo(mc.player) <= distanceSq) {
                    return true;
                }
            }
        }
        return false;
    }

    private IndexEntry<ItemStack> selectBestFood(boolean hurtPriority) {
        return InventoryUtils.findBestPlayerItem(stack -> scoreFood(stack, hurtPriority), false, false);
    }

    private Double scoreFood(ItemStack stack, boolean hurtPriority) {
        if (!isFoodCandidate(stack)) {
            return null;
        }
        FoodComponent food = getFoodComponent(stack);
        double score = 0.0D;
        if (food != null) {
            int hunger = food.nutrition();
            if (hunger > 0) {
                score = food.saturation() / hunger;
            }
        }
        if (hurtPriority && isGoldenAppleFood(stack)) {
            score += 100.0D;
        }
        if (hurtPriority && isHealingPotion(stack)) {
            score += 50.0D;
        }
        if (score <= 0.0D) {
            return null;
        }
        score += stack.getCount() * 0.0001D;
        return score;
    }

    private FoodComponent getFoodComponent(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.get(DataComponentTypes.FOOD);
    }

    private boolean isGoldenAppleFood(ItemStack stack) {
        return stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private boolean isHealingPotion(ItemStack stack) {
        ConsumableComponent consumable = stack.get(DataComponentTypes.CONSUMABLE);
        if (consumable == null) {
            return false;
        }
        if (stack.streamAll(PotionContentsComponent.class).anyMatch(component -> component.effects().stream()
                .anyMatch(effect -> effect.getEffectType().value() == StatusEffects.INSTANT_HEALTH))) {
            return true;
        }
        for (var effect : consumable.onConsumeEffects()) {
            if (effect instanceof ApplyEffectsConsumeEffect apply
                    && apply.effects().stream().anyMatch(s -> s.getEffectType().value() == StatusEffects.INSTANT_HEALTH)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFoodCandidate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!VItem.getInstance().isEatable(stack)) {
            return false;
        }
        if (!whiteListItem.get().test(stack.getItem())) {
            return false;
        }
        return getFoodComponent(stack) != null || isHealingPotion(stack);
    }

    private void startOrContinueEating(IndexEntry<ItemStack> candidate) {
        int slot = candidate.index();
        if (eating && slot == eatingSlot && mc.player.isUsingItem()) {
            return;
        }
        stopEating();
        Runnable callback = InvExtra.INSTANCE.switchOrSwapInventoryIndexToHand(slot);
        if (callback == null) {
            if (log.get()) {
                Debug.chat("[AutoEat] 目标食物无法切到主手");
            }
            return;
        }
        restoreCallback = callback;
        eatingSlot = slot;
        eating = true;
        mc.options.useKey.setPressed(true);
    }

    private void stopEating() {
        if (eating) {
            mc.options.useKey.setPressed(false);
            if (restoreCallback != null) {
                restoreCallback.run();
            }
        }
        restoreCallback = Runnables.doNothing();
        eating = false;
        eatingSlot = -1;
    }
}