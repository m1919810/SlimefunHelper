package me.matl114.hacks.utils.config;

import com.google.common.collect.Streams;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.presets.choices.RegistryChooseScreen;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.RegistryUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Getter
@Accessors(fluent = true)
public class WeakRegistryRegex<T> implements NBTParsable<WeakRegistryRegex<T>>, Predicate<T> {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static final Class<WeakRegistryRegex<EntityType<?>>> ENTITY_TYPE = (Class) WeakRegistryRegex.class;
    public static final Class<WeakRegistryRegex<Item>> ITEM_TYPE = (Class) WeakRegistryRegex.class;
    public static final Class<WeakRegistryRegex<Block>> BLOCK_TYPE = (Class) WeakRegistryRegex.class;

    public static <T> Class<WeakRegistryRegex<T>> parameter() {
        return (Class) WeakRegistryRegex.class;
    }

    public static final NBTType<WeakRegistryRegex> TYPE = new NBTType<>(
            "weakregistryregex",
            RecordCodecBuilder.create(instance -> instance.group(
                            Regex.TYPE.typeCodec().fieldOf("regex").forGetter(WeakRegistryRegex::parent),
                            Identifier.CODEC.fieldOf("registry").forGetter(WeakRegistryRegex::registry))
                    .apply(instance, WeakRegistryRegex::new)),
            WeakRegistryRegex::createTextEditWidget,
            new WeakRegistryRegex(Regex.EMPTY, RegistryKeys.ITEM.getValue()));

    protected final Identifier registry;
    protected final Regex parent;

    protected Set<T> filterEntry;

    public <W extends WeakRegistryRegex<T>> W withParent(Regex parent) {
        return (W) new WeakRegistryRegex<>(parent, this.registry);
    }

    public WeakRegistryRegex(Regex parent, Identifier registry) {
        this.parent = parent;
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    public Optional<Registry<T>> resolveRegistry() {
        var handler = mc.getNetworkHandler();
        if (handler == null || handler.getRegistryManager() == null) {
            return Optional.empty();
        }
        return (Optional<Registry<T>>)
                (Optional) handler.getRegistryManager().getOptional(RegistryKey.ofRegistry(registry));
    }

    public Set<T> getFilterValue() {
        if (filterEntry == null) {
            var registryValue = resolveRegistry().orElse(null);
            if (registryValue == null) {
                return Set.of();
            }
            filterEntry = RegistryUtils.parseWhiteList(registryValue, parent.pattern());
        }
        return filterEntry;
    }

    @Override
    public boolean test(T val) {
        return getFilterValue().contains(val);
    }

    public boolean test(RegistryEntry<T> val) {
        return getFilterValue().contains(val.value());
    }

    public static <T, W extends WeakRegistryRegex<T>> DrawableWidget createTextEditWidget(
            AttrKeyValue<W> attr, int x, int y, int width, int height) {
        SubScreenWidget subScreenWidget = new SubScreenWidget(x, y, width, height);
        W originValue = attr.getOriginValue();
        AttrKeyValue<Regex> attrKeyValue = new TypeConvertAttrKeyValue<>(
                attr, WrapperFactory.<Regex, W>of(originValue::withParent, WeakRegistryRegex::parent), Regex.TYPE);
        var registry = originValue.resolveRegistry();
        if (registry.isPresent()) {
            Registry<T> lookupValue = registry.get();
            subScreenWidget.addDrawableChild(attrKeyValue.generateValueWidget(0, 0, width - height, height));
            subScreenWidget.addDrawableChild(ExecutableWidget.instance(width - height, 0, height, height)
                    .setElementHandler(IconElement.fixedGui(
                                    Constants.LIST_TAG_SPRITE,
                                    ButtonAction.run(() -> openRegexListView(lookupValue, attr)))
                            .withTooltips(TooltipHandler.of(Streams.concat(
                                            originValue.getRules().stream(),
                                            Constants.openListPreviewTooltips().stream())
                                    .toList()))));
        } else {
            subScreenWidget.addDrawableChild(attrKeyValue.generateValueWidget(0, 0, width, height));
        }
        return subScreenWidget;
    }

    private static <T, W extends WeakRegistryRegex<T>> void openRegexListView(
            Registry<T> registry, AttrKeyValue<W> original) {
        AttrKeyValue<W> originalCopy = original.copy();
        var originalValue = originalCopy.getOriginValue();
        AttrKeyValue<Regex> regexWrapper = new TypeConvertAttrKeyValue<>(
                originalCopy,
                WrapperFactory.<Regex, W>of(originalValue::withParent, WeakRegistryRegex::parent),
                Regex.TYPE);
        ScreenAccess.of(
                        new RegistryChooseScreen<T>(registry, (v) -> {
                            original.valueChange(null, originalCopy.getValue());
                        }) {
                            {
                                selectSubScreen.modifiable(false);
                                selectSubScreen.filter((v) -> originalCopy.isValidate()
                                        && originalCopy.getOriginValue().test(v.getC()));
                                originalCopy.addListener(s -> selectSubScreen.updateFilterList());
                                SubScreenWidget subScreenWidget = selectSubScreen.getScrollableBorder();
                                subScreenWidget.clearChildren();
                                subScreenWidget.addDrawableChild(
                                        regexWrapper.generateValueWidget(0, -20, subScreenWidget.getWidth(), 20));
                                subScreenWidget.addDrawableChild(generateInformationButton(subScreenWidget));
                            }

                            public DrawableWidget generateInformationButton(SubScreenWidget subScreenWidget) {
                                return ExecutableWidget.instance(subScreenWidget.getWidth(), -20, 20, 20)
                                        .setElementHandler(IconElement.fixedGui(
                                                        Constants.EDITOR_SPRITE, ButtonAction.run(() -> {}))
                                                .withTooltips(TooltipHandler.of(original.getOriginValue()
                                                        .getRules())));
                            }

                            @Override
                            protected boolean canConfirm(ElementHandler elementHandler) {
                                return originalCopy.isValidate();
                            }
                        })
                .openFromCurrent();
    }

    @Override
    public NBTType<WeakRegistryRegex<T>> type() {
        return TYPE.cast();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof WeakRegistryRegex<?> that)) return false;
        return Objects.equals(registry, that.registry) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registry, parent);
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type)
                && type instanceof WeakRegistryRegex<?> registryRegex
                && Objects.equals(registryRegex.registry, registry);
    }

    @Override
    public <W> Optional<WeakRegistryRegex<T>> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof StringRef stringRef) {
            var regex = this.parent.tryTypeConvert(stringRef);
            if (regex.isPresent()) {
                return Optional.of(new WeakRegistryRegex<>(regex.get(), this.registry));
            }
        }
        return Optional.empty();
    }

    public List<Text> getRules() {
        return ChatUtils.parseTooltipsTranslation("widget.nbt-parsable.registry-regex.rules.tooltips", "");
    }
}
