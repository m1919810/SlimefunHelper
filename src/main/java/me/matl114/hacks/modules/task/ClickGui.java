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
import me.matl114.gui.Constants;
import me.matl114.gui.FilterService;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.clickGui.ClickGuiMainScreen;
import me.matl114.gui.complex.config.ConfigurateNewStyleScreen;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.ColorBoxElement;
import me.matl114.gui.elements.ColorLabelTextElement;
import me.matl114.gui.elements.ColorSplitterElement;
import me.matl114.gui.presets.single.CenterScreen;
import me.matl114.gui.presets.single.SimpleScreen;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.Vec2;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.config.*;
import me.matl114.managers.file.FileStorage;
import me.matl114.managers.input.*;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.algorithms.SerialExecutor;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.NotNull;

public class ClickGui extends BaseModule {
    public static ClickGui INSTANCE;

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
            .registerHotkey(HotKeyUtils.asHandler(this::openGameOptionsMenu))
            .build();

    public KeyBindRef keyBindClickGui = hotkey(clickGui.add("hotkey"))
            .defaultValue(new MultiKeyBind(KeyCode.KEY_RIGHT_ALT))
            .registerHotkey(this::onHotkey)
            .build();

    public NBTRef<Vec2> widgetSize = builder(clickGui.add("widget-size"), Vec2.class)
            .defaultValue(new Vec2(55, 12))
            .build();

    public FileStorage internalGuiData = FileManager.getInstance().getInternalStorage("click-gui-data.nbt");

    public NBTRef<WrapColor> moduleListColor = builder(clickGui.add("gui-frame-style"), WrapColor.class)
            .defaultValue(new WrapColor(("#984FDB")))
            .build();

    public NBTRef<WrapColor> backGroundColor = builder(clickGui.add("gui-background-style"), WrapColor.class)
            .defaultValue(new WrapColor(("#323232")))
            .build();

    public NBTRef<WrapColor> configColor = builder(clickGui.add("gui-config-style"), WrapColor.class)
            .defaultValue(new WrapColor(("#323232")))
            .build();

