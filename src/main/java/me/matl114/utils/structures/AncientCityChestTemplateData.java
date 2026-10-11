package me.matl114.utils.structures;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Lootable chest block-entity markers copied from the vanilla 1.21.11 Ancient City templates. */
public final class AncientCityChestTemplateData {
    private static final List<TemplateChests> VANILLA = List.of(
            fixedChestTemplate(
                    "ancient_city/city_center/city_center_2",
                    pos(9, 8, 20),
                    new StaticStack("golden_apple", 1),
                    new StaticStack("deepslate", 24)),
            template("ancient_city/structures/barracks", "chests/ancient_city", pos(4, 4, 15), pos(12, 4, 15)),
            template("ancient_city/structures/chamber_1", "chests/ancient_city", pos(14, 3, 11)),
            template("ancient_city/structures/chamber_2", "chests/ancient_city", pos(10, 2, 5)),
            template("ancient_city/structures/chamber_3", "chests/ancient_city", pos(7, 1, 5)),
            template(
                    "ancient_city/structures/ice_box_1",
                    "chests/ancient_city_ice_box",
                    false,
                    pos(12, 1, 7)),
            template(
                    "ancient_city/structures/sauna_1",
                    "chests/ancient_city",
                    pos(12, 1, 19),
                    pos(21, 1, 18),
                    pos(25, 1, 10)),
            template("ancient_city/structures/tall_ruin_1", "chests/ancient_city", pos(8, 16, 8)),
            template(
                    "ancient_city/structures/tall_ruin_2",
                    "chests/ancient_city",
                    pos(7, 1, 5),
                    pos(13, 7, 9)),
            template("ancient_city/structures/tall_ruin_3", "chests/ancient_city", pos(10, 14, 7)),
            template("ancient_city/structures/tall_ruin_4", "chests/ancient_city", pos(8, 7, 13)));
    private static final Map<Identifier, TemplateChests> BY_TEMPLATE = createLookup();

    private AncientCityChestTemplateData() {}

    public static Optional<TemplateChests> get(Identifier template) {
        return Optional.ofNullable(BY_TEMPLATE.get(template));
    }

    public static List<TemplateChests> all() {
        return VANILLA;
    }

    /** Maps a piece's local chest markers using the same rotation transform as vanilla templates. */
    public static List<WorldChest> worldChests(
            Identifier template, BlockPos pieceOrigin, BlockRotation rotation) {
        return get(template)
                .stream()
                .flatMap(data -> data.chests().stream()
                        .sorted(Comparator.comparingInt((Chest chest) -> chest.position().getY())
                                .thenComparingInt(chest -> chest.position().getX())
                                .thenComparingInt(chest -> chest.position().getZ()))
                        .map(chest -> new WorldChest(
                                StructureTemplate.transformAround(
                                                chest.position(), BlockMirror.NONE, rotation, BlockPos.ORIGIN)
                                        .add(pieceOrigin),
                                chest.lootTable(),
                                chest.contents(),
                                data.protectedBlocksProcessor())))
                .toList();
    }

    private static Map<Identifier, TemplateChests> createLookup() {
        Map<Identifier, TemplateChests> lookup = new LinkedHashMap<>();
        VANILLA.forEach(data -> lookup.put(data.template(), data));
        return Map.copyOf(lookup);
    }

    private static TemplateChests template(
            String template, String lootTable, BlockPos... positions) {
        return template(template, lootTable, true, positions);
    }

    private static TemplateChests template(
            String template, String lootTable, boolean protectedBlocksProcessor, BlockPos... positions) {
        Identifier templateId = Identifier.ofVanilla(template);
        return new TemplateChests(
                templateId,
                Arrays.stream(positions)
                        .map(position -> new Chest(
                                position,
                                Optional.ofNullable(lootTable).map(Identifier::ofVanilla),
                                List.of()))
                        .toList(),
                protectedBlocksProcessor);
    }

    private static TemplateChests fixedChestTemplate(
            String template, BlockPos position, StaticStack... contents) {
        return new TemplateChests(
                Identifier.ofVanilla(template),
                List.of(new Chest(position, Optional.empty(), Arrays.asList(contents))),
                true);
    }

    private static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    public record TemplateChests(
            Identifier template, List<Chest> chests, boolean protectedBlocksProcessor) {
        public TemplateChests {
            chests = List.copyOf(chests);
        }
    }

    public record Chest(BlockPos position, Optional<Identifier> lootTable, List<StaticStack> contents) {
        public Chest {
            contents = List.copyOf(contents);
        }
    }

    public record WorldChest(
            BlockPos position,
            Optional<Identifier> lootTable,
            List<StaticStack> contents,
            boolean protectedBlocksProcessor) {
        public WorldChest {
            contents = List.copyOf(contents);
        }
    }

    public record StaticStack(Identifier item, int count) {
        public StaticStack(String item, int count) {
            this(Identifier.ofVanilla(item), count);
        }
    }
}
