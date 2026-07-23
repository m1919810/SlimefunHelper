package me.matl114.hacks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Listener;
import me.matl114.gui.complex.config.ConfigurateNewStyleScreen;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModuleEntry;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.task.*;
import me.matl114.hacks.modules.task.ConnectionProxy;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.utils.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;

public class MainTasks {
    public static void init() {}

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static List<String> getSpecialTaskName() {
        return List.of(
                "xray_demo",
                "writable_book_generate",
                "strider_fix",
                "client_crash",
                "client_lite_crash",
                "check_translation_key",
                "show_window");
    }

    @ApiMethod
    public static void runSpecialTask(String taskId, String[] args) {
        try {
            switch (taskId) {
                case "xray_demo" -> {
                    int a = Integer.parseInt(args[0]);
                    int b = Integer.parseInt(args[1]);
                    int c = Integer.parseInt(args[2]);
                }
                case "writable_book_generate" -> {
                    generateWritableBookContent(args);
                }
                case "strider_fix" -> {
                    versionedStriderFix(args);
                }
                case "client_crash" -> {
                    clientCrash(args);
                }
                case "client_lite_crash" -> {
                    clientLiteCrash(args);
                }
                case "check_translation_key" -> {
                    checkTranslationKey(args);
                }
                case "show_window" -> {
                    showWindows(args);
                }
            }
        } catch (Throwable e) {
            Debug.info(e);
        }
    }

    public static void generateWritableBookContent(String[] args) {
        if (mc.player != null) {
            if (mc.player.getMainHandStack().getItem() == Items.WRITABLE_BOOK) {
                Debug.chat("生成了书内容");
                String generatedContent = "§b§k" + ("1a锕β".repeat(250));
                mc.getNetworkHandler()
                        .sendPacket(new BookUpdateC2SPacket(
                                InventoryUtils.getSelectedSlot(),
                                Collections.nCopies(100, generatedContent),
                                args.length > 0 ? Optional.of(String.join("\n", args)) : Optional.empty()));
            } else {
                Debug.chat("手持物品不是书");
            }
        }
    }

    public static void versionedStriderFix(String[] args) {
        // no version problem now
    }

    // store the crash exception

    public static void clientCrash(String[] args) {
        Tasks.scheduleDelayed(
                () -> {
                    mc.world = null;
                    throw new CrashException(new CrashReport("test crash", new NullPointerException()));
                },
                1);
    }

    public static void clientLiteCrash(String[] args) {
        Tasks.scheduleDelayed(
                () -> {
                    throw new CrashException(new CrashReport("test crash", new NullPointerException()));
                },
                1);
    }

    public static void checkTranslationKey(String[] args) {
        Set<String> checkedKeys = new LinkedHashSet<>();
        List<String> missingKeys = new ArrayList<>();
        int wrapperConfigCount = 0;
        int moduleEntryCount = 0;
        int configEnumCount = 0;
        int clickGuiModuleNameCount = 0;
        int configIndexCount = 0;

        for (ModuleGroup group : HackModules.getModuleGroups()) {
            for (BaseModule module : group.getModules()) {
                for (var configWrapper : module.getEditableConfig()) {
                    if (configWrapper.experimental()) {
                        continue;
                    }
                    ++wrapperConfigCount;
                    checkTranslationKey(configWrapper.keyName(), checkedKeys, missingKeys);
                }
                for (ModuleEntry entry : module.getModuleEntries().toList()) {
                    ++moduleEntryCount;
                    checkTranslationKey(entry.getTranslationKey(), checkedKeys, missingKeys);
                }
                ++clickGuiModuleNameCount;
                if (module.hasEditableConfig()) {
                    checkTranslationText(MainTasks.clickGui.getModuleName(module), checkedKeys, missingKeys);
                }
            }
        }

        for (var enumGroup : ConfigEnum.registeredConfigs.values()) {
            for (ConfigEnum configEnum : enumGroup.values()) {
                ++configEnumCount;
                checkTranslationText(configEnum.getDisplay(), checkedKeys, missingKeys);
            }
        }

        for (Config config : Config.getConfigs()) {
            Set<String> indexes = new LinkedHashSet<>();
            for (String path : config.getVisiblePaths()) {
                String[] cut = Config.cutToPath(path);
                if (cut.length > 0) {
                    indexes.add(cut[0]);
                }
            }
            for (String index : indexes) {
                ++configIndexCount;
                checkTranslationKey("config.index." + index, checkedKeys, missingKeys);
            }
        }

        if (missingKeys.isEmpty()) {
            Debug.chat(Text.literal("翻译检查完成，WrapperConfig=" + wrapperConfigCount + "，快捷键入口=" + moduleEntryCount
                            + "，ConfigEnum=" + configEnumCount + "，ClickGui模块名=" + clickGuiModuleNameCount
                            + "，config.index=" + configIndexCount + "，未发现缺失翻译")
                    .formatted(Formatting.GREEN));
            return;
        }
        Debug.chat(Text.literal("翻译检查完成，WrapperConfig=" + wrapperConfigCount + "，快捷键入口=" + moduleEntryCount
                        + "，ConfigEnum=" + configEnumCount + "，ClickGui模块名=" + clickGuiModuleNameCount
                        + "，config.index=" + configIndexCount + "，共发现缺失翻译 " + missingKeys.size() + " 个")
                .formatted(Formatting.YELLOW));
        for (String key : missingKeys) {
            Debug.chat(Text.literal(" - " + key).formatted(Formatting.RED));
        }
    }

