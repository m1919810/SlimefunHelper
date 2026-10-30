package me.matl114.hacks.utils.gui;

import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import me.matl114.gui.*;
import me.matl114.gui.basic.*;
import me.matl114.gui.interfaces.CustomElementFactory;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.task.ClickGui.ClickGuiMetaData;
import me.matl114.hacks.modules.task.ClickGui.ModuleSlideMeta;
import me.matl114.hacks.utils.config.GuiSetting;
import me.matl114.managers.config.*;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.algorithms.SerialExecutor;
import me.matl114.utils.config.Value;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.apache.commons.lang3.mutable.MutableBoolean;

public final class SubScreenClickGuiFactory extends AbstractClassicClickGuiFactory<CustomElementFactory> {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public SubScreenClickGuiFactory(java.util.function.Supplier<GuiSetting> setting) {
        super(new CustomElementFactory(() -> setting.get()));
    }

    private final SerialExecutor taskExecutor = new SerialExecutor(CompletableFuture::runAsync);

    @Override
    protected DrawableWidget createModuleGroupList(List<String> modules, ClickGuiMetaData meta) {
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
                () -> (slideMeta.isSlidingDown() ? moduleList : null), 0, expandHead.height()));
        return subScreen;
    }

    private SubScreenWidget createModuleListHolder(ModuleSlideMeta slideMeta) {
        return new DynamicSubScreenWidget(
                ValueAccessor.of(slideMeta::getX, slideMeta::setX), ValueAccessor.of(slideMeta::getY, slideMeta::setY));
    }

    private SubScreenWidget createModuleList(ModuleGroup moduleGroup, ClickGuiMetaData metaData) {
        return createModuleList(clickGui.getShowModuleList(moduleGroup).toList(), metaData);
    }

    private SubScreenWidget createModuleList(Collection<BaseModule> baseModules, ClickGuiMetaData metaData) {
        SubScreenWidget subScreen = new SubScreenWidget(0, 0, 0, 0);
        int yLevel = 0;
        for (var entry : baseModules) {
            DrawableWidget widget = createClickableModuleWidget(entry, metaData);
            subScreen.addDrawableChild(new ContentDelegateWidget<>(0, yLevel, 0, 0).setContentDelegate(widget));
            yLevel += widget.height();
        }
        return subScreen;
    }

    private DrawableWidget createClickableModuleWidget(BaseModule baseModule, ClickGuiMetaData metaData) {
        FlagRef bindFlag = baseModule.getBindFlag();
        return ExecutableWidget.instance(0, 0, (int) clickGui.widgetSize.get().x(), (int)
                        clickGui.widgetSize.get().y())
                .setElementHandler(elementFactory.createButton(
                        TextProvider.of(clickGui.getModuleName(baseModule)),
                        TooltipHandler.of(clickGui.getModuleButtonTooltips(baseModule)),
                        bindFlag != null
                                ? ButtonAction.isLeft((bl) -> {
                                    if (bl) {
                                        bindFlag.toggle();
                                    } else {
                                        clickGui.openConfigurateScreen(baseModule, metaData);
                                    }
                                })
                                : ButtonAction.run(() -> clickGui.openConfigurateScreen(baseModule, metaData)),
                        () -> true,
                        () -> bindFlag != null && bindFlag.get()));
    }

    private DrawableWidget createSearchList(ClickGuiMetaData metaData, ModuleSlideMeta slideMeta) {
        SubScreenWidget subScreen = createModuleListHolder(slideMeta);
        DrawableWidget expandHead = createDragExpandableHead(SEARCH_MODULE, slideMeta);
        subScreen.addDrawableChild(expandHead);
        DrawableWidget searchContent = createSearchListContent(metaData);
        subScreen.addDrawableChild(new DynamicContentWidget<>(
                () -> (slideMeta.isSlidingDown() ? searchContent : null), 0, expandHead.height()));
        return subScreen;
    }

    private DrawableWidget createSearchListContent(ClickGuiMetaData metaData) {
        SubScreenWidget subScreen = new SubScreenWidget(0, 0, 0, 0);
        int buttonWidth = (int) clickGui.widgetSize.get().x();
        int buttonHeight = (int) clickGui.widgetSize.get().y();
        DynamicListWidget listWidget = new DynamicListWidget(0, buttonHeight, buttonWidth);
        subScreen.addDrawableChild(listWidget);
        Runnable updateTask = () -> {
            String filter = metaData.getSearching();
            List<BaseModule> moduleFilter = new ArrayList<>();
            List<BaseModule> settingsFilter = new ArrayList<>();
            if (filter != null && !filter.isEmpty()) {
                for (var group : HackModules.getModuleGroups()) {
                    clickGui.getShowModuleList(group).forEach(module -> {
                        String moduleName = module.getName();
                        String moduleTranslationName = ChatUtils.textToPlainString(clickGui.getModuleName(module));
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
                    createSearchResultGroupSubList(listWidget::addDrawableChild, "Setting", settingsFilter, metaData);
                });
            } else {
                mc.execute(listWidget::clearChildren);
            }
        };

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
                String next = value == null ? "" : value;
                if (!Objects.equals(next, metaData.getSearching())) {
                    metaData.setSearching(next);
                    taskExecutor.submit(updateTask);
                }
            }
        };
        updateTask.run();
        subScreen.addDrawableChild(elementFactory.createInput(searchValue, 0, 0, buttonWidth, buttonHeight));
        return subScreen;
    }

    private void createSearchResultGroupSubList(
            Consumer<DrawableWidget> childrenAdder, String group, List<BaseModule> list, ClickGuiMetaData metaData) {
        MutableBoolean showFlag = new MutableBoolean(true);
        int buttonWidth = (int) clickGui.widgetSize.get().x();
        int buttonHeight = (int) clickGui.widgetSize.get().y();
        SubScreenWidget subScreen = new SubScreenWidget(0, 0, buttonWidth, buttonHeight);
        ExecutableWidget.instance(0, 0, buttonWidth, buttonHeight)
                .setElementHandler(new AbstractElement()
                        .withInputHandler(InputHandler.clickRun(() -> showFlag.setValue(!showFlag.booleanValue()))))
                .addToSub(subScreen);
        elementFactory
                .createBackground(null, 0, 0, buttonWidth - buttonHeight, buttonHeight)
                .addToSub(subScreen);
        elementFactory
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
                        .combineRender(elementFactory.createBackground())
                        .combineRender(((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                            context.setShaderColor(clickGui.getGuiSettings()
                                    .textColor()
                                    .withAlpha(255)
                                    .color());
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
        DynamicContentWidget<?> dynamic = new DynamicContentWidget<>(() -> showFlag.booleanValue() ? re : null, 0, 0);
        childrenAdder.accept(dynamic);
    }

    private DrawableWidget createDragExpandableHead(String module, ModuleSlideMeta slideMeta) {
        return ExecutableWidget.instance(0, 0, (int) clickGui.widgetSize.get().x(), (int)
                        clickGui.widgetSize.get().y())
                .setElementHandler(new AbstractElement()
                        .withInputHandler(new InputHandler() {
                            @Override
                            public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                                return true;
                            }

                            double startMouseX;
                            double startMouseY;
                            boolean move = false;

                            @Override
                            public boolean onAction(
                                    ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                                if (type == Type.MOUSE_START_DRAG) {
                                    if (element.isOver(mouseX, mouseY)) {
                                        startMouseX = mouseX;
                                        startMouseY = mouseY;
                                        move = false;
                                        return true;
                                    }
                                    return false;
                                }
                                if (type == Type.MOUSE_DRAG) {
                                    if (Math.abs(mouseX - startMouseX) >= 1 || Math.abs(mouseY - startMouseY) >= 1) {
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
                                        if (element.isOver(mouseX, mouseY)) {
                                            slideMeta.setSlidingDown(!slideMeta.isSlidingDown());
                                        }
                                    } else {
                                        move = false;
                                    }
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
}
