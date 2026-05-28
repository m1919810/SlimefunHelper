package me.matl114.hacks.modules.task;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.clickGui.ClickGuiMainScreen;
import me.matl114.gui.complex.config.ConfigurateNewStyleScreen;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.ColorBoxElement;
import me.matl114.gui.elements.ColorLabelTextElement;
import me.matl114.gui.elements.ColorSplitterElement;
import me.matl114.gui.presets.single.CenterScreen;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.utils.config.NBTData;
import me.matl114.hacks.utils.config.Vec2;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.managers.Configs;
import me.matl114.managers.ScheduleService;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.IInputManager;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class ClickGui extends BaseModule {
    public ClickGui() {}

    public ModulePath clickGui = makePath(Configs.HOTKEY_CONFIG, "click-gui");

    public KeyBindRef keyBind = hotkey(clickGui.add("hotkey"))
            .defaultValue(new MultiKeyBind(KeyCode.KEY_RIGHT_ALT))
            .registerHotkey(this::onHotkey)
            .build();

    public NBTRef<Vec2> widgetSize = builder(clickGui.add("widget-size"), Vec2.class)
            .defaultValue(new Vec2(50, 13))
            .build();

    public NBTRef<NBTData> internalGuiData = builder(
                    Configs.INTERNAL_CONFIG, clickGui.add("gui-data").toPath(), NBTData.class)
            .defaultValue(new NBTData(new NbtCompound()))
            .build();

    public NBTRef<WrapColor> moduleListColor = builder(clickGui.add("gui-frame-style"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color("#984FDB")))
            .build();

    public NBTRef<WrapColor> backGroundColor = builder(clickGui.add("gui-background-style"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color("#323232")))
            .build();

    public NBTRef<WrapColor> configColor = builder(clickGui.add("gui-config-style"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color("#323232")))
            .build();

    public NBTRef<WrapColor> textColor = builder(clickGui.add("gui-text-style"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.WHITE)))
            .build();

    private boolean onHotkey(IInputManager manager) {
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
        internalGuiData.set(new NBTData(new NbtCompound()));
    }

    public List<String> getModules() {
        return new ArrayList<>(HackModules.main.getModuleGroups().keySet());
    }

    private static final String SEARCH_MODULE = "Search";

    public ClickGuiMetaData getClickGuiMetadata() {
        NBTData data = internalGuiData.get();
        var result = ClickGuiMetaData.CODEC.decode(NbtOps.INSTANCE, data.nbtElement());
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
        internalGuiData.set(new NBTData(element));
    }

    public static final int DEFAULT_GAP = 5;
    public static final int DEFAULT_Y = 40;

    public void openClickGui() {
        List<String> modules = getModules();
        ClickGuiMetaData meta = getClickGuiMetadata();
        Map<String, Supplier<DrawableWidget>> selections = new LinkedHashMap<>();
        selections.put("Module", () -> this.createModuleGroupList(modules, meta));
        selections.put("Config", () -> this.createConfig(meta));
        selections.put("Test", () -> this.createTest(meta));
        Screen screen = new ClickGuiMainScreen(selections);
        // add save when close
        ScreenAccess.of(screen).addCloseFuture(() -> setClickGuiMeta(meta));
        ScreenAccess.of(screen).openFromCurrent();
    }

    public Stream<BaseModule> getShowModuleList(ModuleGroup group) {
        return group.getModules().stream().filter(BaseModule::hasEditableConfig);
    }

    private DrawableWidget createModuleGroupList(List<String> modules, ClickGuiMetaData meta) {
        SubScreenWidget subScreen = new SubScreenWidget(0, 0, 0, 0);
        for (var re : modules) {
            ModuleGroup group = HackModules.getModuleGroup(re);
            ModuleSlideMeta groupMeta = meta.getModuleMeta(re);
            subScreen.addDrawableChild(createModuleGroup(re, group, groupMeta));
        }
        // search list
        subScreen.addDrawableChild(createSearchList(meta, meta.getModuleMeta(SEARCH_MODULE)));
        return subScreen;
    }

    private SubScreenWidget createModuleListHolder(ModuleSlideMeta slideMeta) {
        return new DynamicSubScreenWidget(
                ValueAccessor.of(slideMeta::getX, slideMeta::setX), ValueAccessor.of(slideMeta::getY, slideMeta::setY));
    }

    private DrawableWidget createModuleGroup(String module, ModuleGroup moduleGroup, ModuleSlideMeta slideMeta) {
        SubScreenWidget subScreen = createModuleListHolder(slideMeta);
        DrawableWidget expandHead = createDragExpandableHead(module, slideMeta);
        subScreen.addDrawableChild(expandHead);
        // add list
        SubScreenWidget moduleList = createModuleList(moduleGroup);
        subScreen.addDrawableChild(new DynamicContentWidget<>(
                () -> (slideMeta.slidingDown ? moduleList : null), 0, expandHead.getHeight()));
        return subScreen;
    }

    private SubScreenWidget createModuleList(ModuleGroup moduleGroup) {
        return createModuleList(getShowModuleList(moduleGroup).toList());
    }

    private SubScreenWidget createModuleList(Collection<BaseModule> baseModules) {
        SubScreenWidget subScreen = new SubScreenWidget(0, 0, 0, 0);
        int yLevel = 0;
        for (var entry : baseModules) {
            DrawableWidget widget = createClickableModuleWidget(entry);
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

    public List<Text> getSettingDescriptionTooltips(String key) {
        return ChatUtils.parseTooltipsTranslation(key + ".tooltips", "暂无介绍");
    }

    private DrawableWidget createClickableModuleWidget(BaseModule baseModule) {
        FlagRef bindFlag = baseModule.getBindFlag();
        return ExecutableWidget.instance(
                        0, 0, (int) widgetSize.get().x(), (int) widgetSize.get().y())
                .setElementHandler(new ColorBoxElement(
                                bindFlag != null
                                        ? ButtonAction.isLeft((bl) -> {
                                            if (bl) {
                                                bindFlag.toggle();
                                            } else {
                                                openConfigurateScreen(baseModule);
                                            }
                                        })
                                        : ButtonAction.run(() -> openConfigurateScreen(baseModule)),
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

    private void openConfigurateScreen(BaseModule baseModule) {
        int width = indexWidth + blankWidth + buttonWidth;
        DynamicListWidget listWidget = new DynamicListWidget(0, 0, width);

        listWidget.addDrawableChild(ExecutableWidget.instance(0, 0, width, buttonHeight)
                .setElementHandler(new ColorLabelTextElement(
                                TextProvider.of(getModuleName(baseModule)),
                                () -> this.textColor.get().withAlpha(255),
                                () -> this.moduleListColor.get().withAlpha(255))
                        .withTooltips(TooltipHandler.of(getModuleDescriptionTooltips(baseModule)))));
        // 占位符。tmd
        for (var configWidget : baseModule.getEditableConfig()) {
            SubScreenWidget keyValue = new SubScreenWidget(0, 0, width, buttonHeight + buttonBlank);
            keyValue.addDrawableChild(DisplayWidget.instance(0, 0, width, buttonBlank));
            AttrKeyValue<?> holder = configWidget.getSecond();
            Text six = Text.translatableWithFallback(holder.getKeyName(), holder.getKeyName());
            // add background placeholder , for isMouseOver()
            keyValue.addDrawableChild(DisplayWidget.instance(0, buttonBlank, width, buttonHeight));
            keyValue.addDrawableChild(ExecutableWidget.instance(0, buttonBlank, indexWidth, buttonHeight)
                    .setElementHandler(new ColorLabelTextElement(
                                    TextProvider.of(six),
                                    () -> this.textColor.get().withAlpha(255),
                                    () -> this.configColor.get().withAlpha(255))
                            .withTooltips(TooltipHandler.of(getSettingDescriptionTooltips(holder.getKeyName())))));
            DrawableWidget widget =
                    configWidget.getSecond().generateValueWidget(indexWidth + blankWidth, 0, buttonWidth, buttonHeight);
            keyValue.addDrawableChild(widget);
            BooleanSupplier showCondition = configWidget.getFirst();
            DynamicContentWidget<?> contentWidget =
                    new DynamicContentWidget<>(() -> showCondition.getAsBoolean() ? keyValue : null, 0, 0);
            listWidget.addDrawableChild(contentWidget);
        }
        baseModule.addCustomWidgets(listWidget::addDrawableChild);
        Screen screen = new CenterScreen(listWidget);
        ScreenAccess.of(screen).openFromCurrent();
        // SubScreenWidget levelSubScreen = new SubScreenWidget(0, 0, 0,0);
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

    private DrawableWidget createSearchListContent(ClickGuiMetaData metaData) {
        SubScreenWidget subScreen = new SubScreenWidget(0, 0, 0, 0);
        int buttonWidth = (int) widgetSize.get().x();
        int buttonHeight = (int) widgetSize.get().y();
        DynamicListWidget listWidget = new DynamicListWidget(0, buttonHeight, buttonWidth);
        subScreen.addDrawableChild(listWidget);
        Runnable refreshListTask = () -> {
            CompletableFuture.supplyAsync(
                            () -> {
                                String filter = metaData.searching;
                                List<BaseModule> moduleFilter = new ArrayList<>();
                                List<BaseModule> settingsFilter = new ArrayList<>();
                                if (filter != null && !filter.isEmpty()) {
                                    for (var group : HackModules.getModuleGroups()) {
                                        getShowModuleList(group).forEach(module -> {
                                            String moduleName = module.getName();
                                            String moduleTranslationName =
                                                    ChatUtils.textToPlainString(getModuleName(module));
                                            // match any
                                            if (FilterService.nameMatch(moduleName, filter)
                                                    || (!Objects.equals(moduleName, moduleTranslationName)
                                                            && FilterService.nameMatch(
                                                                    moduleTranslationName, filter))) {
                                                moduleFilter.add(module);
                                            }
                                            if (module.getEditableConfig().stream()
                                                    .anyMatch((editable) -> {
                                                        String settingsName =
                                                                ChatUtils.parseTranslation(editable.getSecond()
                                                                        .getKeyName());
                                                        return FilterService.nameMatch(settingsName, filter);
                                                    })) {
                                                settingsFilter.add(module);
                                            }
                                        });
                                    }
                                    return Pair.of(moduleFilter, settingsFilter);
                                } else {
                                    return null;
                                }
                            },
                            ScheduleService.getSingleThreadScheduler())
                    .thenAcceptAsync(
                            (pair) -> {
                                listWidget.clearChildren();
                                if (pair != null) {
                                    createSearchResultGroupSubList(
                                            listWidget::addDrawableChild, "Name", pair.getFirst());
                                    createSearchResultGroupSubList(
                                            listWidget::addDrawableChild, "Setting", pair.getSecond());
                                }
                            },
                            mc);
        };
        ContentDelegateWidget<TextFieldWidget> inputWidget = McWidgetHelpers.createTextFieldEditBox(
                0,
                0,
                buttonWidth,
                buttonHeight,
                (v, t) -> {
                    if (!Objects.equals(t, metaData.searching)) {
                        metaData.setSearching(t);
                        refreshListTask.run();
                    }
                },
                metaData.searching);
        // initialize
        refreshListTask.run();
        subScreen.addDrawableChild(inputWidget);
        return subScreen;
    }

    private void createSearchResultGroupSubList(
            Consumer<DrawableWidget> childrenAdder, String group, List<BaseModule> list) {
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
                                    showFlag.booleanValue() ? Constants.EXPAND_GUI_ON_SPRITE : Constants.EXPAND_GUI_OFF_SPRITE,
                                    element.getTextureWidth() - element.getTextureHeight() + 2,
                                    2,
                                    0,
                                    element.getTextureHeight() - 4,
                                    element.getTextureHeight() - 4);
                            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        })))
                .addToSub(subScreen);
        childrenAdder.accept(subScreen);
        var re = createModuleList(list);
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
                                if (type == Type.MOUSE_RELEASE || (type == Type.MOUSE_CLICK && button != 0)) {
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

    private DrawableWidget createTest(ClickGuiMetaData meta) {
        return new SubScreenWidget(0, 0, 0, 0);
    }

    private DrawableWidget createConfig(ClickGuiMetaData meta) {
        var screen = new ConfigurateNewStyleScreen(Config.getConfigs().stream().toList());
        screen.init(mc, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        return new ContentDelegateWidget<>(0, 0, 0, 0).setContentDelegate(screen);
    }

    @Getter
    public static class ClickGuiMetaData {
        Map<String, ModuleSlideMeta> moduleMetaMap;

        @Setter
        String searching;

        public ClickGuiMetaData() {
            this.moduleMetaMap = new LinkedHashMap<>();
            this.searching = "";
        }

        public ClickGuiMetaData(Map<String, ModuleSlideMeta> moduleCoordinates, String searching) {
            this.moduleMetaMap = new LinkedHashMap<>(moduleCoordinates);
            this.searching = searching;
        }

        public void checkDefault(String moduleName, int x, int y) {
            if (!moduleMetaMap.containsKey(moduleName)) {
                ModuleSlideMeta newMeta = new ModuleSlideMeta(x, y, false);
                moduleMetaMap.put(moduleName, newMeta);
            }
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
            this.x = Math.max(x, 0);
        }

        public void setY(int y) {
            this.y = Math.max(y, 0);
        }

        boolean slidingDown;

        public static Codec<ModuleSlideMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("x_coord").forGetter(ModuleSlideMeta::getX),
                        Codec.INT.fieldOf("y_coord").forGetter(ModuleSlideMeta::getY),
                        Codec.BOOL.fieldOf("sliding_down").forGetter(ModuleSlideMeta::isSlidingDown))
                .apply(instance, ModuleSlideMeta::new));
    }
}