    private static void checkTranslationText(Text text, Set<String> checkedKeys, List<String> missingKeys) {
        if (text instanceof MutableText mutableText
                && mutableText.getContent() instanceof TranslatableTextContent translatableTextContent) {
            checkTranslationKey(translatableTextContent.getKey(), checkedKeys, missingKeys);
        }
    }

    private static void checkTranslationKey(String translationKey, Set<String> checkedKeys, List<String> missingKeys) {
        if (translationKey == null || translationKey.isEmpty() || !checkedKeys.add(translationKey)) {
            return;
        }
        if (!ChatUtils.hasTranslation(translationKey)) {
            missingKeys.add(translationKey);
            Debug.info("Missing translation key for", translationKey);
        }
    }

    public static void showWindows(String[] args) {
        // WindowUtils.createNotificationWindow("test1", "test2");
        //        WindowUtils.createNotificationTrayWindow("test1", "test2");
        // WindowUtils.createScriptNotificationWindow("test3", "test4");
    }

    public static void fillFakeSubChunkWithStone() {}

    @ApiMethod
    public static void openConfigNewStyleScreen() {
        ScreenAccess.of(new ConfigurateNewStyleScreen(
                        Config.getConfigs().stream().toList()))
                .openFromCurrent();
    }

    @ApiMethod
    public static void openConfigScreen(Config config) {
        ConfigurateNewStyleScreen newStyleScreen =
                new ConfigurateNewStyleScreen(Config.getConfigs().stream().toList());
        newStyleScreen.setGlobal(config);
        ScreenAccess.of(newStyleScreen).openFromCurrent();
    }

    @ApiMethod
    public static void openModuleScreen(BaseModule module) {
        clickGui.openConfigurateScreen(module);
    }

    public static final Text QUITTING_MULTIPLAYER_TEXT = Text.translatable("multiplayer.status.quitting");

    @ApiMethod
    public static void scheduleDisconnect() {
        Tasks.scheduleDelayed(MainTasks::disconnectImmediately, 0);
    }

    @ApiMethod
    public static void disconnectImmediately() {
        disconnect();
        if (Listener.getClientConnection() != null
                && Listener.getClientConnection().isOpen()) {
            Listener.getClientConnection().disconnect(QUITTING_MULTIPLAYER_TEXT);
        }
    }

    @ApiMethod
    public static void disconnect() {
        if (mc.world != null) {
            mc.world.disconnect(QUITTING_MULTIPLAYER_TEXT);
        }
        mc.disconnect(new ProgressScreen(true), false);

        TitleScreen titleScreen = new TitleScreen();
        mc.setScreen(new MultiplayerScreen(titleScreen));
    }

    @Getter
    private static final ModuleGroup moduleManager = new ModuleGroup("Tasks");

    @Getter
    public static ModuleSettings moduleSettings;

    @Getter
    public static ClickGui clickGui;

    @Getter
    public static ConfigManager configManager;

    @Getter
    public static BindCommand bindCommand;

    @Getter
    public static EventCommand eventCommand;

    @Getter
    public static ServerStorage serverStorage;

    @Getter
    public static ConnectionProxy connectionProxy;

    private static void initModule(ModuleManager m) {
        moduleSettings = new ModuleSettings().register(m);
        clickGui = new ClickGui().register(m);
        configManager = new ConfigManager().register(m);
        bindCommand = new BindCommand().register(m);
        eventCommand = new EventCommand().register(m);
        serverStorage = new ServerStorage().register(m);
        connectionProxy = new ConnectionProxy().register(m);
    }

    // TODO: add entity inspect in info command
    //
    static {
        moduleManager.registerFactories(MainTasks::initModule);
        HackModules.registerModuleGroup(moduleManager);
        MineTasks.init();
        ChatTasks.init();
        RenderTasks.init();
        InvTasks.init();
        CombatTasks.init();
        WorldTasks.init();
        MovTasks.init();
        InteractionTasks.init();
        SlimefunTasks.init();
        ModelTasks.init();
        ACTasks.init();
        ExtraTasks.init();
    }
}
