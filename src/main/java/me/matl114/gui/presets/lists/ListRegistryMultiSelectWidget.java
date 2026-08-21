package me.matl114.gui.presets.lists;

import com.mojang.datafixers.util.Pair;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.gui.presets.single.RegistryDisplays;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import oshi.util.tuples.Triplet;

public class ListRegistryMultiSelectWidget<T> extends ListMultiSelectWidget<Triplet<Text, Identifier, T>> {
    public Set<T> getSelectedRegistries() {
        return buildSelected().stream().map(Triplet::getC).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static final FilterService.Filter<Triplet<Text, Identifier, Object>> filter = (s, b, bl) -> {
        if (bl) {
            try {
                return Pattern.matches(b, s.getB().getPath())
                        || Pattern.matches(b, s.getA().getString());
            } catch (Throwable e) {
                return false;
            }
        } else {
            String id = s.getB().toString();
            if (FilterService.nameMatch(id, b)) {
                return true;
            }
            String zhcn = s.getA().getString();
            if (FilterService.nameMatch(zhcn, b)) {
                return true;
            }
            return false;
        }
    };

    private static <T> Pair<List<Triplet<Text, Identifier, T>>, Set<Triplet<Text, Identifier, T>>> buildPairInternal(
            Registry<T> registry, Set<T> currentSelection, Function<T, Text> localization) {
        var set = new HashSet<Triplet<Text, Identifier, T>>();
        var list = registry.stream()
                .map(s -> new Triplet<Text, Identifier, T>(localization.apply(s), registry.getId(s), s))
                .peek(s -> {
                    if (currentSelection.contains(s.getC())) {
                        set.add(s);
                    }
                })
                .toList(); // , currentSelection.stream().map(s -> new Triplet<String,Identifier,
        // T>(localization.apply(s), registry.getId(s), s)).collect(Collectors.toSet())
        return new Pair<>(list, set);
    }

    public ListRegistryMultiSelectWidget(
            Registry<T> registry,
            Set<T> currentSelection,
            Function<T, Text> localization,
            BiFunction<Triplet<Text, Identifier, T>, AttrKeyValue<Boolean>, RenderHandler> renderFactory,
            ValueAccessor<String> filterInput,
            int x,
            int y,
            int dx,
            int dy,
            int height) {
        this(
                buildPairInternal(registry, currentSelection, localization),
                renderFactory,
                filterInput,
                x,
                y,
                dx,
                dy,
                height);
    }

    private ListRegistryMultiSelectWidget(
            Pair<List<Triplet<Text, Identifier, T>>, Set<Triplet<Text, Identifier, T>>> pairData,
            BiFunction<Triplet<Text, Identifier, T>, AttrKeyValue<Boolean>, RenderHandler> renderFactory,
            ValueAccessor<String> filterInput,
            int x,
            int y,
            int dx,
            int dy,
            int height) {
        super(
                pairData.getFirst(),
                pairData.getSecond(),
                renderFactory,
                filterInput,
                (FilterService.Filter) filter,
                x,
                y,
                dx,
                dy,
                height);
    }

    public static <T> ListRegistryMultiSelectWidget<T> registry(
            Registry<T> registry,
            Set<T> currentSelect,
            ValueAccessor<String> filterInput,
            int x,
            int y,
            int dx,
            int dy,
            int height) {

        return new ListRegistryMultiSelectWidget<>(
                registry,
                currentSelect,
                RegistryDisplays::getDisplay,
                (trp, attr) -> RegistryDisplays.of(registry, trp.getC(), trp.getA(), trp.getB()),
                filterInput,
                x,
                y,
                dx,
                dy,
                height);
    }
}
