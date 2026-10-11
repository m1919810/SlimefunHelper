package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.gui.presets.choices.EntityTypeEntrySetScreen;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.config.AttrKeyValue;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** A set of entity types assembled from spawn groups, additions, and removals. */
public class EntityTypeEntrySet implements NBTParsable<EntityTypeEntrySet>, Predicate<EntityType<?>> {
    private static final Codec<List<SpawnGroup>> GROUP_CODEC = Codec.STRING
            .listOf()
            .xmap(
                    values -> {
                        List<SpawnGroup> groups = new ArrayList<>();
                        for (String value : values) {
                            parseGroup(value).ifPresent(groups::add);
                        }
                        return groups;
                    },
                    values -> values.stream().map(SpawnGroup::getName).toList());

    private final Set<SpawnGroup> groups;
    private final Set<EntityType<?>> additional;
    private final Set<EntityType<?>> excluded;

    public static Class<EntityTypeEntrySet> parameter() {
        return EntityTypeEntrySet.class;
    }

    public EntityTypeEntrySet(
            Collection<SpawnGroup> groups, Collection<EntityType<?>> additional, Collection<EntityType<?>> excluded) {
        this.groups = new LinkedHashSet<>(groups);
        this.additional = new LinkedHashSet<>(additional);
        this.excluded = new LinkedHashSet<>(excluded);
    }

    /** Creates a set using the legacy entity whitelist regex rules. */
    public EntityTypeEntrySet(Regex regex) {
        this(parseLegacyRegex(regex));
    }

    private EntityTypeEntrySet(LegacySelection selection) {
        this(selection.groups(), selection.additional(), selection.excluded());
    }

    private EntityTypeEntrySet(List<SpawnGroup> groups, List<Identifier> additional, List<Identifier> excluded) {
        this(groups, resolve(additional), resolve(excluded));
    }

    private static Optional<SpawnGroup> parseGroup(String value) {
        try {
            return Optional.of(SpawnGroup.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Set<EntityType<?>> resolve(Collection<Identifier> ids) {
        Set<EntityType<?>> resolved = new LinkedHashSet<>();
        for (Identifier id : ids) {
            if (id != null) {
                Registries.ENTITY_TYPE.getOrEmpty(id).ifPresent(resolved::add);
            }
        }
        return resolved;
    }

    private static LegacySelection parseLegacyRegex(Regex regex) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex.regex());
        } catch (PatternSyntaxException ignored) {
            return new LegacySelection(Set.of(), Set.of(), Set.of());
        }

        Set<SpawnGroup> groups = new LinkedHashSet<>();
        for (SpawnGroup group : SpawnGroup.values()) {
            if (pattern.matcher(group.getName()).matches()) {
                groups.add(group);
            }
        }
        if (pattern.matcher("animal").matches()) {
            groups.add(SpawnGroup.CREATURE);
        }
        boolean includesLivingEntities = pattern.matcher("living_entity").matches();
        if (includesLivingEntities) {
            for (SpawnGroup group : SpawnGroup.values()) {
                if (group != SpawnGroup.MISC) {
                    groups.add(group);
                }
            }
        }

        boolean excludesMonsterExceptions = groups.contains(SpawnGroup.MONSTER) && !includesLivingEntities;
        Set<EntityType<?>> additional = new LinkedHashSet<>();
        Set<EntityType<?>> excluded = new LinkedHashSet<>();
        for (EntityType<?> entityType : Registries.ENTITY_TYPE) {
            Identifier id = Registries.ENTITY_TYPE.getId(entityType);
            if (id == null) {
                continue;
            }
            String path = id.getPath();
            boolean matchesEntity = pattern.matcher(path).matches();
            boolean monsterException = entityType == EntityType.ZOMBIFIED_PIGLIN || entityType == EntityType.ENDERMAN;
            boolean includedByGroup =
                    groups.contains(entityType.getSpawnGroup()) && !(excludesMonsterExceptions && monsterException);

            if (matchesEntity
                    && (!groups.contains(entityType.getSpawnGroup())
                            || (excludesMonsterExceptions && monsterException))) {
                additional.add(entityType);
            }
            if (excludesMonsterExceptions && monsterException && !matchesEntity) {
                excluded.add(entityType);
            } else if (!matchesEntity
                    && includedByGroup
                    && pattern.matcher("!" + path).matches()) {
                excluded.add(entityType);
            }
        }
        return new LegacySelection(groups, additional, excluded);
    }

