package me.matl114.utils;

import static net.minecraft.entity.attribute.EntityAttributes.*;

import java.util.*;
import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.hooks.ViaFabricPlusHooks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

public class AttributeUtils {
    public static AttributeContainer getAttributeWith(
            LivingEntity living, Map<EquipmentSlot, ItemStack> equipmentOverrides) {
        AttributeContainer attributeContainer = new AttributeContainer(
                DefaultAttributeRegistry.get((EntityType<? extends LivingEntity>) living.getType()));
        attributeContainer.setFrom(living.getAttributes());
        for (Map.Entry<EquipmentSlot, ItemStack> entry : equipmentOverrides.entrySet()) {
            var slot = entry.getKey();
            var itemStack2 = entry.getValue();
            ItemStack toBeRemoved = living.getEquippedStack(slot);
            if (!toBeRemoved.isEmpty()) {
                toBeRemoved.applyAttributeModifiers(slot, (attribute, modifier) -> {
                    EntityAttributeInstance entityAttributeInstance = attributeContainer.getCustomInstance(attribute);
                    if (entityAttributeInstance != null) {
                        entityAttributeInstance.removeModifier(modifier);
                    }
                });
            }
            if (!itemStack2.isEmpty()
                    && !(itemStack2.isDamageable() && itemStack2.getDamage() >= itemStack2.getMaxDamage())) {
                itemStack2.applyAttributeModifiers(slot, (attribute, modifier) -> {
                    EntityAttributeInstance entityAttributeInstance = attributeContainer.getCustomInstance(attribute);
                    if (entityAttributeInstance != null) {
                        entityAttributeInstance.removeModifier(modifier.id());
                        entityAttributeInstance.addTemporaryModifier(modifier);
                    }
                });
            }
        }
        if (ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(20, 8)) {
            Map<EquipmentSlot, ItemStack> newMap = new HashMap<>(equipmentOverrides);
            for (var re : EquipmentSlot.values()) {
                if (!newMap.containsKey(re)) {
                    newMap.put(re, living.getEquippedStack(re));
                }
            }
            overrideViaAttributes(newMap, attributeContainer);
        }
        return attributeContainer;
    }

    public static void updateAttribute(LivingEntity living) {
        LivingEntityAccess.of(living).updateEquipmentAttributeChange();
    }

    public static Map<EquipmentSlot, ItemStack> getEquipmentChanges() {
        return null;
    }

    public static List<RegistryEntry<EntityAttribute>> ATTRIBUTES_1_21_1 = List.of(
            GENERIC_MOVEMENT_EFFICIENCY,
            GENERIC_WATER_MOVEMENT_EFFICIENCY,
            PLAYER_MINING_EFFICIENCY,
            PLAYER_SNEAKING_SPEED,
            PLAYER_SUBMERGED_MINING_SPEED,
            GENERIC_ATTACK_KNOCKBACK);
    //    public static void removeViaFabricAttributes(AttributeContainer container){
    //        if(ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(20, 8)){
    //            // < 1.21.1
    //            for (var re : ATTRIBUTES_1_21_1) {
    //                var instance = container.getCustomInstance(re);
    //                if(instance != null && instance.getBaseValue() != 0.0D){
    //                    instance.setBaseValue(0.0D);
    //                    instance.clearModifiers();
    //                }
    //            }
    //        }
    //    }
    //
    //    public static void convertVersionedModifierToViaValue(AttributeContainer container){
    //        if(ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(20, 8)){
    //            // < 1.21.1
    //            for (var re : ATTRIBUTES_1_21_1) {
    //                var instance = container.getCustomInstance(re);
    //                if(instance != null && instance.getValue() != 0.0D){
    //                    double value = instance.getValue();
    //                    instance.setBaseValue(value);
    //                    instance.clearModifiers();
    //                }
    //            }
    //        }
    //    }

    private static void setAttributeVia(
            AttributeContainer attributeContainer, RegistryEntry<EntityAttribute> attribute, double level) {
        var attributeInstance = attributeContainer.getCustomInstance(attribute);
        attributeInstance
                .clearModifiers(); // Minecraft is applying attribute modifiers in some situations, remove them before
        // we set the base value
        attributeInstance.setBaseValue(level);
    }

    private static int getEquipmentLevel(
            RegistryKey<Enchantment> enchantment, Map<EquipmentSlot, ItemStack> equipmentOverrides) {
        var entry = ItemStackUtils.registry()
                .getOptional(enchantment.getRegistryRef())
                .flatMap(s -> s.getEntry(enchantment))
                .orElseThrow();
        int i = 0;
        var var4 = equipmentOverrides.entrySet().iterator();

        while (var4.hasNext()) {
            var en = var4.next();
            if (entry.value().slotMatches(en.getKey())) {
                ItemStack itemStack = en.getValue();
                int j = EnchantmentHelper.getLevel(entry, itemStack);
                if (j > i) {
                    i = j;
                }
            }
        }

        return i;
    }

    public static void overrideViaAttributes(Map<EquipmentSlot, ItemStack> equipmentMap, AttributeContainer container) {
        // Update generic attributes for all entities
        setAttributeVia(
                container,
                EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY,
                getEquipmentLevel(Enchantments.DEPTH_STRIDER, equipmentMap) / 3D);
        final int efficiencyLevel = getEquipmentLevel(Enchantments.EFFICIENCY, equipmentMap);
        setAttributeVia(
                container,
                EntityAttributes.PLAYER_MINING_EFFICIENCY,
                efficiencyLevel > 0 ? efficiencyLevel * efficiencyLevel + 1D : 0D);
        setAttributeVia(
                container,
                EntityAttributes.PLAYER_SNEAKING_SPEED,
                0.3D + getEquipmentLevel(Enchantments.SWIFT_SNEAK, equipmentMap) * 0.15D);
        setAttributeVia(
                container,
                EntityAttributes.PLAYER_SUBMERGED_MINING_SPEED,
                getEquipmentLevel(Enchantments.AQUA_AFFINITY, equipmentMap) <= 0 ? 0.2D : 1D);
        setAttributeVia(
                container,
                EntityAttributes.GENERIC_ATTACK_KNOCKBACK,
                getEquipmentLevel(Enchantments.KNOCKBACK, equipmentMap));
    }
}
