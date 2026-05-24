package me.matl114.hacks.modules.chat;

import io.github.reserveword.imblocker.common.gui.FocusableObject;
import java.util.List;
import me.matl114.accessors.access.ChatScreenAccess;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hooks.IMBlockerHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.ScheduleService;
import me.matl114.managers.TaskManagers;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.PropertyTracker;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class ChatTools extends BaseModule {
    public final ModulePath chatTools = makePath(Configs.CHAT_CONFIG, "chat-screen-tools");

    public ChatTools() {
        bindFlag(enableChatScreenTools);
    }

    public final KeyBindRef removeCmdKey = hotkey(
                    Configs.CHAT_CONFIG,
                    chatTools.add("remove-command-prefix-hotkey").toPath(),
                    new MultiKeyBind())
            .registerHotkey(HotKeyUtils.asHandler(this::onRemoveCommandPrefix))
            .build();

    public final FlagRef enableChatScreenTools = flagBuilder(chatTools.add("enable-tools"))
            .updateListener(this::toggleBasicToolScreen)
            .build();

    public final FlagRef enableSpecialChars = flagBuilder(chatTools.add("enable-quick-chars"))
            .updateListener(this::toggleSpecialCharWidget)
            .build();

    public final StringRef specialChars = builder(chatTools.add("quick-chars"), String.class)
            .defaultValue("😡🤓🥵😭🤡😋🤤😊😄🥲😁👉👆🤔😎🐍😅♂♀")
            .updateListener(this::refreshSpecialChars)
            .build();

    public final StringRef chatCache =
            builder(chatTools.add("cached"), String.class).defaultValue("").build();

    public final FlagRef autoSend =
            toggle(Configs.CHAT_CONFIG, chatTools.add("auto-chat").toPath()).build();

    public final IntRef period =
            intBuilder(chatTools.add("auto-chat-period")).defaultValue(21).build();

    public final IntRef multiple =
            intBuilder(chatTools.add("auto-chat-multiple")).defaultValue(1).build();

    public final FlagRef keepChatInv =
            toggle(Configs.CHAT_CONFIG, chatTools.add("keep-chat-inv").toPath()).build();

    public final FlagRef obfLogin =
            flagBuilder(chatTools.add("obf-login-message")).build();

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        counter = 0;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(Listener.getPostInitializeScreen(), this::onChatScreenInitialize);
        registerListener(Listener.getPreSetScreen(), this::onCloseChatScreen);
        registerListener(Listener.getPostInitializeScreen(), this::fixIMBlockerStateError);
        TaskManagers.getToggleManager().register(TaskManagers.PREFIX_BUTTON_TOGGLE + "." + "auto-chat", autoSend);
        TaskManagers.getToggleManager()
                .register(TaskManagers.PREFIX_BUTTON_TOGGLE + "." + "keep-chat-inv", keepChatInv);
    }

    public void onRemoveCommandPrefix() {
        if (mc.currentScreen instanceof ChatScreen chatScreen) {
            if (chatScreen.getFocused() instanceof TextFieldWidget widget
                    && widget.getText().startsWith("/")) {
                widget.setText(widget.getText().substring(1));
            }
        }
    }

    public int counter = 0;

    public void onTick(Event<ClientPlayerEntity> gt) {
        if (mc.getNetworkHandler() != null && autoSend.get()) {
            counter += 1;
            if (counter >= period.getValue()) {
                counter = 0;
                for (var i = 0; i < multiple.get(); ++i) {
                    sendCachedMessage();
                }
            }
        }
    }

    private static final Identifier LOCK_ENABLE_SPRITE = Identifier.tryParse("slimefunhelper:gui/lock_enable");
    private static final Identifier LOCK_DISABLE_SPRITE = Identifier.tryParse("slimefunhelper:gui/lock_disable");
    private static final List<Text> TOOLTIPS_SEND_CACHE = List.of(Text.literal("发送缓存聊天框中的东西"));
    private static final List<Text> TOOLTIPS_AUTO_SEND =
            List.of(Text.literal("自动发送缓存聊天框中的东西"), Text.literal("查看配置界面以调整参数"));
    private static final List<Text> TOOLTIPS_KEEP_INV =
            List.of(Text.literal("切换是否keepChatInv"), Text.literal("若启用,回车发送文字后将仍保持在聊天界面"));
    private static final List<Text> TOOLTIPS_SEL_TO_UNICODE =
            List.of(Text.literal("点击将当前正在输入的输入框中"), Text.literal("输入的字符转为unicode字符"));
    private static final List<Text> TOOLTIPS_INT_TO_CHAR = List.of(Text.literal("可以将旁边的小输入框中的数字和字符进行ascii转换"));
    private static final List<Text> TOOLTIPS_ENCRYPT =
            List.of(Text.literal("左击切换是否进行消息加密"), Text.literal("右击以打开配置文件"), Text.literal("按住ctrl发送可以禁用加密"));
    private static final List<Text> TOOLTIPS_FORMAT = List.of(Text.literal("点卷切换是否进行聊天格式化"));
    private static final List<Text> TOOLTIPS_SPECIAL_CHARS =
            List.of(Text.literal("点击展开/关闭特殊字符快捷键"), Text.literal("可以在配置界面中配置特殊字符列表"));

    public void sendCachedMessage() {
        String val = chatCache.get();
        if (val != null) {
            ChatTasks.sayMessage(val, false);
        }
    }

    private String int2CharFieldContent = "";

    private void tranlateInt2char(TextFieldWidget int2CharInputField) {
        String value = int2CharInputField.getText();
        if (value.isEmpty()) return;
        try {
            int val = Integer.parseInt(value);
            try {
                char ch = (char) val;
                int2CharInputField.setText(String.valueOf(ch));
            } catch (Throwable e) {
                int2CharInputField.setText("Error");
            }
        } catch (Throwable e) {
            char ch = value.charAt(0);
            int2CharInputField.setText(String.valueOf(((int) ch)));
        }
    }

    SubScreenWidget basicSubScreenWidget;
    ContentDelegateWidget<SubScreenWidget> delegateToolScreen;
    ContentDelegateWidget<SubScreenWidget> delegateSpecialCharWidget;
    TextFieldWidget cacheWidget;
    TextFieldWidget int2CharInputField;

    @Nullable
    private TextFieldWidget findCurrentFocusing() {
        if (mc.currentScreen instanceof ChatScreen chat && chat.getFocused() instanceof TextFieldWidget textField) {
            return textField;
        } else if (basicSubScreenWidget != null
                && basicSubScreenWidget.isFocused()
                && basicSubScreenWidget.getSelected() instanceof ContentDelegateWidget<?> contentDelegateWidget
                && contentDelegateWidget.getDelegate() instanceof TextFieldWidget text) {
            return text;
        } else return null;
    }

    private static final Text ENABLE_STATE = Text.literal("-").setStyle(Style.EMPTY.withBold(true));
    private static final Text DISABLE_STATE = Text.literal("+").setStyle(Style.EMPTY.withBold(true));

    private void initToolWidget() {
        int totalWith = 250; // -250 ~ 0
        int totalHeight = 68; //  -104 ~ -36
        SubScreenWidget basicSubScreenWidget = new SubScreenWidget(0, 0, 250, 68);
        // -56 -> -56 - (-104)
        ContentDelegateWidget<TextFieldWidget> helperWidgetWrapper = McWidgetHelpers.createTextFieldEditBox(
                0, 48, 120, 20, PropertyTracker.event(chatCache::set), chatCache.get());
        cacheWidget = helperWidgetWrapper.getDelegate();

        // todo： add translatable to buttons and everything
        ExecutableWidget.instance(120, 48, 60, 20)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(Text.literal("send cache")),
                                ButtonAction.run(() -> ChatTasks.sayMessage(chatCache.get(), true)))
                        .withTooltips(TooltipHandler.of(TOOLTIPS_SEND_CACHE)))
                .addToSub(basicSubScreenWidget);

        Runnable toggle = TaskManagers.getToggleTask(
                Configs.CHAT_CONFIG, chatTools.add("auto-chat").toPath());
        ExecutableWidget.instance(180, 48, 50, 20)
                .setElementHandler(
                        new ButtonElement(TextProvider.of(Text.literal("auto-send")), ButtonAction.run(toggle))
                                .setActivePredicate(el -> autoSend.get())
                                .withTooltips(TooltipHandler.of(TOOLTIPS_AUTO_SEND)))
                .addToSub(basicSubScreenWidget);

        Runnable toggle2 = TaskManagers.getToggleTask(
                Configs.CHAT_CONFIG, chatTools.add("keep-chat-inv").toPath());
        ExecutableWidget.instance(180, 24, 70, 20)
                .setElementHandler(
                        new ButtonElement(TextProvider.of(Text.literal("keep-chat-inv")), ButtonAction.run(toggle2))
                                .setActivePredicate((el) -> keepChatInv.get())
                                .withTooltips(TooltipHandler.of(TOOLTIPS_KEEP_INV)))
                .addToSub(basicSubScreenWidget);

        ExecutableWidget.instance(120, 24, 60, 20)
                .setElementHandler(
                        new ButtonElement(TextProvider.of(Text.literal("to-unicode")), ButtonAction.run(() -> {
                                    TextFieldWidget widget = findCurrentFocusing();
                                    if (widget != null) {
                                        widget.setText(ChatUtils.toUnicodedString(widget.getText()));
                                    }
                                }))
                                .withTooltips(TooltipHandler.of(TOOLTIPS_SEL_TO_UNICODE)))
                .addToSub(basicSubScreenWidget);
        ExecutableWidget.instance(0, 24, 20, 20)
                .setElementHandler(IconElement.statedGuiPredicate(
                                LOCK_ENABLE_SPRITE,
                                LOCK_DISABLE_SPRITE,
                                ButtonAction.isLeft((i) -> {
                                    if (i) {
                                        ChatTasks.getPlayerChat().encrypt.toggle();
                                    } else {
                                        MainTasks.openConfigScreen(Configs.CHAT_CONFIG);
                                    }
                                }),
                                (el) -> ChatTasks.getPlayerChat().shouldEncryptSendMessage())
                        .withTooltips(TooltipHandler.of(TOOLTIPS_ENCRYPT)))
                .addToSub(basicSubScreenWidget);
        ExecutableWidget.instance(20, 24, 20, 20)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(Text.literal("F").formatted(Formatting.BOLD)),
                                ButtonAction.run(ChatTasks.getChatExtra().enableFormat::toggle))
                        .setActivePredicate(
                                (el) -> ChatTasks.getChatExtra().enableFormat.get())
                        .withTooltips(TooltipHandler.of(TOOLTIPS_FORMAT)))
                .addToSub(basicSubScreenWidget);

        ContentDelegateWidget<TextFieldWidget> helperWidget = McWidgetHelpers.createTextFieldEditBox(
                40, 24, 40, 20, PropertyTracker.event(s -> int2CharFieldContent = s), int2CharFieldContent);
        int2CharInputField = helperWidget.getDelegate();

        ExecutableWidget.instance(80, 24, 40, 20)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(Text.literal("int<->char")),
                                ButtonAction.run(() -> tranlateInt2char(int2CharInputField)))
                        .withTooltips(TooltipHandler.of(TOOLTIPS_INT_TO_CHAR)))
                .addToSub(basicSubScreenWidget);
        ContentDelegateWidget<SubScreenWidget> widgetQuickChars =
                new ContentDelegateWidget<>(250, 0, 0, 0).addToSub(basicSubScreenWidget);

        ExecutableWidget.instance(225, 0, 22, 20)
                .setElementHandler(new ButtonElement(
                                (el) -> enableSpecialChars.get() ? ENABLE_STATE : DISABLE_STATE,
                                ButtonAction.run(enableSpecialChars::toggle))
                        .withTooltips(TooltipHandler.of(TOOLTIPS_SPECIAL_CHARS)))
                .addToSub(basicSubScreenWidget);
        this.delegateSpecialCharWidget = widgetQuickChars;
        this.basicSubScreenWidget = basicSubScreenWidget;
        toggleSpecialCharWidget(this.enableSpecialChars.get());
        this.delegateToolScreen = new ContentDelegateWidget<>(-250, -68, 0, 0);
        toggleBasicToolScreen(this.enableChatScreenTools.get());
    }

    private void toggleSpecialCharWidget(boolean bl) {
        if (this.delegateSpecialCharWidget != null) {
            if (bl) {
                String specialChar = specialChars.getValue();
                int len = specialChar.length();
                int totalY = ((len + 1 - 1) / 4) + 1;
                int x = 1, xm = 4;
                int y = 0;
                SubScreenWidget specialCharWidgets = new SubScreenWidget(-100, -24 * totalY, 100, 24 * totalY);

                for (int i = 0; i < len; i++) {
                    x += 1;
                    char c = specialChar.charAt(i);
                    String value = String.valueOf(c);
                    if (!ChatUtils.isNormalCharacter(c)) {
                        ++i;
                        if (i < len) {
                            char d = specialChar.charAt(i);
                            value = new String(new char[] {c, d});
                        }
                    }
                    final String valueOfChar = value;
                    ExecutableWidget.instance(100 - 25 * x, (totalY - y) * 24, 22, 20)
                            .setElementHandler(new ButtonElement(
                                    TextProvider.of(Text.literal(valueOfChar)), ButtonAction.run(() -> {
                                        TextFieldWidget focused = findCurrentFocusing();
                                        if (focused != null) {
                                            focused.write(valueOfChar);
                                        }
                                    })))
                            .addToSub(specialCharWidgets);

                    if (x >= xm) {
                        x = 0;
                        y += 1;
                    }
                }
                this.delegateSpecialCharWidget.setContentDelegate(specialCharWidgets);
            } else {
                this.delegateSpecialCharWidget.setContentDelegate(null);
            }
        } else {
            // return
        }
    }

    private void refreshSpecialChars(String specialChars) {
        toggleSpecialCharWidget(enableSpecialChars.get());
    }

    private void toggleBasicToolScreen(boolean bl) {
        if (delegateToolScreen != null) {
            if (bl) {
                delegateToolScreen.setContentDelegate(basicSubScreenWidget);
            } else {
                delegateToolScreen.setContentDelegate(null);
            }
            if (mc.currentScreen instanceof ChatScreen chat) {
                ScreenAccess access = ScreenAccess.of(chat);
                // do not make concurrent modification
                Tasks.scheduleDelayed(
                        () -> {
                            access.removeChildFrom(cacheWidget);
                            access.removeChildFrom(int2CharInputField);
                            if (bl) {
                                access.addDrawableChildTo(cacheWidget);
                                access.addDrawableChildTo(int2CharInputField);
                            }
                        },
                        0);
            }
        }
    }

    public void onChatScreenInitialize(Event<Screen> event) {
        if (event.context() instanceof ChatScreen chat0) {
            if (basicSubScreenWidget == null) {
                initToolWidget();
            }
            ExecutableWidget.instance(chat0.width - 20, chat0.height - 56, 20, 20)
                    .setElementHandler(new ButtonElement(
                            (el) -> enableChatScreenTools.get() ? ENABLE_STATE : DISABLE_STATE,
                            ButtonAction.run(enableChatScreenTools::toggle)))
                    .addTo(chat0);
            var content = new ContentDelegateWidget<ContentDelegateWidget<SubScreenWidget>>(
                    chat0.width, chat0.height - 36, 0, 0);
            content.setContentDelegate(this.delegateToolScreen);
            content.addTo(chat0);
            var access = ScreenAccess.of(chat0);
            cacheWidget.setX(chat0.width - 250);
            cacheWidget.setY(chat0.height - 56);
            int2CharInputField.setX(chat0.width - 210);
            int2CharInputField.setY(chat0.height - 104 + 24);
            if (enableChatScreenTools.get()) {
                access.addDrawableChildTo(cacheWidget);
                access.addDrawableChildTo(int2CharInputField);
            }
        }
    }

    public void onCloseChatScreen(Event<Screen> event) {
        // do not consider subClasses
        if (keepChatInv.get()
                && mc.player != null
                && mc.world != null
                && mc.currentScreen != null
                && mc.currentScreen.getClass() == ChatScreen.class
                && ScreenUtils.hasEnterDown()) {
            ChatScreenAccess access = ChatScreenAccess.of((ChatScreen) mc.currentScreen);
            access.resetMessageHistoryIndex();
            event.cancel();
        }
    }

    public void fixIMBlockerStateError(Event<Screen> event) {
        if (event.context instanceof ChatScreen chat
                && IMBlockerHooks.getInstance().isEnabled()
                && chat.getFocused() instanceof FocusableObject focusableObject) {
            ScheduleService.launchAsyncDelayedTask(
                    () -> {
                        if (chat.getFocused() == focusableObject) {
                            focusableObject.updateEnglishState();
                        }
                    },
                    100L);
        }
    }
}
