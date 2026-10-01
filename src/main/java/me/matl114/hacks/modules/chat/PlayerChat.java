package me.matl114.hacks.modules.chat;

import com.mojang.authlib.GameProfile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.ChatRecv;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.ChatUtils;
import me.matl114.versioned.api.VRecord;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class PlayerChat extends BaseModule {
    public final ModulePath playerChat = makePath(Configs.CHAT_CONFIG, "player-chat");

    public PlayerChat() {
        super("PlayerChat");
    }

    public final FlagRef detectPlayerName =
            flagBuilder(playerChat.add("detect-all-message-with-player-names")).build();

    public final FlagRef timeStamp =
            flagBuilder(playerChat.add("append-time-stamp")).build();

    public final FlagRef playerHead =
            flagBuilder(playerChat.add("append-chat-head")).build();

    public final Pattern pattern = Pattern.compile("^(?!_)(?![0-9]+$)[a-zA-Z0-9_]{3,16}$");

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getChatMessageReceive(), this::onChatReceive);
    }

    public void onChatReceive(Event<ChatRecv> chatEvent) {
        if (chatEvent.isCancelled() || !hasAnyFunctionEnable()) {
            return;
        }

        ChatRecv recv = chatEvent.context();
        String text = ChatUtils.textToPlainString(recv.text());
        UUID senderUuid = recv.senderProfile().map(VRecord::getId).orElse(null);
        String caughtName = recv.senderName().orElseGet(() -> profileName(recv.senderProfile()));

        if (caughtName == null && detectPlayerName.get() && mc.getNetworkHandler() != null) {
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

        handleParsedChatMessage(chatEvent.context(), text, senderUuid, caughtName, recv.system());
    }

    private String profileName(Optional<GameProfile> profile) {
        return profile.map(VRecord::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(null);
    }

    public boolean hasAnyFunctionEnable() {
        return playerHead.get() || timeStamp.get();
    }

    public void handleParsedChatMessage(
            ChatRecv chatRecv,
            String message,
            @Nullable UUID senderUuid,
            @Nullable String capturedName,
            boolean isSystem) {
        Text text = chatRecv.text();
        MutableBoolean modified = new MutableBoolean(false);
        List<Consumer<ChatUtils.TextBuilder>> appendToFirst = new ArrayList<>();
        appendToFirst.add(handleChatHead(senderUuid, capturedName, modified));
        appendToFirst.add(handleTimeStampAdd(modified, senderUuid, capturedName));
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
        chatRecv.text(newBuilder.end().build());
    }

    public Consumer<ChatUtils.TextBuilder> handleChatHead(
            @Nullable UUID senderUuid, @Nullable String playerName, MutableBoolean mutableBoolean) {
        //        if (!playerHead.get()) {
        //            return null;
        //        }
        //        if (senderUuid != null) {
        //            ObjectTextContent content =
        //                    new ObjectTextContent(new PlayerTextObjectContents(ProfileComponent.ofDynamic(senderUuid),
        // false));
        //            PlayerListEntry entry = mc.getNetworkHandler() == null
        //                    ? null
        //                    : mc.getNetworkHandler().getPlayerListEntry(senderUuid);
        //            mutableBoolean.setTrue();
        //            return builder -> builder.withHoverEvent(ChatUtils.getHoverShowText(List.of(
        //                            Text.literal("玩家:" + (entry == null ? "未知" :
        // VRecord.getName(entry.getProfile()))),
        //                            Text.literal("玩家UUID:" + senderUuid))))
        //                    .withContent(content)
        //                    .withStyle(Style.EMPTY);
        //        }
        //        if (playerName != null && pattern.matcher(playerName).matches()) {
        //            ObjectTextContent content =
        //                    new ObjectTextContent(new PlayerTextObjectContents(ProfileComponent.ofDynamic(playerName),
        // false));
        //            mutableBoolean.setTrue();
        //            return builder -> builder.withHoverEvent(
        //                            ChatUtils.getHoverShowText(List.of(Text.literal("玩家:" + playerName))))
        //                    .withContent(content)
        //                    .withStyle(Style.EMPTY);
        //        }
        return null;
    }

    public Consumer<ChatUtils.TextBuilder> handleTimeStampAdd(
            MutableBoolean shouldModify, @Nullable UUID senderUuid, @Nullable String capturedName) {
        if (timeStamp.get() && (capturedName != null || senderUuid != null)) {
            shouldModify.setTrue();
            String time = new SimpleDateFormat("[HH:mm:ss]").format(new Date());
            return builder -> builder.withFormat(Formatting.GRAY).with(time).withStyle(Style.EMPTY);
        }
        return null;
    }
}
