package me.matl114.utils.worldgen.features;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import me.matl114.utils.worldgen.loot.LootVersion;
import me.matl114.utils.worldgen.loot.VanillaGoldenAppleLootPredictor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** A dungeon chest and the loot seed {@code LootableInventory.setLootTable} drew for it. */
public record DungeonChest(BlockPos position, long lootSeed, LootVersion lootVersion) {
    public static final Identifier LOOT_TABLE = Identifier.ofVanilla("chests/simple_dungeon");

    public DungeonChest {
        position = Objects.requireNonNull(position).toImmutable();
        Objects.requireNonNull(lootVersion);
    }

    public Optional<List<ItemStack>> generateContents() {
        return VanillaGoldenAppleLootPredictor.generateContents(LOOT_TABLE, lootSeed, lootVersion);
    }

    public Optional<List<ItemStack>> generateInventoryContents() {
        return VanillaGoldenAppleLootPredictor.generateInventoryContents(LOOT_TABLE, lootSeed, lootVersion);
    }

    /** Empty when the seed is zero, which vanilla resolves through world random-sequence state. */
    public Optional<Boolean> containsEnchantedGoldenApple() {
        return VanillaGoldenAppleLootPredictor.containsEnchantedGoldenApple(LOOT_TABLE, lootSeed, lootVersion);
    }
}
