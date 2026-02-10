package me.matl114.utils;

import com.mojang.authlib.properties.PropertyMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.matl114.bukkit.BukkitItemStackUtils;
import me.matl114.versioned.api.VHideFlag;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class CustomItemStackBuilder {
    ItemStack stack = new ItemStack(Items.STONE);
    List<Text> tooltip = new ArrayList<>();

    public static CustomItemStackBuilder builder() {
        return new CustomItemStackBuilder();
    }

    public CustomItemStackBuilder() {}

    public CustomItemStackBuilder type(String type) {
        return type(Registries.ITEM.get(Identifier.tryParse(type)));
    }

    public CustomItemStackBuilder type(Item type) {
        if (type != Items.AIR) {
            int cnt = Math.min(1, stack.getCount());
            stack = stack.copyComponentsToNewStackIgnoreEmpty(type, cnt);
        }
        return this;
    }

    public CustomItemStackBuilder amount(int amount) {
        stack.setCount(amount);
        return this;
    }

    public CustomItemStackBuilder name(String name) {
        return name(ChatUtils.stringToText(name));
    }

    public CustomItemStackBuilder name(Text name) {
        ItemStackUtils.setOrRemoveChange(this.stack, DataComponentTypes.CUSTOM_NAME, name);
        return this;
    }

    public CustomItemStackBuilder lore() {
        tooltip.clear();
        return this;
    }

    public CustomItemStackBuilder append(Text tooltip) {
        this.tooltip.add(tooltip);
        return this;
    }

    public CustomItemStackBuilder append(String tooltip) {
        return append(ChatUtils.stringToText(tooltip));
    }

    public CustomItemStackBuilder endLore() {
        ItemStackUtils.setOrRemoveChange(
                this.stack, DataComponentTypes.LORE, new LoreComponent(List.copyOf(this.tooltip)));
        return this;
    }

    public CustomItemStackBuilder hideFlag(VHideFlag flag) {
        flag.setHideFlag(this.stack, true);
        return this;
    }

    public CustomItemStackBuilder skullHash(String hash) {
        ItemStackUtils.setOrRemoveChange(
                stack,
                DataComponentTypes.PROFILE,
                new ProfileComponent(
                        Optional.of("CS-CoreLib"),
                        Optional.of(UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8))),
                        BukkitItemStackUtils.buildPropertyMap(new PropertyMap(), hash)));
        return this;
    }

    public CustomItemStackBuilder skullOwner(String owner) {
        ItemStackUtils.setOrRemoveChange(
                stack,
                DataComponentTypes.PROFILE,
                new ProfileComponent(Optional.of(owner), Optional.empty(), new PropertyMap()));
        return this;
    }

    public CustomItemStackBuilder glint() {
        ItemStackUtils.setOrRemoveChange(stack, DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        return this;
    }

    public ItemStack build() {
        return stack.copy();
    }
}
