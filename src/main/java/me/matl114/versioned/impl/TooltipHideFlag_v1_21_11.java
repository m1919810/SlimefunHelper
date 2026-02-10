package me.matl114.versioned.impl;

import static net.minecraft.component.DataComponentTypes.*;

import java.util.Objects;
import javax.annotation.Nullable;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VHideFlag;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.ItemStack;

public enum TooltipHideFlag_v1_21_11 implements VHideFlag {
    HIDE_ALL("全部", null),
    HIDE_ADDITIONAL("额外", LORE),
    HIDE_ENCHANT("附魔", ENCHANTMENTS),
    HIDE_ATTRIBUTE("属性", ATTRIBUTE_MODIFIERS),
    HIDE_UNBREAKABLE("无法破坏", UNBREAKABLE),
    HIDE_DESTROYS("可破坏", CAN_BREAK),
    HIDE_PLACED_ON("可放置", CAN_PLACE_ON),
    HIDE_DYE("染色", DYED_COLOR),
    HIDE_ARMOR_TRIM("盔甲纹饰", TRIM),
    HIDE_STORED_ENCHANTS("附魔书", STORED_ENCHANTMENTS);
    String name;

    @Nullable
    ComponentType type;

    TooltipHideFlag_v1_21_11(String displayName, ComponentType<?> type) {
        this.name = displayName;
        this.type = type;
    }

    @Override
    public boolean isHide(ItemStack stack) {
        var component = ItemStackUtils.getInPatch(stack, TOOLTIP_DISPLAY);
        if (component == null) return false;
        if (this.type == null) {
            return component.hideTooltip();
        } else {
            return component.hiddenComponents().contains(this.type);
        }
    }

    @Override
    public void setHideFlag(ItemStack stack, boolean hide) {
        var component = ItemStackUtils.getInPatch(stack, TOOLTIP_DISPLAY);
        if (component == null) component = TooltipDisplayComponent.DEFAULT;
        if (this.type == null) {
            component = new TooltipDisplayComponent(hide, component.hiddenComponents());
        } else {
            component = component.with(this.type, hide);
        }
        if (Objects.equals(component, TooltipDisplayComponent.DEFAULT)) {
            ItemStackUtils.setOrRemoveChange(stack, TOOLTIP_DISPLAY, null);
        } else {
            ItemStackUtils.setOrRemoveChange(stack, TOOLTIP_DISPLAY, component);
        }
    }

    @Override
    public String displayName() {
        return name;
    }
}
