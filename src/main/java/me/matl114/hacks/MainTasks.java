package me.matl114.hacks;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Listener;
import me.matl114.gui.complex.config.ConfigurateNewStyleScreen;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.task.ClickGui;
import me.matl114.hacks.modules.task.ConfigSystem;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.Config;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.Debug;
import me.matl114.utils.InventoryUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;

public class MainTasks {
    public static void init() {}

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static List<String> getSpecialTaskName() {
        return List.of("xray_demo", "writable_book_generate", "strider_fix", "client_crash", "client_lite_crash");
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

    public static final Text QUITTING_MULTIPLAYER_TEXT = Text.translatable("multiplayer.status.quitting");

    @ApiMethod
    public static void scheduleDisconnect() {
        Tasks.scheduleDelayed(
                () -> {
                    disconnect(QUITTING_MULTIPLAYER_TEXT);
                    if (Listener.getClientConnection() != null
                            && Listener.getClientConnection().isOpen()) {
                        Listener.getClientConnection().disconnect(QUITTING_MULTIPLAYER_TEXT);
                    }
                },
                0);
    }

    @ApiMethod
    public static void disconnectImmediately() {
        disconnect(QUITTING_MULTIPLAYER_TEXT);
        if (Listener.getClientConnection() != null
                && Listener.getClientConnection().isOpen()) {
            Listener.getClientConnection().disconnect(QUITTING_MULTIPLAYER_TEXT);
        }
    }

    public static void disconnect(Text reasonText) {
        boolean bl = mc.isInSingleplayer();
        ServerInfo serverInfo = mc.getCurrentServerEntry();
        if (mc.world != null) {
            mc.world.disconnect(reasonText);
        }

        if (bl) {
            mc.disconnectWithSavingScreen();
        } else {
            mc.disconnectWithProgressScreen();
        }

        TitleScreen titleScreen = new TitleScreen();
        if (bl) {
            mc.setScreen(titleScreen);
        } else if (serverInfo != null && serverInfo.isRealm()) {
            mc.setScreen(new RealmsMainScreen(titleScreen));
        } else {
            mc.setScreen(new MultiplayerScreen(titleScreen));
        }
    }

    @Getter
    private static final ModuleGroup moduleManager = new ModuleGroup("Tasks");

    @Getter
    public static ConfigSystem configSystem;

    @Getter
    public static ClickGui clickGui;

    private static void initModule(ModuleManager m) {
        configSystem = new ConfigSystem().register(m);
        clickGui = new ClickGui().register(m);
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
        NetworksTasks.init();
        InteractionTasks.init();
        SlimefunTasks.init();
        ModelTasks.init();
        ACTasks.init();
        ExtraTasks.init();
    }
}
