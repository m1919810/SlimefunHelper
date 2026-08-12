package me.matl114.gui.presets.lists;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.*;
import me.matl114.gui.presets.single.RegistryDisplays;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import oshi.util.tuples.Triplet;

public class ListRegistrySelectWidget<T> extends ListSelectWidget<Triplet<Text, Identifier, T>> {
    public T getSelectedRegistry() {
        return selected() == null ? null : selected().getC();
    }

    private static final BiPredicate<Triplet<Text, Identifier, Object>, String> filter = (s, b) -> {
        String id = s.getB().toString();
        if (FilterService.nameMatch(id, b)) {
            return true;
        }
        String zhcn = s.getA().getString();
        if (FilterService.nameMatch(zhcn, b)) {
            return true;
        }
        return false;
    };

    public ListRegistrySelectWidget(
            List<Triplet<Text, Identifier, T>> list,
            Function<Triplet<Text, Identifier, T>, RenderHandler> renderFactory,
            ValueAccessor<String> filterInput,
            int x,
            int y,
            int dx,
            int dy,
            int height) {
        super(list, renderFactory, filterInput, (BiPredicate) filter, x, y, dx, dy, height);
    }

    public static <T> List<T> listRegistry(Registry<T> registry) {
        return registry.stream().toList();
    }

    public static <T> List<Triplet<Text, Identifier, T>> list(
            List<T> lst, Registry<T> registry, Function<T, Text> localization) {
        return lst.stream()
                .map(s -> new Triplet<Text, Identifier, T>(localization.apply(s), registry.getId(s), s))
                .toList();
    }

    public static <T> ListRegistrySelectWidget<T> registry(
            Registry<T> registry, ValueAccessor<String> filterInput, int x, int y, int dx, int dy, int height) {
        return new ListRegistrySelectWidget<>(
                list(listRegistry(registry), registry, RegistryDisplays::getDisplay),
                (trp) -> RegistryDisplays.of(registry, trp.getC(), trp.getA(), trp.getB()),
                filterInput,
                x,
                y,
                dx,
                dy,
                height);
    }

    public static <T> ListRegistrySelectWidget<T> registry(
            List<T> data,
            Registry<T> registry,
            ValueAccessor<String> filterInput,
            int x,
            int y,
            int dx,
            int dy,
            int height) {
        return new ListRegistrySelectWidget<>(
                list(data, registry, RegistryDisplays::getDisplay),
                (trp) -> RegistryDisplays.of(registry, trp.getC(), trp.getA(), trp.getB()),
                filterInput,
                x,
                y,
                dx,
                dy,
                height);
    }
}
