package me.matl114.hacks.utils.config;

import java.util.*;
import java.util.function.Predicate;
import me.matl114.managers.config.*;
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
            EntityTypeRegex.class,
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

    public static final List<Text> RULES_ENTITY = List.of(
            Text.literal("该选项通过\"正则表达式\"来匹配生物类型,但是按照以下快捷匹配规则匹配"),
            Text.literal("1. 匹配生物类型的路径部分时,将其加入列表,如匹配zombie,则僵尸被选中"),
            Text.literal("2. 匹配生物组,匹配animal,monster,ambient,misc以及其他生物生成组,匹配后整组加入列表"),
            Text.literal("3. 匹配活体生物\"living_entity\",匹配后所有活体生物加入列表"),
            Text.literal("4. 匹配移除符号,如匹配\"!enderman\"且不匹配\"enderman\",则将末影人移除列表"));

    @Override
    public List<Text> getRules() {
        return RULES_ENTITY;
    }
}
