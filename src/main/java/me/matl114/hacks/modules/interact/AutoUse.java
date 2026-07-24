package me.matl114.hacks.modules.interact;

import me.matl114.accessors.access.ClientAccess;
import me.matl114.accessors.hacks.KeyBindAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.versioned.api.VItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;

public class AutoUse extends BaseModule {
    public AutoUse() {
        bindFlag(enable);
    }

    public ModulePath path = makePath(Configs.INTERACT_CONFIG, "interaction-tweaks.auto-use");
    public final FlagRef enable = flagBuilder(path.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(path.addHotkey(), new MultiKeyBind(), path.addEnable()).build();

    public final FlagRef log = flagBuilder(path.add("log")).build();

    public final FlagRef onlyWhenEnemyNear =
            flagBuilder(path.add("only-when-enemy-near")).build();

    public final DoubleRef nearDistance =
            doubleBuilder(path.add("enemy-near-distance")).defaultValue(12.0D).build();

    public final FlagRef useFood = flagBuilder(path.add("use-food")).build();

    public final NBTRef<RegistryRegex<Item>> whiteList = builder(
                    path.add("use-item-white-list"), RegistryRegex.ITEM_TYPE)
            .defaultValue(new RegistryRegex<>(new Regex("^(.*spear|.*sword|shield)$"), Registries.ITEM))
            .build();

    public boolean isUsableNotFood(ItemStack stack) {
        var block = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
        if (block != null) return true;
        if (VItem.getInstance().isSpear(stack)) return true;
        var item = stack.getItem();
        if (item instanceof BowItem || item instanceof TridentItem || item instanceof CrossbowItem) return true;
        return false;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInputEvent);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        if (!checkNull() && lastAutoUsingSpear) {
            lastAutoUsingSpear = false;
            KeyBindAccess.of(mc.options.useKey).resetKeyState();
        }
    }

    private boolean pass() {
        if (onlyWhenEnemyNear.get()
                && TargetSelector.INSTANCE.searchAttackEntity(
                                nearDistance.get(), true, pl -> pl instanceof PlayerEntity)
                        == null) {
            return false;
        }
        return true;
    }

    public boolean isWorkingAcceptable(ItemStack stack) {
        return whiteList.get().test(stack.getItem())
                && (isUsableNotFood(stack)
                        || (useFood.get() && VItem.getInstance().isEatable(stack)));
    }

    boolean lastAutoUsingSpear = false;

    public void onInputEvent(Event<Void> event) {
        if (enable.get() && pass()) {
            if (!mc.player.isUsingItem()) {
                ItemStack mainHand = mc.player.getMainHandStack();
                ItemStack offHand = mc.player.getOffHandStack();
                Hand useHand;
                if (isWorkingAcceptable(mainHand)) {
                    useHand = Hand.MAIN_HAND;
                } else if (isWorkingAcceptable(offHand)) {
                    useHand = Hand.OFF_HAND;
                } else {
                    useHand = null;
                }
                if (useHand != null) {
                    ClientAccess.of(mc).simulateUseItem(useHand);
                    if (mc.player.isUsingItem() && mc.player.getActiveHand() == useHand) {
                        mc.options.useKey.setPressed(true);
                        if (log.get()) {
                            Debug.chat(
                                    ChatUtils.stringToText("&c[Use] &fStart to use"),
                                    VItem.getInstance().getFormattedName(mc.player.getActiveItem()));
                        }
                    } else {
                        KeyBindAccess.of(mc.options.useKey).resetKeyState();
                    }
                } else {
                    if (lastAutoUsingSpear) {
                        lastAutoUsingSpear = false;
                        KeyBindAccess.of(mc.options.useKey).resetKeyState();
                    }
                }
            } else {
                if (isWorkingAcceptable(mc.player.getActiveItem())) {
                    mc.options.useKey.setPressed(true);
                    lastAutoUsingSpear = true;
                } else {
                    if (lastAutoUsingSpear) {
                        lastAutoUsingSpear = false;
                        KeyBindAccess.of(mc.options.useKey).resetKeyState();
                    }
                }
            }
        } else {
            if (lastAutoUsingSpear) {
                lastAutoUsingSpear = false;
                KeyBindAccess.of(mc.options.useKey).resetKeyState();
            }
        }
    }
}
