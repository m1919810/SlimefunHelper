package me.matl114.hacks.modules.extra;

import com.google.common.util.concurrent.Runnables;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.gui.presets.choices.QuestionScreen;
import me.matl114.hacks.api.BaseModule;
import me.matl114.events.Listener;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.Debug;
import me.matl114.events.Event;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

public class ClientExtra extends BaseModule {
    public ClientExtra(){

    }

    public static final String[] CLIENT_BRAND_NAME ={"other", "client-brand-name"};

    public static final String[] TEST_NO_CRASH = {
        "other", "no-client-crash"
    };
    public static final String[] IGNORE_PROTOCOL_ERROR = {
        "other", "no-disconnect-on-network-error"
    };
    //todo
    @ApiStatus.Experimental
    public static final String[] PORTAL_GUI = {
        "other", "keep-gui-open-on-portal"
    };
    @ApiStatus.Experimental
    public static final String[] FAKE_SPRINT_TEST = {
        "test", "fake-sprint"
    };

    public final FlagRef noCrash = builder(Configs.TEST_CONFIG
        ,Boolean.class)
        .path(TEST_NO_CRASH)
        .defaultValue(false)
        .build();

    public final FlagRef noNtwException = builder(Configs.TEST_CONFIG
        , Boolean.class)
        .path(IGNORE_PROTOCOL_ERROR)
        .defaultValue(false)
        .build();

    public final FlagRef portalGui = flagBuilder(Configs.TEST_CONFIG, PORTAL_GUI)
        .build();

    public final StringRef clientBrandName = builder(Configs.TEST_CONFIG, CLIENT_BRAND_NAME, StringRef.TYPE)
        .defaultValue("")
        .hideConfig()
        .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getClientMainExit(), this::onCrash);
        registerListener(Listener.getPacketListenerException(), this::onNetworkException);
    }
    private final Text questionCrash = Text.literal("你的游戏刚才因为未知原因崩溃,但是SlimefunHelper拦截了它").formatted(Formatting.RED);
    private final List<QuestionScreen.Solution> crashSolutions = List.of(
        QuestionScreen.Solution.of(Text.literal("我已知晓, 继续游戏").formatted(Formatting.GREEN), Runnables.doNothing()),
        QuestionScreen.Solution.of(Text.literal("我已知晓, 退出游戏").formatted(Formatting.RED), this::exitGame)
    );
    private void exitGame(){
        mc.scheduleStop();
    }

    public void onCrash(Event<MinecraftClient> event){
        if(event.canCancel() && event.context().isRunning() && noCrash.get()){
            event.cancel();
            //must disconnect from server here
            QuestionScreen screen = new QuestionScreen(questionCrash, crashSolutions);
            checkClientData(screen);
        }
    }

    public void onNetworkException(Event<Packet<?>> event){
        if(noNtwException.get()){
            Packet<?> packet = event.context();
            PacketListener listener = event.getArgs(0);
            Exception exception = event.getArgs(1);
            if(mc.player != null){
                Debug.chat(Text.literal("Error while handling a network packet: ").formatted(Formatting.RED).append(Text.literal(packet.getClass().getSimpleName())));
                Debug.chat(Text.literal( exception.getMessage() == null ? "Exception: null": exception.getMessage()));
            }
            Debug.info("Packet Exception INFO :" );
            Debug.info("  PacketListener : ", listener);
            Debug.info("  Packet :", packet);
            Debug.info("Exception StackTrace:");
            Debug.info(exception);
        }
    }

    protected void checkClientData(Screen screen){
        if(mc.player != null && mc.world != null && mc.inGameHud != null && mc.getNetworkHandler() != null){
            ScreenAccess.of(screen).openFromCurrent();
        }else{
            //严重问题
            mc.disconnect(screen, false);
        }
    }

}
