package me.matl114.jsApi;

import com.mojang.serialization.JavaOps;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.inventory.MutableInventory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

@ApiMethod
public class ItemStackHelper {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static Map<String, Object> saveItemToMap(Object itemStack) {
        ItemStack itemStack1 = JsHelper.unwrap(itemStack, ItemStack.class);
        if (itemStack1.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return (Map<String, Object>) ItemStack.CODEC
                .encodeStart(ItemStackUtils.registry().getOps(JavaOps.INSTANCE), itemStack1)
                .getOrThrow();
    }

    public static ItemStack loadItemFromMap(Map<String, Object> itemStack) {
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.CODEC
                .decode(ItemStackUtils.registry().getOps(JavaOps.INSTANCE), itemStack)
                .getOrThrow()
                .getFirst();
    }

    public static String getCustomName(Object what) {
        ItemStack itemStack = JsHelper.unwrap(what, ItemStack.class);
        if (itemStack.isEmpty()) {
            return null;
        }
        Text text = ItemStackUtils.getCustomName(itemStack);
        return Objects.equals(text, Text.empty()) ? null : ChatUtils.textToLegacyString(text);
    }

    public static void setCustomName(Object what, String name) {
        ItemStack itemStack = JsHelper.unwrap(what, ItemStack.class);
        if (itemStack.isEmpty()) {
            return;
        }
        ItemStackUtils.setCustomName(itemStack, name == null ? null : ChatUtils.textFromJsonString(name));
    }

    public static List<String> getLore(Object what) {
        ItemStack itemStack = JsHelper.unwrap(what, ItemStack.class);
        if (itemStack.isEmpty()) {
            return null;
        }
        List<Text> texts = ItemStackUtils.getLore(itemStack);
        return texts.isEmpty()
                ? null
                : texts.stream()
                        .map(ChatUtils::textToLegacyString)
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public static void setLore(Object what, List<String> lore) {
        ItemStack itemStack = JsHelper.unwrap(what, ItemStack.class);
        if (itemStack.isEmpty()) {
            return;
        }
        ItemStackUtils.setLore(
                itemStack,
                (lore == null || lore.isEmpty())
                        ? null
                        : lore.stream()
                                .map(ChatUtils::textFromLegacyString)
                                .map(Text.class::cast)
                                .toList());
    }

    public static ComponentType<?> getComponentType(String name) {
        return RegistryHelper.getInRegistry(Registries.DATA_COMPONENT_TYPE, name);
    }

    public static Optional<?> getComponent(Object what, ComponentType<?> type) {
        ItemStack itemStack = JsHelper.unwrap(what, ItemStack.class);
        if (itemStack.isEmpty()) {
            return null;
        }
        var map = itemStack.components.changedComponents;
        return map == null ? null : map.get(type);
    }

    public static <T> void setComponent(Object what, ComponentType<T> type, @Nullable Optional<T> value) {
        ItemStack itemStack = JsHelper.unwrap(what, ItemStack.class);
        if (itemStack.isEmpty()) {
            return;
        }
        if (value == null) {
            ItemStackUtils.setOrRemoveChange(itemStack, type, null);
        } else if (value.isPresent()) {
            ItemStackUtils.setOrRemoveChange(itemStack, type, value.get());
        } else {
            ItemStackUtils.markRemoveAsChange(itemStack, type);
        }
    }

    public static Object getHelperItem(ItemStack stack) {
        return new xyz.wagyourtail.jsmacros.client.api.helpers.inventory.ItemStackHelper(stack);
    }

    public static Inventory createInventory(List<?> list, int size) {
        return new MutableInventory(
                size,
                list.stream()
                        .map(s -> JsHelper.unwrap(s, ItemStack.class))
                        .collect(Collectors.toCollection(ArrayList::new)));
    }

    public static Inventory createMappingInventory(List<ItemStack> list, int size) {
        return new MutableInventory(size, list);
    }

    public static Inventory createJSMappingInventory(List<?> list, int size) {
        List<xyz.wagyourtail.jsmacros.client.api.helpers.inventory.ItemStackHelper> helpers =
                (List<xyz.wagyourtail.jsmacros.client.api.helpers.inventory.ItemStackHelper>) list;
        if (helpers.size() < size) {
            helpers.add(new xyz.wagyourtail.jsmacros.client.api.helpers.inventory.ItemStackHelper(ItemStack.EMPTY));
        }
        return new Inventory() {
            @Override
            public int size() {
                return size;
            }

            @Override
            public boolean isEmpty() {
                return helpers.stream()
                        .allMatch(xyz.wagyourtail.jsmacros.client.api.helpers.inventory.ItemStackHelper::isEmpty);
            }

            @Override
            public ItemStack getStack(int slot) {
                return helpers.get(slot).getRaw();
            }

            @Override
            public ItemStack removeStack(int slot, int amount) {
                xyz.wagyourtail.jsmacros.client.api.helpers.inventory.ItemStackHelper helper = helpers.get(slot);
                if (!helper.isEmpty() && amount > 0) {
                    return helper.getRaw().split(amount);
                } else {
                    return ItemStack.EMPTY;
                }
            }

            @Override
            public ItemStack removeStack(int slot) {
                ItemStack itemStack = helpers.get(slot).getRaw();
                if (itemStack.isEmpty()) {
                    return ItemStack.EMPTY;
                } else {
                    helpers.set(
                            slot,
                            new xyz.wagyourtail.jsmacros.client.api.helpers.inventory.ItemStackHelper(ItemStack.EMPTY));
                    return itemStack;
                }
            }

            @Override
            public void setStack(int slot, ItemStack stack) {
                helpers.set(slot, new xyz.wagyourtail.jsmacros.client.api.helpers.inventory.ItemStackHelper(stack));
            }

            @Override
            public void markDirty() {}

            @Override
            public boolean canPlayerUse(PlayerEntity player) {
                return true;
            }

            @Override
            public void clear() {
                for (int i = 0; i < size; i++) {
                    setStack(i, ItemStack.EMPTY);
                }
            }
        };
    }
}
