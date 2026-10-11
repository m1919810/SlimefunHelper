package me.matl114.utils.structures;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.SuspiciousStewEffectsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/** Simulates vanilla 1.21.11 loot for supported chest tables. */
public final class VanillaGoldenAppleLootPredictor {
    private static final Identifier DESERT_PYRAMID = Identifier.ofVanilla("chests/desert_pyramid");
    private static final Identifier ANCIENT_CITY = Identifier.ofVanilla("chests/ancient_city");
    private static final Identifier ANCIENT_CITY_ICE_BOX = Identifier.ofVanilla("chests/ancient_city_ice_box");
    private static final Identifier RUINED_PORTAL = Identifier.ofVanilla("chests/ruined_portal");
    private static final Identifier END_CITY = Identifier.ofVanilla("chests/end_city_treasure");

    private static final List<Pool> DESERT_PYRAMID_POOLS = List.of(
            new Pool(2, 4, List.of(
                    Entry.uniform(5, Items.DIAMOND, 1, 3),
                    Entry.uniform(15, Items.IRON_INGOT, 1, 5),
                    Entry.uniform(15, Items.GOLD_INGOT, 2, 7),
                    Entry.uniform(15, Items.EMERALD, 1, 3),
                    Entry.uniform(25, Items.BONE, 4, 6),
                    Entry.uniform(25, Items.SPIDER_EYE, 1, 3),
                    Entry.uniform(25, Items.ROTTEN_FLESH, 3, 7),
                    Entry.uniform(20, Items.LEATHER, 1, 5),
                    Entry.item(15, Items.COPPER_HORSE_ARMOR),
                    Entry.item(15, Items.IRON_HORSE_ARMOR),
                    Entry.item(10, Items.GOLDEN_HORSE_ARMOR),
                    Entry.item(5, Items.DIAMOND_HORSE_ARMOR),
                    Entry.randomlyEnchanted(20, Items.BOOK),
                    Entry.item(20, Items.GOLDEN_APPLE),
                    Entry.item(2, Items.ENCHANTED_GOLDEN_APPLE),
                    Entry.empty(15))),
            new Pool(4, 4, List.of(
                    Entry.uniform(10, Items.BONE, 1, 8),
                    Entry.uniform(10, Items.GUNPOWDER, 1, 8),
                    Entry.uniform(10, Items.ROTTEN_FLESH, 1, 8),
                    Entry.uniform(10, Items.STRING, 1, 8),
                    Entry.uniform(10, Items.SAND, 1, 8))),
            new Pool(1, 1, List.of(
                    Entry.empty(6),
                    Entry.fixed(1, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 2))));

