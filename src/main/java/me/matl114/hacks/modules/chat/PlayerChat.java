package me.matl114.hacks.modules.chat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.ListRef;
import me.matl114.utils.ChatUtils;
import me.matl114.versioned.api.VRecord;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class PlayerChat extends BaseModule {
    public final ModulePath playerChat = makePath(Configs.CHAT_CONFIG, "player-chat");

    public PlayerChat() {
        super("PlayerChat");
    }

    public List<Pattern> compile;

    public final ListRef chatMessageFormat = builder(playerChat.add("game-message-as-player-message"), ListRef.TYPE)
            .defaultValue(List.of(
                    "^.*\\[([^\\]\\[\\s]+)\\]\\s*[:➟→»》]\\s*(.*)$",
                    "^.*\\[[^\\]\\[]+\\].* ([^\\]\\[\\s]+)\\s*[:➟→»》]\\s*(.*)$",
                    "^.*<([^><\\s]+)>\\s*[:➟→»》]\\s*(.*)$",
                    "^.*《([^》《\\s]+)》\\s*[:➟→»》]\\s*(.*)$",
                    "^.*«([^»«\\s]+)»\\s+(.*)$"))
            .listValidator(Configs.REGEX_VALIDATOR)
            .updateListener(
                    list -> compile = list.stream().map(Pattern::compile).toList())
            .build();

    public final FlagRef detectPlayerName =
            flagBuilder(playerChat.add("detect-all-message-with-player-names")).build();

    public final FlagRef timeStamp =
            flagBuilder(playerChat.add("append-time-stamp")).build();

    public final FlagRef playerHead =
            flagBuilder(playerChat.add("append-chat-head")).build();

    private UUID lastAcceptUUID;
    private volatile boolean handleChatMsg = false;
    private volatile boolean safeFlag = false;

    public final Pattern pattern = Pattern.compile("^(?!_)(?![0-9]+$)[a-zA-Z0-9_]{3,16}$");

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getMessageAddToHud(), this::onChatAdd);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(ChatMessageS2CPacket.class),
                (Consumer<Event<ChatMessageS2CPacket>>) this::<ChatMessageS2CPacket>onPacketIn);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(GameMessageS2CPacket.class),
                (Consumer<Event<GameMessageS2CPacket>>) this::<GameMessageS2CPacket>onPacketIn);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(ChatMessageS2CPacket.class),
                (Consumer<Event<ChatMessageS2CPacket>>) this::<ChatMessageS2CPacket>onPacketInPost);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(GameMessageS2CPacket.class),
                (Consumer<Event<GameMessageS2CPacket>>) this::<GameMessageS2CPacket>onPacketInPost);
    }

    public MessageIndicator systemIndicator() {
        return mc.isConnectedToLocalServer() ? MessageIndicator.singlePlayer() : MessageIndicator.system();
    }

    public Matcher matcher(String message) {
        if (compile != null) {
            for (Pattern pattern : compile) {
                Matcher matcher = pattern.matcher(message);
                if (matcher.matches() && matcher.groupCount() >= 1) {
                    return matcher;
                }
            }
        }
        return null;
    }

    public <T extends Packet<?>> void onPacketIn(Event<T> packetEvent) {
        handleChatMsg = true;
        if (packetEvent.context() instanceof ChatMessageS2CPacket chatMessagePacket) {
            lastAcceptUUID = chatMessagePacket.sender();
        }
    }

    public <T extends Packet<?>> void onPacketInPost(Event<T> packetEvent) {
        handleChatMsg = false;
        lastAcceptUUID = null;
    }

    public void onChatAdd(Event<Text> chatAdd) {
        if (chatAdd.isCancelled() || safeFlag || !handleChatMsg || !hasAnyFunctionEnable()) {
            return;
        }
        safeFlag = true;
        try {
            MessageIndicator indicator = chatAdd.getArgs(1);
            String text = ChatUtils.textToPlainString(chatAdd.context());
            Matcher matcher = matcher(text);
            if (matcher != null && matcher.groupCount() >= 2) {
                handleParsedChatMessage(
                        chatAdd, text, matcher.group(matcher.groupCount() - 1), indicator == systemIndicator());
            } else {
                String caughtName = null;
                if (lastAcceptUUID != null) {
                    PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(lastAcceptUUID);
                    if (entry != null) {
                        caughtName = VRecord.getName(entry.getProfile());
                    }
                }
                if (caughtName == null && detectPlayerName.get()) {
                    String findingMsg = text;
                    for (var playerListEntry : mc.getNetworkHandler().getPlayerList()) {
                        String playerName = VRecord.getName(playerListEntry.getProfile());
                        int index = findingMsg.indexOf(playerName);
                        if (index != -1) {
                            findingMsg = findingMsg.substring(0, index);
                            caughtName = playerName;
                        }
                        Text displayName = playerListEntry.getDisplayName();
                        if (displayName != null) {
                            String displayNameText = ChatUtils.textToPlainString(displayName);
                            int idx = findingMsg.indexOf(displayNameText);
                            if (idx != -1) {
                                findingMsg = findingMsg.substring(0, idx);
                                caughtName = VRecord.getName(playerListEntry.getProfile());
                            }
                        }
                        if (findingMsg.isEmpty()) {
                            break;
                        }
                    }
                }
                handleParsedChatMessage(chatAdd, text, caughtName, indicator == systemIndicator());
            }
        } finally {
            safeFlag = false;
        }
    }

    public boolean hasAnyFunctionEnable() {
        return playerHead.get() || timeStamp.get();
    }

    public void handleParsedChatMessage(
            Event<Text> event, String message, @Nullable String capturedName, boolean isSystem) {
        Text text = event.context();
        MutableBoolean modified = new MutableBoolean(false);
        List<Consumer<ChatUtils.TextBuilder>> appendToFirst = new ArrayList<>();
        appendToFirst.add(handleChatHead(capturedName, modified));
        appendToFirst.add(handleTimeStampAdd(modified, capturedName));
        if (!modified.booleanValue()) {
            return;
        }
        ChatUtils.TextBuilder newBuilder = ChatUtils.builder();
        for (var consumer : appendToFirst) {
            if (consumer != null) {
                consumer.accept(newBuilder);
            }
        }
        newBuilder.withStyle(Style.EMPTY);
        text.visit(
                ((style, asString) -> {
                    newBuilder.accept(style, asString);
                    return Optional.empty();
                }),
                Style.EMPTY);
        event.context(newBuilder.end().build());
    }

    public Consumer<ChatUtils.TextBuilder> handleChatHead(String playerName, MutableBoolean mutableBoolean) {
        //        if (!playerHead.get()) return null;
        //        PlayerListEntry entry;
        //        if (lastAcceptUUID != null) {
        //
        //            ObjectTextContent content = new ObjectTextContent(
        //                    new PlayerTextObjectContents(ProfileComponent.ofDynamic(lastAcceptUUID), false));
        //            mutableBoolean.setTrue();
        //            entry = mc.getNetworkHandler().getPlayerListEntry(lastAcceptUUID);
        //            return (builder) -> {
        //                builder.withHoverEvent(ChatUtils.getHoverShowText(List.of(
        //                                Text.literal("玩家:" + (entry == null ? "未知" :
        // VRecord.getName(entry.getProfile()))),
        //                                Text.literal("玩家UUID:" + lastAcceptUUID))))
        //                        .withContent(content)
        //                        .withStyle(Style.EMPTY);
        //            };
        //        }
        //        if (playerName != null && pattern.matcher(playerName).matches()) {
        //
        //            ObjectTextContent content =
        //                    new ObjectTextContent(new PlayerTextObjectContents(ProfileComponent.ofDynamic(playerName),
        // false));
        //            mutableBoolean.setTrue();
        //            return (builder) -> {
        //                builder.withHoverEvent(ChatUtils.getHoverShowText(List.of(Text.literal("玩家:" + playerName))))
        //                        .withContent(content)
        //                        .withStyle(Style.EMPTY);
        //            };
        //        }
        return null;
    }

    public Consumer<ChatUtils.TextBuilder> handleTimeStampAdd(MutableBoolean shouldModify, String capturedName) {
        if (timeStamp.get() && (capturedName != null || lastAcceptUUID != null)) {
            shouldModify.setTrue();
            String time = new SimpleDateFormat("[HH:mm:ss]").format(new Date());
            return builder -> builder.withFormat(Formatting.GRAY).with(time).withStyle(Style.EMPTY);
        }
        return null;
    }
}
