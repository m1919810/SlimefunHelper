package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DisplayWidget;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.presets.choices.RegistrySelectScreen;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

@Getter
@Accessors(fluent = true)
public class EntrySet<T> implements NBTParsable<EntrySet<T>>, Predicate<T> {
    final Registry<T> registry;
    final Set<T> set;
    List<Identifier> data;

    public static <T> Class<EntrySet<T>> parameter() {
        return (Class) EntrySet.class;
    }

    public EntrySet(Regex regex, Registry<T> registry) {
        this.registry = registry;
        this.set = new LinkedHashSet<>();
        for (var re : registry.getIds()) {
            if (regex.test(re.getPath())) {
                this.set.add(registry.get(re));
            }
        }
    }

    public EntrySet(Registry<T> registry, Collection<T> set) {
        this.registry = registry;
        this.set = new LinkedHashSet<>(set);
    }

    public EntrySet(List<Identifier> data, Registry<T> registry) {
        this.registry = registry;
        this.data = new ArrayList<>(data);
        this.set = new LinkedHashSet<>();
        for (Identifier id : data) {
            if (id == null) {
                continue;
            }
            registry.getOrEmpty(id).ifPresent(this.set::add);
        }
    }

    public List<Identifier> idList() {
        if (data == null) {
            List<Identifier> cached = new ArrayList<>(set.size());
            for (T entry : set) {
                Identifier id = registry.getId(entry);
                if (id != null) {
                    cached.add(id);
                }
            }
            this.data = cached;
        }
        return data;
    }

    public List<T> list() {
        return set.stream().toList();
    }

    public static <T> NBTType<EntrySet<T>> create() {
        return new NBTType<>(
                "entryset",
                RecordCodecBuilder.<EntrySet<T>>create(instance -> instance.group(
                                Codec.list(Identifier.CODEC).fieldOf("data").forGetter(EntrySet::idList),
                                ((Codec<Registry<T>>) Registries.REGISTRIES.getCodec())
                                        .fieldOf("key_type")
                                        .forGetter(EntrySet::registry))
                        .apply(instance, EntrySet::new)),
                EntrySet::generateValueWidget,
                (EntrySet<T>) new EntrySet<>(Registries.BLOCK, Set.of()));
    }

    private static <T> DrawableWidget generateValueWidget(
            me.matl114.utils.config.AttrKeyValue<EntrySet<T>> attr, int x, int y, int dx, int dy) {
        return new SubScreenWidget(x, y, dx, dy)
                .addDrawableChild(new ExecutableWidget(dy, 0, dx - dy, dy)
                        .setElementHandler(new ButtonElement(
                                        TextProvider.of(Constants.OPEN_LIST_EDIT_TEXT),
                                        ButtonAction.run(() -> openRegistrySelectScreen(attr)))
                                .withTooltips(TooltipHandler.of(Constants.openListEditTooltips()))))
                .addDrawableChild(DisplayWidget.instance(0, 0, dy - 1, dy)
                        .setRenderHandler(IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.empty())));
    }

    private static <T> void openRegistrySelectScreen(me.matl114.utils.config.AttrKeyValue<EntrySet<T>> attr) {
        EntrySet<T> current = attr.getOriginValue();
        ScreenAccess.of(new RegistrySelectScreen<>(current.registry, current.set, selected -> {
                    if (selected != null) {
                        attr.valueChangeInternal(null, new EntrySet<>(current.registry, selected));
                    }
                }))
                .openFromCurrent();
    }

    public static final NBTType<EntrySet<Object>> TYPE = create();

    @Override
    public NBTType<EntrySet<T>> type() {
        return TYPE.cast();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof EntrySet<?> that)) return false;
        return Objects.equals(registry, that.registry) && Objects.equals(set, that.set);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registry, set);
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type) && type instanceof EntrySet<?> that && that.registry == registry;
    }

    @Override
    public <W> Optional<EntrySet<T>> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof NBTRef nbt && nbt.get() instanceof RegistryRegex<?> oldRegex) {
            if (oldRegex.registry == this.registry) {
                return Optional.of((EntrySet<T>) new EntrySet<>(oldRegex.getParent(), oldRegex.registry));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean test(T t) {
        return set.contains(t);
    }
}
