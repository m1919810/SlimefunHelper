package me.matl114.gui.presets.lists;

import com.mojang.datafixers.util.Pair;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.gui.config.RegistryDisplayRender;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.impl.config.AttrKeyValue;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import oshi.util.tuples.Triplet;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ListRegistryMultiSelectWidget<T> extends ListMultiSelectWidget<Triplet<String, Identifier, T>> {
    public Set<T> getSelectedRegistries(){
        return  buildSelected().stream().map(Triplet::getC).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static final Predicate<Triplet<String, Identifier, Object>> filter = s->{
        String id = s.getB().toString();
        if(FilterService.nameMatch(id, FilterService.currentUserInput)){
            return true;
        }
        String zhcn = s.getA();
        if(FilterService.nameMatch(zhcn, FilterService.currentUserInput)){
            return true;
        }
        return false;
    };
    private static <T> Pair<List<Triplet<String, Identifier, T>>, Set<Triplet<String, Identifier, T>>> buildPairInternal(Registry<T> registry, Set<T> currentSelection, Function<T, String> localization){
        var set = new HashSet<Triplet<String, Identifier, T>>();
        var list =  registry.stream()
            .map(s->new Triplet<String,Identifier, T>(localization.apply(s), registry.getId(s), s))
            .peek(s -> {
                if(currentSelection.contains(s.getC())){
                    set.add(s);
                }
            })
            .toList();//, currentSelection.stream().map(s -> new Triplet<String,Identifier, T>(localization.apply(s), registry.getId(s), s)).collect(Collectors.toSet())
        return new Pair<>(list, set);
    }
    public ListRegistryMultiSelectWidget(Registry<T> registry, Set<T> currentSelection, Function<T, String> localization, BiFunction<Triplet<String, Identifier, T>, AttrKeyValue<Boolean>, RenderHandler> renderFactory, int x, int y, int dx, int dy, int height){
        this(buildPairInternal(registry, currentSelection, localization), renderFactory, x, y, dx, dy, height);
    }
    private ListRegistryMultiSelectWidget(Pair<List<Triplet<String, Identifier, T>>, Set<Triplet<String, Identifier, T>>> pairData,  BiFunction<Triplet<String, Identifier, T>, AttrKeyValue<Boolean>, RenderHandler> renderFactory, int x, int y, int dx, int dy, int height){
        super(pairData.getFirst(), pairData.getSecond(), renderFactory, (Predicate) filter, x, y, dx, dy, height);
    }

    public static ListRegistryMultiSelectWidget<Item> item(Set<Item> currentSelect, int x, int y, int dx, int dy, int height){
        return new ListRegistryMultiSelectWidget<>(
            Registries.ITEM, currentSelect,
            (item -> item.getName().getString()),
            (trp, attr)->new RegistryDisplayRender(new ItemStack(trp.getC()),
                trp.getC().getName(), trp.getB()),
            x,y,dx,dy, height
        );
    }
    public static ListRegistryMultiSelectWidget<Enchantment> enchant(Set<Enchantment> currentSelect, int x, int y, int dx, int dy, int height){
        return new ListRegistryMultiSelectWidget<>(
            ItemStackUtils.registry().get(RegistryKeys.ENCHANTMENT), currentSelect,
            (item -> item.description().getString()),
            (trp, attr)->new RegistryDisplayRender(new ItemStack(Items.ENCHANTED_BOOK),
                trp.getC().description(), trp.getB()),
            x,y,dx,dy, height
        );
    }
    public static ListRegistryMultiSelectWidget<EntityAttribute> attribute(Set<EntityAttribute> currentSelect, int x, int y, int dx, int dy, int height){
        return new ListRegistryMultiSelectWidget<>(
            Registries.ATTRIBUTE, currentSelect,
            (item -> Text.translatable(item.getTranslationKey()).getString()),
            (trp, attr)->new RegistryDisplayRender(new ItemStack(Items.ANVIL),
                Text.translatable(trp.getC().getTranslationKey()), trp.getB()),
            x,y,dx,dy, height
        );
    }
    public static <T> ListRegistryMultiSelectWidget<T> registry(Registry<T> registry, Set<T> currentSelect, int x, int y, int dx, int dy, int height){
        if(registry == Registries.ITEM){
            return (ListRegistryMultiSelectWidget<T>) item((Set) currentSelect, x, y, dx, dy, height);
        }else if(registry == Registries.ATTRIBUTE){
            return (ListRegistryMultiSelectWidget<T>) attribute((Set) currentSelect, x, y, dx, dy, height);
        }else if(registry == ItemStackUtils.registry().get(RegistryKeys.ENCHANTMENT)){
            return (ListRegistryMultiSelectWidget<T>) enchant((Set)currentSelect, x, y, dx, dy, height);
        }else {
            return new ListRegistryMultiSelectWidget<>(
                registry, currentSelect,
                (Object::toString),
                (trp, attr)->new RegistryDisplayRender(new ItemStack(Items.AIR),Text.literal(trp.getA()), trp.getB())
                ,x, y, dx, dy, height
            );
        }
    }
}