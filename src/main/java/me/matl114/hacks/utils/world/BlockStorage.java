package me.matl114.hacks.utils.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import me.matl114.versioned.api.VNbt;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Getter
public class BlockStorage extends IStorage {
    public static final Codec<BlockStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC
                            .xmap(Registries.BLOCK::get, Registries.BLOCK::getId)
                            .fieldOf("type")
                            .forGetter(BlockStorage::getType),
                    World.CODEC.fieldOf("dim").forGetter(BlockStorage::getDimension),
                    BlockPos.CODEC.fieldOf("pos").forGetter(BlockStorage::getPos),
                    Codec.unboundedMap(Codec.STRING, VNbt.CODEC)
                            .fieldOf("storage")
                            .forGetter(v -> v.storage))
            .apply(instance, BlockStorage::new));
    public Block type;
    public final BlockPos pos;

    public void setType(Block type) {
        if (this.type == type) return;
        this.type = type;
        dirty = true;
    }

    public BlockStorage(BlockPos pos) {
        super();
        this.pos = pos;
        this.type = mc.world.getBlockState(pos).getBlock();
    }

    public BlockStorage(RegistryKey<World> dimension, BlockPos pos) {
        this(Blocks.AIR, dimension, pos);
    }

    public BlockStorage(final Block type, final RegistryKey<World> dimension, final BlockPos pos) {
        this(type, dimension, pos, new ConcurrentHashMap<>());
    }

    public BlockStorage(
            final Block type, final RegistryKey<World> dimension, final BlockPos pos, Map<String, NbtElement> storage) {
        super(dimension, storage);
        this.type = type;

        this.pos = pos;
    }
}
