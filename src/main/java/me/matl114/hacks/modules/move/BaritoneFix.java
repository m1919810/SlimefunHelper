package me.matl114.hacks.modules.move;

import java.util.function.Consumer;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.DisplayWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.elements.ColorLabelTextElement;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.task.ClickGui;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.hooks.impl.BaritoneFuture;
import me.matl114.hooks.impl.BaritoneLanding;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class BaritoneFix extends BaseModule {
    public static BaritoneFix INSTANCE;

    public BaritoneFix() {
        INSTANCE = this;
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
        registerListener(Listener.getEntityPostTickListener().getChannel(EntityType.PLAYER), this::onPostTick);
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

    public boolean handleAutoJump() {
        if (this.autoJumpFix.get() && !mc.player.isFallFlying()) {
            Debug.chat(ChatUtils.stringToText("&c[BaritoneFix] &fBaritone autoJump takeOff"));
            ElytraExtra.INSTANCE.autoTakeoff();
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

    public void onPostTick(Event<Entity> event) {
        if (event.context == mc.player) {
            if (this.baritoneExperimental2.get()
                    && mc.player.isFallFlying()
                    && BaritoneHooks.getInstance().isElytraProcessing()
                    && ElytraExtra.INSTANCE.armorFly.get()) {
                boolean usingArmorFly = ElytraExtra.INSTANCE.thisFallFlyingIsArmorFly != -1;
                if (usingArmorFly && mc.player.getY() < exp2Min.get()) {
                    ElytraExtra.INSTANCE.endArmorFlyTransaction(true);
                } else if (!usingArmorFly && mc.player.getY() > exp2Max.get()) {
                    ElytraExtra.INSTANCE.startArmorFlyTransaction(-1);
                }
            }
        }
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
        acceptor.accept(DisplayWidget.instance(0, dblank, dx, dy)
                .setRenderHandler(new ColorLabelTextElement(
                        TextProvider.of(Text.literal(
                                BaritoneHooks.getInstance().isEnabled() ? "已检测到可兼容的Baritone" : "未检出到可兼容的Baritone")),
                        () -> ClickGui.INSTANCE.textColor.get().withAlpha(255),
                        () -> ClickGui.INSTANCE.moduleListColor.get().withAlpha(255))));
    }
}
