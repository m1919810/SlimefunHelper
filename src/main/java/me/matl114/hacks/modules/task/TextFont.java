package me.matl114.hacks.modules.task;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import me.matl114.gui.FilterService;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicContentWidget;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.StringRef;
import me.matl114.renderer.TextRenderers;
import me.matl114.renderer.text.CustomFontTextDrawer;
import me.matl114.renderer.text.LocalFontTextDrawer;
import me.matl114.renderer.text.ResourceFontTextDrawer;
import me.matl114.renderer.text.TextDrawer;
import me.matl114.renderer.text.VanillaTextDrawer;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TextFont extends BaseModule {
    private static final String DEFAULT_FONT_NAME = "华文细黑 Regular";

    public TextFont() {
        super("Font");
        INSTANCE = this;
    }

    public static TextFont INSTANCE;
    public final ModulePath root = makePath(Configs.MISC_CONFIG, "text-fonts");
    public final EnumRef<TextStyle> textStyle = builder(root.add("style"), TextStyle.class)
            .defaultValue(TextStyle.SYSTEM)
            .updateListener(s -> updateTextFont())
            .build();

    public final StringRef textFontType = builder(root.add("font-name"), StringRef.TYPE)
            .defaultValue("")
            .show(() -> textStyle.get().isIn(TextStyle.VANILLA))
            .updateListener(s -> updateTextFont())
            .build();

    public final StringRef resourceFontType = builder(root.add("resource-font"), StringRef.TYPE)
            .defaultValue("minecraft:default")
            .show(() -> textStyle.get().isIn(TextStyle.FONT))
            .updateListener(s -> updateTextFont())
            .build();

    public final DoubleRef fontSize = doubleBuilder(root.add("font-size"))
            .defaultValue(7.0D)
            .validator(Configs.doubleRange(1.0D, 96.0D))
            .show(() -> textStyle.get().isIn(TextStyle.SYSTEM))
            .updateListener(size -> updateTextFont())
            .build();

    @Override
    public void onCreate() {
        super.onCreate();
        String fontSelection = textFontType.get();
        if (fontSelection.isEmpty()) {
            fontSelection = DEFAULT_FONT_NAME;
        }
        LocalFontTextDrawer.findFont(fontSelection)
                .map(LocalFontTextDrawer.FontFile::path)
                .map(path -> path.toAbsolutePath().normalize().toString())
                .filter(path -> !path.equals(textFontType.get()))
                .ifPresent(textFontType::set);
    }

    private void updateTextFont() {
        // delay because it might init before the openGl init
        Tasks.scheduleDelayed(
                () -> {
                    try {
                        TextRenderers.setRenderer(getTextDrawer());
                    } catch (Throwable throwable) {
                        TextRenderers.setRenderer(new VanillaTextDrawer());
                    }
                },
                0);
    }

    public TextDrawer getTextDrawer() {
        return switch (textStyle.get()) {
            case VANILLA -> new CustomFontTextDrawer();
            case SYSTEM -> LocalFontTextDrawer.findFont(textFontType.get())
                    .map(LocalFontTextDrawer.FontFile::path)
                    .map(path -> new LocalFontTextDrawer(path, (float) fontSize.get()))
                    .map(TextDrawer.class::cast)
                    .orElseGet(VanillaTextDrawer::new);
            case FONT -> {
                Identifier fontId = Identifier.tryParse(resourceFontType.get());
                yield fontId == null ? new VanillaTextDrawer() : new ResourceFontTextDrawer(fontId);
            }
        };
    }

    public List<LocalFontTextDrawer.FontFile> getAvailableFonts() {
        return LocalFontTextDrawer.scanFonts();
    }

    public List<Identifier> getAvailableResourceFonts() {
        return MinecraftClient.getInstance()
                .getResourceManager()
                .findResources(
                        "font",
                        id -> id.getPath().startsWith("font/") && id.getPath().endsWith(".json"))
                .keySet()
                .stream()
                .map(id -> id.withPath(
                        id.getPath().substring("font/".length(), id.getPath().length() - ".json".length())))
                .sorted(Comparator.comparing(Identifier::toString, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public void addCustomWidgets(
            Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank, ElementFactory factory) {
        super.addCustomWidgets(acceptor, dx, dy, dblank, factory);
        List<LocalFontTextDrawer.FontFile> fonts = getAvailableFonts();
        ValueAccessor<String> systemFilter = ValueAccessor.holder("");
        addDynamicWidget(
                acceptor,
                factory.createTitle(
                        () -> Text.translatable("widget.text-font.system-fonts.title"), List::of, 0, dblank, dx, dy),
                TextStyle.SYSTEM);
        addDynamicWidget(
                acceptor, FilterService.createFilter(systemFilter, () -> {}, 0, dblank, dx, dy), TextStyle.SYSTEM);
        if (fonts.isEmpty()) {
            addDynamicWidget(
                    acceptor,
                    factory.createLabel(
                            () -> Text.translatable("widget.text-font.system-fonts.empty"),
                            List::of,
                            0,
                            dblank,
                            dx,
                            dy),
                    TextStyle.SYSTEM);
        }

        for (LocalFontTextDrawer.FontFile font : fonts) {
            String fontName = font.displayName();
            String fontPath = font.path().toAbsolutePath().normalize().toString();
            DrawableWidget fontButton = factory.createExecuteButton(
                    () -> fontPath.equals(textFontType.get())
                            ? Text.translatable("widget.text-font.font-entry.selected", fontName)
                            : Text.translatable("widget.text-font.font-entry.name", fontName),
                    () -> List.of(Text.translatable(
                            "widget.text-font.font-entry.system-path",
                            font.path().toString())),
                    ButtonAction.run(() -> textFontType.set(fontPath)),
                    0,
                    dblank,
                    dx,
                    dy);
            addDynamicWidget(
                    acceptor,
                    new DynamicContentWidget<>(
                            () -> FilterService.nameMatch(fontName, systemFilter.getValue()) ? fontButton : null, 0, 0),
                    TextStyle.SYSTEM);
        }

        List<Identifier> resourceFonts = getAvailableResourceFonts();
        ValueAccessor<String> resourceFilter = ValueAccessor.holder("");
        addDynamicWidget(
                acceptor,
                factory.createTitle(
                        () -> Text.translatable("widget.text-font.resource-fonts.title"), List::of, 0, dblank, dx, dy),
                TextStyle.FONT);
        addDynamicWidget(
                acceptor, FilterService.createFilter(resourceFilter, () -> {}, 0, dblank, dx, dy), TextStyle.FONT);
        if (resourceFonts.isEmpty()) {
            addDynamicWidget(
                    acceptor,
                    factory.createLabel(
                            () -> Text.translatable("widget.text-font.resource-fonts.empty"),
                            List::of,
                            0,
                            dblank,
                            dx,
                            dy),
                    TextStyle.FONT);
        }
        for (Identifier fontId : resourceFonts) {
            String fontName = fontId.toString();
            DrawableWidget fontButton = factory.createExecuteButton(
                    () -> fontName.equals(resourceFontType.get())
                            ? Text.translatable("widget.text-font.font-entry.selected", fontName)
                            : Text.translatable("widget.text-font.font-entry.name", fontName),
                    () -> List.of(Text.translatable(
                            "widget.text-font.font-entry.resource-path", fontId.getNamespace(), fontId.getPath())),
                    ButtonAction.run(() -> resourceFontType.set(fontName)),
                    0,
                    dblank,
                    dx,
                    dy);
            addDynamicWidget(
                    acceptor,
                    new DynamicContentWidget<>(
                            () -> FilterService.nameMatch(fontName, resourceFilter.getValue()) ? fontButton : null,
                            0,
                            0),
                    TextStyle.FONT);
        }
    }

    private void addDynamicWidget(Consumer<DrawableWidget> acceptor, DrawableWidget widget, TextStyle style) {
        acceptor.accept(new DynamicContentWidget<>(() -> textStyle.get() == style ? widget : null, 0, 0));
    }

    public enum TextStyle implements ConfigEnum {
        VANILLA,
        SYSTEM,
        FONT;

        @Override
        public String getConfigEnumType() {
            return "text_style";
        }
    }
}
