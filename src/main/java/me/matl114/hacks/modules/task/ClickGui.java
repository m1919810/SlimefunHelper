package me.matl114.hacks.modules.task;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.*;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.config.ConfigMultiLevelListScreen;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.interfaces.CombinedElementFactory;
import me.matl114.gui.interfaces.CustomElementFactory;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.gui.interfaces.VanillaElementFactory;
import me.matl114.gui.presets.single.CenterScreen;
import me.matl114.gui.presets.single.SimpleScreen;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.GuiSetting;
import me.matl114.hacks.utils.config.Vec2;
import me.matl114.hacks.utils.config.WrapRGBA;
import me.matl114.hacks.utils.gui.AlignType;
import me.matl114.hacks.utils.gui.GuiColorSamplier;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.config.*;
import me.matl114.managers.file.FileStorage;
import me.matl114.managers.input.*;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.algorithms.SerialExecutor;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.Text;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.NotNull;

public class ClickGui extends BaseModule implements GuiColorSamplier {
    public static ClickGui INSTANCE;

    public final NBTType<GuiSetting> configInputWidget = GuiSetting.TYPE;

    public ElementFactory getGuiElementFactory() {
        return guiElementFactory;
    }

    public enum ClickGuiStyle implements ConfigEnum {
        SUB_SCREEN,
        SLIDER;

        @Override
        public String getConfigEnumType() {
            return "click_gui_style";
        }
    }

    private ClickGuiFactory createClickGuiFactory(ClickGuiStyle style) {
        return switch (style) {
            case SUB_SCREEN -> new SubScreenClickGuiFactory();
            case SLIDER -> new SliderClickGuiFactory();
        };
    }

    private interface ClickGuiFactory {
        DrawableWidget createModuleGroupList(List<String> modules, ClickGuiMetaData meta);
    }

    private final class SliderClickGuiFactory implements ClickGuiFactory {
        @Override
        public DrawableWidget createModuleGroupList(List<String> modules, ClickGuiMetaData meta) {
            SubScreenWidget list = new SubScreenWidget(0, 0, 0, 0);
            for (var module : modules) {
                ModuleGroup group = HackModules.getModuleGroup(module);
                list.addDrawableChild(createModuleGroup(
                        module, group, meta.getModuleMeta(module), meta.getSliderGroupMeta(module), meta));
            }
            list.addDrawableChild(
                    createSearchGroup(meta, meta.getModuleMeta(SEARCH_MODULE), meta.getSliderGroupMeta(SEARCH_MODULE)));
            return list;
        }

        private int sliderWidth() {
            return Math.max(1, (int) sliderWidgetSize.get().x());
        }

        private int sliderHeight() {
            return Math.max(1, (int) sliderWidgetSize.get().y());
        }

        private DrawableWidget createModuleGroup(
                String module,
                ModuleGroup moduleGroup,
                ModuleSlideMeta slideMeta,
                SliderGroupMeta groupMeta,
                ClickGuiMetaData metaData) {
            DynamicSubScreenWidget group = new DynamicSubScreenWidget(
                    ValueAccessor.of(groupMeta::getX, groupMeta::setX),
                    ValueAccessor.of(groupMeta::getY, groupMeta::setY));
            group.addDrawableChild(createSliderGroupHeader(module, slideMeta, groupMeta));
            group.addDrawableChild(new DynamicContentWidget<>(
                    lazyInitialize(slideMeta::isSlidingDown, () -> createModuleList(moduleGroup, metaData)),
                    0,
                    sliderHeight()));
            return group;
        }

        private DrawableWidget createModuleList(ModuleGroup moduleGroup, ClickGuiMetaData metaData) {
            DynamicListWidget list = new DynamicListWidget(0, 0, sliderWidth());
            getShowModuleList(moduleGroup)
                    .forEach(module -> list.addDrawableChild(createSliderModule(module, metaData)));
            return list;
        }

        private DrawableWidget createSliderModule(BaseModule baseModule, ClickGuiMetaData metaData) {
            String metaKey = getSliderMetaKey(baseModule);
            metaData.checkSliderDefault(metaKey);
            ValueAccessor<Boolean> expanded = ValueAccessor.of(
                    () -> metaData.isSliderExpanded(metaKey), value -> metaData.setSliderExpanded(metaKey, value));
            FlagRef bindFlag = baseModule.getBindFlag();
            int width = sliderWidth();
            int height = sliderHeight();
            ElementHandler moduleElement = guiElementFactory.createButton(
                    TextProvider.of(Text.empty()),
                    TooltipHandler.of(getModuleButtonTooltips(baseModule)),
                    ButtonAction.isLeft(left -> {
                        if (ScreenUtils.hasShiftDown()) {
                            openConfigurateScreen(baseModule, metaData);
                            return;
                        }
                        if (left && bindFlag != null) {
                            bindFlag.toggle();
                            return;
                        }
                        expanded.setValue(!expanded.getValue());
                    }),
                    () -> false,
                    () -> bindFlag != null && bindFlag.get());
            DynamicListWidget module = new DynamicListWidget(0, 0, width);
            SubScreenWidget moduleRow = new SubScreenWidget(0, 0, width, height);
            moduleRow.addDrawableChild(
                    ExecutableWidget.instance(0, 0, width, height).setElementHandler(moduleElement));
            moduleRow.addDrawableChild(createSliderText(
                    TextProvider.of(getModuleName(baseModule), TextAlign.LEFT),
                    TooltipHandler.of(List.of()),
                    TextAlign.LEFT,
                    0,
                    0,
                    width - height,
                    height));
            moduleRow.addDrawableChild(createSliderText(
                    TextProvider.of(() -> Text.literal(expanded.getValue() ? "-" : "+"), TextAlign.RIGHT),
                    TooltipHandler.of(List.of()),
                    TextAlign.RIGHT,
                    width - height,
                    0,
                    height,
                    height));
            module.addDrawableChild(moduleRow);
            module.addDrawableChild(new DynamicContentWidget<>(
                    lazyInitialize(expanded::getValue, () -> createSliderModuleContent(baseModule, metaData)), 0, 0));
            return module;
        }

        private DrawableWidget createSliderModuleContent(BaseModule baseModule, ClickGuiMetaData metaData) {
            DynamicListWidget list = new DynamicListWidget(0, 0, sliderWidth());
            List<WrapperConfigRef<?>> editableConfigs = baseModule.getEditableConfig();
            Map<String, List<WrapperConfigRef<?>>> groupedConfigs = collectConfigSubGroups(editableConfigs);
            if (shouldUseConfigSubGroups(groupedConfigs)) {
                for (var entry : groupedConfigs.entrySet()) {
                    String prefix = entry.getKey();
                    if (shouldShowConfigSubGroupHead(prefix)) {
                        WidgetUtils.addConditionalWidgets(
                                list,
                                List.of(createSliderConfigSubGroup(baseModule, prefix, metaData, entry.getValue())));
                    } else {
                        addSliderConfigRows(list, entry.getValue());
                    }
                }
            } else {
                addSliderConfigRows(list, editableConfigs);
            }
            list.addDrawableChild(guiElementFactory.createExecuteButton(
                    () -> Text.translatable("widget.click-gui.open-full-config"),
                    () -> ChatUtils.parseTooltipsTranslation("widget.click-gui.open-full-config.tooltips", ""),
                    ButtonAction.run(() -> openConfigurateScreen(baseModule, metaData)),
                    0,
                    0,
                    sliderWidth(),
                    sliderHeight()));
            return list;
        }

        private ConditionalWidget createSliderConfigSubGroup(
                BaseModule baseModule, String prefix, ClickGuiMetaData metaData, List<WrapperConfigRef<?>> configs) {
            String metaKey = getConfigSubGroupMetaKey(baseModule, prefix);
            metaData.checkSubGroupDefault(metaKey);
            DynamicListWidget group = new DynamicListWidget(0, 0, sliderWidth());
            group.addDrawableChild(createSliderHeader(
                    getConfigSubGroupTitle(prefix),
                    () -> metaData.isSubGroupExpanded(metaKey),
                    () -> metaData.setSubGroupExpanded(metaKey, !metaData.isSubGroupExpanded(metaKey))));
            group.addDrawableChild(new DynamicContentWidget<>(
                    lazyInitialize(() -> metaData.isSubGroupExpanded(metaKey), () -> createSliderConfigRows(configs)),
                    0,
                    0));
            return new ConditionalWidget(() -> configs.stream().anyMatch(WrapperConfigRef::shouldShow), group);
        }

