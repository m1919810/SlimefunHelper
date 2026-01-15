package me.matl114.hackUtils.modules.extra;

import com.google.common.util.concurrent.Runnables;
import me.matl114.access.ScreenAccess;
import me.matl114.gui.presets.choices.QuestionScreen;
import me.matl114.hackUtils.modules.BaseModule;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.utils.UtilClass.Event;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class ClientExtra extends BaseModule {
    public ClientExtra(){

    }

    public static final String[] TEST_NO_CRASH = {
        "other", "no-client-crash"
    };

    public final Config.FlagRef noCrash = Configs.TEST_CONFIG
        .builder(Boolean.class)
        .path(TEST_NO_CRASH)
        .defaultValue(false)
        .build();


    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getClientMainExit(), this::onCrash);
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

    protected void checkClientData(Screen screen){
        if(mc.player != null && mc.world != null && mc.inGameHud != null && mc.getNetworkHandler() != null){
            ScreenAccess.of(screen).openFromCurrent();
        }else{
            //严重问题
            mc.disconnect(screen);
        }
    }

}
