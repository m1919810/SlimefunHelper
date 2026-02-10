package me.matl114.hacks.utils.multiblock;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;

public interface BlockMatcher {
    public Set<Block> getPotentials();

    default boolean match(Block b) {
        return getPotentials().contains(b);
    }

    public static record Single(Block block) implements BlockMatcher {
        public Set<Block> getPotentials() {
            return Set.of(block);
        }

        public boolean match(Block b) {
            return b == block;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Single && block == ((Single) o).block;
        }
    }

    public static record Tagged(TagKey<Block> blockTagKey) implements BlockMatcher {
        public Set<Block> getPotentials() {
            return Registries.BLOCK.getEntryList(blockTagKey).orElseThrow().stream()
                    .map(RegistryEntry::value)
                    .collect(Collectors.toUnmodifiableSet());
        }

        public boolean match(Block b) {
            return b.getRegistryEntry().isIn(blockTagKey);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Tagged && ((Tagged) o).blockTagKey == this.blockTagKey;
        }
    }
    // means the AIR in the recipe, any block is ok and none is required
    public static final BlockMatcher ANY_MATCH = new BlockMatcher() {
        @Override
        public Set<Block> getPotentials() {
            return Set.of();
        }

        @Override
        public boolean match(Block b) {
            return true;
        }
    };
    public static final BlockMatcher NONE_MATCH = new BlockMatcher() {
        @Override
        public Set<Block> getPotentials() {
            return Set.of();
        }

        @Override
        public boolean match(Block b) {
            return false;
        }
    };
}