    private static final List<Pool> ANCIENT_CITY_POOLS = List.of(
            new Pool(5, 10, List.of(
                    Entry.uniform(1, Items.ENCHANTED_GOLDEN_APPLE, 1, 2),
                    Entry.item(1, Items.MUSIC_DISC_OTHERSIDE),
                    Entry.fixed(2, Items.COMPASS, 1),
                    Entry.uniform(2, Items.SCULK_CATALYST, 1, 2),
                    Entry.item(2, Items.NAME_TAG),
                    Entry.fixed(2, Items.DIAMOND_HOE, 1)
                            .withDamage(0.8F, 1.0F)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 30, 50),
                    Entry.fixed(2, Items.LEAD, 1),
                    Entry.fixed(2, Items.DIAMOND_HORSE_ARMOR, 1),
                    Entry.uniform(2, Items.LEATHER, 1, 5),
                    Entry.item(2, Items.MUSIC_DISC_13),
                    Entry.item(2, Items.MUSIC_DISC_CAT),
                    Entry.item(2, Items.DIAMOND_LEGGINGS)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 30, 50),
                    Entry.item(3, Items.BOOK)
                            .withEnchantment(EnchantmentOperation.RANDOM_FIXED, 1, 3),
                    Entry.uniform(3, Items.SCULK, 4, 10),
                    Entry.uniform(3, Items.SCULK_SENSOR, 1, 3),
                    Entry.uniform(3, Items.CANDLE, 1, 4),
                    Entry.uniform(3, Items.AMETHYST_SHARD, 1, 15),
                    Entry.uniform(3, Items.EXPERIENCE_BOTTLE, 1, 3),
                    Entry.uniform(3, Items.GLOW_BERRIES, 1, 15),
                    Entry.item(3, Items.IRON_LEGGINGS)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.uniform(4, Items.ECHO_SHARD, 1, 3),
                    Entry.uniform(4, Items.DISC_FRAGMENT_5, 1, 3),
                    Entry.uniform(5, Items.POTION, 1, 3).withStrongRegenerationPotion(),
                    Entry.randomlyEnchanted(5, Items.BOOK),
                    Entry.uniform(5, Items.BOOK, 3, 10),
                    Entry.uniform(5, Items.BONE, 1, 15),
                    Entry.uniform(5, Items.SOUL_TORCH, 1, 15),
                    Entry.uniform(7, Items.COAL, 6, 15))),
            new Pool(1, 1, List.of(
                    Entry.empty(75),
                    Entry.item(4, Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE),
                    Entry.item(1, Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE))));

    private static final List<Pool> RUINED_PORTAL_POOLS = List.of(
            new Pool(4, 8, List.of(
                    Entry.uniform(40, Items.OBSIDIAN, 1, 2),
                    Entry.uniform(40, Items.FLINT, 1, 4),
                    Entry.uniform(40, Items.IRON_NUGGET, 9, 18),
                    Entry.item(40, Items.FLINT_AND_STEEL),
                    Entry.item(40, Items.FIRE_CHARGE),
                    Entry.item(15, Items.GOLDEN_APPLE),
                    Entry.uniform(15, Items.GOLD_NUGGET, 4, 24),
                    Entry.randomlyEnchanted(15, Items.GOLDEN_SWORD),
                    Entry.randomlyEnchanted(15, Items.GOLDEN_AXE),
                    Entry.randomlyEnchanted(15, Items.GOLDEN_HOE),
                    Entry.randomlyEnchanted(15, Items.GOLDEN_SHOVEL),
                    Entry.randomlyEnchanted(15, Items.GOLDEN_PICKAXE),
                    Entry.randomlyEnchanted(15, Items.GOLDEN_BOOTS),
                    Entry.randomlyEnchanted(15, Items.GOLDEN_CHESTPLATE),
                    Entry.randomlyEnchanted(15, Items.GOLDEN_HELMET),
                    Entry.randomlyEnchanted(15, Items.GOLDEN_LEGGINGS),
                    Entry.uniform(5, Items.GLISTERING_MELON_SLICE, 4, 12),
                    Entry.item(5, Items.GOLDEN_HORSE_ARMOR),
                    Entry.item(5, Items.LIGHT_WEIGHTED_PRESSURE_PLATE),
                    Entry.uniform(5, Items.GOLDEN_CARROT, 4, 12),
                    Entry.item(5, Items.CLOCK),
                    Entry.uniform(5, Items.GOLD_INGOT, 2, 8),
                    Entry.item(1, Items.BELL),
                    Entry.item(1, Items.ENCHANTED_GOLDEN_APPLE),
                    Entry.uniform(1, Items.GOLD_BLOCK, 1, 2))),
            new Pool(1, 1, List.of(
                    Entry.empty(1),
                    Entry.uniform(2, Items.LODESTONE, 1, 2))));

    private static final List<Pool> ANCIENT_CITY_ICE_BOX_POOLS = List.of(
            new Pool(4, 10, List.of(
                    Entry.uniform(1, Items.SUSPICIOUS_STEW, 2, 6),
                    Entry.uniform(1, Items.GOLDEN_CARROT, 1, 10),
                    Entry.uniform(1, Items.BAKED_POTATO, 1, 10),
                    Entry.uniform(2, Items.PACKED_ICE, 2, 6),
                    Entry.uniform(4, Items.SNOWBALL, 2, 6))));

    private static final List<Pool> END_CITY_POOLS = List.of(
            new Pool(2, 6, List.of(
                    Entry.uniform(5, Items.DIAMOND, 2, 7),
                    Entry.uniform(10, Items.IRON_INGOT, 4, 8),
                    Entry.uniform(15, Items.GOLD_INGOT, 2, 7),
                    Entry.uniform(2, Items.EMERALD, 2, 6),
                    Entry.uniform(5, Items.BEETROOT_SEEDS, 1, 10),
                    Entry.item(3, Items.SADDLE),
                    Entry.item(1, Items.COPPER_HORSE_ARMOR),
                    Entry.item(1, Items.IRON_HORSE_ARMOR),
                    Entry.item(1, Items.GOLDEN_HORSE_ARMOR),
                    Entry.item(1, Items.DIAMOND_HORSE_ARMOR),
                    Entry.randomlyEnchanted(3, Items.DIAMOND_SWORD)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.DIAMOND_SPEAR)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.DIAMOND_BOOTS)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.DIAMOND_CHESTPLATE)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.DIAMOND_LEGGINGS)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.DIAMOND_HELMET)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.DIAMOND_PICKAXE)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.DIAMOND_SHOVEL)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.IRON_SWORD)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.IRON_BOOTS)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.IRON_CHESTPLATE)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.IRON_LEGGINGS)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.IRON_HELMET)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.IRON_PICKAXE)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39),
                    Entry.randomlyEnchanted(3, Items.IRON_SHOVEL)
                            .withEnchantment(EnchantmentOperation.WITH_LEVELS, 20, 39))),
            new Pool(1, 1, List.of(
                    Entry.empty(14),
                    Entry.item(1, Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE))));

    private VanillaGoldenAppleLootPredictor() {}

    /**
     * Returns the complete generated stacks before the chest's final slot shuffle. The returned
     * multiset includes every pool and all stack functions; zero loot seeds use world sequence state
     * and therefore cannot be reconstructed from the chest seed alone.
     */
    public static Optional<List<ItemStack>> generateContents(
            Identifier lootTable, long lootSeed) {
        return simulate(lootTable, lootSeed).map(GeneratedLoot::stacks);
    }

    /**
     * Generates contents from the caller's current random-sequence state. This is required when
     * vanilla's LootTableSeed is zero; the supplied random source is advanced by loot generation.
     */
    public static Optional<List<ItemStack>> generateContents(Identifier lootTable, Random random) {
        return simulate(lootTable, random).map(GeneratedLoot::stacks);
    }

    /** Returns the final 27 chest slots, including vanilla stack splitting and slot shuffling. */
    public static Optional<List<ItemStack>> generateInventoryContents(Identifier lootTable, long lootSeed) {
        return simulate(lootTable, lootSeed).map(generated -> fillChest(generated.stacks(), generated.random()));
    }

    /** Generates final chest slots from the caller's current random-sequence state. */
    public static Optional<List<ItemStack>> generateInventoryContents(Identifier lootTable, Random random) {
        return simulate(lootTable, random).map(generated -> fillChest(generated.stacks(), generated.random()));
    }

    private static Optional<GeneratedLoot> simulate(Identifier lootTable, long lootSeed) {
        if (lootSeed == 0L) {
            return Optional.empty();
        }

        return simulate(lootTable, Random.create(lootSeed));
    }

    private static Optional<GeneratedLoot> simulate(Identifier lootTable, Random random) {
        List<Pool> pools = poolsFor(lootTable);
        if (pools == null) {
            return Optional.empty();
        }
        java.util.Objects.requireNonNull(random);
        RegistryWrapper.Impl<Enchantment> enchantments =
                DefaultWorldgenRegistryManager.registry().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
        Optional<RegistryEntryList.Named<Enchantment>> randomLootSet =
                enchantments.getEntry(EnchantmentTags.ON_RANDOM_LOOT);

        List<ItemStack> contents = new ArrayList<>();
        for (Pool pool : pools) {
            int rolls = pool.rolls(random);
            for (int roll = 0; roll < rolls; roll++) {
                Entry entry = pool.select(random);
                if (entry.empty()) {
                    continue;
                }
                Optional<ItemStack> stack = generateEntry(
                        entry, random, enchantments, randomLootSet);
                if (stack.isEmpty()) {
                    return Optional.empty();
                }
                addProcessedStacks(contents, stack.get());
            }
        }
        return Optional.of(new GeneratedLoot(List.copyOf(contents), random));
    }

    /** Mirrors LootTable.processStacks before LootTable.supplyInventory fills the chest. */
    private static void addProcessedStacks(List<ItemStack> contents, ItemStack stack) {
        if (stack.getCount() < stack.getMaxCount()) {
            contents.add(stack);
            return;
        }

        int remaining = stack.getCount();
        while (remaining > 0) {
            ItemStack processedStack = stack.copyWithCount(Math.min(stack.getMaxCount(), remaining));
            remaining -= processedStack.getCount();
            contents.add(processedStack);
        }
    }

    private static List<ItemStack> fillChest(List<ItemStack> generatedStacks, Random random) {
        List<Integer> freeSlots = new ArrayList<>(27);
        for (int slot = 0; slot < 27; slot++) {
            freeSlots.add(slot);
        }
        Util.shuffle(freeSlots, random);

        List<ItemStack> stacks = new ArrayList<>(generatedStacks);
        spreadStacks(stacks, freeSlots.size(), random);
        List<ItemStack> inventory = new ArrayList<>(java.util.Collections.nCopies(27, ItemStack.EMPTY));
        for (ItemStack stack : stacks) {
            if (freeSlots.isEmpty()) {
                break;
            }
            inventory.set(freeSlots.remove(freeSlots.size() - 1), stack);
        }
        return List.copyOf(inventory);
    }

    private static void spreadStacks(List<ItemStack> stacks, int freeSlots, Random random) {
        List<ItemStack> splitCandidates = new ArrayList<>();
        for (var iterator = stacks.iterator(); iterator.hasNext(); ) {
            ItemStack stack = iterator.next();
            if (stack.isEmpty()) {
                iterator.remove();
            } else if (stack.getCount() > 1) {
                splitCandidates.add(stack);
                iterator.remove();
            }
        }

        while (freeSlots - stacks.size() - splitCandidates.size() > 0 && !splitCandidates.isEmpty()) {
            // Vanilla MathHelper.nextInt skips RNG when min == max; Random.nextInt(1) would consume a draw.
            ItemStack stack = splitCandidates.remove(
                    MathHelper.nextInt(random, 0, splitCandidates.size() - 1));
            ItemStack split = stack.split(MathHelper.nextInt(random, 1, stack.getCount() / 2));
            if (stack.getCount() > 1 && random.nextBoolean()) {
                splitCandidates.add(stack);
            } else {
                stacks.add(stack);
            }
            if (split.getCount() > 1 && random.nextBoolean()) {
                splitCandidates.add(split);
            } else {
                stacks.add(split);
            }
        }

        stacks.addAll(splitCandidates);
        Util.shuffle(stacks, random);
    }

    private record GeneratedLoot(List<ItemStack> stacks, Random random) {}

    public static Optional<Boolean> containsEnchantedGoldenApple(Identifier lootTable, long lootSeed) {
        Optional<List<ItemStack>> contents = generateContents(lootTable, lootSeed);
        return contents.map(stacks -> stacks.stream().anyMatch(stack -> stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)));
    }

    private static Optional<ItemStack> generateEntry(
            Entry entry,
            Random random,
            RegistryWrapper.Impl<Enchantment> enchantments,
            Optional<RegistryEntryList.Named<Enchantment>> randomLootSet) {
        int count = entry.minCount();
        ItemStack stack;
        if (entry.item() == Items.SUSPICIOUS_STEW) {
            boolean nightVision = random.nextInt(2) == 0;
            int minDuration = nightVision ? 7 : 5;
            int maxDuration = nightVision ? 10 : 7;
            int duration = (minDuration + random.nextInt(maxDuration - minDuration + 1)) * 20;
            stack = new ItemStack(Items.SUSPICIOUS_STEW);
            stack.apply(
                    DataComponentTypes.SUSPICIOUS_STEW_EFFECTS,
                    SuspiciousStewEffectsComponent.DEFAULT,
                    new SuspiciousStewEffectsComponent.StewEffect(
                            nightVision ? StatusEffects.NIGHT_VISION : StatusEffects.BLINDNESS,
                            duration),
                    SuspiciousStewEffectsComponent::with);
            count += random.nextInt(entry.maxCount() - entry.minCount() + 1);
            stack.setCount(count);
        } else {
            if (entry.randomCount() && entry.minCount() < entry.maxCount()) {
                count += random.nextInt(entry.maxCount() - entry.minCount() + 1);
            }
            if (entry.strongRegenerationPotion()) {
                stack = PotionContentsComponent.createStack(
                        Items.POTION,
                        Registries.POTION.getEntry(Potions.STRONG_REGENERATION.value()));
                stack.setCount(count);
            } else {
                stack = new ItemStack(entry.item(), count);
            }
        }

        if (entry.damageMin() != null) {
            float damageFraction = entry.damageMin()
                    + random.nextFloat() * (entry.damageMax() - entry.damageMin());
            int damage = (int) Math.floor(stack.getMaxDamage() * (1.0F - Math.clamp(damageFraction, 0.0F, 1.0F)));
            stack.setDamage(damage);
        }

        switch (entry.enchantment()) {
            case NONE -> {}
            case WITH_LEVELS -> {
                int level = entry.minEnchantmentLevel()
                        + random.nextInt(entry.maxEnchantmentLevel() - entry.minEnchantmentLevel() + 1);
                stack = EnchantmentHelper.enchant(random, stack, level, randomLootSet
                        .<java.util.stream.Stream<RegistryEntry<Enchantment>>>map(RegistryEntryList::stream)
                        .orElseGet(() -> enchantments.streamEntries()
                                .map(registryEntry -> (RegistryEntry<Enchantment>) registryEntry)));
            }
            case RANDOM_TAG -> {
                if (randomLootSet.isEmpty()) {
                    return Optional.empty();
                }
                ItemStack candidateStack = stack;
                List<RegistryEntry<Enchantment>> choices = randomLootSet.get().stream()
                        .filter(enchantment -> entry.item() == Items.BOOK
                                || enchantment.value().isSupportedItem(candidateStack))
                        .toList();
                if (choices.isEmpty()) {
                    return Optional.of(stack);
                }
                RegistryEntry<Enchantment> selected = choices.get(random.nextInt(choices.size()));
                int minLevel = selected.value().getMinLevel();
                int maxLevel = selected.value().getMaxLevel();
                int level = minLevel >= maxLevel ? minLevel : minLevel + random.nextInt(maxLevel - minLevel + 1);
                stack = enchant(stack, selected, level);
            }
            case RANDOM_FIXED -> {
                random.nextInt(1); // The table's only option is minecraft:swift_sneak.
                RegistryEntry<Enchantment> selected = enchantments
                        .getOrThrow(RegistryKey.of(
                                RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("swift_sneak")));
                int level = entry.minEnchantmentLevel()
                        + random.nextInt(entry.maxEnchantmentLevel() - entry.minEnchantmentLevel() + 1);
                stack = enchant(stack, selected, level);
            }
        }
        return Optional.of(stack);
    }

    private static ItemStack enchant(ItemStack stack, RegistryEntry<Enchantment> enchantment, int level) {
        if (stack.isOf(Items.BOOK)) {
            stack = new ItemStack(Items.ENCHANTED_BOOK, stack.getCount());
        }
        stack.addEnchantment(enchantment, level);
        return stack;
    }

    private static List<Pool> poolsFor(Identifier lootTable) {
        if (DESERT_PYRAMID.equals(lootTable)) {
            return DESERT_PYRAMID_POOLS;
        }
        if (ANCIENT_CITY.equals(lootTable)) {
            return ANCIENT_CITY_POOLS;
        }
        if (ANCIENT_CITY_ICE_BOX.equals(lootTable)) {
            return ANCIENT_CITY_ICE_BOX_POOLS;
        }
        if (RUINED_PORTAL.equals(lootTable)) {
            return RUINED_PORTAL_POOLS;
        }
        if (END_CITY.equals(lootTable)) {
            return END_CITY_POOLS;
        }
        return null;
    }

    private enum EnchantmentOperation {
        NONE,
        RANDOM_TAG,
        RANDOM_FIXED,
        WITH_LEVELS
    }

    private record Pool(int minRolls, int maxRolls, List<Entry> entries) {
        private int rolls(Random random) {
            return minRolls + (minRolls == maxRolls ? 0 : random.nextInt(maxRolls - minRolls + 1));
        }

        private Entry select(Random random) {
            int totalWeight = entries.stream().mapToInt(Entry::weight).sum();
            int selection = random.nextInt(totalWeight);
            for (Entry entry : entries) {
                selection -= entry.weight();
                if (selection < 0) {
                    return entry;
                }
            }
            throw new AssertionError("Loot pool selection exceeded its total weight");
        }
    }

    private record Entry(
            int weight,
            Item item,
            int minCount,
            int maxCount,
            boolean randomCount,
            Float damageMin,
            Float damageMax,
            EnchantmentOperation enchantment,
            int minEnchantmentLevel,
            int maxEnchantmentLevel,
            boolean strongRegenerationPotion) {
        private static Entry empty(int weight) {
            return create(weight, null, 0, 0, false);
        }

        private static Entry item(int weight, Item item) {
            return create(weight, item, 1, 1, false);
        }

        private static Entry fixed(int weight, Item item, int count) {
            return create(weight, item, count, count, false);
        }

        private static Entry uniform(int weight, Item item, int min, int max) {
            return create(weight, item, min, max, true);
        }

        private static Entry randomlyEnchanted(int weight, Item item) {
            return item(weight, item).withEnchantment(EnchantmentOperation.RANDOM_TAG, 0, 0);
        }

        private Entry withDamage(float min, float max) {
            return new Entry(
                    weight, item, minCount, maxCount, randomCount, min, max, enchantment,
                    minEnchantmentLevel, maxEnchantmentLevel, strongRegenerationPotion);
        }

        private Entry withEnchantment(EnchantmentOperation operation, int minLevel, int maxLevel) {
            return new Entry(
                    weight, item, minCount, maxCount, randomCount, damageMin, damageMax,
                    operation, minLevel, maxLevel, strongRegenerationPotion);
        }

        private Entry withStrongRegenerationPotion() {
            return new Entry(
                    weight, item, minCount, maxCount, randomCount, damageMin, damageMax,
                    enchantment, minEnchantmentLevel, maxEnchantmentLevel, true);
        }

        private boolean empty() {
            return item == null;
        }

        private static Entry create(int weight, Item item, int min, int max, boolean randomCount) {
            return new Entry(
                    weight, item, min, max, randomCount, null, null,
                    EnchantmentOperation.NONE, 0, 0, false);
        }
    }
}
