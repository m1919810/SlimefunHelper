package me.matl114.hacks.modules.chat;

import io.github.reserveword.imblocker.common.gui.FocusableObject;
import java.util.List;
import me.matl114.accessors.access.ChatScreenAccess;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.McWidgetHelpers;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.IconElement;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hooks.IMBlockerHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.ScheduleService;
import me.matl114.managers.TaskManagers;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.client.gui.screen.ChatScreen;
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
        super("ChatTools");
        bindFlag(enableChatScreenTools);
    }

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

    public final FlagRef autoSend = flagBuilder(
                    Configs.CHAT_CONFIG, chatTools.add("auto-chat").toPath())
            .build();

    public final IntRef period =
            intBuilder(chatTools.add("auto-chat-period")).defaultValue(21).build();

    public final IntRef multiple =
            intBuilder(chatTools.add("auto-chat-multiple")).defaultValue(1).build();

    public final FlagRef keepChatInv = flagBuilder(
                    Configs.CHAT_CONFIG, chatTools.add("keep-chat-inv").toPath())
            .build();

    public final FlagRef obfLogin =
            flagBuilder(chatTools.add("obf-login-message")).build();

    public final KeyBindRef removeCmdKey = hotkey(
                    Configs.CHAT_CONFIG,
                    chatTools.add("remove-command-prefix-hotkey").toPath(),
                    new MultiKeyBind())
            .registerHotkey(HotKeyUtils.asHandler(this::onRemoveCommandPrefix))
            .build();

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        counter = 0;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(Listener.getPostInitializeScreen().getChannel(ChatScreen.class), this::onChatScreenInitialize);
        registerListener(Listener.getPreSetScreen().getChannel(ChatScreen.class), this::onCloseChatScreen);
        registerListener(Listener.getPostInitializeScreen().getChannel(ChatScreen.class), this::fixIMBlockerStateError);
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
    private static final List<Text> TOOLTIPS_CHAT_TOOLS = List.of(Text.literal("点击展开/关闭聊天框小工具栏"));

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
        ContentDelegateWidget<TextFieldWidget> helperWidgetWrapper =
                McWidgetHelpers.createTextFieldEditBox(0 + 2, 48 + 2, 120 - 2, 20 - 2, chatCache::set, chatCache.get());
        cacheWidget = helperWidgetWrapper.getDelegate();

        // todo： add translatable to buttons and everything
        createExecuteButton(
                        "widget.chat-tools.send-cache",
                        ButtonAction.run(() -> ChatTasks.sayMessage(chatCache.get(), true)),
                        120 + 1,
                        48 + 1,
                        60 - 2,
                        20 - 2)
                .addToSub(basicSubScreenWidget);

        Runnable toggle = HotKeyUtils.getToggleTask(
                Configs.CHAT_CONFIG, chatTools.add("auto-chat").toPath());
        createToggleButton(
                        "widget.chat-tools.auto-send",
                        ValueAccessor.of(autoSend::get, (bl) -> {
                            if (bl != autoSend.get()) {
                                toggle.run();
                            }
                        }),
                        180 + 1,
                        48 + 1,
                        50 - 2,
                        20 - 2)
                .addToSub(basicSubScreenWidget);
        Runnable toggle2 = HotKeyUtils.getToggleTask(
                Configs.CHAT_CONFIG, chatTools.add("keep-chat-inv").toPath());
        createToggleButton(
                        "widget.chat-tools.keep-chat-inv",
                        ValueAccessor.of(keepChatInv::get, (bl) -> {
                            if (bl != autoSend.get()) {
                                toggle2.run();
                            }
                        }),
                        180 + 1,
                        24 + 1,
                        70 - 2,
                        20 - 2)
                .addToSub(basicSubScreenWidget);
        createExecuteButton(
                        "widget.chat-tools.to-unicode",
                        ButtonAction.run(() -> {
                            TextFieldWidget widget = findCurrentFocusing();
                            if (widget != null) {
                                widget.setText(ChatUtils.toUnicodedString(widget.getText()));
                            }
                        }),
                        120 + 1,
                        24 + 1,
                        60 - 2,
                        20 - 2)
                .addToSub(basicSubScreenWidget);
        createElement(
                        IconElement.statedGuiPredicate(
                                LOCK_ENABLE_SPRITE,
                                LOCK_DISABLE_SPRITE,
                                ButtonAction.empty(),
                                (el) -> ChatTasks.getEncryptChat().shouldEncryptSendMessage()),
                        () -> ChatUtils.parseTooltipsTranslation("widget.chat-tools.toggle-encrypt.tooltips", ""),
                        ButtonAction.isLeft((i) -> {
                            if (i) {
                                ChatTasks.getEncryptChat().encrypt.toggle();
                            } else {
                                MainTasks.openModuleScreen(ChatTasks.getEncryptChat());
                            }
                        }),
                        0 + 1,
                        24 + 1,
                        20 - 2,
                        20 - 2)
                .addToSub(basicSubScreenWidget);

        createExecuteButton(
                        () -> Text.literal("F").formatted(Formatting.BOLD),
                        () -> ChatUtils.parseTooltipsTranslation("widget.chat-tools.toggle-chat-format.tooltips", ""),
                        ButtonAction.isLeft((i) -> {
                            if (i) {
                                ChatExtra.INSTANCE.enableFormat.toggle();
                            } else {
                                MainTasks.openModuleScreen(ChatTasks.getChatExtra());
                            }
                        }),
                        ChatExtra.INSTANCE.enableFormat::get,
                        20 + 1,
                        24 + 1,
                        20 - 2,
                        20 - 2)
                .addToSub(basicSubScreenWidget);

        ContentDelegateWidget<TextFieldWidget> helperWidget = McWidgetHelpers.createTextFieldEditBox(
                40 + 1, 24 + 1, 40 - 2, 20 - 2, s -> int2CharFieldContent = s, int2CharFieldContent);
        int2CharInputField = helperWidget.getDelegate();
        createExecuteButton(
                        "widget.chat-tools.translate-int-to-char",
                        ButtonAction.run(() -> tranlateInt2char(int2CharInputField)),
                        80 + 1,
                        24 + 1,
                        40 - 2,
                        20 - 2)
                .addToSub(basicSubScreenWidget);
        ContentDelegateWidget<SubScreenWidget> widgetQuickChars =
                new ContentDelegateWidget<>(250, 0, 0, 0).addToSub(basicSubScreenWidget);

        createExecuteButton(
                        () -> enableSpecialChars.get() ? ENABLE_STATE : DISABLE_STATE,
                        () -> ChatUtils.parseTooltipsTranslation(
                                "widget.chat-tools.toggle-special-char-input.tooltips", ""),
                        ButtonAction.run(enableSpecialChars::toggle),
                        225 + 1,
                        0 + 1,
                        22 - 2,
                        20 - 2)
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
                    createExecuteButton(
                                    () -> Text.literal(valueOfChar),
                                    List::of,
                                    ButtonAction.run(() -> {
                                        TextFieldWidget focused = findCurrentFocusing();
                                        if (focused != null) {
                                            focused.write(valueOfChar);
                                        }
                                    }),
                                    100 - 25 * x,
                                    (totalY - y) * 24,
                                    22,
                                    20)
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

    public void onChatScreenInitialize(Event<ChatScreen> event) {
        var chat0 = event.context;
        if (basicSubScreenWidget == null) {
            initToolWidget();
        }
        createExecuteButton(
                        () -> enableChatScreenTools.get() ? ENABLE_STATE : DISABLE_STATE,
                        () -> ChatUtils.parseTooltipsTranslation("widget.chat-tools.toggle-hud.tooltips", ""),
                        ButtonAction.run(enableChatScreenTools::toggle),
                        chat0.width - 20 + 1,
                        chat0.height - 56 + 1,
                        20 - 2,
                        20 - 2)
                .addTo(chat0);
        var content =
                new ContentDelegateWidget<ContentDelegateWidget<SubScreenWidget>>(chat0.width, chat0.height - 36, 0, 0);
        content.setContentDelegate(this.delegateToolScreen);
        content.addTo(chat0);
        var access = ScreenAccess.of(chat0);
        cacheWidget.setX(chat0.width - 250 + 1);
        cacheWidget.setY(chat0.height - 56 + 1);
        int2CharInputField.setX(chat0.width - 210 + 1);
        int2CharInputField.setY(chat0.height - 104 + 24 + 1);
        if (enableChatScreenTools.get()) {
            access.addDrawableChildTo(cacheWidget);
            access.addDrawableChildTo(int2CharInputField);
        }
    }

    public void onCloseChatScreen(Event<ChatScreen> event) {
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

    public void fixIMBlockerStateError(Event<ChatScreen> event) {
        if (IMBlockerHooks.getInstance().isEnabled()
                && event.context.getFocused() instanceof FocusableObject focusableObject) {
            var chat = event.context;
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
