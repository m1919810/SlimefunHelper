package me.matl114.hacks.modules.chat;

import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.config.RegexList;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.Text;
import net.minecraft.util.Nullables;

public class ChatSpamFix extends BaseModule {
    public static String[] ENABLE = new String[] {"chat-spam-fix", "enable"};
    public static String[] REGEX = new String[] {"chat-spam-fix", "regex-list"};
    public static String[] LOG_FIXED = new String[] {"chat-spam-fix", "log-hidden-messages"};

    public ChatSpamFix() {}

    public final FlagRef enable = flagBuilder(Configs.CHAT_CONFIG, ENABLE).build();

    public final NBTRef<RegexList> regexList = builder(
                    Configs.CHAT_CONFIG, REGEX, NBTType.<RegexList>parameter(RegexList.class))
            .defaultValue(new RegexList(List.of()))
            .build();

    public final FlagRef logHidden = flagBuilder(Configs.CHAT_CONFIG, LOG_FIXED).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getMessageAddToHud(), this::onMessageAdd);
    }

    public void onMessageAdd(Event<Text> event) {
        if (enable.get()) {
            String message = ChatUtils.textToPlainString(event.context());
            if (regexList.get().test(message)) {
                event.cancel();
                if (logHidden.get()) {
                    String string = event.context()
                            .getString()
                            .replaceAll("\r", "\\\\r")
                            .replaceAll("\n", "\\\\n");
                    String string2 = (String) Nullables.map(event.getArgs(1), MessageIndicator::loggedName);
                    if (string2 != null) {
                        Debug.info("[ChatSpamFix]", string2, string);
                    } else {
                        Debug.info("[ChatSpamFix]", string);
                    }
                }
            }
        }
    }
}
