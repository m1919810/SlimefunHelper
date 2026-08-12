package me.matl114.hacks.utils.config;

import java.util.*;
import java.util.function.Predicate;
import me.matl114.managers.config.*;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.EntityUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

public class EntityTypeRegex extends RegistryRegex<EntityType<?>>
        implements Predicate<EntityType<?>>, NBTParsable<RegistryRegex<EntityType<?>>> {

    public EntityTypeRegex(Regex regex) {
        super(regex, Registries.ENTITY_TYPE);
    }

    public static final NBTType<EntityTypeRegex> TYPE = new NBTType<>(
            "entitytyperegex",
            Regex.TYPE.typeCodec().xmap(EntityTypeRegex::new, EntityTypeRegex::getParent),
            RegistryRegex::createTextEditWidget,
            new EntityTypeRegex(Regex.EMPTY));

    @Override
    public <W extends RegistryRegex<EntityType<?>>> W withParent(Regex parent) {
        return (W) new EntityTypeRegex(parent);
    }

    @Override
    public NBTType<RegistryRegex<EntityType<?>>> type() {
        return TYPE.cast();
    }

    public Set<EntityType<?>> getFilterValue() {
        if (filterEntry == null) {
            filterEntry = new LinkedHashSet<>();
            EntityUtils.parseEntityWhiteList(parent.regex(), filterEntry);
        }
        return filterEntry;
    }

    @Override
    public <W> Optional<RegistryRegex<EntityType<?>>> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof NBTRef<?> nbtType) {
            String nbtTypeName = nbtType.enumType;
            if (Objects.equals(nbtTypeName, RegistryRegex.TYPE.typeName())) {
                NBTParsable.registerNBTType(RegistryRegex.TYPE);
                var regex = nbtType.get();
                if (regex instanceof RegistryRegex regg && regg.registry == Registries.ENTITY_TYPE) {
                    return Optional.of(new EntityTypeRegex(regg.parent));
                }
            } else {
                return Optional.empty();
            }
        } else if (ref instanceof StringRef stringRef) {
            var regex = this.parent.tryTypeConvert(stringRef);
            if (regex.isPresent()) {
                return Optional.of(new EntityTypeRegex(regex.get()));
            }
        }
        return super.tryTypeConvert(ref);
    }

    @Override
    public List<Text> getRules() {
        return ChatUtils.parseTooltipsTranslation("widget.nbt-parsable.entity-type-regex.rules.tooltips", "");
    }
}
