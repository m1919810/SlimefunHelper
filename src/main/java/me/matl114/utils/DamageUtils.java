package me.matl114.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.ComponentType;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.effect.EnchantmentEffectEntry;
import net.minecraft.enchantment.effect.EnchantmentValueEffect;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.loot.condition.DamageSourcePropertiesLootCondition;
import net.minecraft.loot.condition.EntityPropertiesLootCondition;
import net.minecraft.predicate.entity.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

public class DamageUtils {
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isType(RegistryKey<DamageType> key, String type) {
        return key != null && Objects.equals(key.getValue().getPath(), type);
    }

    public static float getMaxRealAttackDamage(
            PlayerEntity player, Entity livingEntity, ItemStack stack, double fallDistance, boolean attributeSwap) {
        var attribute = AttributeUtils.getAttributeWith(
                player, attributeSwap ? Map.of() : Map.of(EquipmentSlot.MAINHAND, stack));
        float f = (float) attribute.getValue(EntityAttributes.ATTACK_DAMAGE);
        return getAttackDamage(player, livingEntity, stack, fallDistance, f, 1.0F);
    }

    public static float getRealAttackDamage(
            PlayerEntity player,
            Entity livingEntity,
            ItemStack stack,
            double fallDistance,
            int cooldownTicks,
            boolean attributeSwap) {
        var attribute = AttributeUtils.getAttributeWith(
                player, attributeSwap ? Map.of() : Map.of(EquipmentSlot.MAINHAND, stack));
        float f = (float) attribute.getValue(EntityAttributes.ATTACK_DAMAGE);
        float g = (float) MathHelper.clamp(
                ((float) cooldownTicks + 0.5)
                        / (float) (1.0 / attribute.getValue(EntityAttributes.ATTACK_SPEED) * 20.0),
                0.0F,
                1.0F);
        return getAttackDamage(player, livingEntity, stack, fallDistance, f, g);
    }

    private static float getAttackDamage(
            PlayerEntity player,
            Entity entity,
            ItemStack stack,
            double fallDistance,
            float attackDamage,
            float cooldownProgress) {
        float f = player.isUsingRiptide() ? 8.0F : attackDamage;
        DamageSource damageSource = createDamageSource(player, player, stack);
        float g = cooldownProgress;
        float h = g * (getDamageAgainst(stack, f, damageSource, entity) - f);
        f *= (0.2F + g * g * 0.8F);
        if (stack.getItem() instanceof MaceItem mace) {
            f += (float) getSmashDamageBonus(stack, entity, damageSource, fallDistance);
        }
        if (canDealCritical(player, fallDistance)) {
            f *= 1.5F;
        }
        return f + h;
    }

    private static float getDamageAgainst(
            ItemStack weapon, float baseDamage, DamageSource damageSource, Entity target) {
        return getConditionalMultiplierByWeaponEnchantment(
                weapon, EnchantmentEffectComponentTypes.DAMAGE, damageSource, target, baseDamage);
    }

