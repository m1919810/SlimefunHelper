package me.matl114.utils.commands.params.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.InputArgument;
import me.matl114.utils.commands.params.types.EntitySelector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class EntityArgumentType extends AbstractArgumentType<EntitySelector> implements ArgumentType<EntitySelector> {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final List<String> SELECTOR_TABS = List.of("@p", "@a", "@r", "@s", "@e", "@n");
    private static final List<String> OPTION_TABS = List.of(
            "x=",
            "y=",
            "z=",
            "distance=",
            "dx=",
            "dy=",
            "dz=",
            "x_rotation=",
            "y_rotation=",
            "limit=",
            "sort=",
            "name=",
            "type=",
            "tag=",
            "nbt=",
            "scores=",
            "team=",
            "gamemode=",
            "advancements=",
            "predicate=",
            "level=");
    private static final Set<String> REPEATABLE_OPTIONS = Set.of("type", "tag", "predicate");
    private static final List<String> SORT_TABS = List.of("nearest", "furthest", "random", "arbitrary");
    private static final List<String> GAMEMODE_TABS = List.of("survival", "creative", "adventure", "spectator");

    public EntityArgumentType(String argsName) {
        super(argsName);
    }

    @Override
    public Stream<String> getTab(CommandExecution sender, List<InputArgument<?>> args) {
        Stream<String> customTabs = super.getTab(sender, args);
        Stream<String> selectorTabs = getSelectorTabs(currentToken(args));
        return Stream.concat(customTabs, selectorTabs).distinct();
    }

    @Nullable
    @Override
    public InputArgument<EntitySelector> consume(
            CommandExecution execution, List<InputArgument<?>> args, ArgumentReader reader) {
        if (!reader.hasNext()) {
            return new EntityArgumentResult(null, this, reader, reader.cursor(), false);
        }
        int startIndex = reader.cursor();
        String raw = reader.next();
        EntitySelector selector = parse(raw);
        if (selector == null) {
            reader.setCursor(startIndex);
            return new EntityArgumentResult(null, this, reader, startIndex, false);
        }
        return new EntityArgumentResult(selector, this, reader, startIndex, true);
    }

    private static String currentToken(List<InputArgument<?>> args) {
        if (args.isEmpty()) {
            return "";
        }
        InputArgument<?> last = args.get(args.size() - 1);
        if (last == null || last.tabbingString() == null) {
            return "";
        }
        return last.tabbingString();
    }

    public static Stream<String> getEntityTabs() {
        List<Entity> entities = EntityArgumentType.allEntities();
        Stream<String> entityTokens = entities.stream()
                .flatMap(entity -> Stream.of("@" + entityName(entity), "@" + entity.getUuidAsString()))
                .filter(token -> token != null && !token.isBlank() && !token.contains(" "))
                .distinct();
        Stream<String> crosshair = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY
                ? Stream.of(
                        "@" + ((EntityHitResult) mc.crosshairTarget).getEntity().getUuidAsString())
                : Stream.empty();
        return Stream.of(SELECTOR_TABS.stream(), entityTokens, crosshair)
                .flatMap(stream -> stream)
                .distinct();
    }

    public static EntitySelector parse(String raw) {
        if (raw == null || raw.isEmpty() || !raw.startsWith("@")) {
            return null;
        }
        if (raw.length() >= 2 && isSelectorHead(raw.charAt(1))) {
            return parseSelector(raw);
        }
        return parseNamedOrUuid(raw.substring(1));
    }

    private static boolean isSelectorHead(char head) {
        return switch (head) {
            case 'a', 'e', 'n', 'p', 'r', 's' -> true;
            default -> false;
        };
    }

    private static EntitySelector parseNamedOrUuid(String raw) {
        try {
            UUID uuid = UUID.fromString(raw);
            return new Selector(raw, execution -> resolveUuid(uuid));
        } catch (IllegalArgumentException ignored) {
            if (raw.isEmpty()) {
                return null;
            }
            return new Selector(raw, execution -> resolveNamed(raw));
        }
    }

    @Nullable
    private static EntitySelector parseSelector(String raw) {
        if (raw.length() < 2) {
            return null;
        }
        SelectorState state =
                switch (raw.charAt(1)) {
                    case 'a' -> SelectorState.allPlayers(raw);
                    case 'e' -> SelectorState.allEntities(raw);
                    case 'n' -> SelectorState.nearestEntity(raw);
                    case 'p' -> SelectorState.nearestPlayer(raw);
                    case 'r' -> SelectorState.randomPlayer(raw);
                    case 's' -> SelectorState.self(raw);
                    default -> null;
                };
        if (state == null) {
            return null;
        }
        if (raw.length() == 2) {
            return state.build();
        }
        if (raw.length() < 4 || raw.charAt(2) != '[' || raw.charAt(raw.length() - 1) != ']') {
            return null;
        }
        String options = raw.substring(3, raw.length() - 1);
        if (!state.applyOptions(options)) {
            return null;
        }
        return state.build();
    }

    private static List<Entity> resolveUuid(UUID uuid) {
        if (mc.world == null) {
            return List.of();
        }
        Entity entity = mc.world.getEntityLookup().get(uuid);
        return entity == null ? List.of() : List.of(entity);
    }

    private static List<Entity> resolveNamed(String raw) {
        if (mc.world == null) {
            return List.of();
        }
        return EntityArgumentType.allEntities().stream()
                .filter(entity -> matchesName(entity, raw))
                .toList();
    }

    private static List<Entity> selfEntity(CommandExecution execution) {
        Entity executor = execution.getExecutor();
        return executor == null || executor.isRemoved() ? List.of() : List.of(executor);
    }

    private static List<Entity> allEntities() {
        return mc.world == null
                ? List.of()
                : StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                        .toList();
    }

    private static Vec3d executionPos(CommandExecution execution) {
        Vector3d pos = execution.getExecutePos();
        return new Vec3d(pos.x, pos.y, pos.z);
    }

    private static Identifier parseIdentifier(String raw) {
        return Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
    }

    private static boolean isPlayer(Entity entity) {
        return entity instanceof PlayerEntity;
    }

    private static String entityName(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            return player.getNameForScoreboard();
        }
        return entity.getName().getString();
    }

    private static String teamName(Entity entity) {
        if (entity instanceof PlayerEntity player && player.getScoreboardTeam() != null) {
            return player.getScoreboardTeam().getName();
        }
        return "";
    }

    private static boolean matchesName(Entity entity, String name) {
        return Objects.equals(entityName(entity), name)
                || Objects.equals(entity.getName().getString(), name);
    }

    private static boolean matchesGameMode(Entity entity, GameMode gameMode) {
        if (!(entity instanceof PlayerEntity)) {
            return false;
        }
        if (mc.getNetworkHandler() == null) {
            return false;
        }
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        return entry != null && entry.getGameMode() == gameMode;
    }

    @Nullable
    private static GameMode parseGameMode(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "survival" -> GameMode.SURVIVAL;
            case "creative" -> GameMode.CREATIVE;
            case "adventure" -> GameMode.ADVENTURE;
            case "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static List<OptionEntry> splitOptions(String options) {
        if (options.isEmpty()) {
            return List.of();
        }
        List<OptionEntry> entries = new ArrayList<>();
        int start = 0;
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < options.length(); i++) {
            char c = options.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                depth++;
                continue;
            }
            if (c == '}' || c == ']' || c == ')') {
                depth--;
                if (depth < 0) {
                    return null;
                }
                continue;
            }
            if (c == ',' && depth == 0) {
                String token = options.substring(start, i).trim();
                if (token.isEmpty()) {
                    return null;
                }
                OptionEntry entry = parseOptionEntry(token);
                if (entry == null) {
                    return null;
                }
                entries.add(entry);
                start = i + 1;
            }
        }
        if (quote != 0 || depth != 0) {
            return null;
        }
        String token = options.substring(start).trim();
        if (!token.isEmpty()) {
            OptionEntry entry = parseOptionEntry(token);
            if (entry == null) {
                return null;
            }
            entries.add(entry);
        }
        return entries;
    }

    @Nullable
    private static OptionEntry parseOptionEntry(String token) {
        int equals = topLevelEquals(token);
        if (equals < 1) {
            return null;
        }
        return new OptionEntry(
                token.substring(0, equals).trim(), token.substring(equals + 1).trim());
    }

    private static int topLevelEquals(String value) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                depth++;
                continue;
            }
            if (c == '}' || c == ']' || c == ')') {
                depth--;
                continue;
            }
            if (c == '=' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static DoubleRange parseDoubleRange(String raw, boolean allowNegative) {
        if (raw.isEmpty()) {
            return null;
        }
        int rangeIndex = raw.indexOf("..");
        try {
            if (rangeIndex < 0) {
                double value = Double.parseDouble(raw);
                if (!allowNegative && value < 0.0D) {
                    return null;
                }
                return new DoubleRange(value, value);
            }
            if (raw.indexOf("..", rangeIndex + 2) >= 0) {
                return null;
            }
            String minRaw = raw.substring(0, rangeIndex);
            String maxRaw = raw.substring(rangeIndex + 2);
            Double min = minRaw.isEmpty() ? null : Double.parseDouble(minRaw);
            Double max = maxRaw.isEmpty() ? null : Double.parseDouble(maxRaw);
            if (!allowNegative && ((min != null && min < 0.0D) || (max != null && max < 0.0D))) {
                return null;
            }
            return new DoubleRange(min, max);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private static IntRange parseIntRange(String raw, boolean allowNegative) {
        if (raw.isEmpty()) {
            return null;
        }
        int rangeIndex = raw.indexOf("..");
        try {
            if (rangeIndex < 0) {
                int value = Integer.parseInt(raw);
                if (!allowNegative && value < 0) {
                    return null;
                }
                return new IntRange(value, value);
            }
            if (raw.indexOf("..", rangeIndex + 2) >= 0) {
                return null;
            }
            String minRaw = raw.substring(0, rangeIndex);
            String maxRaw = raw.substring(rangeIndex + 2);
            Integer min = minRaw.isEmpty() ? null : Integer.parseInt(minRaw);
            Integer max = maxRaw.isEmpty() ? null : Integer.parseInt(maxRaw);
            if (!allowNegative && ((min != null && min < 0) || (max != null && max < 0))) {
                return null;
            }
            return new IntRange(min, max);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean rotationMatches(DoubleRange range, float value) {
        float min = MathHelper.wrapDegrees((float) range.minOr(0.0D));
        float max = MathHelper.wrapDegrees((float) range.maxOr(359.0D));
        float current = MathHelper.wrapDegrees(value);
        if (min > max) {
            return current >= min || current <= max;
        }
        return current >= min && current <= max;
    }

    private static Stream<String> getSelectorTabs(String raw) {
        if (raw == null || raw.isEmpty()) {
            return getEntityTabs();
        }
        if (!raw.startsWith("@")) {
            return filterByToken(getEntityTabs(), raw);
        }
        if (raw.length() == 1) {
            return SELECTOR_TABS.stream().filter(tab -> tab.startsWith(raw));
        }
        if (raw.length() == 2 && SELECTOR_TABS.contains(raw)) {
            return Stream.of(raw + "[");
        }
        if (raw.length() == 2) {
            return SELECTOR_TABS.stream().filter(tab -> tab.startsWith(raw));
        }
        if (!SELECTOR_TABS.contains(raw.substring(0, 2))) {
            return Stream.empty();
        }
        if (raw.charAt(2) != '[') {
            return Stream.empty();
        }
        if (raw.endsWith("]")) {
            return Stream.empty();
        }
        return getOptionTabs(raw);
    }

    private static Stream<String> getOptionTabs(String raw) {
        String options = raw.substring(3);
        int segmentStart = lastTopLevelComma(options) + 1;
        String segment = options.substring(segmentStart);
        String prefix = raw.substring(0, 3 + segmentStart);
        int equals = topLevelEquals(segment);
        if (equals < 0) {
            Stream<String> optionTabs = availableOptionTabs(options.substring(0, segmentStart), segment)
                    .map(option -> prefix + option);
            if (segment.isEmpty()) {
                return Stream.concat(Stream.of(prefix + "]"), optionTabs);
            }
            return optionTabs;
        }
        String option = segment.substring(0, equals).trim();
        String value = segment.substring(equals + 1).trim();
        return getValueTabs(prefix, option, value);
    }

    private static Stream<String> availableOptionTabs(String previousOptions, String partial) {
        Set<String> used = parsedOptionNames(previousOptions);
        return OPTION_TABS.stream()
                .filter(option -> option.startsWith(partial))
                .filter(option -> REPEATABLE_OPTIONS.contains(option.substring(0, option.length() - 1))
                        || !used.contains(option.substring(0, option.length() - 1)));
    }

    private static Set<String> parsedOptionNames(String options) {
        List<OptionEntry> entries =
                splitOptions(options.endsWith(",") ? options.substring(0, options.length() - 1) : options);
        if (entries == null || entries.isEmpty()) {
            return Set.of();
        }
        return entries.stream().map(OptionEntry::key).collect(java.util.stream.Collectors.toSet());
    }

    private static Stream<String> getValueTabs(String prefix, String option, String value) {
        if (!OPTION_TABS.contains(option + "=")) {
            return Stream.empty();
        }
        Stream<String> values =
                switch (option) {
                    case "sort" -> filterByToken(SORT_TABS.stream(), value);
                    case "gamemode" -> invertedValueTabs(GAMEMODE_TABS.stream(), value);
                    case "type" -> entityTypeTabs(value);
                    case "name" -> invertedValueTabs(entityNameTabs(), value);
                    case "team" -> invertedValueTabs(teamTabs(), value);
                    case "tag" -> invertedValueTabs(commandTagTabs(), value);
                    case "limit" -> filterByToken(Stream.of("1"), value);
                    case "distance", "level" -> filterByToken(Stream.of(".."), value);
                    default -> Stream.empty();
                };
        Stream<String> completedValues = values.map(tab -> prefix + option + "=" + tab);
        if (canCloseOption(option, value)) {
            return Stream.concat(
                    completedValues,
                    Stream.of(prefix + option + "=" + value + ",", prefix + option + "=" + value + "]"));
        }
        return completedValues;
    }

    private static Stream<String> invertedValueTabs(Stream<String> values, String value) {
        if (value.startsWith("!")) {
            return Stream.concat(
                    Stream.of("!"), filterByToken(values, value.substring(1)).map(tab -> "!" + tab));
        }
        return Stream.concat(Stream.of("!"), filterByToken(values, value));
    }

    private static Stream<String> entityTypeTabs(String value) {
        if (value.startsWith("!")) {
            return Stream.concat(
                    Stream.of("!", "!#"), entityTypeTabs(value.substring(1)).map(tab -> "!" + tab));
        }
        if (value.startsWith("#")) {
            return Stream.of("#").filter(tab -> tab.startsWith(value));
        }
        Stream<String> ids = Registries.ENTITY_TYPE.stream()
                .map(Registries.ENTITY_TYPE::getId)
                .filter(Objects::nonNull)
                .map(Identifier::toString);
        return Stream.concat(Stream.of("#"), filterByToken(ids, value));
    }

    private static Stream<String> entityNameTabs() {
        return EntityArgumentType.allEntities().stream()
                .map(EntityArgumentType::entityName)
                .filter(name -> name != null && !name.isBlank() && !name.contains(" "))
                .distinct();
    }

    private static Stream<String> teamTabs() {
        if (mc.world == null) {
            return Stream.empty();
        }
        return EntityArgumentType.allEntities().stream()
                .map(EntityArgumentType::teamName)
                .filter(s -> !s.isEmpty())
                .distinct();
    }

    private static Stream<String> commandTagTabs() {
        if (mc.world == null) {
            return Stream.empty();
        }
        return EntityArgumentType.allEntities().stream()
                .flatMap(entity -> entity.getCommandTags().stream())
                .distinct();
    }

    private static Stream<String> filterByToken(Stream<String> stream, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return stream.filter(value -> value.toLowerCase(Locale.ROOT).contains(lower));
    }

    private static boolean canCloseOption(String option, String value) {
        if (value == null) {
            return false;
        }
        return switch (option) {
            case "team", "tag" -> true;
            default -> !value.isEmpty();
        };
    }

    private static int lastTopLevelComma(String value) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        int last = -1;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                depth++;
                continue;
            }
            if (c == '}' || c == ']' || c == ')') {
                depth--;
                continue;
            }
            if (c == ',' && depth == 0) {
                last = i;
            }
        }
        return last;
    }

    public record Selector(String raw, java.util.function.Function<CommandExecution, List<Entity>> resolver)
            implements EntitySelector {
        @Override
        public List<Entity> resolve(CommandExecution execution) {
            return resolver.apply(execution).stream()
                    .filter(entity -> entity != null && !entity.isRemoved())
                    .toList();
        }

        @Override
        public String asString() {
            return raw;
        }
    }

    private record ParsedSelector(SelectorState state) implements EntitySelector {
        @Override
        public List<Entity> resolve(CommandExecution execution) {
            Vec3d origin = state.origin(execution);
            Box box = state.box(origin);
            List<Entity> selected = state.initialEntities(execution).stream()
                    .filter(entity -> entity != null && !entity.isRemoved())
                    .filter(entity ->
                            state.distance == null || state.distance.testSquared(entity.squaredDistanceTo(origin)))
                    .filter(entity -> box == null || box.intersects(entity.getBoundingBox()))
                    .filter(state.predicate())
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            state.sorter.sort(origin, selected);
            if (selected.size() > state.limit) {
                return List.copyOf(selected.subList(0, state.limit));
            }
            return List.copyOf(selected);
        }

        @Override
        public String asString() {
            return state.raw;
        }
    }

    private static final class SelectorState {
        private final String raw;
        private int limit;
        private final Function<CommandExecution, List<Entity>> initialEntitiesResolver;
        private DoubleRange distance;
        private IntRange levelRange;
        private Double x;
        private Double y;
        private Double z;
        private Double dx;
        private Double dy;
        private Double dz;
        private DoubleRange pitchRange;
        private DoubleRange yawRange;
        private Sorter sorter;
        private final List<Predicate<Entity>> predicates = new ArrayList<>();

        private SelectorState(
                String raw,
                int limit,
                Function<CommandExecution, List<Entity>> initialEntitiesResolver,
                Sorter sorter) {
            this.raw = raw;
            this.limit = limit;
            this.initialEntitiesResolver = initialEntitiesResolver;
            this.sorter = sorter;
        }

        private static SelectorState allPlayers(String raw) {
            SelectorState state = new SelectorState(
                    raw, Integer.MAX_VALUE, execution -> EntityArgumentType.allEntities(), Sorter.ARBITRARY);
            state.predicates.add(EntityArgumentType::isPlayer);
            return state;
        }

        private static SelectorState allEntities(String raw) {
            return new SelectorState(
                    raw, Integer.MAX_VALUE, execution -> EntityArgumentType.allEntities(), Sorter.ARBITRARY);
        }

        private static SelectorState nearestEntity(String raw) {
            return new SelectorState(raw, 1, execution -> EntityArgumentType.allEntities(), Sorter.NEAREST);
        }

        private static SelectorState nearestPlayer(String raw) {
            SelectorState state =
                    new SelectorState(raw, 1, execution -> EntityArgumentType.allEntities(), Sorter.NEAREST);
            state.predicates.add(EntityArgumentType::isPlayer);
            return state;
        }

        private static SelectorState randomPlayer(String raw) {
            SelectorState state =
                    new SelectorState(raw, 1, execution -> EntityArgumentType.allEntities(), Sorter.RANDOM);
            state.predicates.add(EntityArgumentType::isPlayer);
            return state;
        }

        private static SelectorState self(String raw) {
            return new SelectorState(raw, 1, EntityArgumentType::selfEntity, Sorter.ARBITRARY);
        }

        private boolean applyOptions(String options) {
            List<OptionEntry> entries = splitOptions(options);
            if (entries == null) {
                return false;
            }
            for (OptionEntry entry : entries) {
                if (!applyOption(entry.key(), entry.value())) {
                    return false;
                }
            }
            return true;
        }

        private boolean applyOption(String key, String value) {
            return switch (key) {
                case "x" -> setDouble(value, number -> this.x = number);
                case "y" -> setDouble(value, number -> this.y = number);
                case "z" -> setDouble(value, number -> this.z = number);
                case "dx" -> setDouble(value, number -> this.dx = number);
                case "dy" -> setDouble(value, number -> this.dy = number);
                case "dz" -> setDouble(value, number -> this.dz = number);
                case "distance" -> setDistance(value);
                case "level" -> setLevel(value);
                case "x_rotation" -> setPitch(value);
                case "y_rotation" -> setYaw(value);
                case "limit" -> setLimit(value);
                case "sort" -> setSort(value);
                case "name" -> addName(value);
                case "type" -> addType(value);
                case "tag" -> addTag(value);
                case "team" -> addTeam(value);
                case "gamemode" -> addGameMode(value);
                case "nbt", "scores", "advancements", "predicate" -> true;
                default -> false;
            };
        }

        private boolean setDouble(String value, DoubleSetter setter) {
            try {
                setter.set(Double.parseDouble(value));
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        private boolean setDistance(String value) {
            DoubleRange range = parseDoubleRange(value, false);
            if (range == null) {
                return false;
            }
            this.distance = range;
            return true;
        }

        private boolean setLevel(String value) {
            IntRange range = parseIntRange(value, false);
            if (range == null) {
                return false;
            }
            this.levelRange = range;
            this.predicates.add(entity -> entity instanceof PlayerEntity player && range.test(player.experienceLevel));
            return true;
        }

        private boolean setPitch(String value) {
            DoubleRange range = parseDoubleRange(value, true);
            if (range == null) {
                return false;
            }
            this.pitchRange = range;
            this.predicates.add(entity -> rotationMatches(range, entity.getPitch()));
            return true;
        }

        private boolean setYaw(String value) {
            DoubleRange range = parseDoubleRange(value, true);
            if (range == null) {
                return false;
            }
            this.yawRange = range;
            this.predicates.add(entity -> rotationMatches(range, entity.getYaw()));
            return true;
        }

        private boolean setLimit(String value) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 1) {
                    return false;
                }
                this.limit = parsed;
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        private boolean setSort(String value) {
            Sorter parsed = Sorter.from(value);
            if (parsed == null) {
                return false;
            }
            this.sorter = parsed;
            return true;
        }

        private boolean addName(String value) {
            InvertedValue inverted = InvertedValue.read(value);
            if (inverted.value().isEmpty()) {
                return false;
            }
            this.predicates.add(entity -> matchesName(entity, inverted.value()) != inverted.inverted());
            return true;
        }

        private boolean addTeam(String value) {
            InvertedValue inverted = InvertedValue.read(value);
            this.predicates.add(entity -> Objects.equals(teamName(entity), inverted.value()) != inverted.inverted());
            return true;
        }

        private boolean addTag(String value) {
            InvertedValue inverted = InvertedValue.read(value);
            this.predicates.add(entity -> {
                if (inverted.value().isEmpty()) {
                    return entity.getCommandTags().isEmpty() != inverted.inverted();
                }
                return entity.getCommandTags().contains(inverted.value()) != inverted.inverted();
            });
            return true;
        }

        private boolean addGameMode(String value) {
            InvertedValue inverted = InvertedValue.read(value);
            GameMode gameMode = parseGameMode(inverted.value());
            if (gameMode == null) {
                return false;
            }
            this.predicates.add(entity -> matchesGameMode(entity, gameMode) != inverted.inverted());
            return true;
        }

        private boolean addType(String value) {
            InvertedValue inverted = InvertedValue.read(value);
            if (inverted.value().isEmpty()) {
                return false;
            }
            if (inverted.value().startsWith("#")) {
                Identifier id = parseIdentifier(inverted.value().substring(1));
                if (id == null) {
                    return false;
                }
                TagKey<EntityType<?>> tag = TagKey.of(RegistryKeys.ENTITY_TYPE, id);
                this.predicates.add(entity -> entity.getType().isIn(tag) != inverted.inverted());
                return true;
            }
            Identifier id = parseIdentifier(inverted.value());
            if (id == null) {
                return false;
            }
            EntityType<?> entityType = Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
            if (entityType == null) {
                return false;
            }
            this.predicates.add(entity -> Objects.equals(entity.getType(), entityType) != inverted.inverted());
            return true;
        }

        private EntitySelector build() {
            return new ParsedSelector(this);
        }

        private List<Entity> initialEntities(CommandExecution execution) {
            return initialEntitiesResolver.apply(execution);
        }

        private Predicate<Entity> predicate() {
            return predicates.stream().reduce(entity -> true, Predicate::and);
        }

        private Vec3d origin(CommandExecution execution) {
            Vec3d base = executionPos(execution);
            return new Vec3d(x == null ? base.x : x, y == null ? base.y : y, z == null ? base.z : z);
        }

        @Nullable
        private Box box(Vec3d origin) {
            if (dx == null && dy == null && dz == null) {
                if (distance != null && distance.max() != null) {
                    double max = distance.max();
                    return new Box(
                            origin.x - max,
                            origin.y - max,
                            origin.z - max,
                            origin.x + max + 1.0D,
                            origin.y + max + 1.0D,
                            origin.z + max + 1.0D);
                }
                return null;
            }
            double boxX = dx == null ? 0.0D : dx;
            double boxY = dy == null ? 0.0D : dy;
            double boxZ = dz == null ? 0.0D : dz;
            double minX = boxX < 0.0D ? boxX : 0.0D;
            double minY = boxY < 0.0D ? boxY : 0.0D;
            double minZ = boxZ < 0.0D ? boxZ : 0.0D;
            double maxX = (boxX < 0.0D ? 0.0D : boxX) + 1.0D;
            double maxY = (boxY < 0.0D ? 0.0D : boxY) + 1.0D;
            double maxZ = (boxZ < 0.0D ? 0.0D : boxZ) + 1.0D;
            return new Box(
                    origin.x + minX,
                    origin.y + minY,
                    origin.z + minZ,
                    origin.x + maxX,
                    origin.y + maxY,
                    origin.z + maxZ);
        }
    }

    private interface DoubleSetter {
        void set(double value);
    }

    private enum Sorter {
        ARBITRARY,
        NEAREST,
        FURTHEST,
        RANDOM;

        @Nullable
        private static Sorter from(String raw) {
            return switch (raw) {
                case "arbitrary" -> ARBITRARY;
                case "nearest" -> NEAREST;
                case "furthest" -> FURTHEST;
                case "random" -> RANDOM;
                default -> null;
            };
        }

        private void sort(Vec3d origin, List<Entity> entities) {
            switch (this) {
                case NEAREST -> entities.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(origin)));
                case FURTHEST -> entities.sort(
                        Comparator.comparingDouble((Entity entity) -> entity.squaredDistanceTo(origin))
                                .reversed());
                case RANDOM -> Collections.shuffle(entities);
                case ARBITRARY -> {}
            }
        }
    }

    private record OptionEntry(String key, String value) {}

    private record InvertedValue(boolean inverted, String value) {
        private static InvertedValue read(String raw) {
            return raw.startsWith("!") ? new InvertedValue(true, raw.substring(1)) : new InvertedValue(false, raw);
        }
    }

    private record DoubleRange(@Nullable Double min, @Nullable Double max) {
        private boolean testSquared(double squared) {
            if (min != null && squared < min * min) {
                return false;
            }
            return max == null || squared <= max * max;
        }

        private double minOr(double fallback) {
            return min == null ? fallback : min;
        }

        private double maxOr(double fallback) {
            return max == null ? fallback : max;
        }
    }

    private record IntRange(@Nullable Integer min, @Nullable Integer max) {
        private boolean test(int value) {
            if (min != null && value < min) {
                return false;
            }
            return max == null || value <= max;
        }
    }
}
