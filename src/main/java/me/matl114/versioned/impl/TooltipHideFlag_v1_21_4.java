package me.matl114.versioned.impl;

import static me.matl114.utils.ItemStackUtils.*;
import static net.minecraft.component.DataComponentTypes.*;
import static net.minecraft.component.DataComponentTypes.STORED_ENCHANTMENTS;

import java.util.function.Predicate;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VHideFlag;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.item.BlockPredicatesChecker;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.trim.ArmorTrim;

public enum TooltipHideFlag_v1_21_4 implements VHideFlag {
    HIDE_ALL("全部", componentPredicate(HIDE_TOOLTIP), ItemStackUtils.TooltipsToggle.byComponent(HIDE_TOOLTIP)),
    HIDE_ADDITIONAL(
            "额外",
            componentPredicate(HIDE_ADDITIONAL_TOOLTIP),
            ItemStackUtils.TooltipsToggle.byComponent(HIDE_ADDITIONAL_TOOLTIP)),
    HIDE_ENCHANT(
            "附魔",
            componentPredicate(ENCHANTMENTS, (i) -> !i.showInTooltip, false),
            ItemStackUtils.TooltipsToggle.onComponent(ENCHANTMENTS, ItemEnchantmentsComponent::withShowInTooltip)),
    HIDE_ATTRIBUTE(
            "属性",
            componentPredicate(ATTRIBUTE_MODIFIERS, inv(AttributeModifiersComponent::showInTooltip), false),
            ItemStackUtils.TooltipsToggle.onComponent(
                    ATTRIBUTE_MODIFIERS, AttributeModifiersComponent::withShowInTooltip)),
    HIDE_UNBREAKABLE(
            "无法破坏",
            componentPredicate(UNBREAKABLE, inv(UnbreakableComponent::showInTooltip), false),
            ItemStackUtils.TooltipsToggle.onComponent(UNBREAKABLE, UnbreakableComponent::withShowInTooltip)),
    HIDE_DESTROYS(
            "可破坏",
            componentPredicate(CAN_BREAK, inv(BlockPredicatesChecker::showInTooltip), false),
            ItemStackUtils.TooltipsToggle.onComponent(CAN_BREAK, BlockPredicatesChecker::withShowInTooltip)),
    HIDE_PLACED_ON(
            "可放置",
            componentPredicate(CAN_PLACE_ON, inv(BlockPredicatesChecker::showInTooltip), false),
            ItemStackUtils.TooltipsToggle.onComponent(CAN_PLACE_ON, BlockPredicatesChecker::withShowInTooltip)),
    HIDE_DYE(
            "染色",
            componentPredicate(DYED_COLOR, inv(DyedColorComponent::showInTooltip), false),
            ItemStackUtils.TooltipsToggle.onComponent(DYED_COLOR, DyedColorComponent::withShowInTooltip)),
    HIDE_ARMOR_TRIM(
            "盔甲纹饰",
            componentPredicate(TRIM, inv(ArmorTrim::showInTooltip), false),
            ItemStackUtils.TooltipsToggle.onComponent(TRIM, ArmorTrim::withShowInTooltip)),
    HIDE_STORED_ENCHANTS(
            "附魔书",
            componentPredicate(STORED_ENCHANTMENTS, i -> !i.showInTooltip, false),
            ItemStackUtils.TooltipsToggle.onComponent(
                    STORED_ENCHANTMENTS, ItemEnchantmentsComponent::withShowInTooltip));
    public String display;
    public Predicate<ItemStack> hideFlagGetter;
    public ItemStackUtils.TooltipsToggle toggle;

    TooltipHideFlag_v1_21_4(String display, Predicate<ItemStack> stack, ItemStackUtils.TooltipsToggle toggle) {
        this.hideFlagGetter = stack;
        this.toggle = toggle;
        this.display = display;
    }

    private static <T> Predicate<T> inv(Predicate<T> tt) {
        return (val) -> !tt.test(val);
    }

    public boolean isHide(ItemStack stack) {
        return hideFlagGetter.test(stack);
    }

    public void setHideFlag(ItemStack stack, boolean hide) {
        this.toggle.apply(stack, !hide);
    }

    @Override
    public String displayName() {
        return display;
    }
}
