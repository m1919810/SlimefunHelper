package me.matl114.hacks.modules.move;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Predicate;
import lombok.Getter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.PlayerInteractItemC2SPacketAccess;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.channels.EventChannel;
import me.matl114.events.impl.EventContainer;
import me.matl114.events.impl.MetadataUpdate;
import me.matl114.events.packets.PacketStorage;
import me.matl114.events.packets.PacketStorageRecvImpl;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.interact.SequencedActionManager;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.task.DisablerManager;
import me.matl114.hacks.modules.task.MovementManager;
import me.matl114.hacks.modules.task.PacketOrderManager;
import me.matl114.hacks.modules.task.PostManager;
import me.matl114.hacks.utils.*;
import me.matl114.hacks.utils.PlayerInputUtils;
import me.matl114.hacks.utils.config.*;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.hacks.utils.enums.BypassMode;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.move.ElytraMotion;
import me.matl114.hacks.utils.move.ElytraOptimizeUtils;
import me.matl114.hacks.utils.tasks.CounterExecutor;
import me.matl114.hacks.utils.tasks.StateExecutor;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import me.matl114.versioned.api.VPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.packet.PlayPackets;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public class ElytraExtra extends BaseModule implements LegalMovementManager.MovementModifier {
    public static ElytraExtra INSTANCE;

    @Getter
    private static final EventChannel<ElytraMotion> fireworkMotion = new EventChannel<>();

    private static LegalMovementManager.DelegateMovementModifier instance;

    public final ModulePath elytra = makePath(Configs.MOV_CONFIG, "elytra");
    public final ModulePath elytraTweaks = elytra.add("elytra-tweaks");
    public final ModulePath unbreakableElytra = elytra.add("unbreakable-elytra");
    public final ModulePath armorFlyPath = elytra.add("armor-fly");
    public final ModulePath elytraBlink = elytra.add("elytra-blink");
    public final ModulePath customFireworksPath = elytra.add("custom-fireworks");

    public ElytraExtra() {
        super("ElytraExtra");
        MovementManager.registerMovementPipeline(this);
        INSTANCE = this;
    }

    public final NBTRef<OptionalPrimitive<WrapEnum<BypassMode>>> noKineticMode = builder(
                    elytraTweaks.add("no-kinetic-mode"), OptionalPrimitive.configEnum(BypassMode.class))
            .defaultValue(new OptionalPrimitive<>(
                    false, NBTTypes.CONFIG_ENUM_TYPE.cast(), new WrapEnum<>(BypassMode.NO_BYPASS)))
            .show(() -> !this.armorFly.get())
            .build();

    // todo: remove this shit,

    public final FlagRef noFallLanding =
            flagBuilder(elytraTweaks.add("no-fall-when-landing")).build();

    public final FlagRef autoSwitch =
            flagBuilder(elytraTweaks.add("auto-switch")).build();

    public final KeyBindRef switchElytra = hotkey(elytraTweaks.add("manually-switch"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::switchElytra))
            .build();

    public final NBTRef<OptionalPrimitive<Integer>> autoMaintain = builder(
                    elytraTweaks.add("auto-maintain"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.INT_TYPE, 10))
            .build();

    public final KeyBindRef autoTakeOff = hotkey(elytraTweaks.add("auto-take-off"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::autoTakeoff))
            .build();

    public final FlagRef autoTakeOffFireworksFromGround =
            flagBuilder(elytraTweaks.add("auto-take-off-fireworks-from-ground")).build();

    public final IntRef autoTakeOffRetryGt = intBuilder(elytraTweaks.add("auto-take-off-retry-ticks"))
            .defaultValue(3)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final FlagRef autoTakeOffWhenJoinServer =
            flagBuilder(elytraTweaks.add("auto-start-fly-join-server")).build();

    public final NBTRef<OptionalPrimitive<Integer>> timeoutReflight = builder(
                    elytraTweaks.add("timeout-reflight"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.INT_TYPE, 40))
            .build();

    public final NBTRef<OptionalPrimitive<Integer>> takeOffForceCooldown = builder(
                    elytraTweaks.add("take-off-force-cooldown"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.INT_TYPE, 7))
            .build();

    public final FlagRef antiKick = builder(elytraTweaks.add("anti-kick"), Boolean.class)
            .defaultValue(true)
            .build();

    //    public final FlagRef elytraResyncBadPacketFix =
    //            flagBuilder(elytraTweaks.add("fix-duplicate-elytra-state-sync")).build();

    // public final FlagRef elytraAntiKB = flagBuilder(Configs.MOV_CONFIG, ELYTRA_ANTI_KB).build();

    public final FlagRef enableUnbreakableElytra =
            flagBuilder(unbreakableElytra.add("enable")).build();

    public final KeyBindRef unbreakableHotkey = toggleHotkey(
                    unbreakableElytra.addHotkey(), new MultiKeyBind(), unbreakableElytra.addEnable())
            .build();

    public final IntRef period = intBuilder(unbreakableElytra.add("period"))
            .defaultValue(16)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef resetVanilla =
            flagBuilder(unbreakableElytra.add("reset-vanilla")).build();

    public final FlagRef armorFly = flagBuilder(armorFlyPath.add("enable"))
            .updateListener(this::onToggleArmorFly)
            .build();

    public final KeyBindRef keyBind = moduleEntry(
                    armorFlyPath.add("enable-hotkey"), new MultiKeyBind(), armorFlyPath.add("enable"))
            .build();

    public final EnumRef<ArmorFlyMode> armorMode = builder(armorFlyPath.add("armor-mode"), ArmorFlyMode.class)
            .defaultValue(ArmorFlyMode.TICK)
            .build();

    public final FlagRef enableLiquidFly =
            flagBuilder(armorFlyPath.add("enable-liquid-fly")).build();

    public final FlagRef enableLiquidFlyOnlyFireworks =
            flagBuilder(armorFlyPath.add("liquid-fly-only-fireworks")).build();

    public final FlagRef enableOnGroundFly =
            flagBuilder(armorFlyPath.add("on-ground-fly-only-fireworks")).build();

    public final FlagRef landAutoClose =
            flagBuilder(armorFlyPath.add("land-auto-close")).build();

    public final FlagRef landAutoSneak =
            flagBuilder(armorFlyPath.add("land-auto-sneak")).build();

    public final NBTRef<OptionalPrimitive<Integer>> closeContinueFly = builder(
                    armorFlyPath.add("close-continue-fly"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(true, NBTTypes.INT_TYPE, 20))
            .validator((v) -> v.getValue() >= 5)
            .build();

    public final FlagRef poseFix = flagBuilder(armorFlyPath.add("pose-fix")).build();

    public final FlagRef renderFix = flagBuilder(armorFlyPath.add("render-fix")).build();

    public final FlagRef handControl = builder(armorFlyPath.add("enable-manually-swap"), Boolean.class)
            .defaultValue(true)
            .build();

    public final NBTRef<OptionalPrimitive<Integer>> armorGlideMaxDelayTicks = builder(
                    armorFlyPath.add("max-delay-ticks"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.INT_TYPE, 5))
            .validator(value -> value.getValue() >= 0)
            .build();

    public final IntRef armorGlideStopFlyingDelayTicks = intBuilder(armorFlyPath.add("delay-ticks"))
            .defaultValue(1)
            .validator(Configs.intRange(0, 1000))
            .show(() -> this.armorMode.get() == ArmorFlyMode.TICK_3C3U)
            .build();

    public final IntRef armorGlideTakeoffIntervalTicks = intBuilder(armorFlyPath.add("takeoff-interval-ticks"))
            .defaultValue(1)
            .validator(Configs.intRange(0, 1000))
            .show(() -> this.armorMode.get() == ArmorFlyMode.TICK_3C3U)
            .build();

    public final DoubleRef fireworkMaxTicks = doubleBuilder(
                    customFireworksPath.add("firework-max-delay-multiply-vanilla"))
            .defaultValue(20.0D)
            .validator(Configs.doubleRange(0.0D, 100.0D))
            .build();

    public final FlagRef offHand = builder(customFireworksPath.add("firework-offhand"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FireworkTimer timerVanilla = new FireworkTimer(fireworkMaxTicks);

    public final FlagRef autoRescale =
            flagBuilder(customFireworksPath.add("auto-rescale-firework-box")).build();

    public final DoubleRef autoRescaleAmount = doubleBuilder(customFireworksPath.add("auto-rescale-firework-amount"))
            .defaultValue(1.65D)
            .validator(Configs.doubleRange(0.0D, Double.MAX_VALUE))
            .show(this.autoRescale::get)
            .build();

    public final FlagRef fireworkSpeedLimitWeb = flagBuilder(customFireworksPath.add("firwork-speed-limit-web"), true)
            .build();

    public final NBTRef<OptionalPrimitive<Double>> fireworkSpeedLimitUp = builder(
                    customFireworksPath.add("firework-speed-limit-up"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 5.0D))
            .show(this.autoRescale::get)
            .build();

    public final NBTRef<OptionalPrimitive<Double>> fireworkSpeedLimitDown = builder(
                    customFireworksPath.add("firework-speed-limit-down"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 5.0D))
            .show(this.autoRescale::get)
            .build();

    public final EnumRef<Al> autoRescaleAl = builder(customFireworksPath.add("auto-rescale-firework-al"), Al.class)
            .defaultValue(Al.V1)
            .show(this.autoRescale::get)
            .build();

    public final DoubleRef autoRescaleThreshold = doubleBuilder(
                    customFireworksPath.add("auto-rescale-firework-anti-lag-threshold"))
            .defaultValue(0.002)
            .build();

    @ApiStatus.Experimental
    public final KeyBindRef switchAlKey = hotkey(
                    customFireworksPath.add("switch-auto-rescale-firework-al-key"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(() -> {
                do {
                    this.autoRescaleAl.next();
                } while (autoRescaleAl.get().ordinal() < Al.V3.ordinal()
                        || autoRescaleAl.get().isPullupOnly());
                logI18NSub(
                        "Firework",
                        "message.module.elytra-extra.al-switch",
                        this.autoRescaleAl.get().getDisplay());
            }))
            .experimental()
            .build();

    public final FlagRef rocketBoost =
            flagBuilder(customFireworksPath.add("firework-boost-enable")).build();

    public final KeyBindRef rocketBoostHotkey = toggleHotkey(
                    customFireworksPath.add("firework-boost-hotkey"),
                    new MultiKeyBind(),
                    customFireworksPath.add("firework-boost-enable"))
            .build();

    public final DoubleRef rocketBoostSpeed = doubleBuilder(customFireworksPath.add("firework-boost-speed"))
            .defaultValue(1.7D)
            .validator(Configs.doubleRange(0.0d, 10000.0D))
            .build();

    public final FlagRef rocketBoostUseRescale = flagBuilder(customFireworksPath.add("firework-boost-use-rescale"))
            .show(this.autoRescale::get)
            .build();

    public final FlagRef flyRocketOnFirstOff =
            flagBuilder(customFireworksPath.add("fly-rocket-on-first-off")).build();

    public final KeyBindRef flyRocketOnFirstOffHotkey = toggleHotkey(
                    customFireworksPath.add("fly-rocket-on-first-off-hotkey"),
                    new MultiKeyBind(),
                    customFireworksPath.add("fly-rocket-on-first-off"))
            .build();

    public final KeyBindRef clickRocket = hotkey(customFireworksPath.add("click-firework-use"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::clickRocket))
            .build();

    public final FlagRef noEnoughFireworksNotify =
            flagBuilder(customFireworksPath.add("log-no-fireworks")).build();

    public final FlagRef fireworksLagDelay =
            flagBuilder(customFireworksPath.add("fireworks-lag-delay")).build();

    public final KeyBindRef fireworkLagHotkey = toggleHotkey(
                    customFireworksPath.add("firework-lag-delay-hotkey"),
                    new MultiKeyBind(),
                    customFireworksPath.add("fireworks-lag-delay"))
            .build();

    public final IntRef fireworksDelayMS = builder(customFireworksPath.add("fireworks-delay-ms"), Integer.class)
            .defaultValue(500)
            .validator(Configs.intRange(0, 3600))
            .build();

    public final FlagRef elytraBlinkEnable =
            flagBuilder(elytraBlink.addEnable()).build();

    public final KeyBindRef hotkeyElytraBlink = toggleHotkey(
                    elytraBlink.addHotkey(), new MultiKeyBind(), elytraBlink.addEnable())
            .build();

    public final IntRef minBlinkTicks =
            intBuilder(elytraBlink.add("min-blink-ticks")).defaultValue(1).build();

    public final IntRef maxBlinkTicks =
            intBuilder(elytraBlink.add("max-blink-ticks")).defaultValue(12).build();

    public final FlagRef blinkDelayInBound =
            flagBuilder(elytraBlink.add("blink-delay-in-bound")).build();

    public final FlagRef blinkAutoSneakResync =
            flagBuilder(elytraBlink.add("blink-sneak-auto-resync"), true).build();

    public final FlagRef blinkFreezeWhenWait =
            flagBuilder(elytraBlink.add("blink-freeze-when-wait")).build();
    // public final FlagRef useFireworks =
    //        flagBuilder(Configs.MOV_CONFIG, ELYTRA_FLIGHT_CONTROL_FIREWORKS).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerFallFlyingTick(), this::runElytraUnbreakable);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.PLAYER), this::handleEntityDataUpdate);
        registerListener(Listener.getPlayerSwitchFallFlying(), this::onLimitStartFallFlyingSpeed, Integer.MIN_VALUE);
        registerListener(Listener.getPlayerSwitchFallFlying(), this::onStartFallFlying);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetBack);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInteractItemC2SPacket.class), this::onUseFireworks);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.FIREWORK_ROCKET), this::onFireworkOwner);
        registerListener(
                Listener.getPacketPoint().getChannel(EntitiesDestroyS2CPacket.class), this::onFireworkRemovePacketPeek);
        registerListener(
                Listener.getEntityRemoveListener().getChannel(EntityType.FIREWORK_ROCKET), this::onFireworkRemove);
        registerListener(Listener.getPlayerRespawnPoint(), this::onWorldSwitch);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);

        registerListener(Listener.getGameJoinPoint(), this::onGameJoinAutoStartFallFlying);
        registerListener(Listener.getPlayerSwitchFallFlying(), this::onMonitorFallFlying, Integer.MAX_VALUE - 1);
        registerListener(Listener.getPlayerSwitchFallFlying(), this::onElytraFlightStart, Integer.MAX_VALUE - 1);
        registerListener(
                Listener.getPacketPoint().getChannel(ClientCommandC2SPacket.class), this::onElytraFlightRocket);
        registerListener(
                Listener.getPacketPoint().getChannel(ClientCommandC2SPacket.class),
                this::onStartFallFlyingPacket,
                Integer.MAX_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(TeleportConfirmC2SPacket.class), this::onAcceptTeleportation);
        registerListener(Listener.getPostGameTick(), this::onArmorStateTick);
        registerListener(Listener.getPostGameTick(), this::onFallFlyingSyncTimeout);
        registerListener(PacketManager.getPacketQueueInEvent(), this::onFireworkRemoval);
        registerListener(PacketManager.getPacketQueueInEvent(), this::onArmorGlideDelay);
        registerListener(PacketManager.getQueueShutdownEvent(), this::onPacketQueueFlush);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(CommonPingS2CPacket.class),
                this::onPacketPing,
                Integer.MIN_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(CommonPongC2SPacket.class), this::onPacketPong, Integer.MIN_VALUE);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvents);
        registerListener(PacketManager.getPacketQueueOutEvent(), this::onPacketOutQueue);
        registerListener(PacketManager.getQueueShutdownEvent(), this::onPacketOutShutdown);
        registerListener(PacketManager.getPacketQueueInEvent(), this::onArmorGlidePost, Integer.MAX_VALUE);
    }

    boolean autoTakeOffFlag = false;

    public boolean autoTakeoff() {
        if (checkNull()) return false;
        if (hasGlidingItem()) {
            autoTakeOffFlag = true;
            return true;
        }
        return false;
    }

    public boolean switchElytra() {
        if (checkNull()) return false;
        if (hasGlidingItem()) {
            ItemStack currentChest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            boolean switchArmor = isValidElytra(currentChest);
            int slotForUse;
            if (switchArmor) {
                slotForUse = findEmptySlotForElytra();
            } else {
                slotForUse = findElytra();
            }
            if (slotForUse == -1) {
                logI18N("Elytra", "message.module.elytra-extra.no-elytra-place");
                return true;
            }
            if (isCurrentArmorGlidingEnable()) {
                if (switchArmor) {
                    this.armorGlideTransactionSlot = -1;
                    startArmorFlyTransaction(slotForUse);
                } else {
                    switchSlotToArmor(slotForUse);
                    armorGlideTransactionAbort = true;
                }
            } else {
                switchSlotToArmor(slotForUse);
                if (!switchArmor) {
                    broadcastSwapBackSlot(slotForUse);
                }
            }
            return true;
        }
        return false;
    }

    public boolean tryStartFallFlying() {
        return tryStartFallFlying(false, -1, null);
    }

    public boolean tryStartFallFlying(boolean autoSwapElytra) {
        return tryStartFallFlying(autoSwapElytra, -1, null);
    }

    public boolean tryStartFallFlying(int swapSlot) {
        return tryStartFallFlying(false, swapSlot, null);
    }

    private boolean tryStartFallFlying(boolean autoSwapElytra, int swapSlot, Runnable afterStart) {
        if (checkNull()) return false;
        if (autoSwapElytra && !isValidElytra(mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
            swapSlot = findElytra();
            if (swapSlot == -1) return false;
        }
        if (swapSlot != -1) {
            switchSlotToArmor(swapSlot);
        }

        MovTasks.getMovExtra().sendPacketsForPreStartFallFlying();
        mc.getNetworkHandler()
                .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        if (afterStart != null) {
            afterStart.run();
        }
        MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
        return true;
    }

    public void onGameJoinAutoStartFallFlying(Event<ClientPlayerEntity> joinServer) {
        if (autoTakeOffWhenJoinServer.get()) {
            MutableInt loadTicks = new MutableInt(0);
            Tasks.scheduleRepeated(
                    () -> {
                        if (mc.player == joinServer.context) {
                            if (mc.player.isLoaded()
                                    && WorldUtils.isChunkLoaded(mc.player.getBlockPos())
                                    && loadTicks.incrementAndGet() > 2) {
                                if (!mc.player.isOnGround() && !mc.player.isFallFlying()) {
                                    autoTakeoff();
                                }
                                return true;
                            } else {
                                return false;
                            }
                        } else {
                            return true;
                        }
                    },
                    20,
                    1);
        }
    }

    public boolean clickRocket() {
        if (mc.player.isFallFlying()) {
            sendCustomUseFireworkPacket(mc.player.getPitch(), mc.player.getYaw());
            return true;
        }
        return false;
    }

    public Vec3d clampFireworkSpeedInWeb(Vec3d oldVelocity) {
        if (fireworkSpeedLimitWeb.get()
                && canFireworkControlMotion()
                && (PlayerStateManager.INSTANCE.lastInWeb || PlayerStateManager.INSTANCE.lastSlowByBlock)) {
            Vec3d simulationVelocity = oldVelocity;
            Vec3d stuckSpeedMultiplier = mc.player.movementMultiplier;
            if (stuckSpeedMultiplier != null && stuckSpeedMultiplier.horizontalLengthSquared() > 1E-7) {
                simulationVelocity = simulationVelocity.multiply(stuckSpeedMultiplier);
            }
            Vec3d slowMovement = new Vec3d(0.25, 0.05F, 0.25);
            if (mc.player.hasStatusEffect(StatusEffects.WEAVING)) {
                slowMovement = new Vec3d(0.5, 0.25, 0.5);
            }
            double multiplier = slowMovement.horizontalLength() / (new Vec3d(1, 0, 1).horizontalLength());
            double delta = 1 - multiplier;
            double threshold = 0.245;
            double maxLength = threshold / delta;
            double scaling = maxLength / simulationVelocity.horizontalLength();
            if (scaling < 1.0D) {
                return oldVelocity.multiply(scaling);
            }
        }
        return oldVelocity;
    }

    // elytra unbreakable?

    private boolean pendingWaitResyncElytraUnbreakableTransaction = false;

    private boolean pendingStartFallFlyingTransaction = false;

    public boolean isCurrentArmorGlidingEnable() {
        return armorFly.get() && armorGlideTransactionSlot != -1;
    }

    public boolean isCurrentArmorGliding() {
        return armorFly.get() && armorGlideTransactionSlot != -1 && !armorGlideTransactionAbort;
    }

    public boolean isCurrentArmorGlidingAbortState() {
        return armorFly.get() && armorGlideTransactionSlot != -1 && armorGlideTransactionAbort;
    }

    public boolean shouldElytraUnbreakable() {
        return enableUnbreakableElytra.get()
                && mc.player != null
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).get(DataComponentTypes.UNBREAKABLE) == null;
    }

    private boolean hasPendingUnbreakableStartFallFlying() {
        return shouldElytraUnbreakable() && pendingWaitResyncElytraUnbreakableTransaction;
    }

    private boolean hasPendingBlinkStartFallFlying() {
        return elytraBlinkEnable.get() && duringElytraGlideBlinkTransaction;
    }

    private boolean hasPendingArmorStartFallFlying() {
        return armorFly.get() && armorGlideTransactionSlot != -1;
    }

    public boolean isWaitingServerResyncFallFlying() {
        return hasResetFallFlyingQueue()
                || hasPendingUnbreakableStartFallFlying()
                || hasPendingArmorStartFallFlying()
                || hasPendingBlinkStartFallFlying();
    }

    public int findElytraUnbreakableSwitchSlot() {
        if (this.armorGlideTransactionSlot != -1
                && this.armorGlideTransactionSlot < mc.player.playerScreenHandler.slots.size()) {
            ItemStack stackArmorFly = mc.player
                    .playerScreenHandler
                    .slots
                    .get(this.armorGlideTransactionSlot)
                    .getStack();
            if (canStopFallFlying(stackArmorFly)) {
                return this.armorGlideTransactionSlot;
            }
        }
        int idx = findEmptySlotForElytra();
        if (idx != -1) {
            broadcastSwapBackSlot(idx);
        }
        return idx;
    }

    private final CounterExecutor elytraUnbreakableDurabilityCounter = new CounterExecutor();
    private final CounterExecutor armorGlideAbortCounter = new CounterExecutor();

    public void onArmorStateTick() {
        if (armorFly.get() && mc.player.isFallFlying() && armorGlideTransactionSlot != -1) {
            if (armorMode.get() == ArmorFlyMode.TICK_LEGACY) {
                armorGlideAbortCounter.reset();
                armorGlideTransactionAbort = false;
                return;
            }
            ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (isValidElytra(stack)) {
                armorGlideAbortCounter.count();
                if (!armorGlideTransactionAbort && armorGlideAbortCounter.executeIf(5)) {
                    if (handControl.get()) {
                        armorGlideTransactionAbort = true;
                    } else {
                        // force armor
                        int oldSlot = armorGlideTransactionSlot;
                        armorGlideTransactionSlot = -1;
                        startArmorFlyTransaction(oldSlot);
                        armorGlideAbortCounter.reset();
                    }
                }
            } else {
                armorGlideAbortCounter.reset();
                armorGlideTransactionAbort = false;
            }
        } else {
            armorGlideAbortCounter.reset();
            armorGlideTransactionAbort = false;
        }
    }

    public void runElytraUnbreakable(Event<Integer> tickEvent) {
        if (mc.player.isFallFlying()) {
            if (isCurrentArmorGliding()) {
                elytraUnbreakableDurabilityCounter.reset();
            } else {
                elytraUnbreakableDurabilityCounter.count();
            }
        } else {
            elytraUnbreakableDurabilityCounter.reset();
        }

        if (shouldElytraUnbreakable() && canContinueGliding() && mc.player != null && mc.player.isFallFlying()) {
            if (elytraUnbreakableDurabilityCounter.executeIf(period.get())) {
                // fix bug: do not override current waiting unbreakable transaction if lagggggg
                if (pendingWaitResyncElytraUnbreakableTransaction
                        && !isValidElytra(mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
                    // what is wrong with the wifi
                    elytraUnbreakableDurabilityCounter.reset();
                } else {
                    int slot = findElytraUnbreakableSwitchSlot();
                    if (slot == -1) {
                        tryStartFallFlying();
                        //                Debug.info("send stop glide");
                        pendingWaitResyncElytraUnbreakableTransaction = true;
                        elytraUnbreakableDurabilityCounter.reset();
                        if (resetVanilla.get()) {
                            tickEvent.context(0);
                        }
                    } else {
                        switchSlotToArmor(slot);
                        pendingWaitResyncElytraUnbreakableTransaction = true;
                        elytraUnbreakableDurabilityCounter.reset();
                        if (resetVanilla.get()) {
                            tickEvent.context(0);
                        }
                    }
                }
            }
        } else {
            pendingWaitResyncElytraUnbreakableTransaction = false;
        }
    }

    public static boolean isUsable(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }

    // may cause fake gliding !!! must be careful
    public void handleEntityDataUpdate(Event<MetadataUpdate> event) {
        // only when elytra unbreakable do
        var serializedEntryUpdateEvent = event.context;
        if (serializedEntryUpdateEvent.entity() instanceof ClientPlayerEntity player
                && player == mc.player
                && serializedEntryUpdateEvent.metadata().id() == VDataFlag.ID_FLAGS) {
            var entry = serializedEntryUpdateEvent.metadata();
            byte data = (byte) entry.value();
            fallFlyingSyncStartTick = -1;
            if (event.isCancelled() || !player.isFallFlying()) {
                return;
            }
            if ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) == 0) {
                elytraUnbreakableDurabilityCounter.reset();
                pendingStartFallFlyingTransaction = isWaitingServerResyncFallFlying();
                if (pendingStartFallFlyingTransaction) {
                    data = (byte) (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX));
                    serializedEntryUpdateEvent.metadata(
                            new DataTracker.SerializedEntry(entry.id(), entry.handler(), data));
                }
            } else {
                // reset delay mode
                pendingStartFallFlyingTransaction = false;
            }
        }
    }

    private final Deque<Integer> nextPacketResetFallFlying = new ArrayDeque<>();

    /**
     * Requests one manual chest-slot swap and keeps the next server-side fall-flying reset
     * from leaving the player grounded.
     */
    public boolean requestManualArmorSwapAndResetFallFlying(int effectiveTicks) {
        if (checkNull() || !mc.player.isFallFlying() || hasPendingFallFlyingReset()) {
            return false;
        }
        int elytraSlot = findEmptySlotForElytra();
        if (elytraSlot == -1) {
            return false;
        }
        switchSlotToArmor(elytraSlot);
        return requestManualResetFallFlying(effectiveTicks);
    }

    public boolean requestManualResetFallFlying(int effectiveTicks) {
        nextPacketResetFallFlying.add(Tasks.getTick() + effectiveTicks);
        return true;
    }

    public boolean hasPendingFallFlyingReset() {
        return isCurrentArmorGliding() || pendingWaitResyncElytraUnbreakableTransaction || hasResetFallFlyingQueue();
    }

    public boolean hasResetFallFlyingQueue() {
        while (!nextPacketResetFallFlying.isEmpty() && nextPacketResetFallFlying.peekFirst() <= Tasks.getTick()) {
            nextPacketResetFallFlying.pollFirst();
        }
        return !nextPacketResetFallFlying.isEmpty()
                && nextPacketResetFallFlying.stream().anyMatch(s -> s > Tasks.getTick());
    }

    private boolean handlePendingFallFlyingReset() {
        while (!nextPacketResetFallFlying.isEmpty()) {
            var resetTick = nextPacketResetFallFlying.poll();
            if (resetTick > Tasks.getTick()) {
                if (canContinueGliding()) {
                    if (hasGlidingEquipments()) {
                        if (tryStartFallFlying(false, -1, this::flushRockets)) {
                            return true;
                        }
                    } else {
                        int slot = findElytra();
                        if (slot != -1 && tryStartFallFlying(false, slot, this::flushRockets)) {
                            return true;
                        }
                    }
                } else {
                    nextPacketResetFallFlying.clear();
                }
                EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                clearRockets();
                return true;
            }
        }
        return false;
    }

    //    public void attackPost(Event<PlayerInteractEntityC2SPacket> packet) {
    //        if (packet.drawContext() == lastHandledPacket) {
    //
    //        }
    //        lastHandledPacket = null;
    //    }

    @Override
    public int priority() {
        return PRIORITY_COMMON;
    }

    public static boolean isValidElytra(ItemStack item) {
        return VItem.getInstance().canGlide(item)
                && mc.player.canEquip(item, EquipmentSlot.CHEST)
                && !item.willBreakNextUse();
    }

    public static int findElytra() {
        // only backpack can operate
        if (InvTasks.getCurrentServerScreenHandler(mc.player) == mc.player.playerScreenHandler) {
            // check hotbar first
            var re = InventoryUtils.findPlayerItem(ElytraExtra::isValidElytra, true, false, false);
            if (re != null) {
                return mc.player
                        .playerScreenHandler
                        .getSlotIndex(mc.player.getInventory(), re.index())
                        .orElse(-1);
            }
        }
        return -1;
    }

    public static int findElytraWithDurability(int dur) {
        if (InvTasks.getCurrentServerScreenHandler(mc.player) == mc.player.playerScreenHandler) {
            // check hotbar first
            var re = InventoryUtils.findBestPlayerItem(
                    (it) -> {
                        if (isValidElytra(it)) {
                            if (it.contains(DataComponentTypes.UNBREAKABLE)) {
                                return Double.MAX_VALUE;
                            }
                            if (it.getMaxDamage() == 0) {
                                return Double.MAX_VALUE;
                            }
                            int dura = it.getMaxDamage() - it.getDamage();
                            return dura >= dur
                                    ? (Double) (double) dura
                                            * (1 + ItemStackUtils.getEnchantmentLevel(it, Enchantments.UNBREAKING))
                                    : null;
                        }
                        return null;
                    },
                    GhostHandMode.INV_SWAP.getSearchSize(false),
                    true,
                    false);
            if (re != null) {
                return mc.player
                        .playerScreenHandler
                        .getSlotIndex(mc.player.getInventory(), re.index())
                        .orElse(-1);
            }
        }
        return -1;
    }

    private boolean canStopFallFlying(ItemStack item) {
        return item.isEmpty() || (!isValidElytra(item) && mc.player.canEquip(item, EquipmentSlot.CHEST));
    }

    public int findEmptySlotForElytra() {
        if (InvTasks.getCurrentServerScreenHandler(mc.player) == mc.player.playerScreenHandler) {
            // use cached autoSwitch slot
            var re = InventoryUtils.findBestPlayerInventory(
                    (entry) -> {
                        if (entry.index() >= 36 && entry.index() != 40) return null;
                        // do not use chest item
                        var item = entry.val();
                        if (item.isEmpty()) return -3.0D;
                        if (canStopFallFlying(item)) {
                            AttributeContainer container =
                                    AttributeUtils.getAttributeWith(mc.player, Map.of(EquipmentSlot.CHEST, item));
                            return container.getValue(EntityAttributes.ARMOR)
                                            * container.getValue(EntityAttributes.ARMOR_TOUGHNESS)
                                    + 16
                                            * (ItemStackUtils.getEnchantmentLevel(item, Enchantments.PROTECTION)
                                                    + ItemStackUtils.getEnchantmentLevel(
                                                            item, Enchantments.BLAST_PROTECTION));
                        }
                        return null;
                    },
                    InventoryUtils.getPlayerInvSize(),
                    true,
                    true,
                    false);
            if (re != null) {
                return mc.player
                        .playerScreenHandler
                        .getSlotIndex(mc.player.getInventory(), re.index())
                        .orElse(-1);
            }
        }
        return -1;
    }

    public Deque<ItemStack> delayQueue = new ConcurrentLinkedDeque<>();

    public void submitDelay(ItemStack stack) {
        timerVanilla.fire();
        if (stack.isEmpty() || stack.isOf(Items.FIREWORK_ROCKET)) {
            delayQueue.removeIf(s -> s.isEmpty() || s.isOf(Items.FIREWORK_ROCKET));
            delayQueue.add(stack);
        } else {
            delayQueue.add(stack);
        }
    }

    public boolean canBeUsedAsFireworks(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else if (stack.isOf(Items.FIREWORK_ROCKET)) {
            return true;
        } else {
            return false;
        }
    }
    // do not catch flushing packets
    boolean flushing = false;

    // todo rewrite this shit
    public void onUseFireworks(Event<PlayerInteractItemC2SPacket> packet) {
        if (!flushing && hasPendingFallFlyingReset()) {
            ItemStack stack =
                    PlayerInteractItemC2SPacketAccess.of(packet.context).getItemStack();
            if (!stack.isEmpty()) {
                // make a stackCopy of origin item with 1 count
                if (canBeUsedAsFireworks(stack)) {
                    // 40-> offhand
                    submitDelay(stack.copyWithCount(1));
                    packet.cancel();
                    NetworkUtils.restoreSequence(packet.context.getSequence());
                }
            }
        }
    }

    public void sendCustomUseFireworkPacket() {
        sendCustomUseFireworkPacket(PlayerStateManager.INSTANCE.lastPitch, PlayerStateManager.INSTANCE.lastYaw);
    }

    public void sendCustomUseFireworkPacket(float pitch, float yaw) {
        if (hasPendingFallFlyingReset()) {
            submitDelay(ItemStack.EMPTY);
        } else {
            sendUsePacket(pitch, yaw, this::canBeUsedAsFireworks);
        }
        CombatTasks.getBlink().onFireworkUse();
    }

    public ItemStack findRocket() {
        var re = InventoryUtils.findPlayerBackpackItem(this::canBeUsedAsFireworks, false, true);
        return re != null ? re.val().getStack() : null;
    }

    public int getRocketLevel(ItemStack stack) {
        if (stack.isOf(Items.FIREWORK_ROCKET)) {
            FireworksComponent component = stack.get(DataComponentTypes.FIREWORKS);
            if (component != null) {
                return 1 + component.flightDuration();
            }
        }
        return 1;
    }

    private void sendUsePacket(float pitch, float yaw, Predicate<ItemStack> predicate) {
        ItemStack stack = mc.player.getStackInHand(Hand.MAIN_HAND);
        if (predicate.test(stack)) {
            timerVanilla.fire();
            sendFirework(Hand.MAIN_HAND, yaw, pitch);
            onHasFirework();
        } else {
            stack = mc.player.getStackInHand(Hand.OFF_HAND);
            if (predicate.test(stack)) {
                timerVanilla.fire();
                sendFirework(Hand.OFF_HAND, yaw, pitch);
                onHasFirework();
            } else {
                // check hotbars
                var findResult = InventoryUtils.findPlayerBackpackItem(predicate, false, true);
                if (findResult != null) {
                    Runnable callback = offHand.get()
                            ? InvExtra.INSTANCE.swapInventorySlotToOffhand(findResult.index())
                            : InvExtra.INSTANCE.swapInventorySlotToHand(findResult.index());
                    if (callback != null) {
                        timerVanilla.fire();
                        sendFirework(offHand.get() ? Hand.OFF_HAND : Hand.MAIN_HAND, yaw, pitch);
                        callback.run();
                    }
                    onHasFirework();
                } else {
                    onNoFireworks();
                }
            }
        }
    }

    public void switchSlotToArmor(int idx) {
        int armorSlot = 6;
        int targetSlot = idx;
        if (mc.player.playerScreenHandler == InvTasks.getCurrentServerScreenHandler(mc.player)) {
            InvExtra.INSTANCE.swapScreenSlots(armorSlot, targetSlot);
        }
    }

    public static boolean canContinueGliding() {
        return !mc.player.isTouchingWater()
                && !mc.player.getAbilities().flying
                && !mc.player.isOnGround()
                && !mc.player.hasVehicle()
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION);
    }

    public boolean canContinueArmorGliding() {
        boolean liquidFlyFlag;
        if (enableLiquidFly.get()) {
            liquidFlyFlag =
                    !mc.player.isTouchingWater() || !enableLiquidFlyOnlyFireworks.get() || canFireworkControlMotion(0);
        } else {
            liquidFlyFlag = !mc.player.isTouchingWater();
        }
        boolean onGroundFlag;
        if (enableOnGroundFly.get()) {
            onGroundFlag = !PlayerStateManager.INSTANCE.lastHasGroundSupport
                    || canFireworkControlMotion(0)
                    || !mc.player.isOnGround();
        } else {
            onGroundFlag = !mc.player.isOnGround();
        }
        return liquidFlyFlag
                && !mc.player.getAbilities().flying
                && onGroundFlag
                && !mc.player.hasVehicle()
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION);
    }

    public static boolean hasGlidingEquipments() {
        EquipmentSlot equipmentSlot = EquipmentSlot.CHEST;
        return mc.player.canGlideWith(mc.player.getEquippedStack(equipmentSlot), equipmentSlot);
    }

    public static boolean hasGlidingItem() {
        return findElytra() != -1;
        // InventoryUtils.findPlayerItem((vv) -> mc.player.canGlideWith(vv, EquipmentSlot.CHEST), false, false)
        // != null;
    }

    public static boolean hasFireworks() {
        return InventoryUtils.findPlayerBackpackItem(ElytraExtra.INSTANCE::canBeUsedAsFireworks, false, true) != null;
    }

    int manuallySwitchTick = 0;

    public void onLimitStartFallFlyingSpeed(Event<Boolean> eventBoolean) {
        if (takeOffForceCooldown.get().isPresent()
                && takeOffForceCooldown.get().getValue() + PlayerStateManager.INSTANCE.lastStartGlidingTick
                        > Tasks.getTick()) {
            eventBoolean.cancel();
        }
    }

    public void onStartFallFlying(Event<Boolean> booleanEvent) {
        // not fallFlying, and not suitable for gliding
        // check armor fly
        // reset fly transaction
        // this is a check during the flying
        if ((Boolean) booleanEvent.getArgs(0) || booleanEvent.isCancelled()) {
            return;
        }
        this.armorGlideTransactionSlot = -1;

        // reset armor fly status
        boolean checkQueue = false;
        if (elytraBlinkEnable.get() && canContinueArmorGliding()) {
            checkQueue = true;
            startOutboundQueue();
        }
        if (!booleanEvent.context()) {

            if (armorFly.get()) {
                if (canContinueGliding()) {
                    // check equipments
                    if (onSwitchItemArmorFallFlying()) {
                        booleanEvent.context(Boolean.TRUE);
                        rocketFlush.state(true);
                    }
                }
            } else if (autoSwitch.get()) {
                // auto switch if not armorFly;
                if (canContinueGliding()) {
                    if (onAutoSwitchItemFallFlying(true)) {
                        booleanEvent.context(Boolean.TRUE);
                    }
                }
            }
            if (!booleanEvent.context() && checkQueue) {
                int slot = findElytra();
                if (slot != -1) {
                    switchSlotToArmor(slot);
                    booleanEvent.context(Boolean.TRUE);
                }
            }
        }
        if (checkQueue) {
            if (booleanEvent.context()) {
                if (blinkAutoSneakResync.get()) {
                    shutdownTemp();
                    PlayerInputUtils.of(mc.player)
                            .sneak(!PlayerStateManager.INSTANCE.lastInput.sneak())
                            .sendPlayerSneakUpdatePacket();
                }
                startOutboundQueue();
                duringElytraGlideBlinkTransaction = true;
                elytraGlideWaitUntilStateResync = false;
            } else {
                flushImmediately();
            }
        }
    }

    public void onMonitorFallFlying(Event<Boolean> booleanEvent) {
        if (booleanEvent.isCancelled()) return;
        if (booleanEvent.context) {
            manuallySwitchTick = Tasks.getTick();
            if (!(Boolean) booleanEvent.getArgs(0)) {
                fallFlyingSyncStartTick = Tasks.getTick();
            }
        }
    }

    private static final int FALL_FLYING_SYNC_TIMEOUT_TICKS = 40;
    private int fallFlyingSyncStartTick = -1;

    public void onFallFlyingSyncTimeout() {
        if (fallFlyingSyncStartTick == -1) {
            return;
        }
        if (checkNull() || !mc.player.isFallFlying()) {
            fallFlyingSyncStartTick = -1;
            return;
        }
        if (timeoutReflight.get().isPresent()) {
            if (Tasks.getTick() - fallFlyingSyncStartTick
                    > timeoutReflight.get().getValue()) {
                PlayerInputManager.INSTANCE.addSneakModifier(Integer.MAX_VALUE, !mc.player.isSneaking(), 2);
                fallFlyingSyncStartTick = Tasks.getTick();
            }
        }
    }

    public boolean onSwitchItemArmorFallFlying() {
        if (!canContinueArmorGliding()) return false;
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!isValidElytra(stack)) {
            // switch one
            int elytraIndex = findElytra();
            if (elytraIndex != -1) {
                // ARMOR FLIGHT
                switchSlotToArmor(elytraIndex);
                postTickSwapWithChestSlot = elytraIndex;
                armorGlideTransactionSlot = postTickSwapWithChestSlot;
                // mc.player.input.playerInput =
                // PlayerInputUtils.of(mc.player.input.playerInput).sprint(false).sneak(false).jump(true).forward(false).backward(false).right(false).left(false).toPlayerInput();
                return true;
            }
            return false;
        } else {
            postTickSwapWithChestSlot = -1;
            return true;
        }
    }

    public void endArmorFlyTransaction(boolean continueFly) {
        if (armorGlideTransactionSlot != -1) {
            boolean willContinueFly = continueFly && canContinueGliding();
            // current ArmoFly
            // switch Elytra on
            // 注意到我们的傻逼延迟任务没有处理干净
            // 我们需要在这里处理干净我们的傻逼延迟业务】
            // 不管是结束飞行还是继续飞行都要
            boolean hasDelayedTakeOffShit = false;
            if (this.postTickSwapWithChestSlot == -1
                    && pendingStartFallFlyingTransaction
                    && !hasResetFallFlyingQueue()
                    && !hasPendingUnbreakableStartFallFlying()
                    && hasPendingArmorStartFallFlying()) {
                pendingStartFallFlyingTransaction = false;
                hasDelayedTakeOffShit = true;
            }
            if (willContinueFly) {
                switchSlotToArmor(armorGlideTransactionSlot);
                if (hasDelayedTakeOffShit) {
                    tryStartFallFlying(false, -1, this::flushRockets);
                } else {
                    // wait next delay takeoff shit
                    nextPacketResetFallFlying.add(
                            Tasks.getTick() + closeContinueFly.get().getValue());
                    autoTakeOffFlag = true;
                }
                //                nextPacketResetFallFlying.add(Tasks.getTick() + closeContinueFly.get().getValue());
                //                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player,
                // ClientCommandC2SPacket.Mode.START_FALL_FLYING));

            } else {
                if (hasDelayedTakeOffShit) {
                    clearRockets();
                    EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                }
            }
            onEndArmorFlyTransaction(willContinueFly);
        }
    }

    public void onEndArmorFlyTransaction(boolean willContinueGliding) {
        if (armorGlideTransactionSlot != -1) {
            if (mc.player.isOnGround() && !mc.player.isFallFlying() && armorFly.get() && landAutoClose.get()) {
                HotKeyUtils.wrapFlagAsToggle(armorFlyPath.add("enable").toPath(), armorFly)
                        .run();
            }
            if (landAutoSneak.get() && !mc.player.isSneaking()) {
                nextLandingSneak = 2;
            }
            // use autoSwitch for
            if (willContinueGliding) {
                broadcastSwapBackSlot(armorGlideTransactionSlot);
            }
        }
        armorGlideTransactionSlot = -1;
        armorGlideTransactionAbort = false;
        pendingDelay3C3UCancel = false;
    }

    private void broadcastSwapBackSlot(int swapBack) {
        if (autoSwitch.get() && mc.player.isFallFlying()) {
            autoSwitchTransaction = true;
        }
        if (this.armorGlideTransactionSlot != -1) {
            this.armorGlideTransactionSlot = swapBack;
        }
    }

    private void clearPreviousResyncTransaction() {
        this.autoSwitchTransaction = false;
        this.pendingWaitResyncElytraUnbreakableTransaction = false;
        this.nextPacketResetFallFlying.clear();
    }

    public void startArmorFlyTransaction(int value) {
        if (checkNull()) return;
        if (armorGlideTransactionSlot == -1) {
            if (value != -1
                    && value < mc.player.playerScreenHandler.slots.size()
                    && canStopFallFlying(
                            mc.player.playerScreenHandler.slots.get(value).getStack())) {
                armorGlideTransactionSlot = value;
                clearPreviousResyncTransaction();
                switchSlotToArmor(value);

            } else {
                if (!isValidElytra(mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
                    int swapBackSlot = findElytra();
                    if (swapBackSlot != -1) {
                        this.armorGlideTransactionSlot = swapBackSlot;
                        clearPreviousResyncTransaction();
                    }
                } else if (hasGlidingEquipments()) {
                    ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                    if (isValidElytra(stack)) {
                        int slot = findEmptySlotForElytra();
                        if (slot != -1) {
                            armorGlideTransactionSlot = slot;
                            clearPreviousResyncTransaction();
                            switchSlotToArmor(slot);
                        }
                    }
                }
            }
        }
    }

    public void onToggleArmorFly(boolean armorFly) {
        if (mc.player != null && mc.player.isFallFlying()) {
            if (!armorFly) {
                endArmorFlyTransaction(closeContinueFly.get().isPresent());
            } else {
                startArmorFlyTransaction(-1);
            }
        }
    }

    public boolean onAutoSwitchItemFallFlying(boolean stopSprint) {
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!isValidElytra(stack)) {
            // switch one
            int elytraIndex = findElytra();
            if (elytraIndex != -1) {
                // ARMOR FLIGHT
                autoSwitchTransaction = true;
                switchSlotToArmor(elytraIndex);
                return true;
            }
            return false;
        } else {
            autoSwitchTransaction = false;
            return true;
        }
    }

    public int postTickSwapWithChestSlot = -1;
    public int armorGlideTransactionSlot = -1;
    public boolean armorGlideTransactionAbort = false;
    public boolean autoSwitchTransaction = false;
    public boolean pendingDelay3C3UCancel = false;
    boolean currentTickStartGlideTransaction = false;

    public boolean isThisTickGlideMovementServerSideGlide() {
        return pendingStartFallFlyingTransaction || currentTickStartGlideTransaction;
    }

    StateExecutor rocketFlush = new StateExecutor();

    boolean setback = false;

    public void onSetBack() {
        setback = true;
    }

    private void sendFirework(Hand hand, float lastYaw, float lastPitch) {
        var packet = new PlayerInteractItemC2SPacket(hand, NetworkUtils.generateNextSequence(), lastYaw, lastPitch);
        InteractUtils.simulateInteractItem(hand);
        mc.getNetworkHandler().sendPacket(packet);
    }

    public void flushRockets() {
        flushing = true;
        int selected = InventoryUtils.getSelectedSlot();
        try {
            float lastYaw = PlayerStateManager.INSTANCE.lastYaw;
            float lastPitch = PlayerStateManager.INSTANCE.lastPitch;
            while (!delayQueue.isEmpty()) {
                var packetEntry = delayQueue.poll();
                if (!packetEntry.isEmpty()) {
                    sendUsePacket(
                            lastYaw, lastPitch, (stack) -> ItemStack.areItemsAndComponentsEqual(stack, packetEntry));
                } else {
                    sendUsePacket(lastYaw, lastPitch, this::canBeUsedAsFireworks);
                }
            }
        } finally {
            flushing = false;
            PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(selected);
        }
    }

    public void clearRockets() {
        delayQueue.clear();
    }

    public void onPreInputEvents() {
        if (checkNull()) {
            return;
        }
        if (!hasPendingFallFlyingReset()) {
            if (anyAliveRocket()) {
                clearRockets();
            } else {
                flushRockets();
            }
        }
    }
    // ArmorFly works
    // tested in 3c3u.uno, 20260311
    // tested in mc.loyisa.cn 1.21.1 20260311

    int nextFlyFireworksTicks = 0;

    public void onElytraFlightStart(Event<Boolean> takeOff) {
        if (takeOff.isCancelled()) return;
        if (takeOff.context
                && (flyRocketOnFirstOff.get()
                        ||
                        // faster takeoff
                        (MovTasks.getElytraFlight().shouldFlyRocketOnFirstOff())
                        || (autoTakeOffFireworksFromGround.get() && lastOnGroundOrInWaterAutoTakeOff))) {
            nextFlyFireworksTicks = Tasks.getTick();
        }
    }

    private void onStartFallFlyingPacket(Event<ClientCommandC2SPacket> event) {
        if (event.context.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING) {
            currentTickStartGlideTransaction = true;
        }
    }

    public void onElytraFlightRocket(Event<ClientCommandC2SPacket> event) {
        if (checkNull()) return;
        if (nextFlyFireworksTicks == Tasks.getTick()
                && mc.player.isFallFlying()
                && event.context.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING
                && checkFireworkUseCondition(false)) {
            rocketFlush.state(true);
            submitDelay(ItemStack.EMPTY);
            // fix sprint issues
            PlayerInputUtils.of(mc.player).sprint(false).applyInput(mc.player);
        }
    }

    public boolean shouldApplyOnGroundFly() {
        return mc.player.isFallFlying() && armorFly.get() && armorGlideTransactionSlot != -1 && enableOnGroundFly.get();
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        tickElytraSimulation();
        //        // queue packets, so that fallflying and movement can be sent together
        //        if (compressPackets.get() && isThisTickGlideMovementServerSideGlide()) {
        //            RotationManager.INSTANCE.startQueuePacket();
        //        }
        // resetFallFly
        if (autoTakeOffFlag) {
            if (player.isFallFlying()) {
                if (PlayerStateManager.INSTANCE.glidingTicks > autoTakeOffRetryGt.get()
                        && !mc.player.isTouchingWater()) {
                    lastOnGroundOrInWaterAutoTakeOff = false;
                    autoTakeOffFlag = false;
                } else {
                    if (autoTakeOffFlag) {
                        lastOnGroundOrInWaterAutoTakeOff = true;
                    } else {
                        lastOnGroundOrInWaterAutoTakeOff = false;
                    }
                }
            } else {
                boolean hasGliding = hasGlidingItem();
                if (hasGliding && mc.player.isOnGround()) {
                    lastOnGroundOrInWaterAutoTakeOff = true;
                    PlayerInputManager.INSTANCE.addJumpModifier(0, true, 0);
                } else {
                    if (hasGliding && player.checkGliding()) {
                        // do not update lastOnGround..., so they will use the last tick status
                        tryStartFallFlying();
                        // optimize water fly
                    } else if (!mc.player.isTouchingWater()) {
                        autoTakeOffFlag = false;
                        lastOnGroundOrInWaterAutoTakeOff = false;
                    } else {
                        lastOnGroundOrInWaterAutoTakeOff = true;
                    }
                }
            }
        } else {
            lastOnGroundOrInWaterAutoTakeOff = false;
        }

        if (!elytraBlinkEnable.get()) {
            duringElytraGlideBlinkTransaction = false;
            this.elytraGlideWaitUntilStateResync = false;
            flushLazy();
        }
        boolean skipArmorFlyProcessing = false;
        if (pendingStartFallFlyingTransaction) {
            pendingStartFallFlyingTransaction = false;
            boolean enableBlink = hasPendingBlinkStartFallFlying();
            if (enableBlink) {
                flushImmediately();
                startOutboundQueue();
            }
            if (handlePendingFallFlyingReset()) {
            } else if (hasPendingUnbreakableStartFallFlying()) {
                pendingWaitResyncElytraUnbreakableTransaction = false;
                int slot = findElytra();
                if (slot != -1) {
                    switchSlotToArmor(slot);
                }
                if (canContinueGliding()) {
                    tryStartFallFlying(false, -1, () -> {
                        if (slot != -1) {
                            flushRockets();
                        }
                    });
                    if (!mc.player.isFallFlying()) {
                        EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, true);
                    }
                } else {
                    clearRockets();
                    EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                }
            } else if (hasPendingArmorStartFallFlying()) {
                if (armorMode.get() == ArmorFlyMode.TICK_3C3U) {
                    if (!canContinueGliding()) {
                        clearRockets();
                        EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                        onEndArmorFlyTransaction(false);
                        skipArmorFlyProcessing = true;
                    } else {
                        pendingDelay3C3UCancel = true;
                    }
                } else if (armorFly.get() && onSwitchItemArmorFallFlying()) {
                    tryStartFallFlying(false, -1, () -> {
                        flushRockets();
                        if (this.postTickSwapWithChestSlot != -1 && armorMode.get() != ArmorFlyMode.TICK_LEGACY) {
                            final int idx = this.postTickSwapWithChestSlot;
                            switchSlotToArmor(idx);
                            this.postTickSwapWithChestSlot = -1;
                        }
                    });
                } else {
                    clearRockets();
                    EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                    onEndArmorFlyTransaction(false);
                    skipArmorFlyProcessing = true;
                }
            } else if (hasPendingBlinkStartFallFlying()) {
                boolean canContinue = canContinueArmorGliding();
                this.elytraGlideWaitUntilStateResync = false;
                if (canContinue) {
                    int slot = findElytra();
                    if (hasGlidingEquipments() || slot != -1) {
                        if (!hasGlidingEquipments()) {
                            switchSlotToArmor(slot);
                        }
                        tryStartFallFlying(false, -1, this::flushRockets);
                    } else {
                        clearRockets();
                        EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                        duringElytraGlideBlinkTransaction = false;
                        flushImmediately();
                    }
                } else {
                    clearRockets();
                    EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                    duringElytraGlideBlinkTransaction = false;
                    flushImmediately();
                }
            } else {
                clearRockets();
                EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
            }
            if (enableBlink) {
                if (mc.player.isFallFlying() && currentTickStartGlideTransaction) {
                    if (blinkAutoSneakResync.get()) {
                        shutdownTemp();
                        PlayerInputUtils.of(mc.player)
                                .sneak(!PlayerStateManager.INSTANCE.lastInput.sneak())
                                .sendPlayerSneakUpdatePacket();
                        ClientPlayerAccess.of(mc.player).resyncSneak();
                    }
                    startOutboundQueue();
                    elytraGlideWaitUntilStateResync = false;
                } else {
                    flushImmediately();
                }
            }
        }
        if (mc.player.isFallFlying() && duringElytraGlideBlinkTransaction) {
            if (blinkDelayInBound.get()) {
                PlayerInputManager.INSTANCE.addSneakModifier(Integer.MAX_VALUE, Tasks.getTick() % 4 < 2, 1);
            }
            if (blinkFreezeWhenWait.get() && elytraBlinkEnable.get() && this.elytraGlideWaitUntilStateResync) {
                FloatingUtils.INSTANCE.setGrimFloatingTick(true);
            }
        } else {
            flushLazy();
        }
        armor_fly:
        if (player.isFallFlying()) {
            if (skipArmorFlyProcessing) {
                break armor_fly;
            }
            if (armorFly.get()) {
                if (isCurrentArmorGlidingAbortState()) {
                    // postTickSwapWithChestSlot = -1;
                    // trigger flush rockets
                    rocketFlush.state(true);
                }

                if (armorMode.get() == ArmorFlyMode.TICK_LEGACY && this.postTickSwapWithChestSlot == -1) {
                    // Without a pending reset transaction, keep the legacy one-swap-per-movement-tick behavior.
                    boolean canGlide = canContinueGliding();
                    if (canGlide) {
                        if (!isValidElytra(player.getEquippedStack(EquipmentSlot.CHEST))) {
                            int idx = findElytra();
                            if (idx != -1) {
                                switchSlotToArmor(idx);
                                this.postTickSwapWithChestSlot = idx;
                                this.armorGlideTransactionSlot = this.postTickSwapWithChestSlot;
                            }
                        } else {
                            // switch to origin armor
                            switchSlotToArmor(armorGlideTransactionSlot);
                            this.postTickSwapWithChestSlot = armorGlideTransactionSlot;
                        }
                    } else {
                        onEndArmorFlyTransaction(false);
                        break armor_fly;
                    }
                }
                if (pendingDelay3C3UCancel
                        && PlayerStateManager.INSTANCE.lastStartGlidingTick + armorGlideTakeoffIntervalTicks.get()
                                <= Tasks.getTick()) {
                    pendingDelay3C3UCancel = false;
                    boolean enable = hasPendingBlinkStartFallFlying();
                    if (enable) {
                        startOutboundQueue();
                    }
                    if (onSwitchItemArmorFallFlying()) {
                        tryStartFallFlying(false, -1, this::flushRockets);
                        if (enable) {
                            if (blinkAutoSneakResync.get()) {
                                shutdownTemp();
                                PlayerInputUtils.of(mc.player)
                                        .sneak(!PlayerStateManager.INSTANCE.lastInput.sneak())
                                        .sendPlayerSneakUpdatePacket();
                                ClientPlayerAccess.of(mc.player).resyncSneak();
                            }
                            startOutboundQueue();
                            elytraGlideWaitUntilStateResync = false;
                        }
                    } else {
                        if (enable) {
                            flushImmediately();
                        }
                        clearRockets();
                        EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                        onEndArmorFlyTransaction(false);
                        break armor_fly;
                    }
                }
            } else {
                onEndArmorFlyTransaction(false);
            }
        } else {
            onEndArmorFlyTransaction(false);
        }
        if (!mc.player.isFallFlying() && pendingWaitResyncElytraUnbreakableTransaction) {
            pendingWaitResyncElytraUnbreakableTransaction = false;
            // unexpected behaviour:
            if (shouldElytraUnbreakable()) {
                int slot = findElytra();
                if (slot != -1) {
                    switchSlotToArmor(slot);
                }
                // cancel stop fallflying only when can continue
                if (canContinueGliding()) {
                    logI18N("message.module.elytra-extra.unexpected-unbreakable-state");
                    mc.player.startGliding();
                    tryStartFallFlying(false, -1, () -> {
                        if (slot != -1) {
                            flushRockets();
                        }
                    });
                } else {
                    clearRockets();
                }
            }
        }
        if (autoSwitchTransaction) {
            if (!player.isFallFlying()) {
                if (isValidElytra(mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
                    int slot = findEmptySlotForElytra();
                    if (slot != -1) {
                        switchSlotToArmor(slot);
                    }
                }
                autoSwitchTransaction = false;
            }
        }
        if (shouldApplyOnGroundFly()) {
            // on Ground Fly
            if (PlayerStateManager.INSTANCE.lastHasGroundSupport && mc.player.getPitch() > 0) {
                mc.player.setPitch(0);
                movementManagerEvent.context.pushImportantRotation(true, false);
                movementManagerEvent.context.markForResetRot();
            }
        }
        if (autoMaintain.get().isPresent() && isValidElytra(mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
            ItemStack elytra = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (!elytra.contains(DataComponentTypes.UNBREAKABLE)
                    && elytra.getMaxDamage() > 0
                    && elytra.getMaxDamage()
                            < elytra.getDamage() + autoMaintain.get().getValue()) {
                int slot = findElytraWithDurability(autoMaintain.get().getValue());
                if (slot != -1) {
                    switchSlotToArmor(slot);
                }
            }
        }

        rocket_boost:
        if (player.isFallFlying() && canFireworkControlMotion()) {
            boolean hasOverride = hasFireworkMotionOverride();
            Vec3d currentTarget = EntityUtils.pitchYawToRotation(player.getPitch(), player.getYaw());
            Event<ElytraMotion> motionEvent;
            if (hasOverride) {
                motionEvent = new Event<>(overrideElytraMotion, true, false);
            } else {
                var motionVal = rocketBoostUseRescale.get()
                        ? new ElytraMotion(
                                autoRescale.get() ? autoRescaleAl.get() : null,
                                mc.player.getPitch(),
                                mc.player.getYaw(),
                                rocketBoostSpeed.get(),
                                rocketBoostSpeed.get())
                        : new ElytraMotion(null, currentTarget, rocketBoostSpeed.get(), rocketBoostSpeed.get());
                motionEvent = new Event<>(motionVal, true, false);
                if (!rocketBoost.get()) {
                    motionEvent.cancel();
                }
            }
            fireworkMotion.handleValue(motionEvent);
            if (motionEvent.isCancelled()) {
                break rocket_boost;
            }
            ElytraMotion motion = motionEvent.context;
            if (motion.algorithm() != null) {
                float eventYaw = motion.yaw();
                float newYaw = eventYaw + ((Tasks.getTick() % 2 == 0) ? 0.01F : -0.01F);
                motion.yaw(newYaw);
            }
            if (motion.pitch() != mc.player.getPitch()) {
                // do not restore rotate if only 0.01 diff
                if (Math.abs(motion.pitch() - mc.player.getPitch()) > 1E-1) {
                    movementManagerEvent.context.markForResetRot();
                }
                EntityUtils.setEntityPitchSafe(mc.player, motion.pitch());
            }
            if (motion.yaw() != mc.player.getYaw()) {
                PlayerStateManager.setPlayerYawSafe(mc.player, motion.yaw());
                movementManagerEvent.context.markForResetRot();
            }
            mc.player.setVelocity(applyAxisLimit(
                    motion.algorithm(), motion.movement(), motion.pitch(), motion.yaw(), !mc.player.hasNoGravity()));
        }
        overrideElytraMotion = null;
        // slow falling with no crash
        if (false && player.isFallFlying()) {
            if (!movementManagerEvent.context().hasImportantRotation()) {
                movementManagerEvent.context.markForResetRot();
                player.setPitch(0.0F);
                if (Tasks.getTick() % 2 == 0) {
                    PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                }
                movementManagerEvent.context.pushImportantRotation(true, true);
            }
        }
    }

    EntityDimensions pose = null;
    int triggerKinetic = 0;
    boolean executeNoKineticAfterTravel = false;
    boolean tempStopFlying = false;

    @Override
    public void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
        if (movementManagerEvent.isCancelled()) {
            return;
        }

        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;

        if (isCurrentArmorGliding()) {
            // fix boundingbox error
            if (poseFix.get()) {
                player.setPose(EntityPose.STANDING);
            } else {
                pose = player.dimensions;
                player.dimensions = player.getDimensions(EntityPose.STANDING);
                player.setBoundingBox(player.dimensions.getBoxAt(player.getPos()));
            }
        } else if (hasPendingFallFlyingReset()) {
            pose = player.dimensions;
            player.dimensions = player.getDimensions(EntityPose.STANDING);
            player.setBoundingBox(player.dimensions.getBoxAt(player.getPos()));
        }

        executeNoKineticAfterTravel = false;
        if (noKineticMode.get().isPresent() && armorGlideTransactionSlot == -1 && player.isFallFlying()) {
            boolean canControl = anyAliveRocket();
            if (noKineticMode.get().getValue().get().hasAc()) {
                // control by rotation and velocity
                // simulation
                Vec3d vec3d = mc.player.getVelocity().multiply(4);
                Vec3d simu2 = MovTasks.simulateMovement(player, mc.player.getPos(), vec3d, false);
                if (vec3d.horizontalLength() > 0.3 && !MathHelper.approximatelyEquals(simu2.x, vec3d.x)
                        || !MathHelper.approximatelyEquals(simu2.z, vec3d.z)) {

                    // player.setVelocity(vec3d.multiply(0.3 / speed));
                    movementManagerEvent.context.markForResetRot();
                    triggerKinetic = 2;
                    // it can work, don't move it
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(player, (-90f + 1e-3f));
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    } else {
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    }
                } else if (triggerKinetic > 0) {
                    triggerKinetic--;
                    movementManagerEvent.context.markForResetRot();
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(player, (-90f + 1e-3f));
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    } else {
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    }
                }
            } else {
                executeNoKineticAfterTravel = true;
            }
        }
        if (isCurrentArmorGliding()
                && armorMode.get().isIn(ArmorFlyMode.TICK_3C3U)
                && (PlayerStateManager.INSTANCE.lastStartGlidingTick + armorGlideStopFlyingDelayTicks.get()
                                <= Tasks.getTick()
                        || pendingDelay3C3UCancel)) {
            tempStopFlying = true;
            EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
            // restore firework boosts
            setOverridingFireworkVelocity(null);
            movementManagerEvent.context.playerStatus.restoreVelocity();
        }
        // movementManagerEvent.cancel();
    }

    @Override
    public void applyAfterTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (pose != null) {
            player.dimensions = pose;
        }
        pose = null;
        if (executeNoKineticAfterTravel) {

            //                Vec3d simu = MovTasks.simulateMovement(player, mc.player.getPos(), vec3d, false);
            //                Vec3d predictedPos = mc.player.getPos().add(simu);
            boolean controlled = false;
            boolean canControl = anyAliveRocket();
            if (true) {
                // use firework to control server motion
                Vec3d vec3d = mc.player.getRotationVector().multiply(0.85 * 6);
                Vec3d simu2 = MovTasks.simulateMovement(player, mc.player.getPos(), vec3d, false);
                if (!MathHelper.approximatelyEquals(simu2.x, vec3d.x)
                        || !MathHelper.approximatelyEquals(simu2.z, vec3d.z)) {

                    // player.setVelocity(vec3d.multiply(0.3 / speed));
                    triggerKinetic = 1;
                    movementManagerEvent.context.markForResetRot();
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(
                                player, (triggerKinetic % 2 == 0) ? (-90f + 1e-3f) : (90f - 1e-3f));
                    } else {
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    }
                    controlled = true;
                }
            }
            if (!controlled && !canControl) {
                Vec3d vec3d = mc.player.getVelocity().multiply(6);
                Vec3d simu2 = MovTasks.simulateMovement(player, mc.player.getPos(), vec3d, false);
                if (!MathHelper.approximatelyEquals(simu2.x, vec3d.x)
                        || !MathHelper.approximatelyEquals(simu2.z, vec3d.z)) {

                    // player.setVelocity(vec3d.multiply(0.3 / speed));
                    triggerKinetic = 2;
                    movementManagerEvent.context.markForResetRot();
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(
                                player, (triggerKinetic % 2 == 0) ? (-90f + 1e-3f) : (90f - 1e-3f));
                    } else {
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    }
                    controlled = true;
                }
            }

            if (!controlled && triggerKinetic > 0) {
                triggerKinetic -= 1;
                movementManagerEvent.context.markForResetRot();
                // EntityUtils.setEntityPitchSafe(player,-player.getPitch());
                if (canControl) {
                    EntityUtils.setEntityPitchSafe(player, (triggerKinetic % 2 == 0) ? (-90f + 1e-3f) : (90f - 1e-3f));
                } else {
                    PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                }
                controlled = true;
            }
        }
    }

    //    FireworkRocketEntity lastRecordedWorldFireworkRocket;
    final Set<FireworkRocketEntity> recordedWorldFireworkRockets = ConcurrentHashMap.newKeySet();
    int lastOneFireworkRemovalTime = 0;
    int lastFireworkSpawnTime = 0;
    // boolean lastFireworkIsDeadSignal = false;

    public void onFireworkOwner(Event<MetadataUpdate> firework) {
        if (firework.context().metadata().id() == VDataFlag.ID_FIREWORK_SHOOTER_ID
                && firework.context().entity() instanceof FireworkRocketEntity fireworkEntity
                && mc.player != null
                && mc.player.isFallFlying()
                && firework.context().metadata().value() instanceof OptionalInt opint
                && opint.isPresent()
                && opint.getAsInt() == mc.player.getId()) {
            recordedWorldFireworkRockets.add(fireworkEntity);
            lastFireworkSpawnTime = Tasks.getTick();
        }
    }

    public void onFireworkRemove(Event<Entity> entityRemoveEvent) {
        if (entityRemoveEvent.context() instanceof FireworkRocketEntity fire) {
            onRemoveFirework(fire);
        }
    }

    public void onFireworkRemovePacketPeek(Event<EntitiesDestroyS2CPacket> eventRemoval) {
        if (checkNull()) return;
        new HashSet<>(this.recordedWorldFireworkRockets)
                .stream()
                        .filter(s -> eventRemoval.context.getEntityIds().contains(s.getId()))
                        .forEach(this::onRemoveFirework);
    }

    public void onWorldSwitch() {
        autoTakeOffFlag = false;
        pendingWaitResyncElytraUnbreakableTransaction = false;
        pendingStartFallFlyingTransaction = false;
        nextPacketResetFallFlying.clear();
        elytraUnbreakableDurabilityCounter.reset();
        armorGlideAbortCounter.reset();
        manuallySwitchTick = 0;
        fallFlyingSyncStartTick = -1;
        postTickSwapWithChestSlot = -1;
        armorGlideTransactionSlot = -1;
        armorGlideTransactionAbort = false;
        autoSwitchTransaction = false;
        pendingDelay3C3UCancel = false;
        currentTickStartGlideTransaction = false;
        rocketFlush.state(false);
        setback = false;
        nextFlyFireworksTicks = 0;
        pose = null;
        triggerKinetic = 0;
        executeNoKineticAfterTravel = false;
        tempStopFlying = false;

        recordedWorldFireworkRockets.clear();
        lastOneFireworkRemovalTime = 0;
        lastFireworkSpawnTime = 0;
        lastLaunchFindNoFireworks = -1;
        delayQueue.clear();
        flushing = false;
        timerVanilla.lastTimeFire = 0;
        timerVanilla.lastTimeWasAuto = false;
        overrideElytraMotion = null;
        nextRequestVelocity = null;

        currentDelayingArmorGlide = false;
        lastTransactionRecv = Integer.MIN_VALUE;
        currentDelayingLastTransaction = null;
        acceptFireworkRemovalDuringDelay = false;
        delayQueuedStopGlidingCount = 0;
        currentDelayingLock = null;
        lastStartDelayOrReleaseMs = 0L;
        elytraGlideBlinkQueueStartTick = -1;
        elytraGlideWaitUntilStateResync = false;
        duringElytraGlideBlinkTransaction = false;
        nextLandingSneak = 0;
        lastOnGroundOrInWaterAutoTakeOff = false;
        nextTickIsOnGroundTick = false;
        noGroundPeriod = new TimerExecutor();
        storedNoFallPacket = null;
        lastLagBackTick = 0;
        simulationFlight = Vec3d.ZERO;
    }

    private void onRemoveFirework(FireworkRocketEntity rocket) {
        if (recordedWorldFireworkRockets.remove(rocket)) {
            if (!anyAliveRocket()) {
                lastOneFireworkRemovalTime = Tasks.getTick();
            }
        }
    }

    public boolean canFireworkControlMotion() {
        return canFireworkControlMotion(1);
    }

    public boolean canFireworkControlMotion(int extraTicks) {
        extraTicks = Math.max(1, extraTicks);
        if (anyAliveRocket()) {
            return true;

        } else if (getTickSinceLastFirework() <= extraTicks) {
            return true;
        }
        return false;
    }

    public int getTickSinceLastFirework() {
        return anyAliveRocket() ? 0 : (Tasks.getTick() - lastOneFireworkRemovalTime);
    }

    public int getTicksSinceLastFireworkSpawn() {
        return anyAliveRocket() ? (Tasks.getTick() - lastFireworkSpawnTime) : -1;
    }

    private boolean anyAliveRocket() {
        return (recordedWorldFireworkRockets.stream().anyMatch(EntityUtils::isEntityValid));
    }

    int lastLaunchFindNoFireworks = -1;

    public void onNoFireworks() {
        // 10 s one mention
        if (lastLaunchFindNoFireworks < Tasks.getTick() - 10 * 20) {
            lastLaunchFindNoFireworks = Tasks.getTick();
            if (noEnoughFireworksNotify.get()) {
                logI18NSub("Firework", "message.module.elytra-extra.firework-not-found");
            }
        }
    }

    public void onHasFirework() {
        lastLaunchFindNoFireworks = -1;
    }

    public boolean isCurrentWaitingFireworkLaunch() {
        return SequencedActionManager.INSTANCE.isWaitingItemResponse(this::canBeUsedAsFireworks)
                || !timerVanilla.canFire()
                || (!delayQueue.isEmpty());
    }

    public boolean checkFireworkUseCondition(boolean vanillaCd) {
        var rocket = findRocket();
        if (rocket != null) {
            onHasFirework();
            int level = getRocketLevel(rocket);
            FireworkTimer timer = timerVanilla;
            boolean onFireworkDelay = currentDelayingLock != null;
            boolean onEndFireworkDelay = lastStartDelayOrReleaseMs > 0
                    && (lastStartDelayOrReleaseMs + fireworksDelayMS.get() - 50L) < System.currentTimeMillis();
            boolean delayingRemovalPacketScheduleLaunchAhead = (onFireworkDelay && onEndFireworkDelay)
                    || (currentDelayingArmorGlide && acceptFireworkRemovalDuringDelay);
            if (!isCurrentWaitingFireworkLaunch()) {
                if (!anyAliveRocket() && getTickSinceLastFirework() >= 1) {
                    // time limit, do not double
                    return true;
                    // use = true;
                }
                if (delayingRemovalPacketScheduleLaunchAhead) {
                    return true;
                }
                // timer use
                if (vanillaCd && timer.tryFire(level)) {
                    return true;
                }
                return false;
            }
        } else {
            onNoFireworks();
        }
        return false;
    }

    public void launchFirework(float pitch, float yaw) {
        if (checkFireworkUseCondition(true)) {
            timerVanilla.fire();
            Tasks.scheduleDelayedPre(
                    () -> {
                        if (checkNull()) return;
                        sendCustomUseFireworkPacket(pitch, yaw);
                    },
                    0);
        }
    }

    boolean currentDelayingArmorGlide;
    Integer lastTransactionRecv = Integer.MIN_VALUE;
    volatile Integer currentDelayingLastTransaction = null;
    boolean acceptFireworkRemovalDuringDelay = false;

    public void onArmorGlideDelay(Event<PacketStorage.Recv> event) {
        if (PacketManager.isAsyncOrNotTransactionS2CPacket(event.context.packetType())) {
            return;
        }
        if (event.context instanceof PacketStorageRecvImpl impl
                && impl.packet() instanceof CommonPingS2CPacket pingPacket) {
            lastTransactionRecv = pingPacket.getParameter();
        }
        boolean elytraBlink = blinkDelayInBound.get() && elytraBlinkEnable.get() && duringElytraGlideBlinkTransaction;
        boolean notWorking = checkNull()
                || !((isCurrentArmorGliding() && armorGlideMaxDelayTicks.get().isPresent()) || elytraBlink);
        if (currentDelayingArmorGlide) {
            event.cancel();

            if (event.context instanceof PacketStorageRecvImpl impl
                    && event.<Boolean>getArgs(1)
                    && containsFireworkRemoval(impl.packet())) {
                acceptFireworkRemovalDuringDelay = true;
            }
            boolean continue1 = isCurrentArmorGliding()
                    && armorGlideMaxDelayTicks.get().isPresent()
                    && Tasks.getTick() - PlayerStateManager.INSTANCE.lastStartGlidingTick
                            < armorGlideMaxDelayTicks.get().getValue();
            boolean continue2 = elytraBlink && (!elytraGlideWaitUntilStateResync || delayQueuedStopGlidingCount <= 0);
            if (notWorking
                    || PacketManager.isDisconnectOrRespawn(event.context.packetType())
                    || (!continue1 && !continue2)
                    || ((event.context instanceof PacketStorageRecvImpl impl
                            && impl.packet() instanceof EntityStatusS2CPacket status
                            && status.getEntity(mc.world) == mc.player
                            && status.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING))) {
                currentDelayingArmorGlide = false;
                currentDelayingLastTransaction = null;
                acceptFireworkRemovalDuringDelay = false;
                PacketManager.scheduleImmediateFlush();
            }
            return;
        }
        if (notWorking) {
            return;
        }

        // give enough time to peek the transaction packet before metadata update comes, and 10ms isn't a big deal
        if (event.context.timestampMS() > System.currentTimeMillis() - 10) {
            event.cancel();
        }
        if (event.context instanceof PacketStorageRecvImpl impl) {
            Iterable<EntityTrackerUpdateS2CPacket> list;
            if (impl.packet() instanceof EntityTrackerUpdateS2CPacket update && update.id() == mc.player.getId()) {
                list = List.of(update);
            } else if (impl.packet() instanceof BundleS2CPacket bundle) {
                List<EntityTrackerUpdateS2CPacket> list0 = new ArrayList<>();
                for (var re : bundle.getPackets()) {
                    if (re instanceof EntityTrackerUpdateS2CPacket update && update.id() == mc.player.getId()) {
                        list0.add(update);
                    }
                }
                if (list0.isEmpty()) return;
                list = list0;
            } else {
                return;
            }
            Boolean finalGlidingOverrideState = null;
            for (var update : list) {
                for (var re : update.trackedValues()) {
                    if (re.id() == VDataFlag.ID_FLAGS) {
                        byte data = (byte) re.value();
                        finalGlidingOverrideState = ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) != 0);
                    }
                }
            }
            if (finalGlidingOverrideState == Boolean.FALSE) {
                boolean queue1 = isCurrentArmorGliding()
                        && armorGlideMaxDelayTicks.get().isPresent()
                        && Tasks.getTick() - PlayerStateManager.INSTANCE.lastStartGlidingTick
                                < armorGlideMaxDelayTicks.get().getValue();
                boolean queue2 = elytraBlink && (delayQueuedStopGlidingCount <= 0);
                if (queue1 || queue2) {
                    currentDelayingArmorGlide = true;
                    if (lastTransactionRecv != null) {
                        PacketManager.handleQueueIn(new PacketStorageRecvImpl(
                                new CommonPingS2CPacket(lastTransactionRecv),
                                event.context.timestampMS(),
                                impl.connection()));
                        currentDelayingLastTransaction = lastTransactionRecv;
                    }
                    event.cancel();
                }
            }
        }
    }

    int delayQueuedStopGlidingCount = 0;

    public void onArmorGlidePost(Event<PacketStorage.Recv> eventPost) {
        if (checkNull()) return;
        if (eventPost.context instanceof PacketStorageRecvImpl impl) {
            Iterable<EntityTrackerUpdateS2CPacket> list;
            if (impl.packet() instanceof EntityTrackerUpdateS2CPacket update && update.id() == mc.player.getId()) {
                list = List.of(update);
            } else if (impl.packet() instanceof BundleS2CPacket bundle) {
                List<EntityTrackerUpdateS2CPacket> list0 = new ArrayList<>();
                for (var re : bundle.getPackets()) {
                    if (re instanceof EntityTrackerUpdateS2CPacket update && update.id() == mc.player.getId()) {
                        list0.add(update);
                    }
                }
                if (list0.isEmpty()) return;
                list = list0;
            } else {
                return;
            }
            Boolean finalGlidingOverrideState = null;
            for (var update : list) {
                for (var re : update.trackedValues()) {
                    if (re.id() == VDataFlag.ID_FLAGS) {
                        byte data = (byte) re.value();
                        finalGlidingOverrideState = ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) != 0);
                    }
                }
            }
            if (finalGlidingOverrideState == Boolean.FALSE) {
                if (eventPost.<Boolean>getArgs(1)) {
                    delayQueuedStopGlidingCount += 1;
                }
                if (!eventPost.isCancelled()) {
                    delayQueuedStopGlidingCount -= 1;
                    delayQueuedStopGlidingCount = Math.max(0, delayQueuedStopGlidingCount);
                }
            }
        }
    }

    public void onPacketPing(Event<CommonPingS2CPacket> event) {
        if (checkNull()) return;
        if (event.isCancelled()) return;
        Integer sendTrans = currentDelayingLastTransaction;
        if (sendTrans != null && event.context.getParameter() == sendTrans) {
            currentDelayingLastTransaction = null;
            event.cancel();
        }
    }

    public void onPacketPong(Event<CommonPongC2SPacket> event) {
        if (checkNull()) return;
        if (event.isCancelled()) return;
        if (lastTransactionRecv != null && lastTransactionRecv == event.context.getParameter()) {
            lastTransactionRecv = null;
        }
        Integer sendTrans = currentDelayingLastTransaction;
        if (sendTrans != null && sendTrans == event.context.getParameter()) {
            currentDelayingLastTransaction = null;
            event.cancel();
        }
    }

    Object currentDelayingLock = null;
    Long lastStartDelayOrReleaseMs = 0L;

    private boolean containsFireworkRemoval(Packet<?> packet) {
        FireworkRocketEntity lastEntity = recordedWorldFireworkRockets.stream()
                .filter(EntityUtils::isEntityValid)
                .findFirst()
                .orElse(null);
        if (lastEntity == null) {
            return false;
        }
        int id = lastEntity.getId();
        EntitiesDestroyS2CPacket destroyS2C = null;
        if (packet instanceof EntitiesDestroyS2CPacket removeEntity) {
            var intList = removeEntity.getEntityIds();
            if (intList.contains(id)) {
                destroyS2C = removeEntity;
            } else {
                return false;
            }
        } else if (packet instanceof BundleS2CPacket bundlePacket) {
            for (var bundle : bundlePacket.getPackets()) {
                if (bundle instanceof EntitiesDestroyS2CPacket removeEntity) {
                    var intList = removeEntity.getEntityIds();
                    if (intList.contains(id)) {
                        destroyS2C = removeEntity;
                        break;
                    } else {
                        continue;
                    }
                }
            }
        } else {
            return false;
        }
        return destroyS2C != null;
    }

    public void onFireworkRemoval(Event<PacketStorage.Recv> eventRemoval) {
        if (eventRemoval.isCancelled()) return;
        if (PacketManager.isAsyncOrNotTransactionS2CPacket(eventRemoval.context.packetType())) {
            return;
        }
        boolean notWork = checkNull() || !fireworksLagDelay.get() || !mc.player.isFallFlying();

        if (currentDelayingLock != null) {
            eventRemoval.cancel();
            if (notWork
                    || PacketManager.isDisconnectOrRespawn(eventRemoval.context.packetType())
                    || eventRemoval.context.packetType() == PlayPackets.PLAYER_POSITION
                    || eventRemoval.context.timestampMS() > lastStartDelayOrReleaseMs + fireworksDelayMS.get()) {
                currentDelayingLock = null;
                PacketManager.scheduleImmediateFlush();
            }
            return;
        }
        if (notWork) {
            return;
        }

        // restart a lock
        if (eventRemoval.context.timestampMS() > lastStartDelayOrReleaseMs + fireworksDelayMS.get() + 50
                && eventRemoval.context instanceof PacketStorageRecvImpl impl
                && recordedWorldFireworkRockets.stream()
                                .filter(EntityUtils::isEntityValid)
                                .count()
                        == 1) {

            if (containsFireworkRemoval(impl.packet())) {
                eventRemoval.cancel();
                currentDelayingLock = new byte[0];
                lastStartDelayOrReleaseMs = System.currentTimeMillis();
            }
        }
    }

    public void onPacketQueueFlush() {
        currentDelayingLock = null;
        lastStartDelayOrReleaseMs = 0L;
    }

    int elytraGlideBlinkQueueStartTick = -1;
    boolean elytraGlideWaitUntilStateResync = false;
    boolean duringElytraGlideBlinkTransaction = false;

    private void flushImmediately() {
        if (checkNull()) return;
        PacketManager.schedule(() -> {
            if (elytraGlideBlinkQueueStartTick > 0) {
                // check for fallflying
                PacketManager.flushOutBound(() -> {
                    elytraGlideBlinkQueueStartTick = -1;
                });
            }
        });
    }

    private void flushLazy() {
        if (elytraGlideBlinkQueueStartTick > 0) {
            flushImmediately();
        }
    }

    private void shutdownTemp() {
        PacketManager.schedule(() -> {
            elytraGlideBlinkQueueStartTick = -1;
        });
    }

    private void startOutboundQueue() {
        if (checkNull()) return;
        int tick = Tasks.getTick();
        PacketManager.schedule(() -> {
            elytraGlideBlinkQueueStartTick = tick;
        });
    }

    public void onPacketOutQueue(Event<PacketStorage.Send> eventIn) {
        if (elytraGlideBlinkQueueStartTick > 0) {
            PacketType<?> packetType = eventIn.context.packetType();
            if (PacketManager.isAsyncOrNotTransactionC2SPacket(packetType)) {
                return;
            }
            eventIn.cancel();
        }
    }

    public void onPacketOutShutdown() {
        elytraGlideBlinkQueueStartTick = -1;
    }

    @Override
    public void postModify(Event<LegalMovementManager> movementManagerEvent) {
        if (storedNoFallPacket != null) {
            mc.getNetworkHandler().sendPacket(storedNoFallPacket);
            storedNoFallPacket = null;
        }
        // avoid packet Order conflict
        rocketFlush.state(false, () -> {
            if (ViaFabricPlusHooks.isSupportEndTick() && PacketOrderManager.INSTANCE.sprinting) {
                PostManager.INSTANCE.addNextTransactionAction((v) -> {
                    flushRockets();
                });
            } else {
                flushRockets();
            }
        });

        if (this.postTickSwapWithChestSlot != -1) {
            final int idx = this.postTickSwapWithChestSlot;
            switchSlotToArmor(idx);
            this.postTickSwapWithChestSlot = -1;
        }
        // using elytra glide
        if (this.manuallySwitchTick == Tasks.getTick()
                && armorFly.get()
                && !handControl.get()
                && armorGlideTransactionSlot == -1) {
            startArmorFlyTransaction(-1);
        }
        if (isCurrentArmorGliding() && poseFix.get()) {
            mc.player.setPose(EntityPose.STANDING);
        }

        //        if(canContinueArmorGliding()){
        //            flushRockets();
        //        }
        if (elytraBlinkEnable.get() && mc.player.isFallFlying()) {
            if (canContinueArmorGliding()) {
                if (duringElytraGlideBlinkTransaction && this.elytraGlideBlinkQueueStartTick > 0) {
                    if (!elytraGlideWaitUntilStateResync
                            && Tasks.getTick() > this.elytraGlideBlinkQueueStartTick + this.maxBlinkTicks.get()) {
                        flushImmediately();
                        boolean hasOtherArmorSwitchModule = hasResetFallFlyingQueue()
                                || hasPendingUnbreakableStartFallFlying()
                                || hasPendingArmorStartFallFlying();
                        if (hasOtherArmorSwitchModule
                                && !isValidElytra(mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
                            elytraGlideWaitUntilStateResync = true;
                            return;
                        }
                        // current may face server resync, item may be resync to not elytra(the state before the
                        // startQueue)
                        int slot = findEmptySlotForElytra();
                        if (slot == -1) {
                            duringElytraGlideBlinkTransaction = false;
                            elytraGlideWaitUntilStateResync = false;
                            return;
                        }
                        switchSlotToArmor(slot);
                        elytraGlideWaitUntilStateResync = true;
                    }
                }
            } else {
                flushImmediately();
            }
        } else {
            flushLazy();
        }
    }

    @Override
    public boolean postTick(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (tempStopFlying) {
            tempStopFlying = false;
            EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, true);
        }
        currentTickStartGlideTransaction = false;
        return true;
    }

    int nextLandingSneak;
    boolean lastOnGroundOrInWaterAutoTakeOff;

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (isCurrentArmorGliding() && isThisTickGlideMovementServerSideGlide()) {
            // fix grimac multiaction c, fix grimac elytra c,
            // ??
            if (DisablerManager.INSTANCE.isInventorySprintFix()) {
                PlayerInputUtils.of(player).sprint(false).applyInput(player);
            }
            PlayerInputUtils.of(player).sneak(false).applyInput(player);
        }

        if (!player.isFallFlying() && nextLandingSneak > 0) {
            nextLandingSneak -= 1;
            if (nextLandingSneak <= 1) {
                if (!player.isSneaking()) {
                    PlayerInputUtils.of(player).sneak(true).applyInput(player);
                }
            }
        }
    }

    @Override
    public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

    boolean nextTickIsOnGroundTick = false;
    TimerExecutor noGroundPeriod = new TimerExecutor();
    // Vec3d storedPos = null;
    Packet<?> storedNoFallPacket;
    int lastLagBackTick = 0;

    public void onAcceptTeleportation() {
        lastLagBackTick = Tasks.getTick();
        nextTickIsOnGroundTick = false;
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (player.isFallFlying()) {
            player.horizontalCollision = false;
            if (DisablerManager.INSTANCE.isInventoryWalkFix() && currentTickStartGlideTransaction) {
                var input = PlayerInputUtils.of(player);
                input.forward(false).backward(false).left(false).right(false).jump(true);
                input.applyInput(player);
            }
        }
        if (player.isFallFlying()) {
            if (isThisTickGlideMovementServerSideGlide() && antiKick.get()) {
                // must send packet to reset flight, which is a common case when player is not freezing or player is
                // freezing using FUtils
                ClientPlayerAccess.of(player).setForceSendPacketUpdate(true);
                FloatingUtils.INSTANCE.setSendPacketIgnoreRotation(true);
                FloatingUtils.INSTANCE.setForceSilent(false);
            } else if (isCurrentArmorGliding()) {
                ClientPlayerAccess.of(player).setResyncMovementPacketTicks(0);
            }
        }

        NoFall noFallModule = MovTasks.getNoFall();
        // handle nofall
        boolean handleNoFall = false;
        noFall:
        if (player.isFallFlying()
                && noFallLanding.get()
                && !nextTickIsOnGroundTick
                && noFallModule.entityStage == 1
                && (player.getY() <= noFallModule.lastOnGroundHeight - noFallModule.safeDistance
                        || Tasks.getTick() < lastLagBackTick + 3)) {
            if (MovTasks.getElytraJump().enable.get()) {
                break noFall;
            }
            if (noGroundPeriod.run(5)) {
                boolean shouldHandle = player.isOnGround() && !movementManagerEvent.context.playerStatus.onGround;
                if (shouldHandle) {
                    // use direct judgement, do not use rocketBuffer
                    if (anyAliveRocket()) {
                        // controlling tick
                        //  must send a PosOnly
                        // otherwise it will be recognized as a duplicateFull
                        storedNoFallPacket = VPacket.newPositionAndOnGround(
                                player.getX(),
                                movementManagerEvent.context.playerStatus.pos.y + 9E-8,
                                player.getZ(),
                                false,
                                mc.player.horizontalCollision);
                        movementManagerEvent.cancel();
                        ClientPlayerAccess.of(player).resyncPos();
                        player.setOnGround(false);
                        MovTasks.getNoFall()
                                .setLastOnGroundHeight(movementManagerEvent.context.playerStatus.pos.y + 9E-8);
                        handleNoFall = true;
                        break noFall;
                    }
                    if (armorFly.get() && armorGlideTransactionSlot != -1) {
                        player.setOnGround(false);
                        movementManagerEvent.context.resetPos();
                        FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                        handleNoFall = true;
                        break noFall;
                    }
                    // what can I say.
                    storedNoFallPacket = VPacket.newPositionAndOnGround(
                            player.getX(),
                            movementManagerEvent.context.playerStatus.pos.y + 9E-8,
                            player.getZ(),
                            false,
                            mc.player.horizontalCollision);
                    // must restore rotation, must send a PosOnly
                    // otherwise it will be recognized as a duplicateFull
                    movementManagerEvent.cancel();
                    ClientPlayerAccess.of(player).resyncPos();
                    player.setOnGround(false);
                    // storedPos = player.getPos();
                    handleNoFall = true;
                }
            }
        }

        if (!handleNoFall) {
            nextTickIsOnGroundTick = false;
        } else {
            nextTickIsOnGroundTick = true;
        }
    }

    Vec3d simulationFlight = Vec3d.ZERO;

    public void tickElytraSimulation() {
        if (mc.player.isFallFlying()) {

        } else {
            simulationFlight = Vec3d.ZERO;
        }
    }

    private ElytraMotion overrideElytraMotion;

    public void submitFireworkMotion(ElytraMotion motion) {
        overrideElytraMotion = motion;
    }

    private boolean hasFireworkMotionOverride() {
        return overrideElytraMotion != null;
    }

    Vec3d nextRequestVelocity = null;

    public void setOverridingFireworkVelocity(Vec3d vec3d) {
        nextRequestVelocity = vec3d;
    }

    public boolean hasFireworkVelocityOverrides() {
        return nextRequestVelocity != null;
    }

    public Vec3d requestNextOverrideVelocity() {
        if (nextRequestVelocity != null) {
            var re = nextRequestVelocity;
            nextRequestVelocity = null;
            return re;
        }
        return null;
    }

    public Vec3d applyAxisLimit(@Nullable Al al, Vec3d currentMotion, float pitch, float yaw, boolean applyGravity) {
        return ElytraOptimizeUtils.applyAxisLimit(al, currentMotion, pitch, yaw, applyGravity);
    }

    public Vec3d applyFireworkLimit(Vec3d currentMotion, float pitch, float yaw) {
        return currentMotion;
    }

    public static enum MotionMode implements ConfigEnum {
        VOID,
        FIRE_WORKS;

        @Override
        public String getConfigEnumType() {
            return "motionmode";
        }
    }

    public static class FireworkTimer {
        int lastTimeFire = 0;
        DoubleRef fireTicks;
        boolean lastTimeWasAuto = false;

        public FireworkTimer(DoubleRef fireTicks) {
            this.fireTicks = fireTicks;
        }

        public boolean canFire() {
            // do not fire too close,
            return Tasks.getTick() > lastTimeFire;
        }

        public boolean tryFire(int level) {
            if (lastTimeFire + (fireTicks.get() * level) < Tasks.getTick()) {
                return true;
            } else {
                return false;
            }
        }

        public void fire() {
            lastTimeFire = Tasks.getTick();
        }
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        if (presetEvent.context().getValue().isOptionPreset()) return;
        switch (presetEvent.context.getValue()) {
            case AC_GRIM, AC_GRIM_LEGACY -> noKineticMode.set(
                    noKineticMode.get().withValue(new WrapEnum<>(BypassMode.BYPASS_GRIM)));
            default -> noKineticMode.set(noKineticMode.get().withValue(new WrapEnum<>(BypassMode.NO_BYPASS)));
        }

        switch (presetEvent.context.getValue()) {
            case AC_GRIM_LEGACY, AC_GRIM -> {
                DisablerManager.INSTANCE.grimBadPacketsFix.set(true);
            }
            default -> {
                DisablerManager.INSTANCE.grimBadPacketsFix.set(false);
            }
        }
    }

    public static enum ArmorFlyMode implements ConfigEnum {
        TICK_LEGACY,
        TICK,
        TICK_3C3U;

        @Override
        public String getConfigEnumType() {
            return "armorflymode";
        }
    }

    public static enum Al implements ConfigEnum {
        V1,
        V2,
        V3,
        V3_PULLUP_ONLY,
        V4,
        V5,
        V5_PULLUP_ONLY;

        @Override
        public String getConfigEnumType() {
            return "elytra_extra_fireworks_al_type";
        }

        public boolean isPullupOnly() {
            return this == V3_PULLUP_ONLY || this == V5_PULLUP_ONLY;
        }
    }
}
