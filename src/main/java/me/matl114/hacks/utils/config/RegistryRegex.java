package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
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
import me.matl114.utils.RegistryUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

public class RegistryRegex<T> implements NBTParsable<RegistryRegex<T>>, Predicate<T> {
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
    final Registry<T> registry;

    @Getter
    final Regex parent;

    private Set<T> filterEntry;

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

    public static DrawableWidget createTextEditWidget(
            AttrKeyValue<RegistryRegex> attr, int x, int y, int width, int height) {
        SubScreenWidget subScreenWidget = new SubScreenWidget(x, y, width, height);
        Registry registry = attr.getOriginValue().getRegistry();
        AttrKeyValue<Regex> attrKeyValue = new TypeConvertAttrKeyValue<>(
                attr,
                WrapperFactory.of((reg) -> new RegistryRegex(reg, registry), RegistryRegex::getParent),
                Regex.TYPE);
        subScreenWidget.addDrawableChild(attrKeyValue.generateValueWidget(0, 0, width - height, height));
        subScreenWidget.addDrawableChild(ExecutableWidget.instance(width - height, 0, height, height)
                .setElementHandler(IconElement.fixedGui(
                                Constants.LIST_TAG_SPRITE,
                                ButtonAction.run(() -> openRegexListView(registry, attrKeyValue)))
                        .withTooltips(TooltipHandler.of(Constants.OPEN_LIST_PREVIEW_TOOLTIPS))));
        return subScreenWidget;
    }

    public static <T> void openRegexListView(Registry<T> registry, AttrKeyValue<Regex> attr) {
        AttrKeyValue<Regex> copy = attr.copy();
        ScreenAccess.of(
                        new RegistryChooseScreen<T>(registry, (v) -> {
                            attr.valueChange(null, copy.getValue());
                        }) {
                            {
                                selectSubScreen.modifiable(false);
                                selectSubScreen.filter((v) -> copy.isValidate()
                                        && copy.getOriginValue().test(v.getB().getPath()));
                                copy.addListener(s -> selectSubScreen.updateFilterList());
                                SubScreenWidget subScreenWidget = selectSubScreen.getScrollableBorder();
                                subScreenWidget.clearChildren();
                                subScreenWidget.addDrawableChild(
                                        copy.generateValueWidget(0, -20, subScreenWidget.getWidth(), 20));
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
}
