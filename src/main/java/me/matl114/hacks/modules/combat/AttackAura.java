package me.matl114.hacks.modules.combat;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.AttributeUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

public class AttackAura extends BaseModule {
    public final ModulePath attBot = makePath(Configs.COMBAT_CONFIG, "att-bot");
    public final ModulePath respectCooldown = attBot.add("respect-cooldown");

    public AttackAura() {
        super("AttackAura");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(attBot.add("auto-att")).build();

    public final KeyBindRef hotkey = moduleEntry(
                    attBot.add("auto-att-hotkey"), new MultiKeyBind(), attBot.add("auto-att"))
            .build();

    public final IntRef maxTargetPerTick = intBuilder(attBot.add("max-at-once"))
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef cooldownWeapon = builder(respectCooldown.add("weapon"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef cooldownHand = builder(respectCooldown.add("hand"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef cooldownUsePacketTime = builder(respectCooldown.add("packet-time"), Boolean.class)
            .defaultValue(true)
            .build();

    public final DoubleRef cooldownProgress = doubleBuilder(respectCooldown.add("progress-threshold"))
            .defaultValue(0.98)
            .validator(Configs.doubleRange(0.0D, 1.0D))
            .build();

    public final IntRef maxAttackInterval = intBuilder(respectCooldown.add("max-attack-interval"))
            .defaultValue(50)
            .show(this.cooldownUsePacketTime::get)
            .build();

    public final IntRef customRate = intBuilder(attBot.add("auto-att-rate"))
            .defaultValue(0)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final FlagRef doNotAttackWhenEat =
            flagBuilder(attBot.add("stop-attack-when-eat")).build();

    public final FlagRef doNotAttackWhenSpear =
            flagBuilder(attBot.add("stop-attack-when-spear")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        acceptor.accept(createTitleLabel("widget.attack.attack.use-argument", 0, dblank, dx, dy));
    }

    private int interval;
    private int lastArua;

    public boolean checkEating() {
        return doNotAttackWhenEat.get()
                && mc.player.isUsingItem()
                && VItem.getInstance().isEatable(mc.player.getActiveItem());
    }

    public boolean checkSpear() {
        return doNotAttackWhenSpear.get()
                && mc.player.isUsingItem()
                && VItem.getInstance().isSpear(mc.player.getActiveItem());
    }

    public boolean checkUsing() {
        return checkEating() || checkSpear();
    }

    private boolean isCooldown(Attack.AttackSettings settings, Entity entity) {
        if (cooldownUsePacketTime.get() && Tasks.getTick() - lastArua >= maxAttackInterval.get()) {
            return true;
        }
        int lastTime = cooldownUsePacketTime.get() ? PlayerStateManager.INSTANCE.lastAttackStrengthResetTick : lastArua;
        int strength = Tasks.getTick() - lastTime;
        IndexEntry<ItemStack> currentWeapon = Attack.selectBestWeapon(settings, entity);
        if (settings.maceSwap()
                && currentWeapon.val().isOf(Items.MACE)
                && PlayerStateManager.INSTANCE.fallDistance > 1.5) {
            return lastArua + 10 <= Tasks.getTick();
        }
        AttributeContainer swapContainer =
                AttributeUtils.getAttributeWith(mc.player, Map.of(EquipmentSlot.MAINHAND, currentWeapon.val()));
        double attackSpeed = swapContainer.getValue(EntityAttributes.ATTACK_SPEED);
        float perTick = (float) (1.0 / attackSpeed * 20.0);
        float progress = (float) MathHelper.clamp(((float) strength + 0.5) / perTick, 0.0F, 1.0F);
        return progress >= cooldownProgress.get();
    }

    public void onTick(Event<ClientPlayerEntity> tickEvent) {
        if (mc.player == null) return;
        if (enable.get()) {
            Attack attack = CombatTasks.getAttack();
            boolean holdingWeapon = CombatTasks.isHoldingWeapon(mc.player);
            // force consider attack interval legal mode
            interval += 1;
            int custom = customRate.get();
            if (custom <= interval) {

                if (attack.legalTargetingMode.get().isLegal()
                        || ((holdingWeapon && cooldownWeapon.get()) || (!holdingWeapon && cooldownHand.get()))) {
                    // do not attack because of legal mode
                    if (checkUsing()) return;
                    Entity entity = attack.getCurrentSelectTarget(true);
                    if (entity == null) return;
                    Attack.AttackSettings settings = attack.createAttackSettings();
                    if (isCooldown(settings, entity)) {
                        // ready for attack
                        // force attack
                        // 十分之七的概率当前攻击， 以此制作概率性的攻击时延
                        interval = 0;
                        attack.attackEntity(entity, settings);
                        lastArua = Tasks.getTick();
                        // 移除随机数,史
                        //                        if (timeRandom.nextInt(10) > 6) {
                        //
                        //                        }
                    }
                } else {
                    // attack! attack! attack!

                    List<Entity> targets = attack.getCurrentRangeEntities();
                    int max = maxTargetPerTick.get();
                    if (!targets.isEmpty()) {
                        // attack this kick
                        interval = 0;
                        lastArua = Tasks.getTick();
                        for (Entity target : targets) {
                            if (attack.attackEntity(target, attack.createAttackSettings())) break;
                            if (--max <= 0) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