    public NBTRef<WrapColor> textColor = builder(clickGui.add("gui-text-style"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.WHITE)))
            .build();

    public final FlagRef enableConfigSubGroup = builder(clickGui.add("enable-config-subgroup"), Boolean.class)
            .defaultValue(true)
            .build();

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
                    .setElementHandler(new ButtonElement(
                            TextProvider.of(Text.literal("SlimefunHelper")), ButtonAction.run(this::openClickGui)));
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
        selections.put("Module", (s) -> this.createModuleGroupList(modules, meta));
        selections.put("Friends", (s) -> this.createFriendSettings(s, meta));
        selections.put("CmdMacros", (s) -> this.createCmdMacrosSettings(s, meta));
        selections.put("Hotkeys", (s) -> this.createKeyBindListSettings(s, meta));
        selections.put("BaseSettings", (s) -> this.createBaseSettings(s, meta));
        selections.put("GuiSettings", (s) -> this.createGuiSettings(s, meta));
        selections.put("Config", (s) -> this.createConfig(meta));
        if (BaritoneHooks.getInstance().isBaritoneAPISupported()) {
            selections.put("Baritone", (s) -> this.createBaritoneScreen(s, meta));
        }
        Screen screen = new ClickGuiMainScreen(selections);
        // add save when close
        ScreenAccess.of(screen).addCloseFuture(() -> setClickGuiMeta(meta));
        ScreenAccess.of(screen).openFromCurrent();
    }

    public Stream<BaseModule> getShowModuleList(ModuleGroup group) {
        return group.getModules().stream().filter(BaseModule::shouldShowInGui);
    }

    private DrawableWidget createModuleGroupList(List<String> modules, ClickGuiMetaData meta) {
        SubScreenWidget subScreen = new SubScreenWidget(0, 0, 0, 0);
        for (var re : modules) {
            ModuleGroup group = HackModules.getModuleGroup(re);
            ModuleSlideMeta groupMeta = meta.getModuleMeta(re);
            subScreen.addDrawableChild(createModuleGroup(re, group, groupMeta, meta));
        }
        // search list
        subScreen.addDrawableChild(createSearchList(meta, meta.getModuleMeta(SEARCH_MODULE)));
        return subScreen;
    }

    private SubScreenWidget createModuleListHolder(ModuleSlideMeta slideMeta) {
        return new DynamicSubScreenWidget(
                ValueAccessor.of(slideMeta::getX, slideMeta::setX), ValueAccessor.of(slideMeta::getY, slideMeta::setY));
    }

    private DrawableWidget createModuleGroup(
            String module, ModuleGroup moduleGroup, ModuleSlideMeta slideMeta, ClickGuiMetaData metaData) {
        SubScreenWidget subScreen = createModuleListHolder(slideMeta);
        DrawableWidget expandHead = createDragExpandableHead(module, slideMeta);
        subScreen.addDrawableChild(expandHead);
        // add list
        SubScreenWidget moduleList = createModuleList(moduleGroup, metaData);
        subScreen.addDrawableChild(new DynamicContentWidget<>(
                () -> (slideMeta.slidingDown ? moduleList : null), 0, expandHead.getHeight()));
        return subScreen;
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

    private DrawableWidget createClickableModuleWidget(BaseModule baseModule, ClickGuiMetaData metaData) {
        FlagRef bindFlag = baseModule.getBindFlag();
        return ExecutableWidget.instance(
                        0, 0, (int) widgetSize.get().x(), (int) widgetSize.get().y())
                .setElementHandler(new ColorBoxElement(
                                bindFlag != null
                                        ? ButtonAction.isLeft((bl) -> {
                                            if (bl) {
                                                bindFlag.toggle();
                                            } else {
                                                openConfigurateScreen(baseModule, metaData);
                                            }
                                        })
                                        : ButtonAction.run(() -> openConfigurateScreen(baseModule, metaData)),
                                TextProvider.of(getModuleName(baseModule)),
                                () -> this.backGroundColor.get().withAlpha(192),
                                () -> this.textColor.get().withAlpha(255),
                                (el, bl) -> {
                                    if (bindFlag != null && bindFlag.get()) {
                                        return moduleListColor.get().withAlpha(255);
                                    } else if (bl) {
                                        return -1;
                                    } else return null;
                                })
                        .withTooltips(TooltipHandler.of(getModuleButtonTooltips(baseModule))));
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
                .setElementHandler(new ColorLabelTextElement(
                                TextProvider.of(getModuleName(baseModule)),
                                () -> this.textColor.get().withAlpha(255),
                                () -> this.moduleListColor.get().withAlpha(255))
                        .withTooltips(TooltipHandler.of(getModuleDescriptionTooltips(baseModule)))));

        List<WrapperConfigRef<?>> editableConfigs = baseModule.getEditableConfig();
        Map<String, List<WrapperConfigRef<?>>> groupedConfigs = collectConfigSubGroups(editableConfigs);
        if (shouldUseConfigSubGroups(groupedConfigs)) {
            for (var entry : groupedConfigs.entrySet()) {
                String prefix = entry.getKey();
                if (shouldShowConfigSubGroupHead(prefix)) {
                    listWidget.addDrawableChild(createConfigSubGroupHead(baseModule, prefix, metaData));
                    BooleanSupplier subGroupEnabled = createSubGroupEnabledPredicate(baseModule, prefix, metaData);
                    for (var configWidget : entry.getValue()) {
                        listWidget.addDrawableChild(createBaseModuleConfigurateRow(configWidget, subGroupEnabled));
                    }
                } else {
                    for (var configWidget : entry.getValue()) {
                        listWidget.addDrawableChild(createBaseModuleConfigurateRow(configWidget));
                    }
                }
            }
        } else {
            for (var configWidget : editableConfigs) {
                listWidget.addDrawableChild(createBaseModuleConfigurateRow(configWidget));
            }
        }
        baseModule.addCustomWidgets(listWidget::addDrawableChild, width, buttonHeight, buttonBlank);
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

    private BooleanSupplier createSubGroupEnabledPredicate(
            BaseModule baseModule, String prefix, ClickGuiMetaData metaData) {
        String metaKey = getConfigSubGroupMetaKey(baseModule, prefix);
        metaData.checkSubGroupDefault(metaKey);
        return () -> metaData.isSubGroupExpanded(metaKey);
    }

    private DrawableWidget createConfigSubGroupHead(BaseModule baseModule, String prefix, ClickGuiMetaData metaData) {
        int width = indexWidth + blankWidth + buttonWidth;
        int buttonHeight = (int) widgetSize.get().y();
        int height = buttonHeight + buttonBlank;
        String metaKey = getConfigSubGroupMetaKey(baseModule, prefix);
        metaData.checkSubGroupDefault(metaKey);
        SubScreenWidget subScreen = new SubScreenWidget(0, 0, width, height);
        ExecutableWidget.instance(0, 0, width, height)
                .setElementHandler(new AbstractElement()
                        .withInputHandler(InputHandler.clickRun(
                                () -> metaData.setSubGroupExpanded(metaKey, !metaData.isSubGroupExpanded(metaKey)))))
                .addToSub(subScreen);
        DisplayWidget.instance(0, buttonBlank, width - buttonHeight, buttonHeight)
                .setRenderHandler(new ColorSplitterElement(
                        TextProvider.of(getConfigSubGroupTitle(prefix)),
                        this.textColor.get().withAlpha(255),
                        () -> backGroundColor.get().withAlpha(192)))
                .addToSub(subScreen);
        ExecutableWidget.instance(width - buttonHeight, buttonBlank, buttonHeight, buttonHeight)
                .setRenderHandler(new AbstractElement()
                        .combineRender(
                                RenderHandler.ofColorQuad(backGroundColor.get().withAlpha(192)))
                        .combineRender((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                            context.setShaderColor(textColor.get().withAlpha(255));
                            context.drawGuiTexture(
                                    metaData.isSubGroupExpanded(metaKey)
                                            ? Constants.EXPAND_GUI_ON_SPRITE
                                            : Constants.EXPAND_GUI_OFF_SPRITE,
                                    element.getTextureWidth() - element.getTextureHeight() + 2,
                                    2,
                                    0,
                                    element.getTextureHeight() - 4,
                                    element.getTextureHeight() - 4);
                            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        }))
                .addToSub(subScreen);
        return subScreen;
    }

    private Text getConfigSubGroupTitle(String prefix) {
        return Text.translatable("config.index." + prefix);
    }

    private String getConfigSubGroupMetaKey(BaseModule baseModule, String prefix) {
        return baseModule.getModuleManager().getName() + "." + baseModule.getName() + ":" + prefix;
    }

    private DrawableWidget createBaseModuleConfigurateRow(WrapperConfigRef<?> configWidget) {
        return createBaseModuleConfigurateRow(configWidget, () -> true);
    }

    private DrawableWidget createBaseModuleConfigurateRow(
            WrapperConfigRef<?> configWidget, BooleanSupplier extraShowCondition) {
        int width = indexWidth + blankWidth + buttonWidth;
        SubScreenWidget keyValue = new SubScreenWidget(0, 0, width, buttonHeight + buttonBlank);
        keyValue.addDrawableChild(DisplayWidget.instance(0, 0, width, buttonBlank + buttonHeight));
        DrawableWidget kvInputWidget = getKeyValueWidget(configWidget.ref(), configWidget.keyName());
        keyValue.addDrawableChild(kvInputWidget);
        BooleanSupplier showCondition = configWidget.showPredicate();
        return new DynamicContentWidget<>(
                () -> showCondition.getAsBoolean() && extraShowCondition.getAsBoolean() ? keyValue : null, 0, 0);
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

    private @NotNull DrawableWidget getKeyValueWidget(Ref<?> wrapper, String keyName) {
        int width = indexWidth + blankWidth + buttonWidth;
        return createRefEditor(keyName, wrapper, 0, buttonBlank, width, buttonHeight);
    }

    private DrawableWidget createSearchList(ClickGuiMetaData metaData, ModuleSlideMeta slideMeta) {
        String module = SEARCH_MODULE;
        SubScreenWidget subScreen = createModuleListHolder(slideMeta);
        DrawableWidget expandHead = createDragExpandableHead(module, slideMeta);
        subScreen.addDrawableChild(expandHead);
        // add dynamic widget
        DrawableWidget subScreen2 = createSearchListContent(metaData);
        subScreen.addDrawableChild(new DynamicContentWidget<>(
                () -> (slideMeta.slidingDown ? subScreen2 : null), 0, expandHead.getHeight()));
        return subScreen;
    }

    private final SerialExecutor taskExecutor = new SerialExecutor(CompletableFuture::runAsync);

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
                        // match any
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

        ContentDelegateWidget<TextFieldWidget> inputWidget = McWidgetHelpers.createTextFieldEditBox(
                0,
                0,
                buttonWidth,
                buttonHeight,
                (v, t) -> {
                    if (!Objects.equals(t, metaData.searching)) {
                        metaData.setSearching(t);
                        taskExecutor.submit(updateTask);
                    }
                },
                metaData.searching);
        // initialize
        updateTask.run();
        subScreen.addDrawableChild(inputWidget);
        return subScreen;
    }

    private void createSearchResultGroupSubList(
            Consumer<DrawableWidget> childrenAdder, String group, List<BaseModule> list, ClickGuiMetaData metaData) {
        MutableBoolean showFlag = new MutableBoolean(true);
        int buttonWidth = (int) widgetSize.get().x();
        int buttonHeight = (int) widgetSize.get().y();
        SubScreenWidget subScreen = new SubScreenWidget(0, 0, buttonWidth, buttonHeight);
        ExecutableWidget.instance(0, 0, buttonWidth, buttonHeight)
                .setElementHandler(new AbstractElement()
                        .withInputHandler(InputHandler.clickRun(() -> showFlag.setValue(!showFlag.booleanValue()))))
                .addToSub(subScreen);
        DisplayWidget.instance(0, 0, buttonWidth - buttonHeight, buttonHeight)
                .setRenderHandler(new ColorSplitterElement(
                        TextProvider.of(Text.literal(group)),
                        this.textColor.get().withAlpha(255),
                        () -> backGroundColor.get().withAlpha(192)))
                .addToSub(subScreen);
        ExecutableWidget.instance(buttonWidth - buttonHeight, 0, buttonHeight, buttonHeight)
                .setRenderHandler(new AbstractElement()
                        .combineRender(
                                RenderHandler.ofColorQuad(backGroundColor.get().withAlpha(192)))
                        .combineRender(((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                            context.setShaderColor(textColor.get().withAlpha(255));
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
        return ExecutableWidget.instance(
                        0, 0, (int) widgetSize.get().x(), (int) widgetSize.get().y())
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
                                    if (element.isMouseOver(mouseX, mouseY)) {
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
                        .combineRender(new ColorLabelTextElement(
                                TextProvider.of(Text.translatableWithFallback(
                                        "widget.click-gui.module-group-name." + module, module)),
                                () -> textColor.get().withAlpha(255),
                                () -> moduleListColor.get().withAlpha(255)))
                        .combineRender(((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
                            context.setShaderColor(backGroundColor.get().withAlpha(255));
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
                        }))
                        .withTooltips(TooltipHandler.of(List.of(Text.literal("拖动或鼠标滚轮以修改位置")))));
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
                CONFIG_PALETTE);
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
                CONFIG_PALETTE);
        return WidgetUtils.createCenterScreenWidget(
                widget, screen.width, screen.height - 2 * ClickGuiMainScreen.BUTTON_HEIGHT);
    }

    private DrawableWidget createTest(ClickGuiMetaData meta) {
        return new SubScreenWidget(0, 0, 0, 0);
    }

    private DrawableWidget createConfig(ClickGuiMetaData meta) {
        var screen = new ConfigurateNewStyleScreen(Config.getConfigs().stream().toList());
        screen.init(mc, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
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
                CONFIG_PALETTE,
                true);
        return WidgetUtils.createCenterScreenWidget(
                widget, screen.width, screen.height - 2 * ClickGuiMainScreen.BUTTON_HEIGHT);
    }

    public static DynamicListWidget createConfigScreen(
            Text title,
            Supplier<List<Text>> titleTooltips,
            List<BaseModule.WrapperConfigRef<?>> configs,
            WidgetUtils.ConfigScreenLayout layout,
            WidgetUtils.ConfigScreenPalette palette) {
        int width = layout.totalWidth();
        DynamicListWidget listWidget = new DynamicListWidget(0, 0, width);

        listWidget.addDrawableChild(ExecutableWidget.instance(0, 0, width, layout.buttonHeight())
                .setElementHandler(new ColorLabelTextElement(
                                TextProvider.of(title),
                                () -> palette.titleTextColor().getColorInt(),
                                () -> palette.titleBackgroundColor().getColorInt())
                        .withTooltips(TooltipHandler.of(titleTooltips))));

        for (var configWidget : configs) {
            SubScreenWidget keyValueRow =
                    new SubScreenWidget(0, 0, width, layout.buttonHeight() + layout.buttonBlank());
            keyValueRow.addDrawableChild(
                    DisplayWidget.instance(0, 0, width, layout.buttonBlank() + layout.buttonHeight()));
            keyValueRow.addDrawableChild(createKeyValueWidget(configWidget, layout, palette));
            DynamicContentWidget<?> contentWidget = new DynamicContentWidget<>(
                    () -> configWidget.showPredicate().getAsBoolean() ? keyValueRow : null, 0, 0);
            listWidget.addDrawableChild(contentWidget);
        }

        return listWidget;
    }

    private static SubScreenWidget createKeyValueWidget(
            BaseModule.WrapperConfigRef<?> wrapper,
            WidgetUtils.ConfigScreenLayout layout,
            WidgetUtils.ConfigScreenPalette palette) {
        return WidgetUtils.createKeyValueWidget(wrapper.ref(), wrapper.keyName(), layout, palette);
    }

    @Getter
    public static class ClickGuiMetaData {
        Map<String, ModuleSlideMeta> moduleMetaMap;
        Map<String, Boolean> subGroupMetaMap;

        @Setter
        String searching;

        public ClickGuiMetaData() {
            this.moduleMetaMap = new LinkedHashMap<>();
            this.subGroupMetaMap = new LinkedHashMap<>();
            this.searching = "";
        }

        public ClickGuiMetaData(
                Map<String, ModuleSlideMeta> moduleCoordinates,
                Map<String, Boolean> subGroupMetaMap,
                String searching) {
            this.moduleMetaMap = new LinkedHashMap<>(moduleCoordinates);
            this.subGroupMetaMap = new LinkedHashMap<>(subGroupMetaMap);
            this.searching = searching;
        }

        public void checkDefault(String moduleName, int x, int y) {
            if (!moduleMetaMap.containsKey(moduleName)) {
                ModuleSlideMeta newMeta = new ModuleSlideMeta(x, y, false);
                moduleMetaMap.put(moduleName, newMeta);
            }
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

        public static Codec<ClickGuiMetaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.unboundedMap(Codec.STRING, ModuleSlideMeta.CODEC)
                                .fieldOf("module_list_metas")
                                .forGetter(ClickGuiMetaData::getModuleMetaMap),
                        Codec.unboundedMap(Codec.STRING, Codec.BOOL)
                                .optionalFieldOf("sub_group_metas", Map.of())
                                .forGetter(ClickGuiMetaData::getSubGroupMetaMap),
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

    public static final WidgetUtils.ConfigScreenPalette CONFIG_PALETTE = new WidgetUtils.ConfigScreenPalette(
            () -> ClickGui.INSTANCE.textColor.get().withAlpha(255),
            () -> ClickGui.INSTANCE.moduleListColor.get().withAlpha(255),
            () -> ClickGui.INSTANCE.textColor.get().withAlpha(255),
            () -> ClickGui.INSTANCE.configColor.get().withAlpha(255));

    static {
        WidgetUtils.DEFAULT_PALETTE = CONFIG_PALETTE;
    }
}
