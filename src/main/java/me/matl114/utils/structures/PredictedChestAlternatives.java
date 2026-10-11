package me.matl114.utils.structures;

import java.util.List;
import java.util.Optional;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/** A known chest loot invocation whose world position has multiple possible Y values. */
public record PredictedChestAlternatives(List<BlockPos> possiblePositions, Identifier lootTable, long lootSeed) {
    public PredictedChestAlternatives {
        possiblePositions =
                possiblePositions.stream().map(BlockPos::toImmutable).distinct().toList();
        if (possiblePositions.isEmpty()) {
            throw new IllegalArgumentException("At least one possible chest position is required");
        }
    }

    public Optional<List<ItemStack>> generateContents() {
        return VanillaGoldenAppleLootPredictor.generateContents(lootTable, lootSeed);
    }

    public Optional<List<ItemStack>> generateContents(Random randomSequence) {
        return VanillaGoldenAppleLootPredictor.generateContents(lootTable, randomSequence);
    }

    public Optional<List<ItemStack>> generateInventoryContents() {
        return VanillaGoldenAppleLootPredictor.generateInventoryContents(lootTable, lootSeed);
    }

    public Optional<List<ItemStack>> generateInventoryContents(Random randomSequence) {
        return VanillaGoldenAppleLootPredictor.generateInventoryContents(lootTable, randomSequence);
    }

    public Optional<Boolean> containsEnchantedGoldenApple() {
        return VanillaGoldenAppleLootPredictor.containsEnchantedGoldenApple(lootTable, lootSeed);
    }
}
