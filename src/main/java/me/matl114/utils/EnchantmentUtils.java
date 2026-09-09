package me.matl114.utils;

import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.effect.EnchantmentEffectEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.AllOfLootCondition;
import net.minecraft.loot.condition.AnyOfLootCondition;
import net.minecraft.loot.condition.InvertedLootCondition;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.registry.entry.RegistryEntry;
import org.apache.commons.lang3.mutable.MutableFloat;

public class EnchantmentUtils {
    public static float calculate(ItemStack stack, Calculator<Float> consumer, float baseValue) {
        MutableFloat mutableFloat = new MutableFloat(baseValue);
        EnchantmentHelper.forEachEnchantment(stack, (enchantment, level) -> {
            mutableFloat.setValue(consumer.calculate(mutableFloat.getValue(), enchantment, level));
        });
        return mutableFloat.getValue();
    }

    public static float calculate(LivingEntity stack, ContextAwareCalculator<Float> consumer, float baseValue) {
        MutableFloat mutableFloat = new MutableFloat(baseValue);
        EnchantmentHelper.forEachEnchantment(stack, (enchantment, level, context) -> {
            mutableFloat.setValue(
                    consumer.calculate(mutableFloat.getValue(), enchantment, level, context.stack(), context.slot()));
        });
        return mutableFloat.getValue();
    }

    public static float calculate(
            Map<EquipmentSlot, ItemStack> slotItemStackMap, ContextAwareCalculator<Float> consumer, float baseValue) {
        MutableFloat mutableFloat = new MutableFloat(baseValue);
        for (Map.Entry<EquipmentSlot, ItemStack> entry : slotItemStackMap.entrySet()) {
            forEachEnchantments(entry.getValue(), entry.getKey(), (enchantment, level) -> {
                mutableFloat.setValue(consumer.calculate(
                        mutableFloat.getValue(), enchantment, level, entry.getValue(), entry.getKey()));
            });
        }
        ;
        return mutableFloat.getValue();
    }

    public static boolean matchPartialCondition(
            EnchantmentEffectEntry<?> effectEntry, Predicate<LootCondition> testCondition) {
        if (effectEntry.requirements().isEmpty()) {
            return true;
        } else {
            LootCondition condition = effectEntry.requirements().get();
            return matchPartialCondition(condition, testCondition);
        }
    }

    public static boolean matchPartialCondition(LootCondition condition, Predicate<LootCondition> testCondition) {
        if (condition instanceof AllOfLootCondition allOf) {
            return allOf.terms.stream().allMatch(s -> matchPartialCondition(s, testCondition));
        } else if (condition instanceof AnyOfLootCondition anyOf) {
            return anyOf.terms.stream().anyMatch(s -> matchPartialCondition(s, testCondition));
        } else if (condition instanceof InvertedLootCondition invert) {
            return !matchPartialCondition(invert.term(), testCondition);
        } else {
            return testCondition.test(condition);
        }
    }

    private static void forEachEnchantments(ItemStack stack, EquipmentSlot slot, EnchantmentHelper.Consumer consumer) {
        if (!stack.isEmpty()) {
            ItemEnchantmentsComponent itemEnchantmentsComponent =
                    (ItemEnchantmentsComponent) stack.get(DataComponentTypes.ENCHANTMENTS);
            if (itemEnchantmentsComponent != null && !itemEnchantmentsComponent.isEmpty()) {

                for (var entry : itemEnchantmentsComponent.getEnchantmentEntries()) {
                    RegistryEntry<Enchantment> registryEntry = entry.getKey();
                    if ((registryEntry.value()).slotMatches(slot)) {
                        consumer.accept(registryEntry, entry.getIntValue());
                    }
                }
            }
        }
    }

    public interface Calculator<T> {
        public T calculate(T current, RegistryEntry<Enchantment> enchantment, int level);
    }

    public interface ContextAwareCalculator<T> {
        public T calculate(
                T current,
                RegistryEntry<Enchantment> enchantment,
                int level,
                ItemStack stack,
                @Nullable EquipmentSlot slot);
    }
}
