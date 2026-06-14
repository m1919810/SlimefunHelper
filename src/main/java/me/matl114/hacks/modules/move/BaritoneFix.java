package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.config.ValueAccessor;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class BaritoneFix extends BaseModule implements LegalMovementManager.MovementModifier {
    public static BaritoneFix INSTANCE;
    static LegalMovementManager.DelegateMovementModifier instance;

    public BaritoneFix() {
        INSTANCE = this;
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final ModulePath fix = makePath(Configs.MOV_CONFIG, "baritone.fix");

    public final FlagRef enableDimensionFix =
            flagBuilder(fix.add("dimension-fix")).build();

    public final FlagRef enableSeedAutoImport =
            flagBuilder(fix.add("auto-import-seed")).build();

    public final FlagRef enableEmergencyLandingFix =
            flagBuilder(fix.add("emergency-landing-fix")).build();

    public final FlagRef disableInventoryCheck =
            flagBuilder(fix.add("disable-inventory-check")).build();

    public final FlagRef enableInventoryFireworks =
            flagBuilder(fix.add("enable-inventory-fireworks")).build();

    public final FlagRef enableGhostHandFireworks =
            flagBuilder(fix.add("enable-firework-swap")).build();

    public final FlagRef enableBaritoneCommandProtect =
            flagBuilder(fix.add("enable-baritone-command-protect")).build();

    public final FlagRef changeLandingToFreeze =
            flagBuilder(fix.add("change-landing-to-elytra-flight")).build();

    public final FlagRef emergencyFixToLog =
            flagBuilder(fix.add("change-landing-to-log")).build();

    public final FlagRef pauseElytraProcess =
            flagBuilder(fix.add("baritone-conditional-pause")).build();

    public final KeyBindRef pauseKey = builder(fix.add("baritone-pause-hotkey"), KeyBindRef.TYPE)
            .defaultValue(new MultiKeyBind())
            .build();

    public final FlagRef fixSimulateError =
            flagBuilder(fix.add("fix-baritone-simulate-error")).build();

    public final FlagRef fixLavaFly =
            flagBuilder(fix.add("fix-baritone-lava-fly")).build();

    public final FlagRef freezeWhenFailCalculate =
            flagBuilder(fix.add("fix-when-fail-calculate")).build();

    public final FlagRef baritoneExperimental1 =
            flagBuilder(fix.add("baritone-experiment-1")).build();

    public final DoubleRef baritoneExperimentHeight = doubleBuilder(fix.add("baritone-experiment-height-1"))
            .defaultValue(36.0D)
            .build();

    public final FlagRef baritoneExperimental2 =
            flagBuilder(fix.add("baritone-experiment-2")).build();

    public final FlagRef exp2LavaFix = flagBuilder(fix.add("exp-2-lava-fix")).build();

    public final DoubleRef exp2Min =
            doubleBuilder(fix.add("exp-2-min-height")).defaultValue(38.0D).build();

    public final DoubleRef exp2Max =
            doubleBuilder(fix.add("exp-2-max-height")).defaultValue(42.0D).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getChatSend(), this::onChat);
        registerListener(Listener.getChatSend(), this::onChatCommand);
    }

    public ValueAccessor<Integer> durabilitySetting;
    public ValueAccessor<Integer> fireworkSetting;
    public ValueAccessor<String> commandPrefix;

    private void initializeBaritoneSettings() {
        if (durabilitySetting == null || fireworkSetting == null || commandPrefix == null) {
            durabilitySetting = BaritoneHooks.getInstance().<Integer>getSetting("elytraMinimumDurability");
            fireworkSetting = BaritoneHooks.getInstance().<Integer>getSetting("elytraMinFireworksBeforeLanding");
            commandPrefix = BaritoneHooks.getInstance().getSetting("prefix");
        }
    }

    private boolean canGlideEquipment() {
        ElytraExtra extra = ElytraExtra.INSTANCE;
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (extra.isCurrentArmorGliding()) {
            return true;
        } else if (extra.enableUnbreakableElytra.get()) {
            return true;
        }
        return stack.getItem() == Items.ELYTRA
                && stack.getMaxDamage() - stack.getDamage() >= durabilitySetting.getValue();
    }

    private boolean hasEnoughFirework() {
        int fireworkAmount = fireworkSetting.getValue();
        ElytraExtra extra = ElytraExtra.INSTANCE;
        return InventoryUtils.computeInventory(
                        stack -> extra.canBeUsedAsFireworks(stack) ? (double) stack.getCount() : null, false)
                >= fireworkAmount;
    }

    public boolean checkCanContinueFlyingCustom() {
        initializeBaritoneSettings();
        return canGlideEquipment() && hasEnoughFirework();
    }

    public void onChat(Event<String> chatEvent) {
        if (enableBaritoneCommandProtect.get() && chatEvent.context().startsWith("#")) {
            if (!BaritoneHooks.getInstance().isEnabled()) {
                Debug.chat(
                        ChatUtils.stringToText(
                                "&c[BaritoneFix] &fLooks like you are using Baritone commands without Baritone, so we cancel your message"));
                chatEvent.cancel();
            } else {
                initializeBaritoneSettings();
                if (!chatEvent.context().startsWith(commandPrefix.getValue())) {
                    Debug.chat(ChatUtils.stringToText(
                            "&c[BaritoneFix] &fLooks like you are using Baritone commands # but your baritone command prefix has been set to "
                                    + commandPrefix.getValue() + " , so we cancel your message"));
                    chatEvent.cancel();
                }
            }
        }
    }

    public void checkFireworkSettings() {
        if (ElytraExtra.INSTANCE.rocketBoost.get()) {
            Debug.chat(
                    ChatUtils.stringToText(
                            "&c[BaritoneFix] &fLooks like you are using Baritone commands #elytra, So we turn off the ElytraExtra.rocketBoost to avoid conflict with Baritone"));
            ElytraExtra.INSTANCE.rocketBoost.set(false);
        }
    }

    public void onChatCommand(Event<String> eventCommandSay) {
        if (BaritoneHooks.getInstance().isEnabled() && eventCommandSay.context.startsWith("/")) {
            // handle baritone with comand prefix;
            initializeBaritoneSettings();
            String prefix = commandPrefix.getValue();
            if (eventCommandSay.context.startsWith(prefix)) {
                if (BaritoneHooks.getInstance().handleCommand(eventCommandSay.context)) {
                    eventCommandSay.cancel();
                }
            }
        }
    }

    public boolean handleLog(String situation) {
        if (this.emergencyFixToLog.getValue()) {
            Debug.info("Disconnect because of Emergency situation:", situation);
            MainTasks.scheduleDisconnect();
            return true;
        }
        return false;
    }

    public boolean handleFreeze(String situation) {
        if (this.changeLandingToFreeze.get()) {
            if (!MovTasks.getElytraFlight().enable.get()) {
                Debug.chat(ChatUtils.stringToText(
                        "&c[BaritoneFix] &fBaritone landing cancelled, reason: %s, turing on ElytraFlight..."
                                .formatted(situation)));
                FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                MovTasks.getElytraFlight().enable.set(true);
            }
            return true;
        }
        return false;
    }

    public boolean shouldPauseBaritoneElytra() {
        if (pauseElytraProcess.get()) {
            // DO NOT use other modules judgement
            if (FloatingUtils.INSTANCE.enableGrim.get()) {
                return true;
            }
            if (MovTasks.getElytraFlight().enable.get()) {
                return true;
            }
            if (pauseKey.get().isAllPressed()) {
                return true;
            }
        }
        return false;
    }

    public Box processBoxOfElytraFlight() {
        if (ElytraExtra.INSTANCE.armorFly.get() && ElytraExtra.INSTANCE.thisFallFlyingIsArmorFly != -1) {
            if (ElytraExtra.INSTANCE.canFireworkControlMotion()) {
                return mc.player.getBoundingBox().stretch(0, -0.5, 0).expand(0.0, 0, 0.0);
            }
            int ticksArmorFly = ElytraExtra.INSTANCE.getTickSinceLastFirework();
            double extraHeight = Math.min(4.0, 0.5 + ticksArmorFly * 0.08);
            return mc.player.getBoundingBox().stretch(0, -extraHeight, 0).expand(0.0, 0, 0.0);
        }
        return null;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        if ((this.fixLavaFly.get() || (this.baritoneExperimental2.get() && this.exp2LavaFix.get()))
                && mc.player.isFallFlying()
                && BaritoneHooks.getInstance().isElytraProcessing()
        // do not freeze when in fluid
        ) {
            // check condition
            var box = mc.player.getBoundingBox();
            Box box2 = null; // processBoxOfElytraFlight();
            box = (box2 != null ? box2 : box).expand(0.05, 0.3, 0.05);
            var blocks = (MathUtils.getOccupiedBlockPositions(box));
            for (var block : blocks) {
                BlockState state = mc.world.getBlockState(block);
                if (state.isLiquid() || state.getFluidState().getFluid() != Fluids.EMPTY) {
                    handleMayFlyIntoFluid(block, movementManagerEvent);
                    return;
                }
            }
        }
    }

    private int usingElytraFlightEmergency = 0;

    private void handleMayFlyIntoFluid(BlockPos block, Event<LegalMovementManager> movementManagerEvent) {
        if (fixLavaFly.get() && (PlayerStateManager.INSTANCE.lastInWall || PlayerStateManager.INSTANCE.lastInLava)) {
            Debug.chat(ChatUtils.stringToText(
                    "&c[BaritoneFix] &fBaritone flying into fluid detected, cancelling move..."));
            movementManagerEvent.context.playerStatus.restorePos();
            movementManagerEvent.cancel();
        }
        if (this.baritoneExperimental2.get() && exp2LavaFix.get() && ElytraExtra.INSTANCE.armorFly.get()) {
            boolean usingArmorFly = ElytraExtra.INSTANCE.thisFallFlyingIsArmorFly != -1;
            if (usingArmorFly) {
                ElytraExtra.INSTANCE.endArmorFlyTransaction();
                usingElytraFlightEmergency = Tasks.getTick() + 20;
            }
            if (Tasks.getTick() + 4 < usingElytraFlightEmergency) {
                movementManagerEvent.context.playerStatus.restorePos();
                movementManagerEvent.cancel();
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (this.baritoneExperimental2.get()
                && mc.player.isFallFlying()
                && BaritoneHooks.getInstance().isElytraProcessing()
                && ElytraExtra.INSTANCE.armorFly.get()) {
            boolean usingArmorFly = ElytraExtra.INSTANCE.thisFallFlyingIsArmorFly != -1;
            if (usingElytraFlightEmergency > Tasks.getTick()) {
                if (usingArmorFly) {
                    ElytraExtra.INSTANCE.endArmorFlyTransaction();
                }
            } else {
                if (usingArmorFly && mc.player.getY() < exp2Min.get()) {
                    ElytraExtra.INSTANCE.endArmorFlyTransaction();
                } else if (!usingArmorFly && mc.player.getY() > exp2Max.get()) {
                    ElytraExtra.INSTANCE.startArmorFlyTransaction(-1);
                }
            }
        }
        return true;
    }
}
