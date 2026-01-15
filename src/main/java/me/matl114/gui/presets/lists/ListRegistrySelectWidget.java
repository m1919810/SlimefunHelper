package me.matl114.gui.presets.lists;

import me.matl114.gui.FilterService;
import me.matl114.gui.basic.*;
import me.matl114.gui.config.RegistryDisplayRender;
import me.matl114.utils.ItemStackUtils;
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

import java.util.function.Function;
import java.util.function.Predicate;

public class ListRegistrySelectWidget<T> extends ListSelectWidget<Triplet<String, Identifier, T>> {
    public T getSelectedRegistry(){
        return selected == null? null: selected.getC();
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
    public ListRegistrySelectWidget(Registry<T> registry, Function<T, String> localization, Function<Triplet<String, Identifier, T>, RenderHandler> renderFactory, int x, int y, int dx, int dy, int height){
        super(registry.stream()
            .map(s->new Triplet<String,Identifier, T>(localization.apply(s), registry.getId(s), s))
            .toList(), renderFactory, (Predicate) filter, x, y, dx, dy, height);
    }
    public static ListRegistrySelectWidget<Item> item(int x, int y, int dx, int dy, int height){
        return new ListRegistrySelectWidget<>(
            Registries.ITEM,
            (item -> item.getName().getString()),
            (trp)->new RegistryDisplayRender(new ItemStack(trp.getC()),
                trp.getC().getName(), trp.getB()),
            x,y,dx,dy, height
        );
    }
    public static ListRegistrySelectWidget<Enchantment> enchant(int x, int y, int dx, int dy, int height){
        return new ListRegistrySelectWidget<>(
            ItemStackUtils.registry().get(RegistryKeys.ENCHANTMENT),
            (item -> item.description().getString()),
            (trp)->new RegistryDisplayRender(new ItemStack(Items.ENCHANTED_BOOK),
                trp.getC().description(), trp.getB()),
            x,y,dx,dy, height
        );
    }
    public static ListRegistrySelectWidget<EntityAttribute> attribute(int x, int y, int dx, int dy, int height){
        return new ListRegistrySelectWidget<>(
            Registries.ATTRIBUTE,
            (item -> Text.translatable(item.getTranslationKey()).getString()),
            (trp)->new RegistryDisplayRender(new ItemStack(Items.ANVIL),
                Text.translatable(trp.getC().getTranslationKey()), trp.getB()),
            x,y,dx,dy, height
        );
    }
    public static <T> ListRegistrySelectWidget<T> registry(Registry<T> registry, int x, int y, int dx, int dy, int height){
        if(registry == Registries.ITEM){
            return (ListRegistrySelectWidget<T>) item(x, y, dx, dy, height);
        }else if(registry == Registries.ATTRIBUTE){
            return (ListRegistrySelectWidget<T>) attribute(x, y, dx, dy, height);
        }else if(registry == ItemStackUtils.registry().get(RegistryKeys.ENCHANTMENT)){
            return (ListRegistrySelectWidget<T>) enchant(x, y, dx, dy, height);
        }else {
            return new ListRegistrySelectWidget<>(
                registry,
                (Object::toString),
                (trp)->new RegistryDisplayRender(new ItemStack(Items.AIR),Text.literal(trp.getA()), trp.getB())
                ,x, y, dx, dy, height
            );
        }
    }
}
