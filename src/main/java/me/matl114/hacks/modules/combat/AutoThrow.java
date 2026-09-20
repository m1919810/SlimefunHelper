package me.matl114.hacks.modules.combat;

import com.google.common.base.Predicates;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.swing.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.SequencedActionManager;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CollisionUtil;
import me.matl114.utils.InventoryUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class AutoThrow extends BaseModule {
    public AutoThrow() {
        super("AutoThrow");
    }

    public final ModulePath root = makePath(Configs.COMBAT_CONFIG, "combat-utils.auto-throw");

    public final FlagRef ground = flagBuilder(root.add("ground-throw")).build();

    public final DoubleRef acceptHeight =
            doubleBuilder(root.add("ground-height")).defaultValue(0.5).build();

    public final FlagRef ceiling = flagBuilder(root.add("ceiling-throw")).build();

    public final DoubleRef ceilingHeight =
            doubleBuilder(root.add("ceiling-height")).defaultValue(0.5).build();

    public final FlagRef flyAlways = flagBuilder(root.add("fly-always")).build();

    public final FlagRef upFly = flagBuilder(root.add("up-fly")).build();

    public final FlagRef avoidEnemies = flagBuilder(root.add("avoid-enemies")).build();

    public final DoubleRef avoidEnemyDistance =
            doubleBuilder(root.add("avoid-enemies-distance")).defaultValue(1.5).build();

    public final FlagRef eatingAbort = builder(root.add("using-item-abort"), Boolean.class)
            .defaultValue(false)
            .build();
    public final FlagRef offhand = flagBuilder(root.add("offhand")).build();
    public final EnumRef<GhostHandMode> ghostHand = builder(root.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final FlagRef swingHand =
            builder(root.add("swing-hand"), Boolean.class).defaultValue(true).build();

    public final ModulePath xp = root.add("xp-bottles");

    public final ModulePath potions = root.add("potions");

    public final FlagRef xpEnable = flagBuilder(xp.addEnable()).build();

    public final KeyBindRef xpHotkey =
            moduleEntry(xp.addHotkey(), new MultiKeyBind(), xp.addEnable()).build();

    public final FlagRef checkDurability =
            flagBuilder(xp.add("check-durability")).build();

    public final IntRef startFixArmorDur =
            intBuilder(xp.add("start-fixing-durability")).defaultValue(100).build();

    public final IntRef stopFixArmorDur =
            intBuilder(xp.add("stop-fixing-durability")).defaultValue(50).build();

    public final IntRef delay = intBuilder(xp.add("delay")).defaultValue(3).build();
    public final IntRef mul = intBuilder(xp.add("multiply")).defaultValue(8).build();

    public final FlagRef autoClose = flagBuilder(xp.add("auto-close")).build();

    public final FlagRef potionEnable = flagBuilder(potions.addEnable()).build();

    public final KeyBindRef potionHotkey = moduleEntry(potions.addHotkey(), new MultiKeyBind(), potions.addEnable())
            .build();

    private final TimerExecutor effectCheck = new TimerExecutor();

    public final NBTRef<EntrySet<StatusEffect>> keepPotions = builder(
                    potions.add("keep-potion"), EntrySet.<StatusEffect>parameter())
            .defaultValue(new EntrySet<>(Registries.STATUS_EFFECT, List.of()))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
    }

    boolean startMending = false;

    private boolean canThrow() {
        Box box = mc.player.getBoundingBox();
        if (avoidEnemies.get()) {
            Box box2 = box.expand(avoidEnemyDistance.get(), avoidEnemyDistance.get(), avoidEnemyDistance.get());
            if (TargetSelector.INSTANCE.getAttackableEntities(3).stream()
                    .filter(s -> s instanceof PlayerEntity)
                    .anyMatch(s -> s.getBoundingBox().intersects(box2))) {
                return false;
            }
        }
        if (ground.get()) {
            box = box.stretch(0, -acceptHeight.get(), 0);
        }
        if (ceiling.get()) {
            box = box.stretch(0, ceilingHeight.get(), 0);
        }
        if (CollisionUtil.isBoxCollided(mc.world, mc.player, box)) {
            return true;
        }

        if (upFly.get()) {
            Vec3d vec3d = PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed.normalize();
            if (EntityUtils.rotationToPitch(vec3d) < -80 && PlayerStateManager.INSTANCE.lastPitch < -80) {
                return true;
            }
        }
        if (flyAlways.get()) {
            if (mc.player.isFallFlying()
                    && PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed.horizontalLengthSquared() < 1E-2) {
                return true;
            }
        }
        return false;
    }

    private boolean canMend() {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.isDamageable()) {
                continue;
            }

            if (stack.getDamage() >= stopFixArmorDur.get()) {
                return true;
            }
        }

        return false;
    }

    private boolean needMending() {
        int startFix = startFixArmorDur.get();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.isDamageable()) {
                continue;
            }
            if (stack.getDamage() >= startFix) {
                return true;
            }
        }
        return false;
    }

    int timer = 0;

    public void onPreInputEvent(Event<Void> event) {
        if (checkNull()) return;
        if (canThrow()) {
            if (eatingAbort.get() && mc.player.isUsingItem()) return;
            if (++timer >= delay.get()) {
                timer = 0;
                //
                if (xpEnable.get()) {
                    if (checkDurability.get()) {
                        if (startMending) {
                            if (canMend()) {
                                throwItem((item) -> item.isOf(Items.EXPERIENCE_BOTTLE) ? 1.0D : null, mul.get());
                            } else {
                                startMending = false;
                                if (autoClose.get()) {
                                    HotKeyUtils.wrapFlagAsToggle(xp.addEnable().toPath(), xpEnable)
                                            .run();
                                }
                            }
                        } else {
                            if (needMending()) {
                                startMending = true;
                            }
                        }
                    } else {
                        startMending = false;
                        throwItem((item) -> item.isOf(Items.EXPERIENCE_BOTTLE) ? 1.0D : null, mul.get());
                    }

                } else {
                    startMending = false;
                }
                if (potionEnable.get() && effectCheck.run(5)) {
                    if (SequencedActionManager.INSTANCE.isWaitingResponse(s -> s.isOf(Items.SPLASH_POTION))) {
                        return;
                    }
                    // check flying potions

                    Set<RegistryEntry<StatusEffect>> onePotion = new HashSet<>();
                    for (var re : keepPotions.get().set()) {
                        var entry = Registries.STATUS_EFFECT.getEntry(re);
                        var statusInstance = mc.player.getStatusEffect(entry);
                        if (statusInstance == null || statusInstance.isDurationBelow(20)) {
                            onePotion.add(entry);
                        }
                    }
                    Box checkUpperBox =
                            mc.player.getBoundingBox().expand(3.6, 8.0, 3.6).stretch(0, 5, 0);
                    mc.world
                            .getEntitiesByType(EntityType.SPLASH_POTION, checkUpperBox, Predicates.alwaysTrue())
                            .forEach(potion -> {
                                var po = potion.getStack().get(DataComponentTypes.POTION_CONTENTS);
                                if (po != null) {
                                    po.getEffects().forEach(effect -> {
                                        onePotion.remove(effect.getEffectType());
                                    });
                                }
                            });
                    if (!onePotion.isEmpty()) {
                        throwItem(
                                s -> {
                                    if (s.isOf(Items.SPLASH_POTION) && s.contains(DataComponentTypes.POTION_CONTENTS)) {
                                        var con = s.get(DataComponentTypes.POTION_CONTENTS);
                                        if (con == null) return null;
                                        int totalLevel = 0;
                                        for (var re : con.getEffects()) {
                                            if (onePotion.contains(re.getEffectType())) {
                                                totalLevel += re.getAmplifier();
                                            }
                                        }
                                        return totalLevel > 0 ? (double) totalLevel : null;
                                    }
                                    return null;
                                },
                                1);
                    }
                }
            } else {
                return;
            }
        }
    }

    public void throwItem(Function<ItemStack, Double> stackPredicate, int multiply) {
        Box box = mc.player.getBoundingBox();
        Box groundCheck = box.stretch(0, -acceptHeight.get(), 0);
        Box ceilingCheck = box.stretch(0, ceilingHeight.get(), 0);
        float pitch;
        boolean needSpeed = false;
        if (CollisionUtil.isBoxCollided(mc.world, mc.player, groundCheck)) {
            pitch = 90.0F;
        } else if (CollisionUtil.isBoxCollided(mc.world, mc.player, ceilingCheck)) {
            pitch = -90.0F;
        } else {
            needSpeed = PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed.y > 0.1;
            pitch = -90.0F;
        }
        var entry = InventoryUtils.findBestPlayerItem(
                stackPredicate, ghostHand.get().getSearchSize(offhand.get()), true, false);
        if (entry == null) return;
        int mul = Math.min(multiply, entry.val().getCount());
        var cb = InvExtra.INSTANCE.swapItemToHand(entry.index(), offhand.get(), ghostHand.get());
        if (cb != null) {
            for (var re = 0; re < mul; ++re) {
                if (needSpeed) {
                    mc.interactionManager.sendSequencedPacket(
                            mc.world,
                            (seq) -> new PlayerInteractItemC2SPacket(
                                    offhand.get() ? Hand.OFF_HAND : Hand.MAIN_HAND,
                                    seq,
                                    PlayerStateManager.INSTANCE.lastYaw,
                                    pitch));
                    if (swingHand.get()) {
                        mc.player.swingHand(offhand.get() ? Hand.OFF_HAND : Hand.MAIN_HAND);
                    }
                } else {
                    InteractionTasks.interactItem(
                            offhand.get() ? Hand.OFF_HAND : Hand.MAIN_HAND,
                            pitch,
                            PlayerStateManager.INSTANCE.lastYaw,
                            true,
                            swingHand.get());
                }
            }
            cb.run();
        }
    }
}