    public static float getMultipliedDamageByDifficulty(World world, float amount) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            amount = 0.0F;
        }

        if (world.getDifficulty() == Difficulty.EASY) {
            amount = Math.min(amount / 2.0F + 1.0F, amount);
        }

        if (world.getDifficulty() == Difficulty.HARD) {
            amount = amount * 3.0F / 2.0F;
        }
        return amount;
    }

    public static float getRawDamageByDifficulty(World world, float amount) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            amount = 0.0F;
        }
        if (world.getDifficulty() == Difficulty.EASY) {
            amount = amount >= 2.0F ? (amount - 1.0F) * 2.0F : amount;
        }

        if (world.getDifficulty() == Difficulty.HARD) {
            amount = amount * 2.0F / 3.0F;
        }
        return amount;
    }

    public static DamageSource createDamageSource(
            RegistryKey<DamageType> type, @Nullable Entity source, @Nullable Entity attacker) {
        RegistryEntry<DamageType> re =
                RegistryUtils.getRegistryEntry(mc.getNetworkHandler().getRegistryManager(), type);
        re = re == null
                ? RegistryUtils.getRegistryEntry(mc.getNetworkHandler().getRegistryManager(), DamageTypes.PLAYER_ATTACK)
                : re;
        return new DamageSource(re, source, attacker);
    }

    public static DamageSource createDirectDamageSource(RegistryKey<DamageType> type, @Nullable Entity attacker) {
        return createDamageSource(type, attacker, attacker);
    }

    public static DamageSource createDirectDamageSource(PlayerEntity attacker, ItemStack weapon) {
        return createDamageSource(attacker, attacker, weapon);
    }

    public static DamageSource createDamageSource(Entity source, PlayerEntity attacker, ItemStack weapon) {
        DamageSource newSource;
        try {
            newSource = (DamageSource) Optional.ofNullable(weapon.getItem().getDamageSource(attacker))
                    .orElse(attacker.getDamageSources().playerAttack(attacker));
            ;
        } catch (Throwable e) {
            newSource = createDamageSource(DamageTypes.PLAYER_ATTACK, source, attacker);
        }
        return newSource;
    }

    public static boolean canDealCritical(LivingEntity attacker, double fallDistance) {
        return fallDistance > 0.0
                && !attacker.isOnGround()
                && !attacker.isClimbing()
                && !attacker.isTouchingWater()
                && !attacker.hasStatusEffect(StatusEffects.BLINDNESS)
                && !attacker.hasVehicle()
                && !attacker.isSprinting();
    }

    public static double getSmashDamageBonus(ItemStack stack, Entity target, DamageSource source, double f) {
        if (target instanceof LivingEntity lv) {
            if (f <= 1.5) {
                return 0.0D;
            }
            double g;
            if (f <= 3.0) {
                g = 4.0 * f;
            } else if (f <= 8.0) {
                g = 12.0 + 2.0 * (f - 3.0);
            } else {
                g = 22.0 + f - 8.0;
            }
            return g
                    + EnchantmentUtils.calculate(
                                    stack,
                                    ((current, enchantment, level) -> {
                                        for (var re : enchantment
                                                .value()
                                                .getEffect(
                                                        EnchantmentEffectComponentTypes
                                                                .SMASH_DAMAGE_PER_FALLEN_BLOCK)) {
                                            current = re.effect().apply(level, randomSource, current);
                                        }
                                        return current;
                                    }),
                                    0.0F)
                            * f;
        } else {
            return 0.0D;
        }
    }

    public static final int STAGE_IS_INVULNERABLE = 0;
    public static final int STAGE_MULTIPLY_DIFFICULTY = 1;
    public static final int STAGE_CALCULATE_HURT_TIME = 2;
    public static final int STAGE_APPLY_ARMOR = 3;
    public static final int STAGE_APPLY_PROTECTION = 4;
    public static final int STAGE_APPLY_DAMAGE_AND_ABSORPTION = 5;

    public static boolean isPlayerInvulnerableTo(DamageSource source) {
        return false;
    }

    public static float getFinalDamage(PlayerEntity player, float rawDamage, DamageSource damageSource) {
        DamageContext context = fromPlayer(player).build();
        rawDamage = getDamageAfterDifficulty(rawDamage, damageSource, context);
        if (rawDamage <= 0.0F) {
            return 0.0F;
        }
        rawDamage = getDamageAfterHurtTime(rawDamage, damageSource, context);
        if (rawDamage <= 0.0F) {
            return 0.0F;
        }
        rawDamage = DamageUtils.getDamageAfterArmorReduce(rawDamage, null, damageSource, context);
        if (rawDamage <= 0.0F) {
            return 0.0F;
        }
        rawDamage = DamageUtils.getDamageAfterEffectAndProtection(rawDamage, damageSource, context);
        if (rawDamage <= 0.0F) {
            return 0.0F;
        }
        return rawDamage;
    }

    public static float getFinalAttackDamageAgainst(
            PlayerEntity attacker, ItemStack weapon, float rawDamage, DamageContext context) {
        DamageSource damageSource = createDamageSource(attacker, attacker, weapon);
        rawDamage = getDamageAfterDifficulty(rawDamage, damageSource, context);
        if (rawDamage <= 0.0F) return 0.0F;
        rawDamage = getDamageAfterHurtTime(rawDamage, damageSource, context);
        if (rawDamage <= 0.0F) return 0.0F;
        rawDamage = getDamageAfterArmorReduce(rawDamage, weapon, damageSource, context);
        if (rawDamage <= 0.0F) return 0.0F;
        return getDamageAfterEffectAndProtection(rawDamage, damageSource, context);
    }

    public static float getDamageAfterDifficulty(float currentVal, DamageSource source, DamageContext context) {
        if (source.isScaledWithDifficulty()) {
            return getMultipliedDamageByDifficulty(context.world, currentVal);
        }
        return currentVal;
    }

    public static float getDamageAfterHurtTime(float currentVal, DamageSource source, DamageContext context) {
        return currentVal;
    }

    public static float getDamageAfterArmorReduce(
            float currentVal, @Nullable ItemStack weaponOverride, DamageSource source, DamageContext context) {
        if (!source.isIn(DamageTypeTags.BYPASSES_ARMOR)) {
            currentVal = getDamageLeftAfterArmor(
                    (weaponOverride != null && source.getSource() == source.getAttacker())
                            ? weaponOverride
                            : source.getWeaponStack(),
                    currentVal,
                    source,
                    context.armor,
                    context.armorToughness);
        }

        return currentVal;
    }

    public static float getDamageAfterEffectAndProtection(
            float currentVal, DamageSource source, DamageContext context) {
        if (source.isIn(DamageTypeTags.BYPASSES_EFFECTS)) {
            return currentVal;
        } else {
            if (context.statusEffects.containsKey(StatusEffects.RESISTANCE)
                    && !source.isIn(DamageTypeTags.BYPASSES_RESISTANCE)) {
                int i = (context.statusEffects.get(StatusEffects.RESISTANCE) + 1) * 5;
                int j = 25 - i;
                float f = currentVal * (float) j;
                float g = currentVal;
                currentVal = Math.max(f / 25.0F, 0.0F);
            }

            if (currentVal <= 0.0F) {
                return 0.0F;
            } else if (source.isIn(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
                return currentVal;
            } else {
                float k = context.assumeProtectionFour
                        ? 16.0F
                        : getProtectionAmount(
                                context.armorSlots, EnchantmentEffectComponentTypes.DAMAGE_PROTECTION, source);

                if (k > 0.0F) {
                    currentVal = DamageUtil.getInflictedDamage(currentVal, k);
                }

                return currentVal;
            }
        }
    }

    public static void damageOrAbsorption(LivingEntity livingEntity, DamageSource source, float amount) {
        float f = amount;
        amount = Math.max(amount - livingEntity.getAbsorptionAmount(), 0.0F);
        livingEntity.setAbsorptionAmount(livingEntity.getAbsorptionAmount() - (f - amount));

        if (amount != 0.0F) {
            livingEntity.getDamageTracker().onDamage(source, amount);
            livingEntity.setHealth(livingEntity.getHealth() - amount);
            livingEntity.setAbsorptionAmount(livingEntity.getAbsorptionAmount() - amount);
        }
    }

    private static float getDamageLeftAfterArmor(
            @Nullable ItemStack sourceWeapon,
            float damageAmount,
            DamageSource damageSource,
            float armor,
            float armorToughness) {
        float i;
        label12:
        {
            float f = 2.0F + armorToughness / 4.0F;
            float g = MathHelper.clamp(armor - damageAmount / f, armor * 0.2F, 20.0F);
            float h = g / 25.0F;
            ItemStack itemStack = sourceWeapon;
            if (itemStack != null) {

                i = MathHelper.clamp(
                        getConditionalMultiplierByWeaponEnchantment(
                                itemStack, EnchantmentEffectComponentTypes.ARMOR_EFFECTIVENESS, damageSource, null, h),
                        0.0F,
                        1.0F);
                break label12;
            }

            i = h;
        }

        float j = 1.0F - i;
        return damageAmount * j;
    }

    private static final Random randomSource = new LocalRandom(1145141919);

    private static float getConditionalMultiplierByWeaponEnchantment(
            ItemStack stack,
            ComponentType<List<EnchantmentEffectEntry<EnchantmentValueEffect>>> listComponentType,
            DamageSource source,
            @Nullable Entity target,
            float h) {
        return EnchantmentUtils.calculate(
                stack,
                (v, en, i) -> {
                    Enchantment ench = en.value();
                    for (var re : ench.getEffect(listComponentType)) {
                        if (EnchantmentUtils.matchPartialCondition(re, (loot) -> {
                            if (loot instanceof DamageSourcePropertiesLootCondition damage) {
                                return damage.predicate().isEmpty()
                                        || matchDamageSource(
                                                source, damage.predicate().get());
                            } else if (target != null && loot instanceof EntityPropertiesLootCondition predicate) {
                                return predicate.predicate().isEmpty()
                                        || matchEntityTypes(
                                                target, predicate.predicate().get());
                            } else {
                                return true;
                            }
                        })) {
                            v = re.effect().apply(i, randomSource, v);
                        }
                    }
                    return v;
                },
                h);
    }

    private static float getProtectionAmount(
            Map<EquipmentSlot, ItemStack> equipments,
            ComponentType<List<EnchantmentEffectEntry<EnchantmentValueEffect>>> listComponentType,
            DamageSource source) {
        return EnchantmentUtils.calculate(
                equipments,
                (current, enchantment, level, stack, slot) -> {
                    for (var re : enchantment.value().getEffect(listComponentType)) {
                        if (EnchantmentUtils.matchPartialCondition(re, (loot -> {
                            if (loot instanceof DamageSourcePropertiesLootCondition damage) {
                                return damage.predicate().isEmpty()
                                        || matchDamageSource(
                                                source, damage.predicate().get());
                            } else {
                                return true;
                            }
                        }))) {
                            current = re.effect().apply(level, randomSource, current);
                        }
                    }
                    return current;
                },
                0.0F);
    }

    public static boolean matchDamageSource(DamageSource source, DamageSourcePredicate predicate) {
        for (var re : predicate.tags()) {
            if (!re.test(source.getTypeRegistryEntry())) {
                return false;
            }
        }
        if (predicate.directEntity().isPresent()
                && !matchEntityTypes(
                        source.getSource(), predicate.directEntity().get())) {
            return false;
        }
        if (predicate.sourceEntity().isPresent()
                && !matchEntityTypes(
                        source.getAttacker(), predicate.sourceEntity().get())) {
            return false;
        }
        if (predicate.isDirect().isPresent() && predicate.isDirect().get() != source.isDirect()) {
            return false;
        }
        return true;
    }

    private static boolean matchEntityTypes(Entity entity, EntityPredicate predicate) {
        if (entity == null) {
            return false;
        } else if (predicate.type().isPresent() && !(predicate.type().get()).matches(entity.getType())) {
            return false;
        } else {
            return true;
        }
    }

    public static DamageContext.Builder fromPlayer(PlayerEntity player) {
        return fromLivingEntity(player);
    }

    public static DamageContext.Builder fromLivingEntity(LivingEntity entity) {
        DamageContext.Builder builder = new DamageContext.Builder();
        AttributeContainer container = AttributeUtils.getAttributeWith(entity, Map.of());
        builder.withArmor((float) container.getValue(EntityAttributes.ARMOR));
        builder.withArmorToughness((float) container.getValue(EntityAttributes.ARMOR_TOUGHNESS));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getEquippedStack(slot);
            builder.withArmorSlot(slot, stack);
        }
        for (var re : entity.getStatusEffects()) {
            builder.withStatusEffect(re);
        }
        return builder;
    }

    public static DamageContext createContext(LivingEntity entity) {
        return fromLivingEntity(entity).build();
    }

    public static DamageContext createAssumeProtectionFourContext(LivingEntity entity) {
        return fromLivingEntity(entity).assumeProtectionFour(true).build();
    }

    public static final class DamageContext {
        private final World world;
        private final float armor;
        private final float armorToughness;
        private final boolean assumeProtectionFour;
        private final Map<EquipmentSlot, ItemStack> armorSlots;
        private final Map<RegistryEntry<net.minecraft.entity.effect.StatusEffect>, Integer> statusEffects;

        private DamageContext(DamageContext.Builder builder) {
            this.world = builder.world;
            this.armor = builder.armor;
            this.armorToughness = builder.armorToughness;
            this.assumeProtectionFour = builder.assumeProtectionFour;
            this.armorSlots = Map.copyOf(builder.armorSlots);
            this.statusEffects = Map.copyOf(builder.statusEffects);
        }

        public static final class Builder {
            private World world;
            private float armor;
            private float armorToughness;
            private boolean assumeProtectionFour;
            private final Map<EquipmentSlot, ItemStack> armorSlots = new HashMap<>();
            private final Map<RegistryEntry<net.minecraft.entity.effect.StatusEffect>, Integer> statusEffects =
                    new HashMap<>();

            private Builder() {
                this.world = MinecraftClient.getInstance().world;
            }

            private Builder(DamageContext context) {
                this.world = context.world;
                this.armor = context.armor;
                this.armorToughness = context.armorToughness;
                this.assumeProtectionFour = context.assumeProtectionFour;
                this.armorSlots.putAll(context.armorSlots);
                this.statusEffects.putAll(context.statusEffects);
            }

            public Builder withWorld(World world) {
                this.world = world;
                return this;
            }

            public Builder withArmor(float armor) {
                this.armor = armor;
                return this;
            }

            public Builder withArmorToughness(float armorToughness) {
                this.armorToughness = armorToughness;
                return this;
            }

            public Builder assumeProtectionFour(boolean assumeProtectionFour) {
                this.assumeProtectionFour = assumeProtectionFour;
                return this;
            }

            public Builder withArmorSlots(Map<EquipmentSlot, ItemStack> armorSlots) {
                this.armorSlots.putAll(armorSlots);
                return this;
            }

            public Builder withArmorSlot(EquipmentSlot slot, ItemStack stack) {
                this.armorSlots.put(slot, stack);
                return this;
            }

            public Builder withStatusEffect(
                    RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int amplifier) {
                if (effect != null && amplifier >= 0) {
                    this.statusEffects.merge(effect, amplifier, Math::max);
                }
                return this;
            }

            public Builder withPotionEffect(
                    RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int amplifier) {
                return withStatusEffect(effect, amplifier);
            }

            public Builder withStatusEffect(StatusEffectInstance statusEffect) {
                return withStatusEffect(statusEffect.getEffectType(), statusEffect.getAmplifier());
            }

            public DamageContext build() {
                return new DamageContext(this);
            }
        }
    }
}
