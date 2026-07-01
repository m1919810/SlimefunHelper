package me.matl114.hacks.utils.config;

import static me.matl114.utils.config.BaseAttrKeyValue.*;
import static me.matl114.utils.config.kv.AttrKeyValues.*;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.presets.choices.ColorSelectIcon;
import me.matl114.gui.presets.lists.NBTBoundedListScreen;
import me.matl114.gui.presets.lists.NBTListModifyScreen;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.collections.InitializationTask;
import me.matl114.utils.config.*;
import me.matl114.utils.config.kv.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public interface NBTTypes {
    public NBTType<Integer> INT_TYPE = new NBTType<>("int", Codec.INT, getWidgetFactory(), INT_FACTORY, 0);

    public NBTType<Long> LONG_TYPE = new NBTType<>("long", Codec.LONG, getWidgetFactory(), LONG_FACTORY, 0L);

    public NBTType<Double> DOUBLE_TYPE =
            new NBTType<>("double", Codec.DOUBLE, getWidgetFactory(), DOUBLE_FACTORY, 0.0D);

    public NBTType<Boolean> BOOLEAN_TYPE =
            new NBTType<>("boolean", Codec.BOOL, BOOLEAN_WIDGET_FACTORY, BOOL_FACTORY, false);

    public NBTType<String> STRING_TYPE = new NBTType<>("string", Codec.STRING, getWidgetFactory(), STRING_FACTORY, "");

    public NBTType<TextColor> COLOR_TYPE = new NBTType<>(
            "color",
            Codec.withAlternative(
                    TextColor.CODEC,
                    Codec.INT.comapFlatMap(
                            s -> {
                                if (s >= 0 && s <= 16777215) {
                                    return DataResult.success(TextColor.fromRgb(s));
                                } else {
                                    return DataResult.error(() -> "Color value out of range: " + s);
                                }
                            },
                            TextColor::getRgb)),
            NBTTypes::generateColorInputWidget,
            COLOR_FACTORY,
            TextColor.fromFormatting(Formatting.BLACK));

    public NBTType<MultiKeyBind> KEY_BIND_TYPE = new NBTType<>(
            "keybind",
            Codec.STRING.comapFlatMap(
                    (str) -> {
                        try {
                            return DataResult.success(new MultiKeyBind(str));
                        } catch (Throwable e) {
                            return DataResult.error(() -> "Invalid keybind: " + str);
                        }
                    },
                    MultiKeyBind::asString),
            KeyBindRef.WIDGET_FACTORY,
            KeyBindRef.FACTORY,
            new MultiKeyBind());

    @SuppressWarnings("unchecked")
    public NBTType<Registry<?>> REGISTRY_TYPE = new NBTType<>(
            "registry",
            (Codec<Registry<?>>) (Codec<?>) Registries.REGISTRIES.getCodec(),
            (s, x, y, dx, dy) ->
                    RegistryAttrKeyValue.generateTextInputWithRegistrySearch(Registries.REGISTRIES, s, x, y, dx, dy),
            WrapperFactory.<String, Registry<?>>of(
                    s -> Registries.REGISTRIES.get(Identifier.tryParse(s)),
                    v -> ((Registry) Registries.REGISTRIES).getId(v).toString()),
            Registries.BLOCK);

    public NBTType<Pattern> REGEX_TYPE =
            createComapFlatMap("pattern", STRING_TYPE, WrapperFactory.of(Pattern::compile, Pattern::pattern));

    public NBTType<Identifier> IDENTIFIER_TYPE = createComapFlatMap(
            "identifier",
            STRING_TYPE,
            WrapperFactory.of(Identifier::of, Identifier::toString),
            Identifier.ofVanilla(""));

    public NBTType<NbtElement> NBT_ELEMENT_TYPE = createComapFlatMap("nbtelement", STRING_TYPE, NBT_FACTORY);
    public NBTType<NbtCompound> NBT_COMPOUND_TYPE = new NBTType<NbtCompound>(
            "nbtcompound",
            NbtCompound.CODEC,
            BaseAttrKeyValue.<NbtCompound>getWidgetFactory(),
            NBT_COMPOUND_FACTORY,
            new NbtCompound());

    public NBTType<WrapEnum<?>> CONFIG_ENUM_TYPE = WrapEnum.TYPE.cast();

    public NBTType<Vec2> VEC2_TYPE = Vec2.TYPE;

    public NBTType<Vec3> VEC3_TYPE = Vec3.TYPE;

    public NBTType<Pos3> POS3_TYPE = Pos3.TYPE;

    public static DrawableWidget generateColorInputWidget(
            AttrKeyValue<TextColor> keyValue, int x, int y, int dx, int dy) {
        SubScreenWidget subScreenWidget = new SubScreenWidget(x, y, dx, dy);
        subScreenWidget.addDrawableChild(BaseAttrKeyValue.generateTextInputValueWidget(keyValue, 0, 0, dx - dy, dy));
        subScreenWidget.addDrawableChild(new ExecutableWidget(dx - dy, 0, dy, dy)
                .setElementHandler(new ColorSelectIcon(ValueAccessor.of(keyValue))));
        return subScreenWidget;
    }

    // todo: color type, registry type getter, etc

    public static <T, W> NBTType<T> createXMap(Class<T> targetClass, NBTType<W> type, WrapperFactory<W, T> wrapper) {
        return createXMap(targetClass.getSimpleName().toLowerCase(Locale.ROOT), type, wrapper);
    }

    public static <T, W> NBTType<T> createXMap(String name, NBTType<W> type, WrapperFactory<W, T> wrapper) {
        return new NBTType<>(
                name,
                type.typeCodec().<T>xmap(wrapper::create, wrapper::get),
                (attr, x, y, dx, dy) -> {
                    return new TypeConvertAttrKeyValue<>(
                                    attr, wrapper, type.customWidgetFactory(), type.stringifyFactory())
                            .generateValueWidget(x, y, dx, dy);
                },
                type.stringifyFactory().concat(wrapper),
                wrapper.create(type.empty()));
    }

    public static <T, W> NBTType<T> createComapFlatMap(
            String value, NBTType<W> type, WrapperFactory<W, T> wrapper, T empty) {
        return new NBTType<>(
                value,
                type.typeCodec()
                        .<T>comapFlatMap(
                                (s) -> {
                                    try {
                                        return DataResult.success(wrapper.create(s));
                                    } catch (Throwable e) {
                                        return DataResult.error(() -> "Error while creating");
                                    }
                                },
                                wrapper::get),
                (attr, x, y, dx, dy) -> {
                    return new TypeConvertAttrKeyValue<>(
                                    attr, wrapper, type.customWidgetFactory(), type.stringifyFactory())
                            .generateValueWidget(x, y, dx, dy);
                },
                type.stringifyFactory().concat(wrapper),
                empty);
    }

    public static <T, W> NBTType<T> createComapFlatMap(String value, NBTType<W> type, WrapperFactory<W, T> wrapper) {
        return createComapFlatMap(value, type, wrapper, wrapper.create(type.empty()));
    }

    public static <T, W> NBTType<T> createComapFlatMap(
            Class<T> targetClass, NBTType<W> type, WrapperFactory<W, T> wrapper) {
        return createComapFlatMap(
                targetClass.getSimpleName().toLowerCase(Locale.ROOT), type, wrapper, wrapper.create(type.empty()));
    }

    public static <T, W> NBTType<T> createListLke(
            Class<T> targetClass, NBTType<W> type, WrapperFactory<List<W>, T> wrapper, int listWidth, int listHeight) {
        return createListLke(
                targetClass.getSimpleName().toLowerCase(Locale.ROOT), type, wrapper, listWidth, listHeight);
    }

    public static <T, W> NBTType<T> createListLke(
            String targetClass, NBTType<W> type, WrapperFactory<List<W>, T> wrapper, int listWidth, int listHeight) {
        return new NBTType<>(
                targetClass,
                Codec.list(type.typeCodec()).xmap(wrapper::create, wrapper::get),
                (attr, x, y, dx, dy) -> {
                    return generateListModifyButton(
                            new WrapperAttrKeyValue<>(attr, wrapper),
                            type,
                            type::empty,
                            x,
                            y,
                            dx,
                            dy,
                            listWidth,
                            listHeight);
                },
                AttrKeyValues.STR_LIST_FACTORY
                        .concat(WrapperFactory.list(type.stringifyFactory()))
                        .concat(wrapper),
                wrapper.create(List.of()));
    }

    public static <T, W> NBTType<T> createListLke(
            String targetClass,
            NBTType<W> type,
            WrapperFactory<List<W>, T> wrapper,
            Supplier<W> customNewElementSupplier,
            int listWidth,
            int listHeight) {
        return new NBTType<>(
                targetClass,
                Codec.list(type.typeCodec()).xmap(wrapper::create, wrapper::get),
                (attr, x, y, dx, dy) -> {
                    return generateListModifyButton(
                            new WrapperAttrKeyValue<>(attr, wrapper),
                            type,
                            customNewElementSupplier,
                            x,
                            y,
                            dx,
                            dy,
                            listWidth,
                            listHeight);
                },
                AttrKeyValues.STR_LIST_FACTORY
                        .concat(WrapperFactory.list(type.stringifyFactory()))
                        .concat(wrapper),
                wrapper.create(List.of()));
    }

    public static <T, K1, K2> NBTType<T> createPairLike(
            Class<T> targetClass,
            NBTType<K1> k1Type,
            String name1,
            NBTType<K2> k2Type,
            String name2,
            PairLikeFactory<K1, K2, T> pairFactory,
            UnaryOperator<AttrKeyValue.CustomWidgetFactory<K1>> k1Resize,
            UnaryOperator<AttrKeyValue.CustomWidgetFactory<K2>> k2Resize) {

        return new NBTType<>(
                targetClass.getSimpleName().toLowerCase(Locale.ROOT),
                RecordCodecBuilder.<T>create(instance -> instance.group(
                                k1Type.typeCodec().fieldOf(name1).forGetter(pairFactory::getFirst),
                                k2Type.typeCodec().fieldOf(name2).forGetter(pairFactory::getSecond))
                        .apply(instance, pairFactory::create)),
                (s, x, y, dx, dy) -> {
                    AttrKeyValue<T> sourceAttr = s;
                    AttrKeyValue<K1> key1Attr =
                            new TypeConvertAttrKeyValue<>(s, pairFactory.asFirstWrapper(s::getOriginValue), k1Type);
                    AttrKeyValue<K2> key2Attr =
                            new TypeConvertAttrKeyValue<>(s, pairFactory.asSecondWrapper(s::getOriginValue), k2Type);
                    SubScreenWidget subScreenWidget = new SubScreenWidget(x, y, dx, dy);
                    subScreenWidget
                            .addDrawableChild(
                                    k1Resize.apply(k1Type.customWidgetFactory()).generateWidget(key1Attr, 0, 0, dx, dy))
                            .addDrawableChild(k2Resize.apply(k2Type.customWidgetFactory())
                                    .generateWidget(key2Attr, 0, 0, dx, dy));
                    return subScreenWidget;
                },
                WrapperFactory.of(
                        s -> {
                            List<String> list = STR_LIST_FACTORY.create(s);
                            return pairFactory.create(
                                    k1Type.stringifyFactory().create(list.get(0)),
                                    k2Type.stringifyFactory().create(list.get(1)));
                        },
                        (v) -> {
                            return STR_LIST_FACTORY.get(List.of(
                                    k1Type.stringifyFactory().get(pairFactory.getFirst(v)),
                                    k2Type.stringifyFactory().get(pairFactory.getSecond(v))));
                        }),
                pairFactory.create(k1Type.empty(), k2Type.empty()));
    }

    public static <T, K1, K2> NBTType<T> createArrayMapLike(
            Class<T> targetClass,
            NBTType<K1> k1Type,
            String name1,
            NBTType<K2> k2Type,
            String name2,
            WrapperFactory<Map<K1, K2>, T> mapLike,
            UnaryOperator<AttrKeyValue.CustomWidgetFactory<K1>> k1Resize,
            UnaryOperator<AttrKeyValue.CustomWidgetFactory<K2>> k2Resize,
            int width,
            int height) {
        return createArrayMapLike(
                targetClass.getSimpleName().toLowerCase(Locale.ROOT),
                k1Type,
                name1,
                k2Type,
                name2,
                mapLike,
                k1Resize,
                k2Resize,
                width,
                height);
    }

    public static <T, K1, K2> NBTType<T> createArrayMapLike(
            String targetClass,
            NBTType<K1> k1Type,
            String name1,
            NBTType<K2> k2Type,
            String name2,
            WrapperFactory<Map<K1, K2>, T> mapLike,
            UnaryOperator<AttrKeyValue.CustomWidgetFactory<K1>> k1Resize,
            UnaryOperator<AttrKeyValue.CustomWidgetFactory<K2>> k2Resize,
            int width,
            int height) {
        return createArrayMapLike(
                targetClass,
                k1Type,
                k1Type::empty,
                name1,
                k2Type,
                k2Type::empty,
                name2,
                mapLike,
                k1Resize,
                k2Resize,
                width,
                height);
    }

    public static <T, K1, K2> NBTType<T> createArrayMapLike(
            String targetClass,
            NBTType<K1> k1Type,
            Supplier<K1> k1Supplier,
            String name1,
            NBTType<K2> k2Type,
            Supplier<K2> k2Supplier,
            String name2,
            WrapperFactory<Map<K1, K2>, T> mapLike,
            UnaryOperator<AttrKeyValue.CustomWidgetFactory<K1>> k1Resize,
            UnaryOperator<AttrKeyValue.CustomWidgetFactory<K2>> k2Resize,
            int width,
            int height) {
        PairLikeFactory<K1, K2, Pair<K1, K2>> pairFactory =
                PairLikeFactory.of(Pair::of, Pair::getFirst, Pair::getSecond);
        NBTType<Pair<K1, K2>> pairK1K2 =
                createPairLike((Class) Pair.class, k1Type, name1, k2Type, name2, pairFactory, k1Resize, k2Resize);
        WrapperFactory<List<Pair<K1, K2>>, T> listToT =
                WrapperFactory.<K1, K2>getListMapWrapper().concat(mapLike);
        return createListLke(
                targetClass,
                pairK1K2,
                listToT,
                () -> pairFactory.create(k1Supplier.get(), k2Supplier.get()),
                width,
                height);
    }
    // the type must support null storage
    public static <T> NBTType<Optional<T>> createOptional(String targetClass, NBTType<T> type, String defaultValue) {
        WrapperFactory<String, T> original = type.stringifyFactory();
        WrapperFactory<T, Optional<T>> factory = WrapperFactory.of(Optional::ofNullable, s -> s.orElse(null));
        WrapperFactory<String, Optional<T>> stringifyFactory = WrapperFactory.of(
                s -> {
                    if (Objects.equals(s, defaultValue)) {
                        return Optional.empty();
                    } else {
                        return Optional.ofNullable(original.create(s));
                    }
                },
                t -> t.map(original::get).orElse(defaultValue));
        WrapperFactory<String, T> stringifyFactory2 = stringifyFactory.concat(factory.inverse());
        return new NBTType<>(
                targetClass,
                factory.wrapCodecSafe(type.typeCodec()),
                (attr, x, y, dx, dy) -> {
                    return new TypeConvertAttrKeyValue<Optional<T>, T>(
                                    attr, factory, type.customWidgetFactory(), stringifyFactory2)
                            .generateValueWidget(x, y, dx, dy);
                },
                stringifyFactory,
                Optional.empty());
    }

    public static <T> NBTType<Optional<T>> createOptionalNonnull(
            String targetClass, NBTType<T> type, String defaultValue) {
        WrapperFactory<String, T> original = type.stringifyFactory();
        WrapperFactory<T, Optional<T>> factory = WrapperFactory.of(Optional::of, s -> s.orElse(null));
        WrapperFactory<String, Optional<T>> stringifyFactory = WrapperFactory.of(
                s -> {
                    if (Objects.equals(s, defaultValue)) {
                        return Optional.empty();
                    } else {
                        return Optional.of(original.create(s));
                    }
                },
                t -> t.map(original::get).orElse(defaultValue));
        WrapperFactory<String, T> stringifyFactory2 = stringifyFactory.concat(factory.inverse());
        return new NBTType<>(
                targetClass,
                factory.wrapCodec(type.typeCodec()),
                (attr, x, y, dx, dy) -> {
                    return new TypeConvertAttrKeyValue<Optional<T>, T>(
                                    attr, factory, type.customWidgetFactory(), stringifyFactory2)
                            .generateValueWidget(x, y, dx, dy);
                },
                stringifyFactory,
                Optional.empty());
    }

    public static <T> NBTType<T> createEnumLike(
            String targetClass, Map<String, T> finiteLookup, Function<T, String> string) {
        return new NBTType<>(
                targetClass,
                CodecUtils.finiteMapCodec(finiteLookup, string),
                EnumAttrKeyValue.createFiniteLookupWidgetFactory(finiteLookup),
                EnumAttrKeyValue.createFiniteMapLookup(finiteLookup),
                finiteLookup.values().iterator().next());
    }

    public static <W> DrawableWidget generateListModifyButton(
            AttrKeyValue<List<W>> keyValue,
            NBTType<W> typeW,
            Supplier<W> supplier,
            int x,
            int y,
            int dx,
            int dy,
            int listWidth,
            int listHeight) {
        return new SubScreenWidget(x, y, dx, dy)
                .addDrawableChild(new ExecutableWidget(dy, 0, dx - dy, dy)
                        .setElementHandler(new ButtonElement(
                                        TextProvider.of(Constants.OPEN_LIST_EDIT_TEXT), ButtonAction.run(() -> {
                                            openListModifyScreen(keyValue, typeW, supplier, listWidth, listHeight);
                                        }))
                                .withTooltips(TooltipHandler.of(Constants.OPEN_LIST_EDIT_TOOLTIPS))))
                .addDrawableChild(DisplayWidget.instance(0, 0, dy - 1, dy)
                        .setRenderHandler(IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.empty())));
    }

    public static <T, W> DrawableWidget generateBoundedListModifyButton(
            AttrKeyValue<Map<T, W>> keyValue,
            List<T> keyBound,
            NBTType<W> valueType,
            WidgetFactory<T> keyWidget,
            int x,
            int y,
            int dx,
            int dy,
            int keyLabelWidth,
            int listWidth,
            int listHeight) {
        return new SubScreenWidget(x, y, dx, dy)
                .addDrawableChild(new ExecutableWidget(dy, 0, dx - dy, dy)
                        .setElementHandler(new ButtonElement(
                                        TextProvider.of(Constants.OPEN_LIST_EDIT_TEXT), ButtonAction.run(() -> {
                                            openBoundedListModifyScreen(
                                                    keyValue,
                                                    keyBound,
                                                    valueType,
                                                    keyWidget,
                                                    keyLabelWidth,
                                                    listWidth,
                                                    listHeight);
                                        }))
                                .withTooltips(TooltipHandler.of(Constants.OPEN_LIST_EDIT_TOOLTIPS))))
                .addDrawableChild(DisplayWidget.instance(0, 0, dy - 1, dy)
                        .setRenderHandler(IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.empty())));
    }

    public static <W> void openListModifyScreen(
            AttrKeyValue<List<W>> keyValue, NBTType<W> type, Supplier<W> supplier, int listWidth, int listHeight) {
        ScreenAccess.of(new NBTListModifyScreen<>(
                        keyValue,
                        type,
                        supplier,
                        (lst) -> keyValue.valueChangeInternal(null, lst),
                        listWidth,
                        listHeight))
                .openFromCurrent();
    }

    public static <T, W> void openBoundedListModifyScreen(
            AttrKeyValue<Map<T, W>> keyValue,
            List<T> bound,
            NBTType<W> type,
            WidgetFactory<T> keyWidget,
            int keyLabelWidth,
            int listWidth,
            int listHeight) {
        Map<T, W> twMap = keyValue.getOriginValue();
        boolean add = false;
        for (var re : bound) {
            if (!twMap.containsKey(re)) {
                add = true;
                twMap = new LinkedHashMap<>(twMap);
                twMap.put(re, type.createEmpty());
            }
        }
        if (add) {
            keyValue.valueChangeInternal(null, twMap);
        }
        NBTBoundedListScreen<T, W> listModifyScreenImmutable = new NBTBoundedListScreen<>(
                keyValue,
                type,
                keyWidget,
                (map) -> keyValue.valueChangeInternal(null, map),
                keyLabelWidth,
                listWidth,
                listHeight);
        ScreenAccess.of(listModifyScreenImmutable).openFromCurrent();
    }

    public static Map<String, NBTType<?>> PRIMITIVE_TYPES = new LinkedHashMap<>();

    public static <T> NBTType<T> primitiveTypes(String string) {
        return (NBTType<T>) PRIMITIVE_TYPES.get(string);
    }

    public static Codec<NBTType<?>> CODEC = Codec.STRING.flatXmap(
            s -> {
                var nbtType = primitiveTypes(s);
                return nbtType == null ? DataResult.error(() -> "Not found") : DataResult.success(nbtType);
            },
            t -> {
                if (PRIMITIVE_TYPES.containsKey(t.typeName())) {
                    return DataResult.success(t.typeName());
                } else {
                    return DataResult.error(() -> "Not a primitive type: " + t.typeName());
                }
            });

    public static <W> Codec<NBTType<W>> codec() {
        return (Codec) CODEC;
    }

    public static void init() {
        Field[] fields = NBTTypes.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                if (Modifier.isStatic(field.getModifiers()) && NBTType.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    NBTType nbtType = (NBTType) field.get(null);
                    PRIMITIVE_TYPES.put(nbtType.typeName(), nbtType);
                }
            } catch (Throwable e) {
            }
        }
    }

    public InitializationTask INIT = InitializationTask.of(NBTTypes::init);
}