        private DrawableWidget createSliderConfigRows(List<WrapperConfigRef<?>> configs) {
            DynamicListWidget list = new DynamicListWidget(0, 0, sliderWidth());
            addSliderConfigRows(list, configs);
            return list;
        }

        private void addSliderConfigRows(DynamicListWidget list, List<WrapperConfigRef<?>> configs) {
            WidgetUtils.addConditionalWidgets(
                    list, configs.stream().map(this::createSliderConfigRow).toList());
        }

        private DrawableWidget createSliderText(
                TextProvider provider, TooltipHandler handler, TextAlign align, int x, int y, int width, int height) {
            return createSliderText(provider, handler, align, x, y, width, height, false);
        }

        private DrawableWidget createSliderText(
                TextProvider provider,
                TooltipHandler handler,
                TextAlign align,
                int x,
                int y,
                int width,
                int height,
                boolean splitter) {
            ElementHandler text = guiElementFactory.createText(provider, handler, splitter, align);
            return ExecutableWidget.instance(x, y, width, height).setElementHandler(text);
        }

        private DrawableWidget createSliderConfigText(
                TextProvider provider, TooltipHandler handler, TextAlign align, int x, int y, int width, int height) {
            return createSliderText(provider, handler, align, x, y + height / 6, width, height * 2 / 3, false);
        }

