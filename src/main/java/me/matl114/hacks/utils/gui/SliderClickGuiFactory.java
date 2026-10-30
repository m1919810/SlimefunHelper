package me.matl114.hacks.utils.gui;

import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import me.matl114.gui.*;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.IconElement;
import me.matl114.gui.interfaces.CustomElementFactory;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.BaseModule.WrapperConfigRef;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.task.ClickGui.ClickGuiMetaData;
import me.matl114.hacks.modules.task.ClickGui.ModuleSlideMeta;
import me.matl114.hacks.modules.task.ClickGui.SliderGroupMeta;
import me.matl114.hacks.utils.config.GuiSetting;
import me.matl114.managers.config.*;
import me.matl114.managers.input.*;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class SliderClickGuiFactory extends AbstractClassicClickGuiFactory<CustomElementFactory> {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public SliderClickGuiFactory(java.util.function.Supplier<GuiSetting> setting) {
        super(new CustomElementFactory(() -> setting.get()));
    }

    @Override
    protected DrawableWidget createModuleGroupList(List<String> modules, ClickGuiMetaData meta) {
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
        return Math.max(1, (int) clickGui.sliderWidgetSize.get().x());
    }

    private int sliderHeight() {
        return Math.max(1, (int) clickGui.sliderWidgetSize.get().y());
    }

    private DrawableWidget createModuleGroup(
            String module,
            ModuleGroup moduleGroup,
            ModuleSlideMeta slideMeta,
            SliderGroupMeta groupMeta,
            ClickGuiMetaData metaData) {
        DynamicSubScreenWidget group = new DynamicSubScreenWidget(
                ValueAccessor.of(groupMeta::getX, groupMeta::setX), ValueAccessor.of(groupMeta::getY, groupMeta::setY));
        group.addDrawableChild(createSliderGroupHeader(module, slideMeta, groupMeta));
        group.addDrawableChild(new DynamicContentWidget<>(
                lazyInitialize(slideMeta::isSlidingDown, () -> createModuleList(moduleGroup, metaData)),
                0,
                sliderHeight()));
        return group;
    }

    private DrawableWidget createModuleList(ModuleGroup moduleGroup, ClickGuiMetaData metaData) {
        DynamicListWidget list = new DynamicListWidget(0, 0, sliderWidth());
        clickGui.getShowModuleList(moduleGroup)
                .forEach(module -> list.addDrawableChild(createSliderModule(module, metaData)));
        return list;
    }

    private DrawableWidget createSliderModule(BaseModule baseModule, ClickGuiMetaData metaData) {
        String metaKey = clickGui.getSliderMetaKey(baseModule);
        metaData.checkSliderDefault(metaKey);
        ValueAccessor<Boolean> expanded = ValueAccessor.of(
                () -> metaData.isSliderExpanded(metaKey), value -> metaData.setSliderExpanded(metaKey, value));
        FlagRef bindFlag = baseModule.getBindFlag();
        int width = sliderWidth();
        int height = sliderHeight();
        ElementHandler moduleElement = elementFactory.createButton(
                TextProvider.of(Text.empty()),
                TooltipHandler.of(clickGui.getModuleButtonTooltips(baseModule)),
                ButtonAction.isLeft(left -> {
                    if (ScreenUtils.hasShiftDown()) {
                        clickGui.openConfigurateScreen(baseModule, metaData);
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
                TextProvider.of(clickGui.getModuleName(baseModule), TextAlign.LEFT),
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
        Map<String, List<WrapperConfigRef<?>>> groupedConfigs = clickGui.collectConfigSubGroups(editableConfigs);
        if (clickGui.shouldUseConfigSubGroups(groupedConfigs)) {
            for (var entry : groupedConfigs.entrySet()) {
                String prefix = entry.getKey();
                if (clickGui.shouldShowConfigSubGroupHead(prefix)) {
                    WidgetUtils.addConditionalWidgets(
                            list, List.of(createSliderConfigSubGroup(baseModule, prefix, metaData, entry.getValue())));
                } else {
                    addSliderConfigRows(list, entry.getValue());
                }
            }
        } else {
            addSliderConfigRows(list, editableConfigs);
        }
        list.addDrawableChild(elementFactory.createExecuteButton(
                () -> Text.translatable("widget.click-gui.open-full-config"),
                () -> ChatUtils.parseTooltipsTranslation("widget.click-gui.open-full-config.tooltips", ""),
                ButtonAction.run(() -> clickGui.openConfigurateScreen(baseModule, metaData)),
                0,
                0,
                sliderWidth(),
                sliderHeight()));
        return list;
    }

    private ConditionalWidget createSliderConfigSubGroup(
            BaseModule baseModule, String prefix, ClickGuiMetaData metaData, List<WrapperConfigRef<?>> configs) {
        String metaKey = clickGui.getConfigSubGroupMetaKey(baseModule, prefix);
        metaData.checkSubGroupDefault(metaKey);
        DynamicListWidget group = new DynamicListWidget(0, 0, sliderWidth());
        group.addDrawableChild(createSliderHeader(
                clickGui.getConfigSubGroupTitle(prefix),
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
        ElementHandler text = elementFactory.createText(provider, handler, splitter, align);
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
                () -> Text.translatableWithFallback(configWidget.keyName(), configWidget.keyName()), TextAlign.LEFT);
        if (ref instanceof FlagRef) {
            var keyValue = configWidget.createKeyValue();
            SubScreenWidget line = createSliderConfigLine(
                    key,
                    TooltipHandler.of(ChatUtils.parseTooltipsTranslation(configWidget.keyName() + ".tooltips", "")),
                    keyValue.generateValueWidget(clickGui.configElementFactory(), width - height, 0, height, height),
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
            return elementFactory.createElement(
                    IconElement.fixedGui(Constants.LIST_TAG_SPRITE, ButtonAction.empty()),
                    null,
                    null,
                    width - height,
                    0,
                    height,
                    height);
        }
        if (ref instanceof NBTRef) {
            return elementFactory.createElement(
                    IconElement.fixedGui(Constants.FORMATTING_TEXTURE_SPRITE, ButtonAction.empty()),
                    null,
                    null,
                    width - height,
                    0,
                    height,
                    height);
        }
        return elementFactory.createElement(
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
        line.addDrawableChild(elementFactory.createBackground(0, 0, width, height))
                .addDrawableChild(createSliderConfigText(key, handler, TextAlign.LEFT, 0, 0, width - height, height))
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
            return keyValue.generateValueWidget(clickGui.configElementFactory(), 0, 0, sliderWidth(), sliderHeight());
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
                            public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                                return true;
                            }

                            @Override
                            public boolean onAction(
                                    ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                                if (type == Type.MOUSE_START_DRAG) {
                                    startMouseX = mouseX;
                                    startMouseY = mouseY;
                                    move = false;
                                    return element.isOver(mouseX, mouseY);
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
                                    if (!move && element.isOver(mouseX, mouseY)) {
                                        slideMeta.setSlidingDown(!slideMeta.isSlidingDown());
                                    }
                                    move = false;
                                    return true;
                                }
                                return true;
                            }
                        })
                        .combineRender(elementFactory.createTitle(
                                TextProvider.of(Text.translatableWithFallback(
                                        "widget.click-gui.module-group-name." + module, module)),
                                TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                        "widget.click-gui.module-group.header.tooltips", ""))))
                        .combineRender(((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                            context.setShaderColor(clickGui.getGuiSettings()
                                    .textColor()
                                    .withAlpha(255)
                                    .color());
                            context.drawGuiTexture(
                                    slideMeta.isSlidingDown()
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
        header.addDrawableChild(elementFactory.createBackground(0, 0, width, height));
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
                            context.setShaderColor(clickGui.getGuiSettings()
                                    .textColor()
                                    .withAlpha(255)
                                    .color());
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
                ValueAccessor.of(groupMeta::getX, groupMeta::setX), ValueAccessor.of(groupMeta::getY, groupMeta::setY));
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
                return metaData.getSearching();
            }

            @Override
            public String get() {
                return metaData.getSearching();
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
                .setElementHandler(elementFactory.createInput(searchValue, TextAlign.LEFT)));
        content.addDrawableChild(results);
        refreshSearchResults(results, metaData);
        return content;
    }

    private void refreshSearchResults(DynamicListWidget results, ClickGuiMetaData metaData) {
        results.clearChildren();
        String filter = metaData.getSearching();
        if (filter == null || filter.isEmpty()) {
            return;
        }
        Set<BaseModule> matched = new LinkedHashSet<>();
        for (ModuleGroup group : HackModules.getModuleGroups()) {
            clickGui.getShowModuleList(group).forEach(module -> {
                String moduleName = module.getName();
                String translatedName = ChatUtils.textToPlainString(clickGui.getModuleName(module));
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

    private <T extends DrawableWidget> Supplier<T> lazyInitialize(BooleanSupplier condition, Supplier<T> initializer) {
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
