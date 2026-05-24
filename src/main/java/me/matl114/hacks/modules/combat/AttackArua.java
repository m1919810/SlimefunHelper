package me.matl114.hacks.modules.combat;

import java.util.List;
import java.util.Random;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

public class AttackArua extends BaseModule {
    public final ModulePath attBot = makePath(Configs.COMBAT_CONFIG, "att-bot");
    public final ModulePath respectCooldown = attBot.add("respect-cooldown");

    public AttackArua() {
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

    public final IntRef customRate = intBuilder(attBot.add("auto-att-rate"))
            .defaultValue(0)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final FlagRef doNotAttackWhenEat =
            flagBuilder(attBot.add("stop-attack-when-eat")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
    }

    private int interval;
    private final Random timeRandom = new Random();

    public void onTick(Event<ClientPlayerEntity> tickEvent) {
        if (mc.player == null) return;
        if (enable.get()) {
            Attack attack = CombatTasks.getAttack();
            boolean holdingWeapon = CombatTasks.isHoldingWeapon(mc.player);
            // force consider attack interval legal mode
            interval += 1;
            int custom = customRate.get();
            if (custom <= interval) {
                if (doNotAttackWhenEat.get()
                        && mc.player.isUsingItem()
                        && VItem.getInstance().isEatable(mc.player.getActiveItem())) {
                    return;
                }
                if (attack.legalMode.get()
                        || ((holdingWeapon && cooldownWeapon.get()) || (!holdingWeapon && cooldownHand.get()))) {
                    // do not attack because of legal mode

                    if (mc.player.getAttackCooldownProgress(0.5F) > 0.98) {
                        // ready for attack
                        // force attack
                        // 十分之七的概率当前攻击， 以此制作概率性的攻击时延
                        interval = 0;
                        attack.tryAttack(true);
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