        private ConditionalWidget createSliderConfigRow(WrapperConfigRef<?> configWidget) {
            int width = sliderWidth();
            int height = sliderHeight();
            Ref<?> ref = configWidget.ref();
            DynamicListWidget row = new DynamicListWidget(0, 0, width);
            TextProvider key = TextProvider.of(
                    () -> Text.translatableWithFallback(configWidget.keyName(), configWidget.keyName()),
                    TextAlign.LEFT);
            if (ref instanceof FlagRef) {
                var keyValue = configWidget.createKeyValue();
                SubScreenWidget line = createSliderConfigLine(
                        key,
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(configWidget.keyName() + ".tooltips", "")),
                        keyValue.generateValueWidget(BaseModule.CONFIG_FACTORY, width - height, 0, height, height),
                        width,
                        height);
                row.addDrawableChild(line);
            } else if (ref instanceof IntRef
                    || ref instanceof DoubleRef
                    || ref instanceof FloatRef
                    || ref instanceof LongRef) {
                ValueAccessor<Boolean> editorOpen = ValueAccessor.holder(false);
                row.addDrawableChild(createSliderConfigLine(
                        key,
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(configWidget.keyName() + ".tooltips", "")),
                        createSliderConfigText(
                                TextProvider.of(() -> createSliderSpecialDisplay(ref), TextAlign.RIGHT),
                                TooltipHandler.of(List.of()),
                                TextAlign.RIGHT,
                                0,
                                0,
                                width,
                                height),
                        width,
                        height,
                        () -> editorOpen.setValue(!editorOpen.getValue())));
                row.addDrawableChild(new DynamicContentWidget<>(
                        lazyInitialize(editorOpen::getValue, () -> createSliderEditor(configWidget)), 0, 0));
            } else {
                ValueAccessor<Boolean> editorOpen = ValueAccessor.holder(false);
                SubScreenWidget line = createSliderConfigLine(
                        key,
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(configWidget.keyName() + ".tooltips", "")),
                        getSpecificTypeSprite(ref, width, height),
                        width,
                        height,
                        () -> editorOpen.setValue(!editorOpen.getValue()));
                row.addDrawableChild(line);
                row.addDrawableChild(new DynamicContentWidget<>(
                        lazyInitialize(editorOpen::getValue, () -> createSliderEditor(configWidget)), 0, 0));
            }
            return new ConditionalWidget(configWidget::shouldShow, row);
        }

        private DrawableWidget getSpecificTypeSprite(Ref<?> ref, int width, int height) {
            if (ref instanceof ListRef || ref instanceof MapRef) {
                return guiElementFactory.createElement(
                        IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.empty()),
                        null,
                        null,
                        width - height,
                        0,
                        height,
                        height);
            }
            if (ref instanceof NBTRef) {
                return guiElementFactory.createElement(
                        IconElement.fixedGui(Constants.FORMATTING_TEXTURE_SPRITE, ButtonAction.empty()),
                        null,
                        null,
                        width - height,
                        0,
                        height,
                        height);
            }
            return guiElementFactory.createElement(
                    IconElement.fixedGui(Constants.EDIT_SPRITE, ButtonAction.empty()),
                    null,
                    null,
                    width - height + height / 4,
                    height / 4,
                    height / 2,
                    height / 2);
        }

        private SubScreenWidget createSliderConfigLine(
                TextProvider key, TooltipHandler handler, DrawableWidget value, int width, int height) {
            return createSliderConfigLine(key, handler, value, width, height, null);
        }

        private SubScreenWidget createSliderConfigLine(
                TextProvider key,
                TooltipHandler handler,
                DrawableWidget value,
                int width,
                int height,
                Runnable clickAction) {
            SubScreenWidget line = new SubScreenWidget(0, 0, width, height);
            if (clickAction != null) {
                line.addDrawableChild(ExecutableWidget.instance(0, 0, width, height)
                        .setElementHandler(new AbstractElement().withInputHandler(InputHandler.clickRun(clickAction))));
            }
            line.addDrawableChild(guiElementFactory.createBackground(0, 0, width, height))
                    .addDrawableChild(
                            createSliderConfigText(key, handler, TextAlign.LEFT, 0, 0, width - height, height))
                    .addDrawableChild(value);

            return line;
        }

        private Text createSliderSpecialDisplay(Ref<?> ref) {
            if (ref instanceof IntRef intRef) {
                return Text.literal(String.valueOf(intRef.get()));
            }
            if (ref instanceof DoubleRef doubleRef) {
                return Text.literal(String.valueOf(doubleRef.get()));
            }
            if (ref instanceof EnumRef<?> enumRef) {
                if (!enumRef.resolved) {
                    return Text.literal(enumRef.enumType + ":" + enumRef.enumValue);
                }
                ConfigEnum value = enumRef.get();
                return value.getDisplay();
            }
            if (ref instanceof KeyBindRef keyBindRef) {
                MultiKeyBind keyBind = keyBindRef.get();
                List<String> keyCodes =
                        keyBind == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(keyBind.getKeys()));
                String context = keyCodes.isEmpty() ? "None" : String.join(",", keyCodes);
                return Text.literal(context);
            }
            if (ref instanceof ListRef listRef) {
                return Text.literal(String.join(", ", listRef.get()));
            }
            if (ref instanceof MapRef mapRef) {
                return Text.literal(String.valueOf(mapRef.getValue()));
            }
            if (ref instanceof ObjectRef<?> objectRef) {
                return Text.literal(String.valueOf(objectRef.get()));
            }
            throw new IllegalArgumentException(
                    "Unsupported Slider config type: " + ref.getClass().getName());
        }

        private DrawableWidget createSliderEditor(WrapperConfigRef<?> configWidget) {
            try {
                AttrKeyValue keyValue = configWidget.createKeyValue();
                return keyValue.generateValueWidget(BaseModule.CONFIG_FACTORY, 0, 0, sliderWidth(), sliderHeight());
            } catch (UnsupportedOperationException | IllegalStateException exception) {
                return new SubScreenWidget(0, 0, sliderWidth(), sliderHeight());
            }
        }

        private DrawableWidget createSliderGroupHeader(
                String module, ModuleSlideMeta slideMeta, SliderGroupMeta groupMeta) {
            int width = sliderWidth();
            int height = sliderHeight();
            return ExecutableWidget.instance(0, 0, width, height)
                    .setElementHandler(new AbstractElement()
                            .withInputHandler(new InputHandler() {
                                double startMouseX;
                                double startMouseY;
                                boolean move;

                                @Override
                                public boolean onClick(
                                        ExecutableWidget element, double mouseX, double mouseY, int button) {
                                    return true;
                                }

                                @Override
                                public boolean onAction(
                                        ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                                    if (type == Type.MOUSE_START_DRAG) {
                                        startMouseX = mouseX;
                                        startMouseY = mouseY;
                                        move = false;
                                        return element.isMouseOver(mouseX, mouseY);
                                    }
                                    if (type == Type.MOUSE_DRAG) {
                                        int currentX = groupMeta.getX();
                                        int currentY = groupMeta.getY();
                                        int nextX = (int) Math.round(currentX + mouseX - startMouseX);
                                        int nextY = (int) Math.round(currentY + mouseY - startMouseY);
                                        if (nextX != currentX || nextY != currentY) {
                                            groupMeta.setX(nextX);
                                            groupMeta.setY(nextY);
                                            move = true;
                                        }
                                        return true;
                                    }
                                    if ((type == Type.MOUSE_RELEASE && button == 0)
                                            || (type == Type.MOUSE_CLICK && button != 0)) {
                                        if (!move && element.isMouseOver(mouseX, mouseY)) {
                                            slideMeta.slidingDown = !slideMeta.slidingDown;
                                        }
                                        move = false;
                                        return true;
                                    }
                                    return true;
                                }
                            })
                            .combineRender(guiElementFactory.createTitle(
                                    TextProvider.of(Text.translatableWithFallback(
                                            "widget.click-gui.module-group-name." + module, module)),
                                    TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                            "widget.click-gui.module-group.header.tooltips", ""))))
                            .combineRender(((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                                context.setShaderColor(
                                        textColor().withAlpha(255).color());
                                context.drawGuiTexture(
                                        slideMeta.slidingDown
                                                ? Constants.EXPAND_GUI_ON_SPRITE
                                                : Constants.EXPAND_GUI_OFF_SPRITE,
                                        element.getTextureWidth() - element.getTextureHeight() + 2,
                                        2,
                                        0,
                                        element.getTextureHeight() - 4,
                                        element.getTextureHeight() - 4);
                                context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                            })));
        }

        private DrawableWidget createSliderHeader(Text title, BooleanSupplier expanded, Runnable toggle) {
            int width = sliderWidth();
            int height = sliderHeight();
            int textWidth = Math.max(1, width - height);
            int textY = height / 6;
            int textHeight = height * 2 / 3;
            SubScreenWidget header = new SubScreenWidget(0, 0, width, height);
            header.addDrawableChild(guiElementFactory.createBackground(0, 0, width, height));
            header.addDrawableChild(ExecutableWidget.instance(0, 0, width, height)
                    .setElementHandler(new AbstractElement().withInputHandler(InputHandler.clickRun(toggle))));
            header.addDrawableChild(createSliderText(
                    TextProvider.of(title),
                    TooltipHandler.of(List.of()),
                    TextAlign.MIDDLE,
                    0,
                    textY,
                    textWidth,
                    textHeight,
                    true));
            header.addDrawableChild(ExecutableWidget.instance(width - height, 0, height, height)
                    .setElementHandler(new AbstractElement()
                            .withInputHandler(InputHandler.clickRun(toggle))
                            .combineRender((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                                context.setShaderColor(
                                        textColor().withAlpha(255).color());
                                context.drawGuiTexture(
                                        expanded.getAsBoolean()
                                                ? Constants.EXPAND_GUI_ON_SPRITE
                                                : Constants.EXPAND_GUI_OFF_SPRITE,
                                        2,
                                        2,
                                        0,
                                        Math.max(1, element.getTextureWidth() - 4),
                                        Math.max(1, element.getTextureHeight() - 4));
                                context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                            })));
            return header;
        }

        private DrawableWidget createSearchGroup(
                ClickGuiMetaData metaData, ModuleSlideMeta slideMeta, SliderGroupMeta groupMeta) {
            DynamicSubScreenWidget group = new DynamicSubScreenWidget(
                    ValueAccessor.of(groupMeta::getX, groupMeta::setX),
                    ValueAccessor.of(groupMeta::getY, groupMeta::setY));
            group.addDrawableChild(createSliderGroupHeader(SEARCH_MODULE, slideMeta, groupMeta));
            group.addDrawableChild(new DynamicContentWidget<>(
                    lazyInitialize(slideMeta::isSlidingDown, () -> createSearchContent(metaData)), 0, sliderHeight()));
            return group;
        }

        private DrawableWidget createSearchContent(ClickGuiMetaData metaData) {
            int width = sliderWidth();
            int height = sliderHeight();
            DynamicListWidget content = new DynamicListWidget(0, 0, width);
            DynamicListWidget results = new DynamicListWidget(0, 0, width);
            Value<String> searchValue = new Value<>() {
                @Override
                public String getInput() {
                    return metaData.searching;
                }

                @Override
                public String get() {
                    return metaData.searching;
                }

                @Override
                public boolean setOriginValue(String value) {
                    setInput(value);
                    return true;
                }

                @Override
                public boolean isValueValid(String value) {
                    return value != null;
                }

                @Override
                public boolean isValidate() {
                    return true;
                }

                @Override
                public String toInput(String value) {
                    return value == null ? "" : value;
                }

                @Override
                public void setInput(String value) {
                    metaData.setSearching(value == null ? "" : value);
                    refreshSearchResults(results, metaData);
                }
            };
            content.addDrawableChild(ExecutableWidget.instance(0, 0, width, height)
                    .setElementHandler(guiElementFactory.createInput(searchValue, TextAlign.LEFT)));
            content.addDrawableChild(results);
            refreshSearchResults(results, metaData);
            return content;
        }

        private void refreshSearchResults(DynamicListWidget results, ClickGuiMetaData metaData) {
            results.clearChildren();
            String filter = metaData.searching;
            if (filter == null || filter.isEmpty()) {
                return;
            }
            Set<BaseModule> matched = new LinkedHashSet<>();
            for (ModuleGroup group : HackModules.getModuleGroups()) {
                getShowModuleList(group).forEach(module -> {
                    String moduleName = module.getName();
                    String translatedName = ChatUtils.textToPlainString(getModuleName(module));
                    boolean matchedName = FilterService.nameMatch(moduleName, filter)
                            || (!Objects.equals(moduleName, translatedName)
                                    && FilterService.nameMatch(translatedName, filter));
                    boolean matchedSetting = module.getEditableConfig().stream()
                            .anyMatch(config ->
                                    FilterService.nameMatch(ChatUtils.parseTranslation(config.keyName()), filter));
                    if (matchedName || matchedSetting) {
                        matched.add(module);
                    }
                });
            }
            matched.forEach(module -> results.addDrawableChild(createSliderModule(module, metaData)));
        }

        private <T extends DrawableWidget> Supplier<T> lazyInitialize(
                BooleanSupplier condition, Supplier<T> initializer) {
            return new Supplier<>() {
                private T value;

                @Override
                public T get() {
                    if (!condition.getAsBoolean()) {
                        return null;
                    }
                    if (value == null) {
                        value = initializer.get();
                    }
                    return value;
                }
            };
        }
    }

    private final class SubScreenClickGuiFactory implements ClickGuiFactory {
        private final SerialExecutor taskExecutor = new SerialExecutor(CompletableFuture::runAsync);

        @Override
        public DrawableWidget createModuleGroupList(List<String> modules, ClickGuiMetaData meta) {
            SubScreenWidget subScreen = new SubScreenWidget(0, 0, 0, 0);
            for (var re : modules) {
                ModuleGroup group = HackModules.getModuleGroup(re);
                ModuleSlideMeta groupMeta = meta.getModuleMeta(re);
                subScreen.addDrawableChild(createModuleGroup(re, group, groupMeta, meta));
            }
            subScreen.addDrawableChild(createSearchList(meta, meta.getModuleMeta(SEARCH_MODULE)));
            return subScreen;
        }

        private DrawableWidget createModuleGroup(
                String module, ModuleGroup moduleGroup, ModuleSlideMeta slideMeta, ClickGuiMetaData metaData) {
            SubScreenWidget subScreen = createModuleListHolder(slideMeta);
            DrawableWidget expandHead = createDragExpandableHead(module, slideMeta);
            subScreen.addDrawableChild(expandHead);
            SubScreenWidget moduleList = createModuleList(moduleGroup, metaData);
            subScreen.addDrawableChild(new DynamicContentWidget<>(
                    () -> (slideMeta.slidingDown ? moduleList : null), 0, expandHead.getHeight()));
            return subScreen;
        }

        private SubScreenWidget createModuleListHolder(ModuleSlideMeta slideMeta) {
            return new DynamicSubScreenWidget(
                    ValueAccessor.of(slideMeta::getX, slideMeta::setX),
                    ValueAccessor.of(slideMeta::getY, slideMeta::setY));
        }

        private SubScreenWidget createModuleList(ModuleGroup moduleGroup, ClickGuiMetaData metaData) {
            return createModuleList(getShowModuleList(moduleGroup).toList(), metaData);
        }

        private SubScreenWidget createModuleList(Collection<BaseModule> baseModules, ClickGuiMetaData metaData) {
            SubScreenWidget subScreen = new SubScreenWidget(0, 0, 0, 0);
            int yLevel = 0;
            for (var entry : baseModules) {
                DrawableWidget widget = createClickableModuleWidget(entry, metaData);
                subScreen.addDrawableChild(new ContentDelegateWidget<>(0, yLevel, 0, 0).setContentDelegate(widget));
                yLevel += widget.getHeight();
            }
            return subScreen;
        }

        private DrawableWidget createClickableModuleWidget(BaseModule baseModule, ClickGuiMetaData metaData) {
            FlagRef bindFlag = baseModule.getBindFlag();
            return ExecutableWidget.instance(0, 0, (int) widgetSize.get().x(), (int)
                            widgetSize.get().y())
                    .setElementHandler(guiElementFactory.createButton(
                            TextProvider.of(getModuleName(baseModule)),
                            TooltipHandler.of(getModuleButtonTooltips(baseModule)),
                            bindFlag != null
                                    ? ButtonAction.isLeft((bl) -> {
                                        if (bl) {
                                            bindFlag.toggle();
                                        } else {
                                            openConfigurateScreen(baseModule, metaData);
                                        }
                                    })
                                    : ButtonAction.run(() -> openConfigurateScreen(baseModule, metaData)),
                            () -> true,
                            () -> bindFlag != null && bindFlag.get()));
        }

        private DrawableWidget createSearchList(ClickGuiMetaData metaData, ModuleSlideMeta slideMeta) {
            SubScreenWidget subScreen = createModuleListHolder(slideMeta);
            DrawableWidget expandHead = createDragExpandableHead(SEARCH_MODULE, slideMeta);
            subScreen.addDrawableChild(expandHead);
            DrawableWidget searchContent = createSearchListContent(metaData);
            subScreen.addDrawableChild(new DynamicContentWidget<>(
                    () -> (slideMeta.slidingDown ? searchContent : null), 0, expandHead.getHeight()));
            return subScreen;
        }

        private DrawableWidget createSearchListContent(ClickGuiMetaData metaData) {
            SubScreenWidget subScreen = new SubScreenWidget(0, 0, 0, 0);
            int buttonWidth = (int) widgetSize.get().x();
            int buttonHeight = (int) widgetSize.get().y();
            DynamicListWidget listWidget = new DynamicListWidget(0, buttonHeight, buttonWidth);
            subScreen.addDrawableChild(listWidget);
            Runnable updateTask = () -> {
                String filter = metaData.searching;
                List<BaseModule> moduleFilter = new ArrayList<>();
                List<BaseModule> settingsFilter = new ArrayList<>();
                if (filter != null && !filter.isEmpty()) {
                    for (var group : HackModules.getModuleGroups()) {
                        getShowModuleList(group).forEach(module -> {
                            String moduleName = module.getName();
                            String moduleTranslationName = ChatUtils.textToPlainString(getModuleName(module));
                            if (FilterService.nameMatch(moduleName, filter)
                                    || (!Objects.equals(moduleName, moduleTranslationName)
                                            && FilterService.nameMatch(moduleTranslationName, filter))) {
                                moduleFilter.add(module);
                            }
                            if (module.getEditableConfig().stream().anyMatch((editable) -> {
                                String settingsName = ChatUtils.parseTranslation(editable.keyName());
                                return FilterService.nameMatch(settingsName, filter);
                            })) {
                                settingsFilter.add(module);
                            }
                        });
                    }
                    mc.execute(() -> {
                        listWidget.clearChildren();
                        createSearchResultGroupSubList(listWidget::addDrawableChild, "Name", moduleFilter, metaData);
                        createSearchResultGroupSubList(
                                listWidget::addDrawableChild, "Setting", settingsFilter, metaData);
                    });
                } else {
                    mc.execute(listWidget::clearChildren);
                }
            };

            Value<String> searchValue = new Value<>() {
                @Override
                public String getInput() {
                    return metaData.searching;
                }

                @Override
                public String get() {
                    return metaData.searching;
                }

                @Override
                public boolean setOriginValue(String value) {
                    setInput(value);
                    return true;
                }

                @Override
                public boolean isValueValid(String value) {
                    return value != null;
                }

                @Override
                public boolean isValidate() {
                    return true;
                }

                @Override
                public String toInput(String value) {
                    return value == null ? "" : value;
                }

                @Override
                public void setInput(String value) {
                    String next = value == null ? "" : value;
                    if (!Objects.equals(next, metaData.searching)) {
                        metaData.setSearching(next);
                        taskExecutor.submit(updateTask);
                    }
                }
            };
            updateTask.run();
            subScreen.addDrawableChild(guiElementFactory.createInput(searchValue, 0, 0, buttonWidth, buttonHeight));
            return subScreen;
        }

        private void createSearchResultGroupSubList(
                Consumer<DrawableWidget> childrenAdder,
                String group,
                List<BaseModule> list,
                ClickGuiMetaData metaData) {
            MutableBoolean showFlag = new MutableBoolean(true);
            int buttonWidth = (int) widgetSize.get().x();
            int buttonHeight = (int) widgetSize.get().y();
            SubScreenWidget subScreen = new SubScreenWidget(0, 0, buttonWidth, buttonHeight);
            ExecutableWidget.instance(0, 0, buttonWidth, buttonHeight)
                    .setElementHandler(new AbstractElement()
                            .withInputHandler(InputHandler.clickRun(() -> showFlag.setValue(!showFlag.booleanValue()))))
                    .addToSub(subScreen);
            guiElementFactory
                    .createBackground(null, 0, 0, buttonWidth - buttonHeight, buttonHeight)
                    .addToSub(subScreen);
            guiElementFactory
                    .createText(
                            TextProvider.of(Text.literal(group)),
                            TooltipHandler.of(List.of()),
                            0,
                            0,
                            buttonWidth - buttonHeight,
                            buttonHeight,
                            true)
                    .addToSub(subScreen);

            ExecutableWidget.instance(buttonWidth - buttonHeight, 0, buttonHeight, buttonHeight)
                    .setRenderHandler(new AbstractElement()
                            .combineRender(guiElementFactory.createBackground())
                            .combineRender(((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                                context.setShaderColor(
                                        textColor().withAlpha(255).color());
                                context.drawGuiTexture(
                                        showFlag.booleanValue()
                                                ? Constants.EXPAND_GUI_ON_SPRITE
                                                : Constants.EXPAND_GUI_OFF_SPRITE,
                                        element.getTextureWidth() - element.getTextureHeight() + 2,
                                        2,
                                        0,
                                        element.getTextureHeight() - 4,
                                        element.getTextureHeight() - 4);
                                context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                            })))
                    .addToSub(subScreen);
            childrenAdder.accept(subScreen);
            var re = createModuleList(list, metaData);
            re.refreshScreenSize();
            DynamicContentWidget<?> dynamic =
                    new DynamicContentWidget<>(() -> showFlag.booleanValue() ? re : null, 0, 0);
            childrenAdder.accept(dynamic);
        }

        private DrawableWidget createDragExpandableHead(String module, ModuleSlideMeta slideMeta) {
            return ExecutableWidget.instance(0, 0, (int) widgetSize.get().x(), (int)
                            widgetSize.get().y())
                    .setElementHandler(new AbstractElement()
                            .withInputHandler(new InputHandler() {
                                @Override
                                public boolean onClick(
                                        ExecutableWidget element, double mouseX, double mouseY, int button) {
                                    return true;
                                }

                                double startMouseX;
                                double startMouseY;
                                boolean move = false;

                                @Override
                                public boolean onAction(
                                        ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                                    if (type == Type.MOUSE_START_DRAG) {
                                        if (element.isMouseOver(mouseX, mouseY)) {
                                            startMouseX = mouseX;
                                            startMouseY = mouseY;
                                            move = false;
                                            return true;
                                        }
                                        return false;
                                    }
                                    if (type == Type.MOUSE_DRAG) {
                                        if (Math.abs(mouseX - startMouseX) >= 1
                                                || Math.abs(mouseY - startMouseY) >= 1) {
                                            int deltaX = (int) (mouseX - startMouseX);
                                            int deltaY = (int) (mouseY - startMouseY);
                                            slideMeta.setX(slideMeta.getX() + deltaX);
                                            slideMeta.setY(slideMeta.getY() + deltaY);
                                            move = true;
                                        }
                                        return true;
                                    }
                                    if ((type == Type.MOUSE_RELEASE && button == 0)
                                            || (type == Type.MOUSE_CLICK && button != 0)) {
                                        if (!move) {
                                            if (element.isMouseOver(mouseX, mouseY)) {
                                                slideMeta.slidingDown = !slideMeta.slidingDown;
                                            }
                                        } else {
                                            move = false;
                                        }
                                        return true;
                                    }
                                    return true;
                                }
                            })
                            .combineRender(guiElementFactory.createTitle(
                                    TextProvider.of(Text.translatableWithFallback(
                                            "widget.click-gui.module-group-name." + module, module)),
                                    TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                            "widget.click-gui.module-group.header.tooltips", ""))))
                            .combineRender(((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                                context.setShaderColor(
                                        textColor().withAlpha(255).color());
                                context.drawGuiTexture(
                                        slideMeta.slidingDown
                                                ? Constants.EXPAND_GUI_ON_SPRITE
                                                : Constants.EXPAND_GUI_OFF_SPRITE,
                                        element.getTextureWidth() - element.getTextureHeight() + 2,
                                        2,
                                        0,
                                        element.getTextureHeight() - 4,
                                        element.getTextureHeight() - 4);
                                context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                            })));
        }
    }

    @Override
    public WrapRGBA activeFrameColor() {
        return clickGuiConfig.get().activeFrameColor();
    }

    @Override
    public WrapRGBA inactiveFrameColor() {
        return clickGuiConfig.get().inactiveFrameColor();
    }

    @Override
    public WrapRGBA backgroundColor() {
        return clickGuiConfig.get().backgroundColor();
    }

    @Override
    public WrapRGBA plateColor() {
        return clickGuiConfig.get().plateColor();
    }

    @Override
    public WrapRGBA titleColor() {
        return clickGuiConfig.get().titleColor();
    }

    @Override
    public WrapRGBA labelColor() {
        return clickGuiConfig.get().labelColor();
    }

    @Override
    public WrapRGBA buttonActiveColor() {
        return clickGuiConfig.get().buttonActiveColor();
    }

    @Override
    public WrapRGBA buttonInactiveColor() {
        return clickGuiConfig.get().buttonInactiveColor();
    }

    @Override
    public WrapRGBA textColor() {
        return clickGuiConfig.get().textColor();
    }

    @Override
    public WrapRGBA textBackground() {
        return clickGuiConfig.get().textBackground();
    }

    @Override
    public WrapRGBA sliderColor() {
        return clickGuiConfig.get().sliderColor();
    }

    public ClickGui() {
        super("ClickGui");
        INSTANCE = this;
    }

    public final ModulePath hotkeys = makePath(Configs.MISC_CONFIG, "hotkeys");
    public final ModulePath config = makePath(Configs.MISC_CONFIG, "hotkeys");
    public ModulePath clickGui = makePath(Configs.MISC_CONFIG, "click-gui");
    public final KeyBindRef keyBindConfigScreen = hotkey(hotkeys.add("open-menu"))
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_G))
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::openConfigMenu))
            .build();

    public final KeyBindRef optionsKeyBind = hotkey(hotkeys.add("open-options-menu"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::openGameOptionsMenu))
            .build();

    public KeyBindRef keyBindClickGui = hotkey(clickGui.add("hotkey"))
            .defaultValue(new MultiKeyBind(KeyCode.KEY_RIGHT_ALT))
            .registerHotkey(this::onHotkey)
            .build();

    public final EnumRef<ClickGuiStyle> guiStyle = builder(clickGui.add("click-gui-style"), ClickGuiStyle.class)
            .defaultValue(ClickGuiStyle.SUB_SCREEN)
            .build();

    public NBTRef<Vec2> widgetSize = builder(clickGui.add("widget-size"), Vec2.class)
            .defaultValue(new Vec2(55, 12))
            .show(() -> guiStyle.get().isIn(ClickGuiStyle.SUB_SCREEN))
            .build();

    public NBTRef<Vec2> sliderWidgetSize = builder(clickGui.add("slider-widget-size"), Vec2.class)
            .defaultValue(new Vec2(70, 12))
            .show(() -> guiStyle.get().isIn(ClickGuiStyle.SLIDER))
            .build();

    public FileStorage internalGuiData = FileManager.getInstance().getInternalStorage("click-gui-data.nbt");

    public final FlagRef enableConfigSubGroup = builder(clickGui.add("enable-config-subgroup"), Boolean.class)
            .defaultValue(true)
            .build();

    public final ModulePath configType = clickGui.add("config-editor-style");

    public final NBTRef<GuiSetting> configGuiConfig = builder(configType.add("setting"), GuiSetting.class)
            .defaultValue(GuiSetting.DEFAULT)
            .build();

    public final ModulePath widgetType = clickGui.add("widget-style");

    public final NBTRef<GuiSetting> widgetGuiConfig = builder(widgetType.add("setting"), GuiSetting.class)
            .defaultValue(GuiSetting.DEFAULT)
            .show(() -> guiStyle.get() == ClickGuiStyle.SUB_SCREEN)
            .build();

    public final ModulePath sliderWidgetType = clickGui.add("slider-widget-style");

    public final NBTRef<GuiSetting> sliderWidgetGuiConfig = builder(sliderWidgetType.add("setting"), GuiSetting.class)
            .defaultValue(GuiSetting.SLIDER_DEFAULT)
            .show(() -> guiStyle.get() == ClickGuiStyle.SLIDER)
            .build();

    private final CustomElementFactory guiElementFactory = new CustomElementFactory(
            () -> guiStyle.get() == ClickGuiStyle.SLIDER ? sliderWidgetGuiConfig.get() : widgetGuiConfig.get());

    private boolean onHotkey(IHotKey iHotKey, IInputManager manager) {
        if (HotKeyUtils.isValidState()) {
            openClickGui();
            return true;
        } else if (mc.currentScreen instanceof ClickGuiMainScreen gui) {
            gui.close();
            return true;
        } else return false;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPostInitializeScreen().getChannel(MultiplayerScreen.class), this::onScreenInitialize);
    }

    public void openConfigScreen(Config config) {
        MainTasks.openConfigScreen(config);
    }

    public void openGameOptionsMenu() {
        GameOptions options = mc.options;
        List<SimpleOption<?>> options1 = new ArrayList<>();
        for (var field : GameOptions.class.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getType().isAssignableFrom(SimpleOption.class)) {
                try {
                    SimpleOption<?> option = (SimpleOption<?>) field.get(options);
                    if (option != null) {
                        options1.add(option);
                    }
                } catch (Throwable e) {

                }
            }
        }
        ScrollableListWidget widget = new ScrollableListWidget(20, 20, 360, 280);
        int yLevel = 0;
        for (var sim : options1) {
            var re = sim.createWidget(mc.options);
            widget.addScrollingWidget(new ContentDelegateWidget<>(20, yLevel, 320, 40).setContentDelegate(re));
            //                SubScreenWidget.instance(20, 0 , 320, 40)
            //                    .addDrawableChild(
            ////                        DisplayWidget.instance(0,0, 150, 40)
            ////                            .setRenderHandler(
            ////                                new ButtonElement(TextProvider.of(sim.))
            ////                            )
            //                    )
            //            )
            yLevel += re.getHeight();
        }
        SimpleScreen screen = new SimpleScreen(Text.literal("Options Screen"), 400, 320, widget);

        ScreenAccess.of(screen).openFromCurrent();
    }

    public void openConfigMenu() {
        MainTasks.openConfigNewStyleScreen();
    }

    private WeakReference<ContentDelegateWidget<ExecutableWidget>> delegateWidget = null;

    public void onScreenInitialize(Event<MultiplayerScreen> screenEvent) {
        if (screenEvent.context() instanceof MultiplayerScreen mp) {
            // todo: add
            ExecutableWidget executableWidget = ExecutableWidget.instance(0, 0, 100, 20)
                    .setElementHandler(VanillaElementFactory.INSTANCE.createButton(
                            TextProvider.of(Text.literal("SlimefunHelper")),
                            TooltipHandler.of(List.of()),
                            ButtonAction.run(this::openClickGui),
                            () -> true,
                            () -> false));
            if (delegateWidget != null && delegateWidget.get() != null) {
                ScreenAccess.of(mp).removeChildFrom(delegateWidget.get());
            }
            delegateWidget = null;
            ContentDelegateWidget<ExecutableWidget> dynamicWidget =
                    new ContentDelegateWidget<>(mp.width - 100, 5, 0, 0);
            dynamicWidget.setContentDelegate(executableWidget);
            dynamicWidget.addTo(mp);
            this.delegateWidget = new WeakReference<>(dynamicWidget);
        }
    }

    public void resetGui() {
        if (mc.currentScreen instanceof ClickGuiMainScreen guiMain) {
            guiMain.close();
        }
        internalGuiData.write(new NbtCompound(), NbtOps.INSTANCE);
    }

    public List<String> getModules() {
        return new ArrayList<>(HackModules.main.getModuleGroups().keySet());
    }

    private static final String SEARCH_MODULE = "Search";

    public ClickGuiMetaData getClickGuiMetadata() {
        NbtCompound data = internalGuiData.asReadOnly(NbtOps.INSTANCE);
        var result = ClickGuiMetaData.CODEC.decode(NbtOps.INSTANCE, data);
        ClickGuiMetaData meta;
        if (result.isSuccess()) {
            meta = result.getOrThrow().getFirst();
        } else {
            meta = new ClickGuiMetaData();
        }
        List<String> modules = getModules();
        modules.add(SEARCH_MODULE);
        meta.checkDefault(
                modules, (int) widgetSize.get().x(), (int) widgetSize.get().y());
        meta.checkSliderGroupDefault(modules, (int) sliderWidgetSize.get().x(), (int)
                sliderWidgetSize.get().y());

        setClickGuiMeta(meta);
        return meta;
    }

    public void setClickGuiMeta(ClickGuiMetaData meta) {
        NbtElement element =
                ClickGuiMetaData.CODEC.encodeStart(NbtOps.INSTANCE, meta).getOrThrow();
        internalGuiData.write(element, NbtOps.INSTANCE);
    }

    public static final int DEFAULT_GAP = 5;
    public static final int DEFAULT_Y = 40;

    public void openClickGui() {
        List<String> modules = getModules();
        ClickGuiMetaData meta = getClickGuiMetadata();
        Map<String, Function<Screen, DrawableWidget>> selections = new LinkedHashMap<>();
        selections.put("Module", (s) -> createClickGuiFactory(guiStyle.get()).createModuleGroupList(modules, meta));
        selections.put("Friends", (s) -> this.createFriendSettings(s, meta));
        selections.put("CmdMacros", (s) -> this.createCmdMacrosSettings(s, meta));
        selections.put("Hotkeys", (s) -> this.createKeyBindListSettings(s, meta));
        selections.put("BaseSettings", (s) -> this.createBaseSettings(s, meta));
        selections.put("GuiSettings", (s) -> this.createGuiSettings(s, meta));
        selections.put("Font", (s) -> this.createFontSettings(s, meta));
        selections.put("Config", (s) -> this.createConfig(meta));
        if (BaritoneHooks.getInstance().isBaritoneAPISupported()) {
            selections.put("Baritone", (s) -> this.createBaritoneScreen(s, meta));
        }
        Screen screen = new ClickGuiMainScreen(this, selections);
        // add save when close
        ScreenAccess.of(screen).addCloseFuture(() -> setClickGuiMeta(meta));
        ScreenAccess.of(screen).openFromCurrent();
    }

    public Stream<BaseModule> getShowModuleList(ModuleGroup group) {
        return group.getModules().stream().filter(BaseModule::shouldShowInGui);
    }

    public Text getModuleName(BaseModule baseModule) {
        return Text.translatableWithFallback(
                "widget.click-gui.module-name." + baseModule.getModuleManager().getName() + "." + baseModule.getName(),
                baseModule.getName());
    }

    private static final List<Text> TOOLTIP_HAS_BIND = List.of(Text.literal("左键切换模块是否启用"), Text.literal("右键打开模块配置界面"));
    private static final List<Text> TOOLTIPS_NO_BIND = List.of(Text.literal("点击打开模块配置界面"));

    public List<Text> getModuleButtonTooltips(BaseModule baseModule) {
        List<Text> texts = new ArrayList<>(ChatUtils.parseTooltipsTranslation(
                "widget.click-gui.module-name." + baseModule.getModuleManager().getName() + "." + baseModule.getName()
                        + ".tooltips",
                ""));
        if (!texts.isEmpty()) {
            texts.add(Text.empty());
        }
        if (baseModule.getBindFlag() != null) {
            texts.addAll(TOOLTIP_HAS_BIND);
        } else {
            texts.addAll(TOOLTIPS_NO_BIND);
        }
        return texts;
    }

    public List<Text> getModuleDescriptionTooltips(BaseModule baseModule) {
        return ChatUtils.parseTooltipsTranslation(
                "widget.click-gui.module-name." + baseModule.getModuleManager().getName() + "." + baseModule.getName()
                        + ".tooltips",
                "暂无介绍");
    }

    private static final int indexWidth = 140;
    private static final int blankWidth = 10;
    private static final int buttonWidth = 180;
    private static final int buttonHeight = 18;
    private static final int buttonBlank = 2;

    public DrawableWidget createBaseModuleConfigurateScreen(BaseModule baseModule, ClickGuiMetaData metaData) {
        int width = indexWidth + blankWidth + buttonWidth;
        DynamicListWidget listWidget = new DynamicListWidget(0, 0, width);

        listWidget.addDrawableChild(ExecutableWidget.instance(0, 0, width, buttonHeight)
                .setElementHandler(guiElementFactory.createTitle(
                        TextProvider.of(getModuleName(baseModule)),
                        TooltipHandler.of(getModuleDescriptionTooltips(baseModule)))));

        List<WrapperConfigRef<?>> editableConfigs = baseModule.getEditableConfig();
        Map<String, List<WrapperConfigRef<?>>> groupedConfigs = collectConfigSubGroups(editableConfigs);
        if (shouldUseConfigSubGroups(groupedConfigs)) {
            for (var entry : groupedConfigs.entrySet()) {
                String prefix = entry.getKey();
                if (shouldShowConfigSubGroupHead(prefix)) {
                    WidgetUtils.addConditionalWidgets(
                            listWidget,
                            List.of(createConfigSubGroupHead(baseModule, prefix, metaData, entry.getValue())));
                } else {
                    for (var configWidget : entry.getValue()) {
                        WidgetUtils.addConditionalWidgets(
                                listWidget, List.of(createBaseModuleConfigurateRow(configWidget)));
                    }
                }
            }
        } else {
            for (var configWidget : editableConfigs) {
                WidgetUtils.addConditionalWidgets(listWidget, List.of(createBaseModuleConfigurateRow(configWidget)));
            }
        }
        baseModule.addCustomWidgets(listWidget::addDrawableChild, width, buttonHeight, buttonBlank, CONFIG_FACTORY);
        return listWidget;
    }

    private Map<String, List<WrapperConfigRef<?>>> collectConfigSubGroups(List<WrapperConfigRef<?>> editableConfigs) {
        Map<String, List<WrapperConfigRef<?>>> groupedConfigs = new LinkedHashMap<>();
        for (var configWidget : editableConfigs) {
            String prefix = getConfigSubGroupPrefix(configWidget);
            groupedConfigs.computeIfAbsent(prefix, ignored -> new ArrayList<>()).add(configWidget);
        }
        return groupedConfigs;
    }

    private boolean shouldUseConfigSubGroups(Map<String, List<WrapperConfigRef<?>>> groupedConfigs) {
        return enableConfigSubGroup.get() && groupedConfigs.size() > 1;
    }

    private boolean shouldShowConfigSubGroupHead(String prefix) {
        return prefix != null && !prefix.isEmpty();
    }

    private String getConfigSubGroupPrefix(WrapperConfigRef<?> configWidget) {
        String[] path = configWidget.path();
        if (path == null || path.length <= 1) {
            return "";
        }
        return String.join(".", Arrays.copyOf(path, path.length - 1));
    }

    private ConditionalWidget createConfigSubGroupHead(
            BaseModule baseModule, String prefix, ClickGuiMetaData metaData, List<WrapperConfigRef<?>> subs) {
        int width = indexWidth + blankWidth + buttonWidth;
        int height = 12;
        String metaKey = getConfigSubGroupMetaKey(baseModule, prefix);
        metaData.checkSubGroupDefault(metaKey);
        DrawableWidget header = guiElementFactory.createLabel(
                TextProvider.of(getConfigSubGroupTitle(prefix)),
                TooltipHandler.of(List.of()),
                0,
                buttonBlank,
                width - height,
                height,
                true);
        DynamicListWidget group = new DynamicListWidget(0, 0, width);
        List<ConditionalWidget> widgets =
                subs.stream().map(this::createBaseModuleConfigurateRow).toList();
        WidgetUtils.addCollapsibleGroup(
                group,
                header,
                ValueAccessor.<Boolean>of(
                        () -> metaData.isSubGroupExpanded(metaKey),
                        value -> metaData.setSubGroupExpanded(metaKey, value)),
                widgets,
                guiElementFactory,
                width - height,
                buttonBlank,
                height,
                height);
        return new ConditionalWidget(() -> subs.stream().anyMatch(WrapperConfigRef::shouldShow), group);
    }

    private Text getConfigSubGroupTitle(String prefix) {
        return Text.translatable("config.index." + prefix);
    }

    private String getConfigSubGroupMetaKey(BaseModule baseModule, String prefix) {
        return baseModule.getModuleManager().getName() + "." + baseModule.getName() + ":" + prefix;
    }

    private String getSliderMetaKey(BaseModule baseModule) {
        return baseModule.getModuleManager().getName() + "." + baseModule.getName();
    }

    private ConditionalWidget createBaseModuleConfigurateRow(WrapperConfigRef<?> configWidget) {
        return createBaseModuleConfigurateRow(configWidget, () -> true);
    }

    private ConditionalWidget createBaseModuleConfigurateRow(
            WrapperConfigRef<?> configWidget, BooleanSupplier extraShowCondition) {
        int width = indexWidth + blankWidth + buttonWidth;
        SubScreenWidget keyValue = new SubScreenWidget(0, 0, width, buttonHeight + buttonBlank);
        keyValue.addDrawableChild(DisplayWidget.instance(0, 0, width, buttonBlank + buttonHeight));
        DrawableWidget kvInputWidget = getKeyValueWidget(configWidget.ref(), configWidget.keyName());
        keyValue.addDrawableChild(kvInputWidget);
        BooleanSupplier showCondition = configWidget.showPredicate();
        return new ConditionalWidget(() -> showCondition.getAsBoolean() && extraShowCondition.getAsBoolean(), keyValue);
    }

    public NBTRef<GuiSetting> clickGuiConfig = builder(clickGui.add("gui-config"), GuiSetting.class)
            .defaultValue(GuiSetting.DEFAULT)
            .build();

    @Override
    public AlignType buttonTextAlign() {
        return clickGuiConfig.get().buttonTextAlign();
    }

    @Override
    public AlignType inputTextAlign() {
        return clickGuiConfig.get().inputTextAlign();
    }

    @Override
    public AlignType sliderTextAlign() {
        return clickGuiConfig.get().sliderTextAlign();
    }

    public void openConfigurateScreen(BaseModule baseModule) {
        openConfigurateScreen(baseModule, getClickGuiMetadata());
    }

    public void openConfigurateScreen(BaseModule baseModule, ClickGuiMetaData metaData) {
        var listWidget = createBaseModuleConfigurateScreen(baseModule, metaData);
        Screen screen = new CenterScreen(listWidget);
        ScreenAccess.of(screen).addCloseFuture(() -> setClickGuiMeta(metaData));
        ScreenAccess.of(screen).openFromCurrent();
        // SubScreenWidget levelSubScreen = new SubScreenWidget(0, 0, 0,0);
    }

    {
        CONFIG_FACTORY = new CombinedElementFactory(configGuiConfig::get);
        ELEMENT_FACTORY = new CombinedElementFactory(widgetGuiConfig::get);
    }

    private @NotNull DrawableWidget getKeyValueWidget(Ref<?> wrapper, String keyName) {
        ElementFactory factory = CONFIG_FACTORY;
        return WidgetUtils.createKeyValueWidget(wrapper, keyName, WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT, factory);
    }

    private DrawableWidget createBaseSettings(Screen screen, ClickGuiMetaData meta) {
        DrawableWidget widget = createBaseModuleConfigurateScreen(ModuleSettings.INSTANCE, meta);
        return WidgetUtils.createCenterScreenWidget(
                widget, screen.width, screen.height - 2 * ClickGuiMainScreen.BUTTON_HEIGHT);
    }

    private DrawableWidget createGuiSettings(Screen screen, ClickGuiMetaData metaData) {
        DrawableWidget widget = createBaseModuleConfigurateScreen(ClickGui.INSTANCE, metaData);
        return WidgetUtils.createCenterScreenWidget(
                widget, screen.width, screen.height - 2 * ClickGuiMainScreen.BUTTON_HEIGHT);
    }

    private DrawableWidget createFontSettings(Screen screen, ClickGuiMetaData metaData) {
        DrawableWidget widget = createBaseModuleConfigurateScreen(MainTasks.getTextFont(), metaData);
        return WidgetUtils.createCenterScreenWidget(
                widget, screen.width, screen.height - 2 * ClickGuiMainScreen.BUTTON_HEIGHT);
    }

    private DrawableWidget createFriendSettings(Screen screen, ClickGuiMetaData meta) {
        DrawableWidget widget = createBaseModuleConfigurateScreen(TargetSelector.INSTANCE, meta);
        return WidgetUtils.createCenterScreenWidget(
                widget, screen.width, screen.height - 2 * ClickGuiMainScreen.BUTTON_HEIGHT);
    }

    private DrawableWidget createCmdMacrosSettings(Screen screen, ClickGuiMetaData meta) {
        List<WrapperConfigRef<?>> configRefs = Stream.of(BindCommand.INSTANCE, EventCommand.INSTANCE)
                .flatMap(s -> s.getEditableConfig().stream())
                .toList();
        DrawableWidget widget = createConfigScreen(
                Text.translatable("widget.click-gui.selection.CmdMacros"),
                List::of,
                configRefs,
                WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT,
                CONFIG_FACTORY);
        return WidgetUtils.createCenterScreenWidget(
                widget, screen.width, screen.height - 2 * ClickGuiMainScreen.BUTTON_HEIGHT);
    }

    private DrawableWidget createKeyBindListSettings(Screen screen, ClickGuiMetaData meta) {
        List<WrapperConfigRef<?>> allKeyBinds = HackModules.getModuleGroups().stream()
                .flatMap(s -> s.getModules().stream())
                .flatMap(s -> s.getEditableConfig().stream())
                .filter(s -> s.ref() instanceof KeyBindRef)
                .toList();
        DrawableWidget widget = createConfigScreen(
                Text.translatable("widget.click-gui.selection.Hotkeys"),
                List::of,
                allKeyBinds,
                WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT,
                CONFIG_FACTORY);
        return WidgetUtils.createCenterScreenWidget(
                widget, screen.width, screen.height - 2 * ClickGuiMainScreen.BUTTON_HEIGHT);
    }

    private DrawableWidget createTest(ClickGuiMetaData meta) {
        return new SubScreenWidget(0, 0, 0, 0);
    }

    private DrawableWidget createConfig(ClickGuiMetaData meta) {
        var screen = new ConfigMultiLevelListScreen(Config.REGISTRY.stream().toList(), CONFIG_FACTORY);
        screen.init(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        return new ContentDelegateWidget<>(0, 0, 0, 0).setContentDelegate(screen);
    }

    private DrawableWidget createBaritoneScreen(Screen screen, ClickGuiMetaData metaData) {
        List<Pair<String, ValueAccessor>> baritones = BaritoneHooks.getInstance().getAllSettings().entrySet().stream()
                .map(s -> Pair.of("widget.click-gui.baritone." + s.getKey(), (ValueAccessor) s.getValue()))
                .toList();
        var widget = WidgetUtils.createValueAccessorsEditScreen(
                Text.translatable("widget.click-gui.selection.Baritone"),
                List::of,
                (List) baritones,
                WidgetUtils.DEFAULT_CONFIG_SCREEN_LAYOUT,
                CONFIG_FACTORY,
                true);
        return WidgetUtils.createCenterScreenWidget(
                widget, screen.width, screen.height - 2 * ClickGuiMainScreen.BUTTON_HEIGHT);
    }

    public DynamicListWidget createConfigScreen(
            Text title,
            Supplier<List<Text>> titleTooltips,
            List<BaseModule.WrapperConfigRef<?>> configs,
            WidgetUtils.ConfigScreenLayout layout,
            ElementFactory factory) {
        int width = layout.totalWidth();
        DynamicListWidget listWidget = new DynamicListWidget(0, 0, width);

        listWidget.addDrawableChild(ExecutableWidget.instance(0, 0, width, layout.buttonHeight())
                .setElementHandler(factory.createTitle(TextProvider.of(title), TooltipHandler.of(titleTooltips))));

        for (var configWidget : configs) {
            SubScreenWidget keyValueRow =
                    new SubScreenWidget(0, 0, width, layout.buttonHeight() + layout.buttonBlank());
            keyValueRow.addDrawableChild(
                    DisplayWidget.instance(0, 0, width, layout.buttonBlank() + layout.buttonHeight()));
            keyValueRow.addDrawableChild(createKeyValueWidget(configWidget, layout, factory));
            WidgetUtils.addConditionalWidgets(
                    listWidget, List.of(new ConditionalWidget(configWidget.showPredicate(), keyValueRow)));
        }

        return listWidget;
    }

    private static SubScreenWidget createKeyValueWidget(
            BaseModule.WrapperConfigRef<?> wrapper, WidgetUtils.ConfigScreenLayout layout, ElementFactory factory) {
        return WidgetUtils.createKeyValueWidget(wrapper.ref(), wrapper.keyName(), layout, factory);
    }

    @Getter
    @AllArgsConstructor
    public static class SliderGroupMeta {
        private int x;
        private int y;

        public void setX(int x) {
            this.x = x;
        }

        public void setY(int y) {
            this.y = y;
        }

        public static Codec<SliderGroupMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("x_coord").forGetter(SliderGroupMeta::getX),
                        Codec.INT.fieldOf("y_coord").forGetter(SliderGroupMeta::getY))
                .apply(instance, SliderGroupMeta::new));
    }

    @Getter
    public static class ClickGuiMetaData {
        Map<String, ModuleSlideMeta> moduleMetaMap;
        Map<String, SliderGroupMeta> sliderGroupMetaMap;
        Map<String, Boolean> subGroupMetaMap;
        Map<String, Boolean> sliderMetaMap;

        @Setter
        String searching;

        public ClickGuiMetaData() {
            this.moduleMetaMap = new LinkedHashMap<>();
            this.sliderGroupMetaMap = new LinkedHashMap<>();
            this.subGroupMetaMap = new LinkedHashMap<>();
            this.sliderMetaMap = new LinkedHashMap<>();
            this.searching = "";
        }

        public ClickGuiMetaData(
                Map<String, ModuleSlideMeta> moduleCoordinates,
                Map<String, SliderGroupMeta> sliderGroupMetaMap,
                Map<String, Boolean> subGroupMetaMap,
                Map<String, Boolean> sliderMetaMap,
                String searching) {
            this.moduleMetaMap = new LinkedHashMap<>(moduleCoordinates);
            this.sliderGroupMetaMap = new LinkedHashMap<>(sliderGroupMetaMap);
            this.subGroupMetaMap = new LinkedHashMap<>(subGroupMetaMap);
            this.sliderMetaMap = new LinkedHashMap<>(sliderMetaMap);
            this.searching = searching;
        }

        public void checkDefault(String moduleName, int x, int y) {
            if (!moduleMetaMap.containsKey(moduleName)) {
                ModuleSlideMeta newMeta = new ModuleSlideMeta(x, y, false);
                moduleMetaMap.put(moduleName, newMeta);
            }
        }

        public void checkSliderGroupDefault(String moduleName, int x, int y) {
            sliderGroupMetaMap.putIfAbsent(moduleName, new SliderGroupMeta(x, y));
        }

        public void checkSubGroupDefault(String key) {
            subGroupMetaMap.putIfAbsent(key, true);
        }

        public boolean isSubGroupExpanded(String key) {
            checkSubGroupDefault(key);
            return subGroupMetaMap.get(key);
        }

        public void setSubGroupExpanded(String key, boolean expanded) {
            subGroupMetaMap.put(key, expanded);
        }

        public void checkSliderDefault(String key) {
            sliderMetaMap.putIfAbsent(key, false);
        }

        public boolean isSliderExpanded(String key) {
            checkSliderDefault(key);
            return sliderMetaMap.get(key);
        }

        public void setSliderExpanded(String key, boolean expanded) {
            sliderMetaMap.put(key, expanded);
        }

        public void checkDefault(List<String> moduleNames, int wX, int wY) {
            int sze = moduleNames.size();
            int cntY = 0;
            int yLevel = 0;
            for (int i = 0; i < sze; ++i, ++cntY) {
                int idx = cntY * (wX + DEFAULT_GAP);
                if (idx + wX > mc.getWindow().getScaledWidth()) {
                    cntY = 0;
                    idx = 0;
                    yLevel += 1;
                }
                checkDefault(moduleNames.get(i), idx, DEFAULT_Y + yLevel * (wY * 2));
            }
        }

        public ModuleSlideMeta getModuleMeta(String moduleName) {
            return Objects.requireNonNull(moduleMetaMap.get(moduleName));
        }

        public SliderGroupMeta getSliderGroupMeta(String moduleName) {
            return Objects.requireNonNull(sliderGroupMetaMap.get(moduleName));
        }

        public void checkSliderGroupDefault(List<String> moduleNames, int wX, int wY) {
            int cntX = 0;
            int yLevel = 0;
            for (String moduleName : moduleNames) {
                int x = cntX * (wX + DEFAULT_GAP);
                if (x + wX > mc.getWindow().getScaledWidth()) {
                    cntX = 0;
                    x = 0;
                    yLevel += 1;
                }
                checkSliderGroupDefault(moduleName, x, DEFAULT_Y + yLevel * (wY * 2));
                cntX++;
            }
        }

        public static Codec<ClickGuiMetaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.unboundedMap(Codec.STRING, ModuleSlideMeta.CODEC)
                                .fieldOf("module_list_metas")
                                .forGetter(ClickGuiMetaData::getModuleMetaMap),
                        Codec.unboundedMap(Codec.STRING, SliderGroupMeta.CODEC)
                                .optionalFieldOf("slider_group_metas", Map.of())
                                .forGetter(ClickGuiMetaData::getSliderGroupMetaMap),
                        Codec.unboundedMap(Codec.STRING, Codec.BOOL)
                                .optionalFieldOf("sub_group_metas", Map.of())
                                .forGetter(ClickGuiMetaData::getSubGroupMetaMap),
                        Codec.unboundedMap(Codec.STRING, Codec.BOOL)
                                .optionalFieldOf("slider_metas", Map.of())
                                .forGetter(ClickGuiMetaData::getSliderMetaMap),
                        Codec.STRING.optionalFieldOf("searching", "").forGetter(ClickGuiMetaData::getSearching))
                .apply(instance, ClickGuiMetaData::new));
    }

    @Getter
    @AllArgsConstructor
    public static class ModuleSlideMeta {
        private int x;
        private int y;
        // limit, do not move out of bound
        public void setX(int x) {
            this.x = x;
        }

        public void setY(int y) {
            this.y = y;
        }

        boolean slidingDown;

        public static Codec<ModuleSlideMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("x_coord").forGetter(ModuleSlideMeta::getX),
                        Codec.INT.fieldOf("y_coord").forGetter(ModuleSlideMeta::getY),
                        Codec.BOOL.fieldOf("sliding_down").forGetter(ModuleSlideMeta::isSlidingDown))
                .apply(instance, ModuleSlideMeta::new));
    }

    public static class ClickGuiMainScreen extends GenericScreen {
        ClickGui clickGui;
        Map<String, Function<Screen, DrawableWidget>> widgets;
        DrawableWidget widget;
        String selecting;

        public ClickGuiMainScreen(ClickGui clickGuiModule, Map<String, Function<Screen, DrawableWidget>> widgets) {
            super(Text.empty(), 0, 0);
            this.clickGui = clickGuiModule;
            this.widgets = widgets;
            String val = this.widgets.keySet().iterator().next();
            setGlobal(val);
        }

        protected void init0() {
            super.init0();
            // FULL SCREEN
            this.x = 0;
            this.y = 0;
        }

        public static final int BUTTON_HEIGHT = 12;
        public static final int BUTTON_MAX_WIDTH = 60;

        protected void setGlobal(String string) {
            if (!Objects.equals(string, selecting)) {
                selecting = string;
                widget = widgets.get(selecting).apply(this);
                if (widgetDelegate != null) {
                    widgetDelegate.setContentDelegate(widget);
                }
            }
        }

        ContentDelegateWidget<DrawableWidget> widgetDelegate;

        @Override
        protected void init() {
            super.init();
            int size = widgets.size();
            int blank;
            int width;
            if (size * BUTTON_MAX_WIDTH > this.width) {
                blank = 0;
                width = this.width / size;
            } else {
                blank = (this.width - size * BUTTON_MAX_WIDTH) / 2;
                width = BUTTON_MAX_WIDTH;
            }
            int cnt = 0;
            for (String entry : widgets.keySet()) {
                String selecting = entry;
                ElementHandler element = clickGui.guiElementFactory.createButton(
                        TextProvider.of(
                                Text.translatableWithFallback("widget.click-gui.selection." + selecting, selecting)),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                "widget.click-gui.selection." + selecting + ".tooltips", "暂无介绍")),
                        ButtonAction.run(() -> this.setGlobal(selecting)),
                        () -> true,
                        () -> Objects.equals(this.selecting, selecting));
                ExecutableWidget.instance(blank + cnt * width, 0, width, BUTTON_HEIGHT)
                        .setElementHandler(element)
                        .addTo(this);
                cnt += 1;
            }
            // resize
            String currentSelect = selecting;
            selecting = null;
            setGlobal(currentSelect);
            widgetDelegate = new ContentDelegateWidget<>(0, BUTTON_HEIGHT, this.width, this.height - BUTTON_HEIGHT)
                    .setContentDelegate(widget)
                    .addTo(this);
        }
    }
}
