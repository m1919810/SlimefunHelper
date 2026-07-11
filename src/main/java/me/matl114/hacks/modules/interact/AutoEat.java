package me.matl114.hacks.modules.interact;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import me.matl114.accessors.access.ClientAccess;
import me.matl114.accessors.hacks.KeyBindAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.apache.commons.lang3.stream.Streams;

public class AutoEat extends BaseModule {
    public final ModulePath interactionTweaks = makePath(Configs.INTERACT_CONFIG, "interaction-tweaks");
    public final ModulePath autoEat = interactionTweaks.add("auto-eat");

    public AutoEat() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(autoEat.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(autoEat.addHotkey(), new MultiKeyBind(), autoEat.addEnable())
            .build();

    public final FlagRef log =
            builder(autoEat.add("log"), Boolean.class).defaultValue(true).build();

    public final FlagRef inv =
            builder(autoEat.add("inv"), Boolean.class).defaultValue(true).build();

    public final FlagRef forceEatLeftClick =
            flagBuilder(autoEat.add("left-click-tool-force-eat")).build();

    public final FlagRef enableHealth = builder(autoEat.add("enable-health"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef enableHunger = builder(autoEat.add("enable-hunger"), Boolean.class)
            .defaultValue(true)
            .build();

    public final DoubleRef healthLevel = doubleBuilder(autoEat.add("health-level"))
            .defaultValue(10.0D)
            .validator(Configs.doubleRange(0.0D, 20.0D))
            .build();

    public final IntRef hungerLevel = intBuilder(autoEat.add("hunger-level"))
            .defaultValue(16)
            .validator(Configs.intRange(0, 20))
            .build();

    public final FlagRef noEnemy =
            builder(autoEat.add("no-enemy"), Boolean.class).defaultValue(true).build();

    public final DoubleRef noEnemyAir = doubleBuilder(autoEat.add("no-enemy-distance-air"))
            .defaultValue(8.0D)
            .validator(Configs.doubleRange(0.0D, 64.0D))
            .build();

    public final DoubleRef noEnemyGround = doubleBuilder(autoEat.add("no-enemy-distance-ground"))
            .defaultValue(8.0D)
            .validator(Configs.doubleRange(0.0D, 64.0D))
            .build();

    public final IntRef cooldown =
            intBuilder(autoEat.add("cooldown")).defaultValue(20).build();

    public final NBTRef<RegistryRegex<Item>> whiteListItem = builder(
                    autoEat.add("white-list-item"), RegistryRegex.ITEM_TYPE)
            .defaultValue(new RegistryRegex<>(new Regex("^(golden_apple|potion|golden_carrot)$"), Registries.ITEM))
            .build();

    public final FlagRef fireworkFix = builder(autoEat.add("firework-fix"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef autoFireworks = builder(autoEat.add("auto-fireworks"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef pauseInLava = builder(autoEat.add("pause-in-liquid"), Boolean.class)
            .defaultValue(false)
            .build();

    private boolean eating;
    private Runnable restoreCallback = null;
    private int eatingSlot = -1;
    private int eatingCooldownTick = 0;
    private int nextTickStartEat = 0;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onTickPre);
        registerListener(Listener.getPostHandleInputEvents(), this::onTickPost);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(EntityStatusS2CPacket.class), this::onStatusConsumed);
        registerListener(Listener.getPrePlayerUseItem(), this::onRightClick);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        stopEating();
    }

    private void onStatusConsumed(Event<EntityStatusS2CPacket> event) {
        if (eating
                && event.context.getEntity(mc.world) == mc.player
                && event.context.getStatus() == EntityStatuses.CONSUME_ITEM) {
            stopEating();
            eatingCooldownTick = Tasks.getTick() + cooldown.get();
        }
    }

    private boolean canContinueEat() {
        return eatingSlot >= 0
                && mc.player.isUsingItem()
                && (mc.player.getActiveHand() == (eatingSlot == 40 ? Hand.OFF_HAND : Hand.MAIN_HAND))
                && (InventoryUtils.getSelectedSlot() == eatingSlot || eatingSlot == 40)
                && eating;
    }

    private IndexEntry<ItemStack> findHandStack(boolean health) {
        ItemStack stack = mc.player.getMainHandStack();
        ItemStack stackOffhand = mc.player.getOffHandStack();
        Double main = scoreFood(stack, health);
        Double off = scoreFood(stackOffhand, health);
        if (main != null) {
            if (off != null && off > main) {
                return new IndexEntry<>(40, stackOffhand);
            }
            return new IndexEntry<>(InventoryUtils.getSelectedSlot(), stack);
        }
        return off != null ? new IndexEntry<>(40, stackOffhand) : null;
    }

    private IndexEntry<ItemStack> findFood(boolean useInv) {
        // TODO VALIDATE
        boolean healthPriority = enableHealth.get() && mc.player.getHealth() <= healthLevel.get();
        return useInv
                ? InventoryUtils.findBestPlayerItem(stack -> scoreFood(stack, healthPriority), true, false)
                : findHandStack(healthPriority);
    }

    private void tryStartEating(@Nonnull IndexEntry<ItemStack> re, boolean offHand) {

        if (log.get()) {
            Text text = re.val().getName();
            Debug.chat(ChatUtils.stringToText("&c[Eat] &fStart to eat"), text);
        }
        offHand = offHand || re.index() == 40;
        Runnable cbb = offHand
                ? InvExtra.INSTANCE.swapInventoryIndexToOffhand(re.index())
                : InvExtra.INSTANCE.swapInventoryIndexToHand(re.index());
        if (cbb != null) {
            ClientAccess.of(mc).simulateUseItem(offHand ? Hand.OFF_HAND : Hand.MAIN_HAND);
            if (mc.player.isUsingItem()
                    && ((mc.player.getActiveHand() == Hand.OFF_HAND) == offHand)
                    && ItemStack.areItemsAndComponentsEqual(re.val(), mc.player.getActiveItem())) {
                mc.options.useKey.setPressed(true);
                eating = true;
                restoreCallback = cbb;
                eatingSlot = offHand ? 40 : InventoryUtils.getSelectedSlot();
            } else {
                KeyBindAccess.of(mc.options.useKey).resetKeyState();
                cbb.run();
            }
        }
    }

    private void onWorldSwitch(Event<World> event) {
        stopEating();
    }

    private void stopEating() {
        if (eating) {
            KeyBindAccess.of(mc.options.useKey).resetKeyState(); // mc.options.useKey.setPressed(false);
            if (!checkNull() && restoreCallback != null) {
                restoreCallback.run();
            }
        }
        restoreCallback = null;
        eating = false;
        eatingSlot = -1;
        eatingCooldownTick = Tasks.getTick() + cooldown.get();
    }

    boolean lastAutoFireworkIsDone = false;

    public void onTickPre(Event<Void> event) {
        ClientPlayerEntity player = mc.player;
        if (checkNull()) {
            if (eating) {
                stopEating();
            }
        }
        if (eating) {
            if (canContinueEat()) {
                mc.options.useKey.setPressed(true);
            } else {
                if (log.get()) {
                    Debug.chat(ChatUtils.stringToText("&c[Eat] &fStop Eating"));
                }
                stopEating();
            }
        }
    }

    public boolean mayUseItem() {
        if (mc.player.isUsingItem()) {
            return true;
        } else if ((VItem.getInstance().isSpear(mc.player.getStackInHand(Hand.MAIN_HAND))
                || VItem.getInstance().isSpear(mc.player.getStackInHand(Hand.OFF_HAND)))) {
            if (mc.options.useKey.isPressed()) {
                return true;
            }
            if (InteractionTasks.getAutoUse().lastAutoUsingSpear) {
                return true;
            }
        }
        return false;
    }

    public void onTickPost(Event<Void> event) {
        if (checkNull()) {
            return;
        }
        ClientPlayerEntity player = mc.player;
        if (enable.get()) {
            if (!eating) {
                boolean canStartEat = false;
                boolean useInv = inv.get();
                boolean offHand = false;
                if (!mayUseItem()) {
                    find_eat_condition:
                    {
                        if (nextTickStartEat != 0) {
                            canStartEat = true;
                            useInv = true;
                            offHand = nextTickStartEat > 1;
                            nextTickStartEat = 0;
                            break find_eat_condition;
                        }
                        if (eatingCooldownTick > Tasks.getTick()) {
                            break find_eat_condition;
                        }
                        if (pauseInLava.get()
                                && (PlayerStateManager.INSTANCE.lastInLava || PlayerStateManager.INSTANCE.lastInWall)) {
                            break find_eat_condition;
                        }
                        if (noEnemy.get()) {
                            double dist = mc.player.isFallFlying() ? noEnemyAir.get() : noEnemyGround.get();
                            if (dist > 1E-6
                                    && TargetSelector.INSTANCE.searchAttackEntity(
                                                    dist, true, (pl) -> pl instanceof PlayerEntity)
                                            != null) {
                                break find_eat_condition;
                            }
                        }
                        if (enableHealth.get() && player.getHealth() <= healthLevel.get()) {
                            canStartEat = true;
                            break find_eat_condition;
                        }
                        if (enableHunger.get() && player.getHungerManager().getFoodLevel() <= hungerLevel.get()) {
                            canStartEat = true;
                            break find_eat_condition;
                        }
                    }
                }
                if (canStartEat) {
                    boolean canStartNow = true;
                    var re = findFood(useInv);
                    if (re == null) {
                        return;
                    }
                    if (fireworkFix.get() && player.isFallFlying()) {
                        // using
                        if (ElytraExtra.INSTANCE.getTicksSinceLastFireworkSpawn() > 10) {
                            canStartNow = false;
                        }
                    }
                    if (autoFireworks.get() && player.isFallFlying() && !canStartNow) {
                        // fresh
                        if (!lastAutoFireworkIsDone) {
                            ElytraExtra.INSTANCE.sendCustomUseFireworkPacket();
                            lastAutoFireworkIsDone = true;
                        }
                    }
                    if (canStartNow) {
                        lastAutoFireworkIsDone = false;
                        tryStartEating(re, false);
                    }
                }
            }
        }
    }

    public void onRightClick(Event<ActionResult> event) {
        Hand hand = event.getArgs(0);
        if (enable.get() && forceEatLeftClick.get() && mc.options.useKey.isPressed() && !eating) {
            ItemStack stack = mc.player.getStackInHand(hand);
            Hand offhand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
            ItemStack offhandStack = mc.player.getStackInHand(offhand);
            if ((VItem.getInstance().isTool(stack) || VItem.getInstance().isWeapon(stack))
                    && !VItem.getInstance().isSpear(stack)
                    && !InteractUtils.canHoldUse(offhandStack)) {
                var re = findFood(true);
                if (re == null) {
                    return;
                }
                boolean canStartEat = true;
                if (fireworkFix.get() && mc.player.isFallFlying()) {
                    // using
                    if (ElytraExtra.INSTANCE.getTicksSinceLastFireworkSpawn() > 10) {
                        canStartEat = false;
                    }
                }
                if (autoFireworks.get() && mc.player.isFallFlying() && !canStartEat) {
                    // fresh
                    if (!lastAutoFireworkIsDone) {
                        ElytraExtra.INSTANCE.sendCustomUseFireworkPacket();
                        lastAutoFireworkIsDone = true;
                    }
                }
                if (canStartEat) {
                    lastAutoFireworkIsDone = false;
                    tryStartEating(re, hand == Hand.OFF_HAND);
                    if (eating) {
                        event.cancel();
                        event.context(ActionResult.SUCCESS);
                    }
                }
            }
        }
    }

    private Double scoreFood(ItemStack stack, boolean hurtPriority) {
        if (!VItem.getInstance().isEatable(stack)) {
            return null;
        }
        if (!whiteListItem.get().test(stack.getItem())) {
            return null;
        }
        FoodComponent food = getFoodComponent(stack);

        double score;
        if (food != null) {
            if (mc.player.canConsume(food.canAlwaysEat())) {
                int hunger = food.nutrition();
                if (hunger > 0) {
                    score = food.saturation() / hunger;
                } else {
                    score = food.saturation();
                }
            } else {
                return null;
            }
        } else {
            score = 0;
        }
        // only combat eat gapple
        if (hurtPriority && mc.world.getPlayers().size() > 1) {
            if (isGoldenAppleFood(stack)) {
                score += 100.0D;
            }
            if (isHealingPotion(stack)) {
                score += 50.0D;
            }
        }
        if (score <= 0.0D) {
            return null;
        }
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

    private final Set<RegistryEntry<StatusEffect>> healingEffects = new HashSet<>();

    {
        healingEffects.add(StatusEffects.INSTANT_HEALTH);
        healingEffects.add(StatusEffects.REGENERATION);
    }

    private boolean isHealingPotion(ItemStack stack) {
        ConsumableComponent consumable = stack.get(DataComponentTypes.CONSUMABLE);
        if (consumable == null) {
            return false;
        }
        if (stack.streamAll(PotionContentsComponent.class).anyMatch(component -> Streams.of(component.getEffects())
                .map(StatusEffectInstance::getEffectType)
                .anyMatch(healingEffects::contains))) {
            return true;
        }
        for (var effect : consumable.onConsumeEffects()) {
            if (effect instanceof ApplyEffectsConsumeEffect apply
                    && apply.effects().stream()
                            .map(StatusEffectInstance::getEffectType)
                            .anyMatch(healingEffects::contains)) {
                return true;
            }
        }
        return false;
    }
}