    private record LegacySelection(
            Set<SpawnGroup> groups, Set<EntityType<?>> additional, Set<EntityType<?>> excluded) {}

    public Set<SpawnGroup> groups() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(groups));
    }

    public Set<EntityType<?>> additional() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(additional));
    }

    public Set<EntityType<?>> excluded() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(excluded));
    }

    public Set<EntityType<?>> set() {
        Set<EntityType<?>> result = new LinkedHashSet<>();
        for (EntityType<?> entityType : Registries.ENTITY_TYPE) {
            if (groups.contains(entityType.getSpawnGroup())) {
                result.add(entityType);
            }
        }
        result.addAll(additional);
        result.removeAll(excluded);
        return result;
    }

    public Set<EntityType<?>> getFilterValue() {
        return set();
    }

    public List<EntityType<?>> list() {
        return set().stream().toList();
    }

    public List<SpawnGroup> groupList() {
        return groups.stream().toList();
    }

    public List<Identifier> additionalList() {
        return identifiers(additional);
    }

    public List<Identifier> excludedList() {
        return identifiers(excluded);
    }

    private static List<Identifier> identifiers(Collection<EntityType<?>> values) {
        List<Identifier> ids = new ArrayList<>(values.size());
        for (EntityType<?> value : values) {
            Identifier id = Registries.ENTITY_TYPE.getId(value);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static NBTType<EntityTypeEntrySet> create() {
        return new NBTType<>(
                "entitytypeentryset",
                RecordCodecBuilder.<EntityTypeEntrySet>create(instance -> instance.group(
                                GROUP_CODEC.fieldOf("groups").forGetter(EntityTypeEntrySet::groupList),
                                Codec.list(Identifier.CODEC)
                                        .fieldOf("additional")
                                        .forGetter(EntityTypeEntrySet::additionalList),
                                Codec.list(Identifier.CODEC)
                                        .fieldOf("excluded")
                                        .forGetter(EntityTypeEntrySet::excludedList))
                        .apply(instance, EntityTypeEntrySet::new)),
                EntityTypeEntrySet::generateValueWidget,
                new EntityTypeEntrySet(Set.of(), Set.of(), Set.of()));
    }

    private static DrawableWidget generateValueWidget(
            ElementFactory factory, AttrKeyValue<EntityTypeEntrySet> attr, int x, int y, int dx, int dy) {
        return new SubScreenWidget(x, y, dx, dy)
                .addDrawableChild(factory.createExecuteButton(
                        () -> Constants.OPEN_LIST_EDIT_TEXT,
                        Constants::openListEditTooltips,
                        ButtonAction.run(() -> openEditor(factory, attr)),
                        dy,
                        0,
                        dx - dy,
                        dy))
                .addDrawableChild(factory.createElement(
                        IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.empty()),
                        null,
                        null,
                        0,
                        0,
                        dy,
                        dy));
    }

    private static void openEditor(ElementFactory factory, AttrKeyValue<EntityTypeEntrySet> attr) {
        EntityTypeEntrySet current = attr.get();
        ScreenAccess.of(new EntityTypeEntrySetScreen(current, attr::accept, factory))
                .openFromCurrent();
    }

    public static final NBTType<EntityTypeEntrySet> TYPE = create();

    @Override
    public NBTType<EntityTypeEntrySet> type() {
        return TYPE;
    }

    @Override
    public boolean test(EntityType<?> entityType) {
        return set().contains(entityType);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof EntityTypeEntrySet that)) return false;
        return Objects.equals(groups, that.groups)
                && Objects.equals(additional, that.additional)
                && Objects.equals(excluded, that.excluded);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groups, additional, excluded);
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return type instanceof EntityTypeEntrySet;
    }

    @Override
    public <W> Optional<EntityTypeEntrySet> tryTypeConvert(Ref<W> ref) {
        NBTParsable.registerNBTType(EntityTypeRegex.TYPE);
        if (ref instanceof NBTRef<?> nbt && nbt.get() instanceof EntityTypeRegex regex) {
            return Optional.of(new EntityTypeEntrySet(regex.getParent()));
        }
        return Optional.empty();
    }
}
