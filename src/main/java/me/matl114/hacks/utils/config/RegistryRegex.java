package me.matl114.hacks.utils.config;

import com.google.common.collect.Streams;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.Getter;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.presets.choices.RegistryChooseScreen;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.RegistryUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

public class RegistryRegex<T> implements NBTParsable<RegistryRegex<T>>, Predicate<T> {
    public static final Class<RegistryRegex<EntityType<?>>> ENTITY_TYPE = (Class) RegistryRegex.class;
    public static final Class<RegistryRegex<Item>> ITEM_TYPE = (Class) RegistryRegex.class;
    public static final Class<RegistryRegex<Block>> BLOCK_TYPE = (Class) RegistryRegex.class;

    public static <T> Class<RegistryRegex<T>> parameter() {
        return (Class) RegistryRegex.class;
    }

    public static final NBTType<RegistryRegex> TYPE = new NBTType<>(
            RegistryRegex.class,
            RecordCodecBuilder.create(instance -> instance.group(
                            Regex.TYPE.typeCodec().fieldOf("regex").forGetter(RegistryRegex::getParent),
                            ((Codec<Registry<?>>) Registries.REGISTRIES.getCodec())
                                    .fieldOf("registry")
                                    .forGetter(RegistryRegex::getRegistry))
                    .apply(instance, RegistryRegex::new)),
            RegistryRegex::createTextEditWidget,
            new RegistryRegex(Regex.EMPTY, Registries.ITEM));

    @Getter
    protected final Registry<T> registry;

    @Getter
    protected final Regex parent;

    protected Set<T> filterEntry;

    public <W extends RegistryRegex<T>> W withParent(Regex parent) {
        return (W) new RegistryRegex<>(parent, this.registry);
    }

    public RegistryRegex(Regex parent, Registry<T> registry) {
        this.parent = parent;
        this.registry = registry;
    }

    public Set<T> getFilterValue() {
        if (filterEntry == null) {
            filterEntry = RegistryUtils.parseWhiteList(registry, parent.pattern());
        }
        return filterEntry;
    }

    public boolean test(T val) {
        return getFilterValue().contains(val);
    }

    public boolean test(RegistryEntry<T> val) {
        return getFilterValue().contains(val.value());
    }

    public static <T, W extends RegistryRegex<T>> DrawableWidget createTextEditWidget(
            AttrKeyValue<W> attr, int x, int y, int width, int height) {
        SubScreenWidget subScreenWidget = new SubScreenWidget(x, y, width, height);
        W originValue = attr.getOriginValue();
        Registry<T> registry = originValue.getRegistry();
        AttrKeyValue<Regex> attrKeyValue = new TypeConvertAttrKeyValue<W, Regex>(
                attr, WrapperFactory.<Regex, W>of(originValue::<W>withParent, RegistryRegex::getParent), Regex.TYPE);
        subScreenWidget.addDrawableChild(attrKeyValue.generateValueWidget(0, 0, width - height, height));
        subScreenWidget.addDrawableChild(ExecutableWidget.instance(width - height, 0, height, height)
                .setElementHandler(IconElement.fixedGui(
                                Constants.LIST_TAG_SPRITE,
                                ButtonAction.run(() -> openRegexListView(registry, attrKeyValue, originValue)))
                        .withTooltips(TooltipHandler.of(Streams.concat(
                                        originValue.getRules().stream(), Constants.OPEN_LIST_PREVIEW_TOOLTIPS.stream())
                                .toList()))));
        return subScreenWidget;
    }

    private static <T, W extends RegistryRegex<T>> void openRegexListView(
            Registry<T> registry, AttrKeyValue<Regex> attr, W predicate) {
        AttrKeyValue<Regex> copy = attr.copy();
        ScreenAccess.of(
                        new RegistryChooseScreen<T>(registry, (v) -> {
                            attr.valueChange(null, copy.getValue());
                        }) {
                            {
                                selectSubScreen.modifiable(false);
                                selectSubScreen.filter((v) -> copy.isValidate()
                                        && predicate
                                                .withParent(copy.getOriginValue())
                                                .test(registry.get(v.getB())));
                                copy.addListener(s -> selectSubScreen.updateFilterList());
                                SubScreenWidget subScreenWidget = selectSubScreen.getScrollableBorder();
                                subScreenWidget.clearChildren();
                                subScreenWidget.addDrawableChild(
                                        copy.generateValueWidget(0, -20, subScreenWidget.getWidth(), 20));
                                subScreenWidget.addDrawableChild(generateInformationButton(subScreenWidget));
                            }

                            public DrawableWidget generateInformationButton(SubScreenWidget subScreenWidget) {
                                return ExecutableWidget.instance(subScreenWidget.getWidth(), -20, 20, 20)
                                        .setElementHandler(IconElement.fixedGui(
                                                        Constants.EDITOR_SPRITE, ButtonAction.run(() -> {}))
                                                .withTooltips(TooltipHandler.of(predicate.getRules())));
                            }

                            @Override
                            protected boolean canConfirm(ElementHandler elementHandler) {
                                return copy.isValidate();
                            }
                        })
                .openFromCurrent();
    }

    @Override
    public NBTType<RegistryRegex<T>> type() {
        return (NBTType) TYPE;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof RegistryRegex<?> that)) return false;
        return Objects.equals(registry, that.registry) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registry, parent);
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type)
                && type instanceof RegistryRegex<?> registryRegex
                && registryRegex.registry == registry;
    }

    @Override
    public <W> Optional<RegistryRegex<T>> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof StringRef str) {
            var regex = this.parent.tryTypeConvert(str);
            if (regex.isPresent()) {
                return Optional.of(new RegistryRegex<>(regex.get(), this.registry));
            }
        }
        return Optional.empty();
    }

    public static final List<Text> TOOLTIPS_RULES =
            List.of(Text.literal("该选项通过\"正则表达式\"匹配注册表项"), Text.literal("仅匹配路径,如minecraft:air(空气)只匹配air部分"));

    public List<Text> getRules() {
        return TOOLTIPS_RULES;
    }
}
