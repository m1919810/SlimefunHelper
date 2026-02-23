package me.matl114.hacks.modules.combat;

import java.util.List;
import java.util.Random;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

public class AttackArua extends BaseModule {
    public static final String[] DO_INTERVEL_WEAPON = {"att-bot", "respect-cooldown", "weapon"};
    public static final String[] DO_INTERVEL_HAND = {"att-bot", "respect-cooldown", "hand"};

    public static final String[] AUTO_ATTACK = {"hotkeys-toggle", "auto-att"};
    public static final String[] ONCE_MAX = {"att-bot", "max-at-once"};

    public static final String[] CUSTOM_ATTACKING_RATE = {"att-bot", "auto-att-rate"};

    public AttackArua() {
        bindFlag(enable);
    }

    public final FlagRef enable = toggle(AUTO_ATTACK).build();

    public final KeyBindRef hotkey = toggleHotkey(
                    AUTO_ATTACK,
                    new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_LEFT_SHIFT, KeyCode.KEY_APOSTROPHE))
            .build();

    public final IntRef maxTargetPerTick = builder(Configs.COMBAT_CONFIG, ONCE_MAX, Integer.class)
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef cooldownWeapon = builder(Configs.COMBAT_CONFIG, DO_INTERVEL_WEAPON, Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef cooldownHand = builder(Configs.COMBAT_CONFIG, DO_INTERVEL_HAND, Boolean.class)
            .defaultValue(true)
            .build();

    public final IntRef customRate = builder(Configs.COMBAT_CONFIG, CUSTOM_ATTACKING_RATE, IntRef.TYPE)
            .defaultValue(0)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getGameTick(), this::onTick);
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
                if (attack.legalMode.get()
                        || ((holdingWeapon && cooldownWeapon.get()) || (!holdingWeapon && cooldownHand.get()))) {
                    // do not attack because of legal mode

                    if (mc.player.getAttackCooldownProgress(0.5F) > 0.98) {
                        // ready for attack
                        // force attack
                        // 十分之七的概率当前攻击， 以此制作概率性的攻击时延
                        if (timeRandom.nextInt(10) > 6) {
                            interval = 0;
                            attack.tryAttack(true);
                        }
                    }
                } else {
                    // attack! attack! attack!

                    List<Entity> targets = attack.getCurrentRangeEntities();
                    int max = maxTargetPerTick.get();
                    if (!targets.isEmpty()) {
                        // attack this kick
                        interval = 0;
                        for (Entity target : targets) {
                            if (attack.attackEntity(target)) break;
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
