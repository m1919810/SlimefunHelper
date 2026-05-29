package me.matl114.hacks.modules.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.stream.Stream;
import me.matl114.accessors.access.ChatScreenAccess;
import me.matl114.commands.MainCommand;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.*;
import me.matl114.utils.ClientUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.collections.Point;
import me.matl114.utils.commands.commandGroup.BridgeSubCommand;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import net.minecraft.client.Keyboard;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.TextureFilteringMode;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SleepMode extends BaseModule {

    private int sleepingLevel = 0;
    public final ModulePath render = makePath(Configs.RENDER_CONFIG, "render");

    public SleepMode() {}

    public final KeyBindRef keyBindRef = hotkey(
                    Configs.RENDER_CONFIG, render.add("wake-up-screen").toPath())
            .defaultValue(new MultiKeyBind(KeyCode.KEY_F11))
            .build();

    public final FlagRef runnerOptimize =
            flagBuilder(render.add("sleep-mode-runner-optimize")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        // todo: make them  temporary listeners
        registerListener(Listener.getGameRender(), this::onGameRender);
        registerListener(Listener.getResolutionChange(), this::onSleepingResizeScreen);
        registerListener(Listener.getMidSetScreen(), this::interceptScreenSetup);
        registerListener(Listener.getKeyboardInput(), this::interceptScreenKeyboardAction);
        registerListener(Listener.getMouseButton(), this::interceptScreenMouseAction);
        registerListener(Listener.getMouseScroll(), this::interceptScreenMouseScroll);
        registerListener(Listener.getCharTyped(), this::interceptCharType);
        registerListener(Listener.getMouseMove(), this::interceptMouseMove);
        registerListener(Listener.getMouseDrag(), this::interceptMouseDragged);
        registerListener(Listener.getPreSetScreen(), this::interceptSetScreen);
        registerCommandBootstrap(this::onSleepCommandBootstrap);
        registerListener(Listener.getPacketPoint().getChannel(ChunkDataS2CPacket.class), this::onChunkData);
    }

    boolean runnerOptimizeStart = false;

    public void checkOptimizeState() {
        if (!isScreenSleeping()) {
            runnerOptimizeStart = false;
        }
    }

    public void onSleepCommandBootstrap(MainCommand mainCommand) {
        mainCommand.registerSub(new BridgeSubCommand(
                "sleep",
                SubCommand.taskBuilder()
                        .name("sleep")
                        .helper("<level> <confirm> 进入睡眠状态")
                        .arg(SimpleCommandArgs.argumentBuilder()
                                .name("level")
                                .intValue()
                                .build())
                        .arg(SimpleCommandArgs.argumentBuilder()
                                .name("confirm")
                                .dispatchLastArg((str) -> {
                                    int val = str.getInt();
                                    if (val > 0) {
                                        return Stream.of("confirm");
                                    } else {
                                        return Stream.of("第一个参数请输入正整数");
                                    }
                                })
                                .defaultValue("")
                                .build())
                        .arg(SimpleCommandArgs.argumentBuilder().name("display").build())
                        .post(e -> e.executor(CommandContext.run(this::onSleep)))
                        .build()));
    }

    public void onSleep(ArgumentInputStream re) {
        int level = re.nextClampedInt(1, 3);
        if (level != 1 && level != 2) {
            Debug.chat("请输入范围内的数字: 1~2");
            return;
        }
        String val = re.nextNonnull();
        String val2 = re.nextArg();
        if ("confirm".equals(val)) {
            if (runnerOptimize.get()) {
                runnerOptimizeStart = true;
            }
            Tasks.scheduleDelayed(() -> RenderTasks.getSleepMode().setCustomScreenSleeping(level, val2), 1);
        } else {
            Debug.chat("使用sleep confirm 确认进入睡眠模式, 进入睡眠模式后可以按 "
                    + RenderTasks.getSleepMode().getWakeupButton() + " 键离开");
        }
    }

    public void onGameRender(Event<GameRenderer> rendererEvent) {
        if (isScreenSleeping()) {
            if (sleepingRenderTick(rendererEvent.context(), rendererEvent.getArgs(0))) {
                rendererEvent.cancel();
            }
        }
    }

    public boolean isScreenSleeping() {
        return sleepingLevel != 0;
    }

    public boolean wakeUpScreen() {
        if (setScreenSleeping(0)) {
            if (mc.player != null) Debug.chat(Text.literal("睡眠状态结束, 欢迎回来!").formatted(Formatting.GREEN));
            return true;
        } else return false;
    }

    public boolean setScreenSleeping(int s) {
        return setCustomScreenSleeping(s, null);
    }

    public boolean setCustomScreenSleeping(int s, String sleep) {
        if (sleepingLevel != s) {

            if (s != 0) {
                sleepingLevel = s;
                setUpSleepingScreen(sleep == null ? getDefaultDisplayText() : Text.literal(sleep));
            } else {
                // sleeping = false;
                sleepingLevel = s;
                // 递归关闭全部sleepingScreen
                //                while (mc.currentScreen != null && mc.currentScreen == sleepingScreenInstance){
                //                    sleepingScreenInstance.close();
                //                }
                sleepingScreenInstance = null;
                currentRenderingSleeping = null;
                if (mc.currentScreen == null) {
                    mc.setScreen(null);
                }
            }
            return true;
        }
        return false;
    }

    private Screen sleepingScreenInstance;
    private Screen currentRenderingSleeping;

    public Screen getCurrentRenderingSleeping() {
        return currentRenderingSleeping;
    }

    private class SleepingChatScreen extends ChatScreen implements SleepOverlay {
        Text displayMessage;

        public SleepingChatScreen(String originalChatText, Text displayMessage) {
            super(originalChatText, false);
            this.displayMessage = displayMessage;
        }

        protected void init() {
            super.init();
            sleepingScreenInstance = this;
            DisplayWidget.instance(this.width - 80, 0, 80, 40)
                    .setRenderHandler(LabelElement.instance(displayMessage))
                    .addTo(this);
            shouldFreshSleepScreen = true;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            //            Debug.info(mouseX, mouseY, mc.inGameHud.getChatHud().getTextStyleAt(mouseX, mouseY));
            shouldFreshSleepScreen = true;
        }

        public boolean keyPressed(KeyInput input) {
            // fix: SleepingScreen may be wrongly set on currentScreen
            if (sleepingScreenInstance == this && input.isEnter()) {
                // intercept send, else left for super
                this.sendMessage(this.chatField.getText(), true);
                this.chatField.setText("");
                ChatScreenAccess.of(this).resetMessageHistoryIndex();
                return true;
            } else return super.keyPressed(input);
        }

        @Override
        public void close() {
            // do not close till sleeping is over or game exit
            if (sleepingScreenInstance != this) {
                super.close();
            }
            //            if(mc.currentScreen == this){
            //                super.close();
            //                return;
            //            }
            //            if(mc.player == null || !isScreenSleeping()){
            //                super.close();
            //            }
        }
    }

    private class SleepingScreen extends Screen implements SafeSleepingScreen {
        Text displayMessage;

        protected SleepingScreen(Text title, Text displayMessage) {
            super(title);
            this.displayMessage = displayMessage;
        }

        @Override
        protected void init() {
            super.init();
            sleepingScreenInstance = this;
            DisplayWidget.instance(40, 40, this.width - 80, this.height - 80)
                    .setRenderHandler(LabelElement.instance(displayMessage))
                    .addTo(this);
            shouldFreshSleepScreen = true;
        }

        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    }

    private class GameExitWhileSleepingScreen extends Screen implements SafeSleepingScreen {
        protected GameExitWhileSleepingScreen() {
            super(Text.empty());
        }

        @Override
        protected void init() {
            super.init();
            sleepingScreenInstance = this;
            DisplayWidget.instance(40, 20, this.width - 80, this.height / 3 - 40)
                    .setRenderHandler(LabelElement.instance(Text.literal("您的游戏在待机中退出,目前已停止刷新")))
                    .addTo(this);
            DisplayWidget.instance(40, this.height / 3 + 20, this.width - 80, this.height / 3 - 40)
                    .setRenderHandler(LabelElement.instance(Text.literal("按 " + getWakeupButton() + " 键退出休眠模式")))
                    .addTo(this);
            ExecutableWidget.instance(40, (this.height * 2) / 3 + 20, this.width - 80, this.height / 3 - 40)
                    .setElementHandler(
                            new ButtonElement(TextProvider.of(Text.literal("点击下方按钮以刷新屏幕")), ButtonAction.run(() -> {
                                if (isScreenSleeping()) {
                                    if (ClientUtils.isPlayerOnline()) {
                                        sleepingScreenInstance = null;
                                        setUpSleepingScreen(getDefaultDisplayText());
                                    } else {
                                        // keep this screen
                                    }
                                }
                            })))
                    .addTo(this);
            shouldFreshSleepScreen = true;
        }

        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    }

    private Text getDefaultDisplayText() {
        return Text.literal("按 " + getWakeupButton() + " 键退出休眠模式");
    }

    private static interface SafeSleepingScreen extends SleepOverlay {
        // screen which implement this can keep even when player exit game, which means it does not need mc.player or
        // mc.world or sth
    }
    //
    public void setUpSleepingScreen(Text display) {
        if (sleepingScreenInstance == null) {
            switch (sleepingLevel) {
                case 1:
                    sleepingScreenInstance = new SleepingChatScreen("", display);
                    break;
                default:
                    sleepingScreenInstance = new SleepingScreen(Text.empty(), display);
                    break;
            }
        }
    }

    private void setCurrentRenderingSleeping(Screen screen) {
        if (screen != null) {
            mc.mouse.unlockCursor();
            KeyBinding.unpressAll();
            currentRenderingSleeping = screen;
            currentRenderingSleeping.init(
                    mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());

        } else {
            currentRenderingSleeping = null;
            // reset cursor and keybinds
            if (mc.currentScreen != null) {
                mc.mouse.unlockCursor();
                KeyBinding.unpressAll();
            } else {
                mc.mouse.lockCursor();
                mc.getSoundManager().resumeAll();
            }
        }
    }

    public boolean ensureSleepingScreen() {
        if (!isScreenSleeping()) {
            return false;
        }
        boolean refresh = false;
        if (ClientUtils.isPlayerOnline()) {
            if (currentRenderingSleeping != sleepingScreenInstance) {
                setCurrentRenderingSleeping(sleepingScreenInstance);
                refresh = true;
            }
        } else {
            if (!(sleepingScreenInstance instanceof GameExitWhileSleepingScreen)) {
                setCurrentRenderingSleeping(sleepingScreenInstance = new GameExitWhileSleepingScreen());
                refresh = true;
            }
        }
        if (refresh) {
            // clear current  view
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .clearDepthTexture(mc.getFramebuffer().getDepthAttachment(), 1.0);
            mc.gameRenderer.guiState.clear();
            //            mc.getFramebuffer().clear(true);
            //            mc.getFramebuffer().endRead();
            //            mc.getFramebuffer().beginWrite(true);
            return true;
        }
        return true;
    }

    private boolean shouldFreshSleepScreen = false;

    public void onSleepingResizeScreen(Event<Point> event) {
        if (currentRenderingSleeping != null) {
            currentRenderingSleeping.resize(event.context.x, event.context.y);
        }
    }

    public boolean sleepingRenderTick(GameRenderer gameRenderer, RenderTickCounter tickCounter) {

        if (ensureSleepingScreen()) {
            if (currentRenderingSleeping != null) {
                shouldFreshSleepScreen = true;
                if (shouldFreshSleepScreen) {
                    shouldFreshSleepScreen = false;
                    mc.gameRenderer
                            .getGlobalSettings()
                            .set(
                                    mc.getWindow().getFramebufferWidth(),
                                    mc.getWindow().getFramebufferHeight(),
                                    (Double) mc.options.getGlintStrength().getValue(),
                                    mc.world == null ? 0L : mc.world.getTime(),
                                    tickCounter,
                                    mc.options.getMenuBackgroundBlurrinessValue(),
                                    mc.gameRenderer.getCamera(),
                                    mc.options.getTextureFiltering().getValue() == TextureFilteringMode.RGSS);

                    int i = (int) (mc.mouse.getX()
                            * (double) mc.getWindow().getScaledWidth()
                            / (double) mc.getWindow().getWidth());
                    int j = (int) (mc.mouse.getY()
                            * (double) mc.getWindow().getScaledHeight()
                            / (double) mc.getWindow().getHeight());

                    RenderSystem.getDevice()
                            .createCommandEncoder()
                            .clearDepthTexture(mc.getFramebuffer().getDepthAttachment(), 1.0);
                    mc.gameRenderer.guiState.clear();
                    DrawContext drawContext = new DrawContext(mc, mc.gameRenderer.guiState, i, j);

                    currentRenderingSleeping.renderWithTooltip(drawContext, i, j, tickCounter.getDynamicDeltaTicks());
                    mc.gameRenderer.guiRenderer.render(
                            mc.gameRenderer.fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));
                    mc.gameRenderer.guiRenderer.incrementFrame();
                    drawContext.applyCursorTo(mc.getWindow());
                    mc.gameRenderer.getEntityRenderCommandQueue().onNextFrame();
                    mc.gameRenderer.getEntityRenderDispatcher().endLayeredCustoms();
                    mc.gameRenderer.pool.decrementLifespan();
                }
            } else {
                setUpSleepingScreen(getDefaultDisplayText());
            }
            return true;
        }
        return false;
    }

    public void interceptScreenSetup(Event<Screen> event) {
        if (isScreenSleeping()) {
            event.cancel();
        }
    }

    public void interceptScreenKeyboardAction(Event<Keyboard> event) {
        if (isScreenSleeping()) {
            event.cancel();
            if (((Integer) event.getArgs(0)).intValue() == keyBindRef.get().getLastKey()) {
                wakeUpScreen();
                return;
            }
            if (sleepingScreenInstance != null) {
                ScreenUtils.simulateKeyAction(
                        sleepingScreenInstance,
                        (Integer) event.extraArgs[0],
                        (Integer) event.extraArgs[1],
                        (Integer) event.extraArgs[2],
                        (Integer) event.extraArgs[3]);
            }
        }
    }

    public void interceptScreenMouseAction(Event<Mouse> event) {
        if (isScreenSleeping()) {
            event.cancel();
            if (sleepingScreenInstance != null) {
                ScreenUtils.simulateMouseButton(
                        sleepingScreenInstance, (Integer) event.getArgs(0), (Integer) event.getArgs(1), (Integer)
                                event.getArgs(2));
            }
        }
    }

    public void interceptScreenMouseScroll(Event<Mouse> event) {
        if (isScreenSleeping()) {
            event.cancel();

            if (sleepingScreenInstance != null) {
                ScreenUtils.simulateMouseScroll(
                        sleepingScreenInstance, (double) event.extraArgs[0], (double) event.extraArgs[1]);
            }
        }
    }

    public void interceptCharType(Event<Character> event) {
        if (isScreenSleeping()) {
            event.cancel();
            if (sleepingScreenInstance != null) {
                sleepingScreenInstance.charTyped(new CharInput(event.context, (Integer) event.extraArgs[1]));
            }
        }
    }

    public void interceptMouseMove(Event<Mouse> event) {
        if (isScreenSleeping()) {
            event.cancel();
            if (sleepingScreenInstance != null) {
                sleepingScreenInstance.mouseMoved((Double) event.extraArgs[0], (Double) event.extraArgs[1]);
            }
        }
    }

    public void interceptMouseDragged(Event<Mouse> event) {
        if (isScreenSleeping()) {
            event.cancel();
            if (sleepingScreenInstance != null) {
                sleepingScreenInstance.mouseDragged(
                        new Click((double) event.extraArgs[0], (double) event.extraArgs[1], event.context.activeButton),
                        (Double) event.extraArgs[2],
                        (Double) event.extraArgs[3]);
            }
        }
    }

    private static interface SleepOverlay {}

    public String getWakeupButton() {
        return keyBindRef.get().getKeyStr();
    }

    public void interceptSetScreen(Event<Screen> setScreen) {
        if (setScreen.context instanceof SleepOverlay) {
            setScreen.cancel();
            //
            mc.setScreen(null);
        }
    }

    public void onChunkData(Event<ChunkDataS2CPacket> dataS2CPacket) {
        if (!runnerOptimizeStart) return;
        checkOptimizeState();
        if (checkNull()) return;
        if (runnerOptimizeStart && mc.player.getY() > mc.world.getBottomY() + mc.world.getHeight()) {
            dataS2CPacket.cancel();
        }
    }

    // TODO: add status renderer , inGameHud
}
