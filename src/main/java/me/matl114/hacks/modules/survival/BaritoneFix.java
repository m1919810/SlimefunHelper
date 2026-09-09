package me.matl114.hacks.modules.survival;

import java.util.function.Consumer;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.move.FloatingUtils;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.hooks.impl.baritone.BaritoneFuture;
import me.matl114.hooks.impl.baritone.BaritoneLanding;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;

public class BaritoneFix extends BaseModule implements LegalMovementManager.MovementModifier {
    public static BaritoneFix INSTANCE;

    static LegalMovementManager.DelegateMovementModifier instance;

    public BaritoneFix() {
        super("BaritoneFix");
        INSTANCE = this;
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final ModulePath fix = makePath(Configs.SURVIVAL_CONFIG, "baritone.fix");

    {
        portConfigs(makePath(Configs.MOV_CONFIG, "baritone.fix"), fix);
    }

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

    public final FlagRef autoJumpFix = flagBuilder(fix.add("auto-jump-fix")).build();

    public final FlagRef emergencyFixToLog =
            flagBuilder(fix.add("change-landing-to-log")).build();

    public final FlagRef pauseElytraProcess =
            flagBuilder(fix.add("baritone-conditional-pause")).build();

    public final KeyBindRef pauseKey = builder(fix.add("baritone-pause-hotkey"), KeyBindRef.TYPE)
            .defaultValue(new MultiKeyBind())
            .build();

    public final FlagRef fixSimulateError =
            flagBuilder(fix.add("fix-baritone-simulate-error")).build();

    public final FlagRef freezeWhenFailCalculate =
            flagBuilder(fix.add("fix-when-fail-calculate")).build();

    public final FlagRef baritoneExperimental1 =
            flagBuilder(fix.add("baritone-experiment-1")).build();

    public final DoubleRef baritoneExperimentHeight = doubleBuilder(fix.add("baritone-experiment-height-1"))
            .defaultValue(36.0D)
            .build();

    public final FlagRef baritoneExperimental2 =
            flagBuilder(fix.add("baritone-experiment-2")).build();

    public final DoubleRef exp2Min =
            doubleBuilder(fix.add("exp-2-min-height")).defaultValue(38.0D).build();

    public final DoubleRef exp2Max =
            doubleBuilder(fix.add("exp-2-max-height")).defaultValue(42.0D).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getChatSend(), this::onChat);
        registerListener(Listener.getChatSend(), this::onChatCommand);
        registerListener(BaritoneHooks.getMoveRotEvent(), this::onBaritoneMoveRot);
        registerListener(BaritoneHooks.getLandingEvent(), this::onBaritoneComplete);
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
        return InventoryUtils.computePlayerInventory(
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
                logI18N("message.module.baritone-fix.command-without-baritone");
                chatEvent.cancel();
            } else {
                initializeBaritoneSettings();
                if (!chatEvent.context().startsWith(commandPrefix.getValue())) {
                    logI18N("message.module.baritone-fix.command-prefix-mismatch", commandPrefix.getValue());
                    chatEvent.cancel();
                }
            }
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
                logI18N("message.module.baritone-fix.landing-cancelled", situation);
                FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                MovTasks.getElytraFlight().enable.set(true);
            }
            return true;
        }
        return false;
    }

    TimerExecutor lastAutoJumpExecutor = new TimerExecutor();

    public boolean handleAutoJump() {
        if (this.autoJumpFix.get() && !mc.player.isFallFlying()) {
            if (lastAutoJumpExecutor.run(20)) {
                logI18N("message.module.baritone-fix.autojump-takeoff");
                ElytraExtra.INSTANCE.autoTakeoff();
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

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (!lastAutoJumpExecutor.canRun(20) && mc.player.isFallFlying()) {
            Box blockCheckBox = mc.player.getBoundingBox().stretch(0, -1, 0).expand(2, 0, 2);
            Box checkHeadBox = mc.player.getBoundingBox().stretch(0, 1, 0);
            if (CollisionUtil.isBoxCollided(mc.world, mc.player, blockCheckBox)
                    && !CollisionUtil.isBoxCollided(mc.world, mc.player, checkHeadBox)) {
                BaritoneHooks.getInstance().updateBaritoneLookTarget(-89.0F, mc.player.getYaw());
            }
        }
    }

    public void onBaritoneMoveRot(Event<Vec2f> vec2fEvent) {}

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (this.baritoneExperimental2.get()
                && mc.player.isFallFlying()
                && BaritoneHooks.getInstance().isBaritoneElytraProcessing()
                && ElytraExtra.INSTANCE.armorFly.get()) {
            boolean usingArmorFly = ElytraExtra.INSTANCE.isCurrentArmorGliding();
            if (usingArmorFly && mc.player.getY() < exp2Min.get()) {
                ElytraExtra.INSTANCE.endArmorFlyTransaction(true);
            } else if (!usingArmorFly && mc.player.getY() > exp2Max.get()) {
                ElytraExtra.INSTANCE.startArmorFlyTransaction(-1);
            }
        }
        return true;
    }

    public void onBaritoneComplete(Event<BaritoneFuture> event) {
        if (event.context.getOnCompleteFutures().isEmpty()) {
            BaritoneLanding landingType = event.getArgs(0);
            switch (landingType) {
                case EMERGENCY -> {
                    if (handleLog("Emergency Landing")) {
                        event.cancel();
                        return;
                    }
                    if (handleFreeze("Emergency Landing")) {
                        event.cancel();
                        return;
                    }
                }
                case PATH_COMPLETE -> {
                    if (handleLog("Path Complete")) {
                        event.cancel();
                        return;
                    }
                    if (handleFreeze("Path Complete")) {
                        event.cancel();
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        acceptor.accept(createTitleLabel(
                BaritoneHooks.getInstance().isBaritoneAPISupported()
                        ? "widget.baritone-fix.baritone-api-support"
                        : "widget.baritone-fix.baritone-api-not-support",
                0,
                dblank,
                dx,
                dy));
        acceptor.accept(createTitleLabel(
                BaritoneHooks.getInstance().isBaritoneVersionSupported()
                        ? "widget.baritone-fix.baritone-support"
                        : "widget.baritone-fix.baritone-not-support",
                0,
                dblank,
                dx,
                dy));
    }
}
