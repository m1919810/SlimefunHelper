package me.matl114.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.EntityTypeTags;
import org.jetbrains.annotations.Nullable;

public class DamageUtils {
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isType(RegistryKey<DamageType> key, String type) {
        return key != null && Objects.equals(key.getValue().getPath(), type);
    }

    public static double getAttributeValue(
            RegistryEntry<EntityAttribute> entry, PlayerEntity player, ItemStack stack, EquipmentSlot slot) {
        double att = player.getAttributeBaseValue(entry);
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && !modifiers.modifiers().isEmpty()) {
            att = applyOperations(modifiers.modifiers(), entry, att, slot);
        }
        return att;
    }

    public static double getArmorValue(PlayerEntity player, ItemStack stack, EquipmentSlot slot) {
        return getAttributeValue(EntityAttributes.GENERIC_ARMOR, player, stack, slot);
    }

    public static double getArmorToughnessValue(PlayerEntity player, ItemStack stack, EquipmentSlot slot) {
        return getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, player, stack, slot);
    }

    public static double getAttackSpeed(PlayerEntity player, ItemStack stack) {
        double speed = player.getAttributeBaseValue(EntityAttributes.GENERIC_ATTACK_SPEED);
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && !modifiers.modifiers().isEmpty()) {
            speed = applyOperations(
                    modifiers.modifiers(), EntityAttributes.GENERIC_ATTACK_SPEED, speed, EquipmentSlot.MAINHAND);
        }
        return speed;
    }

    public static double applyOperations(
            List<AttributeModifiersComponent.Entry> modifiers,
            RegistryEntry<EntityAttribute> entityAttribute,
            double base,
            EquipmentSlot slot) {
        double d = base;
        Iterator var6 = modifiers.iterator();

        while (var6.hasNext()) {
            AttributeModifiersComponent.Entry entry = (AttributeModifiersComponent.Entry) var6.next();
            if (entry.slot().matches(slot) && Objects.equals(entityAttribute, entry.attribute())) {
                double e = entry.modifier().value();
                double var10001;
                switch (entry.modifier().operation()) {
                    case ADD_VALUE -> var10001 = e;
                    case ADD_MULTIPLIED_BASE -> var10001 = e * base;
                    case ADD_MULTIPLIED_TOTAL -> var10001 = e * d;
                    default -> throw new MatchException((String) null, (Throwable) null);
                }

                d += var10001;
            }
        }
        return d;
    }

    public static double getEnchantmentBonus(PlayerEntity player, Entity target, ItemStack stack) {
        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        float bonus = 0.0F;
        if (enchantments != null && !enchantments.isEmpty()) {
            for (var entry : enchantments.getEnchantmentEntries()) {
                RegistryEntry<Enchantment> enchantment = entry.getKey();
                int level = entry.getIntValue();

                // 锋利 (Sharpness)
                if (enchantment.matchesKey(Enchantments.SHARPNESS)) {
                    bonus += 1.0f + (level - 1) * 0.5f;
                }
                // 亡灵杀手 (Smite)
                else if (enchantment.matchesKey(Enchantments.SMITE)
                        && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_SMITE)) {
                    bonus += 2.5f * level;
                }
                // 节肢杀手 (Bane of Arthropods)
                else if (enchantment.matchesKey(Enchantments.BANE_OF_ARTHROPODS)
                        && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
                    bonus += 2.5f * level;
                }
                // 穿刺 (Impaling) —— 仅对三叉戟且目标为水生生物生效
                else if (enchantment.matchesKey(Enchantments.IMPALING)
                        && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_IMPALING)) {
                    bonus += 2.5f * level;
                }
            }
        }
        return bonus;
    }

    public static double getMaceAttackBonus(ItemStack stack, float height) {
        if (height <= 1.5) {
            // 原版在 shouldDealAdditionalDamage 中要求 >1.5 且不在滑翔
            // 此处仅返回 0，上层调用者应自行判断条件
            return 0.0;
        }
        double baseBonus;
        if (height <= 3.0) {
            baseBonus = 4.0 * height;
        } else if (height <= 8.0) {
            baseBonus = 12.0 + 2.0 * (height - 3.0);
        } else {
            baseBonus = 22.0 + (height - 8.0);
        }

        // 2. 密度附魔加成
        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null) {
            int densityLevel = ItemStackUtils.getEnchantmentLevel(enchantments, Enchantments.DENSITY);
            baseBonus += densityLevel * 0.5 * height;
        }

        return baseBonus;
    }

    public static double getAttackDamage(PlayerEntity player, Entity livingEntity, ItemStack stack) {
        double att = player.getAttributeBaseValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && !modifiers.modifiers().isEmpty()) {
            att = applyOperations(
                    modifiers.modifiers(), EntityAttributes.GENERIC_ATTACK_DAMAGE, att, EquipmentSlot.MAINHAND);
        }
        att += getEnchantmentBonus(player, livingEntity, stack);
        return att;
    }

    public static double getAttackDamage(
            PlayerEntity player, LivingEntity livingEntity, ItemStack stack, float cooldownProgress) {
        return getAttackDamage(player, livingEntity, stack);
    }

    public static double getAttackDamage(LivingEntity livingEntity, ItemStack stack) {
        return getAttackDamage(mc.player, livingEntity, stack);
    }

    public static float estimateDamageAfterReductions(PlayerEntity player, DamageSource source, float amount) {
        return estimateDamageAfterReductions(DamageContext.from(player, source), amount);
    }

    public static float estimateDamageAfterReductions(DamageContext context, float amount) {
        if (context == null || amount <= 0.0F) {
            return Math.max(amount, 0.0F);
        }

        DamageSource source = context.source();
        if (source == null || !source.isIn(DamageTypeTags.BYPASSES_ARMOR)) {
            amount = getEstimatedArmorReducedDamage(context, amount);
        }

        if (source != null && source.isIn(DamageTypeTags.BYPASSES_EFFECTS)) {
            return amount;
        }

        int resistanceAmplifier = context.getStatusEffectLevel(StatusEffects.RESISTANCE);
        if (resistanceAmplifier >= 0 && (source == null || !source.isIn(DamageTypeTags.BYPASSES_RESISTANCE))) {
            int reduction = (resistanceAmplifier + 1) * 5;
            amount = Math.max(amount * (25.0F - reduction) / 25.0F, 0.0F);
        }

        if (amount <= 0.0F || (source != null && source.isIn(DamageTypeTags.BYPASSES_ENCHANTMENTS))) {
            return Math.max(amount, 0.0F);
        }

        float protection = context.getProtectionAmount();
        if (protection > 0.0F) {
            amount = DamageUtil.getInflictedDamage(amount, protection);
        }

        return Math.max(amount, 0.0F);
    }

    private static float getEstimatedArmorReducedDamage(DamageContext context, float damageAmount) {
        float armor = context.armor();
        float armorToughness = context.armorToughness();
        float armorScale = 2.0F + armorToughness / 4.0F;
        float effectiveArmor = clamp(armor - damageAmount / armorScale, armor * 0.2F, 20.0F);
        float armorEffectiveness = effectiveArmor / 25.0F;
        return damageAmount * (1.0F - armorEffectiveness);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class DamageContext {
        private final @Nullable DamageSource source;
        private final @Nullable RegistryEntry<DamageType> damageType;
        private final float armor;
        private final float armorToughness;
        private final Map<RegistryKey<Enchantment>, Integer> enchantments;
        private final Map<RegistryEntry<net.minecraft.entity.effect.StatusEffect>, Integer> statusEffects;

        private DamageContext(Builder builder) {
            this.source = builder.source;
            this.damageType = builder.damageType;
            this.armor = builder.armor;
            this.armorToughness = builder.armorToughness;
            this.enchantments = Map.copyOf(builder.enchantments);
            this.statusEffects = Map.copyOf(builder.statusEffects);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Builder builder(@Nullable DamageSource source) {
            return new Builder().withDamageSource(source);
        }

        public static DamageContext from(@Nullable PlayerEntity player, @Nullable DamageSource source) {
            Builder builder = builder(source);
            if (player == null) {
                return builder.build();
            }

            builder.withArmor((float) player.getArmor());
            builder.withArmorToughness((float) player.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS));
            for (EquipmentSlot armorSlot : EquipmentSlot.values()) {
                ItemStack armorStack = player.getEquippedStack(armorSlot);
                if (armorStack == null || armorStack.isEmpty()) {
                    continue;
                }

                ItemEnchantmentsComponent enchantments = armorStack.get(DataComponentTypes.ENCHANTMENTS);
                if (enchantments == null || enchantments.isEmpty()) {
                    continue;
                }

                builder.withEnchantment(
                        Enchantments.PROTECTION,
                        ItemStackUtils.getEnchantmentLevel(enchantments, Enchantments.PROTECTION));
                builder.withEnchantment(
                        Enchantments.BLAST_PROTECTION,
                        ItemStackUtils.getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION));
                builder.withEnchantment(
                        Enchantments.FIRE_PROTECTION,
                        ItemStackUtils.getEnchantmentLevel(enchantments, Enchantments.FIRE_PROTECTION));
                builder.withEnchantment(
                        Enchantments.PROJECTILE_PROTECTION,
                        ItemStackUtils.getEnchantmentLevel(enchantments, Enchantments.PROJECTILE_PROTECTION));
                builder.withEnchantment(
                        Enchantments.FEATHER_FALLING,
                        ItemStackUtils.getEnchantmentLevel(enchantments, Enchantments.FEATHER_FALLING));
            }

            if (player.hasStatusEffect(StatusEffects.RESISTANCE)) {
                builder.withStatusEffect(
                        StatusEffects.RESISTANCE,
                        player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier());
            }

            return builder.build();
        }

        public DamageContext withDamageSource(@Nullable DamageSource source) {
            return builder(this).withDamageSource(source).build();
        }

        public DamageContext withDamageType(@Nullable RegistryEntry<DamageType> damageType) {
            return builder(this).withDamageType(damageType).build();
        }

        public DamageContext withArmor(float armor) {
            return builder(this).withArmor(armor).build();
        }

        public DamageContext withArmorToughness(float armorToughness) {
            return builder(this).withArmorToughness(armorToughness).build();
        }

        public DamageContext withEnchantment(RegistryKey<Enchantment> enchantment, int level) {
            return builder(this).withEnchantment(enchantment, level).build();
        }

        public DamageContext withStatusEffect(
                RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int amplifier) {
            return builder(this).withStatusEffect(effect, amplifier).build();
        }

        public DamageContext withPotionEffect(
                RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int amplifier) {
            return withStatusEffect(effect, amplifier);
        }

        public static Builder builder(DamageContext context) {
            return new Builder(context);
        }

        public @Nullable DamageSource source() {
            return source;
        }

        public @Nullable RegistryEntry<DamageType> damageType() {
            return damageType;
        }

        public float armor() {
            return armor;
        }

        public float armorToughness() {
            return armorToughness;
        }

        public int getEnchantmentLevel(RegistryKey<Enchantment> enchantment) {
            return enchantments.getOrDefault(enchantment, 0);
        }

        public int getStatusEffectLevel(RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect) {
            return statusEffects.getOrDefault(effect, -1);
        }

        public float getProtectionAmount() {
            DamageSource source = this.source;
            float protection = getEnchantmentLevel(Enchantments.PROTECTION);
            if (source == null) {
                return protection;
            }

            if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return 0.0F;
            }

            boolean isExplosion = source.isIn(DamageTypeTags.IS_EXPLOSION);
            boolean isFire = source.isIn(DamageTypeTags.IS_FIRE);
            boolean isProjectile = source.isIn(DamageTypeTags.IS_PROJECTILE);
            boolean isFall = source.isIn(DamageTypeTags.IS_FALL);
            if (isExplosion) {
                protection += getEnchantmentLevel(Enchantments.BLAST_PROTECTION) * 2.0F;
            }
            if (isFire) {
                protection += getEnchantmentLevel(Enchantments.FIRE_PROTECTION) * 2.0F;
            }
            if (isProjectile) {
                protection += getEnchantmentLevel(Enchantments.PROJECTILE_PROTECTION) * 2.0F;
            }
            if (isFall) {
                protection += getEnchantmentLevel(Enchantments.FEATHER_FALLING) * 3.0F;
            }

            return protection;
        }

        public static final class Builder {
            private @Nullable DamageSource source;
            private @Nullable RegistryEntry<DamageType> damageType;
            private float armor;
            private float armorToughness;
            private final Map<RegistryKey<Enchantment>, Integer> enchantments = new HashMap<>();
            private final Map<RegistryEntry<net.minecraft.entity.effect.StatusEffect>, Integer> statusEffects =
                    new HashMap<>();

            private Builder() {}

            private Builder(DamageContext context) {
                this.source = context.source;
                this.damageType = context.damageType;
                this.armor = context.armor;
                this.armorToughness = context.armorToughness;
                this.enchantments.putAll(context.enchantments);
                this.statusEffects.putAll(context.statusEffects);
            }

            public Builder withDamageSource(@Nullable DamageSource source) {
                this.source = source;
                this.damageType = source == null ? null : source.getTypeRegistryEntry();
                return this;
            }

            public Builder withDamageType(@Nullable RegistryEntry<DamageType> damageType) {
                this.damageType = damageType;
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

            public Builder withEnchantment(RegistryKey<Enchantment> enchantment, int level) {
                if (enchantment != null && level > 0) {
                    this.enchantments.merge(enchantment, level, Integer::sum);
                }
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

            public DamageContext build() {
                return new DamageContext(this);
            }
        }
    }
}
