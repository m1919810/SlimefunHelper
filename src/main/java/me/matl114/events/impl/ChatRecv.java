package me.matl114.events.impl;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.utils.ChatUtils;
import me.matl114.versioned.api.VRecord;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EntityType;
import net.minecraft.network.message.MessageType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

@Getter
@Setter
@AllArgsConstructor
@Accessors(fluent = true)
public class ChatRecv {
    Text text;
    final Optional<GameProfile> senderProfile;
    final Optional<String> senderName;
    final boolean system;
    final Optional<RegistryEntry<MessageType>> messageType;
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    public static ChatRecv parseSystemMessage(Text text) {
        var handler = mc.getNetworkHandler();
        if (handler == null) return new ChatRecv(text, Optional.empty(), Optional.empty(), true, Optional.empty());
        Optional<Pair<Optional<String>, Optional<GameProfile>>> result = ChatUtils.textStream(text)
                .map(Text::getStyle)
                .filter(s -> s.getClickEvent() != null || s.getHoverEvent() != null || s.getInsertion() != null)
                .map(ChatRecv::parseNameInStyle)
                .filter(Objects::nonNull)
                .map(s -> Pair.of(
                        Optional.ofNullable(s.map(
                                v -> {
                                    return handler.getPlayerListEntry(v) != null ? v : null;
                                },
                                v -> Optional.ofNullable(handler.getPlayerListEntry(v))
                                        .map(PlayerListEntry::getProfile)
                                        .map(VRecord::getName)
                                        .orElse(null))),
                        Optional.ofNullable(s.map(handler::getPlayerListEntry, handler::getPlayerListEntry))
                                .map(PlayerListEntry::getProfile)))
                .filter(s -> s.getFirst().isPresent() || s.getSecond().isPresent())
                .findFirst();
        return new ChatRecv(
                text, result.flatMap(Pair::getSecond), result.flatMap(Pair::getFirst), true, Optional.empty());
    }

    private static final Pattern MESSAGE_PATTERN = Pattern.compile("^/([a-zA-Z:0-9]+)\\s([^\\s]+)\\s");

    public static Either<String, UUID> parseNameInStyle(Style style) {
        if (style.getClickEvent() != null) {
            var click = style.getClickEvent();
            if (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                String suggestCommand = click.getValue();
                Matcher matcher = MESSAGE_PATTERN.matcher(suggestCommand);
                if (matcher.find()) {
                    String name = matcher.group(2);
                    return Either.left(name);
                }
            }
        }
        if (style.getHoverEvent() != null) {
            var hover = style.getHoverEvent();
            if (hover.getAction() == HoverEvent.Action.SHOW_ENTITY
                    && hover.getValue(HoverEvent.Action.SHOW_ENTITY) instanceof HoverEvent.EntityContent entity
                    && entity.entityType == EntityType.PLAYER) {
                var uid = entity.uuid;
                if (uid != null) {
                    return Either.right(uid);
                }
            }
        }
        if (style.getInsertion() != null) {
            return Either.left(style.getInsertion());
        }
        return null;
    }
}
