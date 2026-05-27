package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

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

    public final FlagRef disableInventoryCheck = flagBuilder(fix.add("disable-inventory-check"))
            .updateListener(s -> {
                if (s) {
                    setAllowInventoryToTrue();
                }
            })
            .build();

    public final FlagRef enableGhostHandFireworks =
            flagBuilder(fix.add("enable-firework-swap")).build();

    public final FlagRef enableBaritoneCommandProtect =
            flagBuilder(fix.add("enable-baritone-command-protect")).build();

    public final FlagRef emergencyFixToLog = flagBuilder(fix.add("change-landing-to-log"))
            .updateListener(s -> {
                if (s) {
                    setDisconnectWhenArrive();
                }
            })
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getChatSend(), this::onChat);
    }

    private void setAllowInventoryToTrue() {
        if (this.allowInventory != null) {
            this.allowInventory.setValue(true);
            if (mc.player != null) {
                Debug.chat(ChatUtils.stringToText("&c[BaritoneFix] &fSet baritone settings allowInventory to true"));
            }
        }
    }

    private void setDisconnectWhenArrive() {
        if (this.disconnectOnArrival != null) {
            this.disconnectOnArrival.setValue(true);
            if (mc.player != null) {
                Debug.chat(
                        ChatUtils.stringToText("&c[BaritoneFix] &fSet baritone settings disconnectOnArrival to true"));
            }
        }
    }

    public ValueAccessor<Integer> durabilitySetting;
    public ValueAccessor<Integer> fireworkSetting;
    public ValueAccessor<Boolean> allowInventory;
    public ValueAccessor<Boolean> disconnectOnArrival;

    private void initializeBaritoneSettings() {
        if (durabilitySetting == null
                || fireworkSetting == null
                || allowInventory == null
                || disconnectOnArrival == null) {
            durabilitySetting = BaritoneHooks.getInstance().<Integer>getSetting("elytraMinimumDurability");
            fireworkSetting = BaritoneHooks.getInstance().<Integer>getSetting("elytraMinFireworksBeforeLanding");
            allowInventory = BaritoneHooks.getInstance().<Boolean>getSetting("allowInventory");
            disconnectOnArrival = BaritoneHooks.getInstance().<Boolean>getSetting("disconnectOnArrival");
            setAllowInventoryToTrue();
            setDisconnectWhenArrive();
        }
    }

    private boolean canGlideEquipment() {
        ElytraExtra extra = ElytraExtra.INSTANCE;
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (extra.isCurrentArmorGliding()) {
            if (stack.getItem() != Items.ELYTRA) {
                return true;
            }
        } else if (extra.shouldElytraUnbreakable()) {
            if (extra.elytraUnbreakableSwitchSlot != -1) {
                return true;
            }
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

    public boolean handleLog() {
        if (this.emergencyFixToLog.getValue()) {
            MainTasks.scheduleDisconnect();
            return true;
        }
        return false;
    }
}
