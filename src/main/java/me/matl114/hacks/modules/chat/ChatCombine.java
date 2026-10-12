package me.matl114.hacks.modules.chat;

import java.util.HashSet;
import java.util.ListIterator;
import java.util.Set;
import java.util.UUID;
import me.matl114.accessors.events.ChatHudAccess;
import me.matl114.accessors.events.ChatHudLineAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.ChatUtils;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class ChatCombine extends BaseModule {
    public final ModulePath chatCombine = makePath(Configs.CHAT_CONFIG, "chat-combine");
    private static final String formatCombinedMessage = " &r&7&l[x&a%d&7&l]";
    private static final String RAW_ID_PREFIX = "slimefunhelper:chatcombine/";
    private static final String STACK_ID_PREFIX = "slimefunhelper:chatcombine_stack/";
    private static final String LINE_ID_PREFIX = "slimefunhelper:chatcombine_line/";

    public ChatCombine() {
        super("ChatCombine");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(chatCombine.add("enable")).build();
    public final FlagRef removeAll =
            flagBuilder(chatCombine.add("remove-all")).defaultValue(true).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getMessageAddToHud(), this::onMessageAdd);
    }

    public void onMessageAdd(Event<Text> textEvent) {
        if (!enable.get() || textEvent.isCancelled()) return;

        Text text = textEvent.context();
        String rawString = ChatUtils.getOrderedTextString(text.asOrderedText());
        String rawId = RAW_ID_PREFIX + rawString;

        ChatHud hud = mc.inGameHud.getChatHud();
        ChatHudAccess hudAccess = hud == null ? null : ChatHudAccess.of(hud);
        int maxStack = 1;
        boolean matched = false;
        if (hud != null) {
            var visibleHistory = hudAccess.getVisibleLines();
            boolean removeEverywhere = removeAll.get();
            ListIterator<ChatHudLine.Visible> lineIterator = visibleHistory.listIterator();
            Set<String> visitedLineIds = new HashSet<>();
            while (lineIterator.hasNext()) {
                ChatHudLine.Visible visible = lineIterator.next();
                Set<String> messageIds = ChatHudLineAccess.of(visible).getUniqueMessageIds();
                if (!messageIds.contains(rawId)) {
                    if (!removeEverywhere) break;
                    continue;
                }

                String lineId = messageIds.stream()
                        .filter(id -> id.startsWith(LINE_ID_PREFIX))
                        .findFirst()
                        .orElse("legacy:" + System.identityHashCode(visible));
                if (visitedLineIds.add(lineId)) {
                    maxStack = Math.max(maxStack, getStackSize(messageIds));
                    matched = true;
                }

                lineIterator.remove();
            }
        }
        int stack = matched ? maxStack + 1 : 1;
        if (hudAccess != null) {
            hudAccess.addPendingUniqueMessageId(rawId);
            hudAccess.addPendingUniqueMessageId(STACK_ID_PREFIX + stack);
            hudAccess.addPendingUniqueMessageId(LINE_ID_PREFIX + UUID.randomUUID());
        }
        if (matched) {
            String newLineLegacy = formatCombinedMessage.formatted(stack);
            MutableText newLine = ChatUtils.copyText(text);
            newLine.append(ChatUtils.stringToText(newLineLegacy));
            textEvent.context(newLine);
        }
    }

    private int getStackSize(Set<String> messageIds) {
        int stack = 1;
        for (String id : messageIds) {
            if (!id.startsWith(STACK_ID_PREFIX)) continue;
            try {
                stack = Math.max(stack, Integer.parseInt(id.substring(STACK_ID_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                // Ignore malformed stack metadata.
            }
        }
        return stack;
    }
}
