package me.matl114.hacks.modules.chat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;

public class ClientSideCommand extends BaseModule {
    public static final String[] CLIENT_COMMAND_OVERRIDE = {"client-side-command", "client-side-command-override"};
    public static final String[] CHAT_HELPER_CLIENT_GIVE = {"client-side-command", "client-side-give"};

    public ClientSideCommand() {
        bindFlag(enable);
    }

    public final FlagRef enable =
            flagBuilder(Configs.CHAT_CONFIG, CLIENT_COMMAND_OVERRIDE).build();

    public final FlagRef enableGive =
            flagBuilder(Configs.CHAT_CONFIG, CHAT_HELPER_CLIENT_GIVE).build();

    private static final Predicate<CommandSource> requirement = (val) -> true;
    private static final Command<CommandSource> success = (val) -> Command.SINGLE_SUCCESS;

    private <T extends CommandSource> void addOurCommandNodesInRoot(RootCommandNode<T> node) {
        // try add deop command
        // fix: plugin give commands
        if (enableGive.get()) {
            CommandNode<T> give = node.getChild("minecraft:give");
            LiteralCommandNode<T> giveCommand;
            if (give == null) {
                giveCommand =
                        new LiteralCommandNode<>("minecraft:give", null, (Predicate<T>) requirement, null, null, false);
                node.addChild(giveCommand);
            } else {
                giveCommand = (LiteralCommandNode<T>) give;
            }
            if (node.getChild("give") == null) {
                LiteralCommandNode<T> mcGiveCommand =
                        new LiteralCommandNode<>("give", null, (Predicate<T>) requirement, giveCommand, null, false);
                node.addChild(mcGiveCommand);
            }
            if (give == null) {
                CommandRegistryAccess commandRegistryAccess =
                        CommandRegistryAccess.of(ItemStackUtils.delegate(), FeatureFlags.DEFAULT_ENABLED_FEATURES);

                ArgumentCommandNode<T, EntitySelector> targetArgument = new ArgumentCommandNode<>(
                        "targets",
                        EntityArgumentType.players(),
                        null,
                        (Predicate<T>) requirement,
                        null,
                        null,
                        false,
                        // use default because if "minecraft:give" node is absent, then we definitely have no permission
                        // of requesting this
                        null);
                giveCommand.addChild(targetArgument);
                ArgumentCommandNode<T, ItemStackArgument> itemArgument = new ArgumentCommandNode<>(
                        "item",
                        ItemStackArgumentType.itemStack(commandRegistryAccess),
                        (Command<T>) success,
                        (Predicate<T>) requirement,
                        null,
                        null,
                        false,
                        null);
                targetArgument.addChild(itemArgument);
                ArgumentCommandNode<T, Integer> countAmount = new ArgumentCommandNode<>(
                        "count",
                        IntegerArgumentType.integer(1),
                        (Command<T>) success,
                        (Predicate<T>) requirement,
                        null,
                        null,
                        false,
                        null);
                itemArgument.addChild(countAmount);
            }
        }
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(CommandTreeS2CPacket.class),
                (Consumer<Event<CommandTreeS2CPacket>>) this::onClientCommandReload);
        registerListener(Listener.getChatSend(), this::onCommandSend, 1);
    }

    private <T extends CommandSource> void onClientCommandReload(Event<CommandTreeS2CPacket> reload) {
        CommandDispatcher<T> clientTree =
                (CommandDispatcher<T>) mc.getNetworkHandler().getCommandDispatcher();
        RootCommandNode<T> root = clientTree.getRoot();
        if (root != null && enable.get()) {
            addOurCommandNodesInRoot(root);
        }
    }

    public List<String> supportedCommand = List.of("give", "minecraft:give");

    public void onCommandSend(Event<String> commandEvent) {
        String command = commandEvent.context();
        if (enable.get() && command.startsWith("/")) {
            command = command.substring(1);
            if (supportedCommand.stream().anyMatch(command::startsWith)) {

                if (dispatchVanillaCommand(command)) {
                    commandEvent.cancel();
                    return;
                }
            }
        }
    }

    private static ResultConsumer<ClientCommandSource> consumer = (c, s, r) -> {};

    private boolean dispatchVanillaCommand(String command) {
        // Debug.info(command);
        if (mc.player == null) return false;
        mc.player.setClientPermissionLevel(4);
        try {
            ParseResults<ClientCommandSource> parse =
                    (ParseResults) mc.getNetworkHandler().getCommandDispatcher().parse(command, (ClientCommandSource)
                            mc.getNetworkHandler().getCommandSource());
            if (parse.getReader().canRead()) {
                if (parse.getExceptions().size() == 1) {
                    throw parse.getExceptions().values().iterator().next();
                } else if (parse.getContext().getRange().isEmpty()) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                            .dispatcherUnknownCommand()
                            .createWithContext(parse.getReader());
                } else {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                            .dispatcherUnknownArgument()
                            .createWithContext(parse.getReader());
                }
            }

            final String commandStr = parse.getReader().getString();
            final CommandContextBuilder<ClientCommandSource> originalBuilder = parse.getContext();
            // flatten this
            List<CommandContextBuilder<ClientCommandSource>> modifiers = new ArrayList<>();
            CommandContextBuilder<ClientCommandSource> contextData = originalBuilder;
            while (true) {
                CommandContextBuilder<ClientCommandSource> child = contextData.getChild();
                if (child == null) {
                    if (contextData.getCommand() == null) {
                        consumer.onCommandComplete(originalBuilder.build(commandStr), false, 0);
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                                .dispatcherUnknownCommand()
                                .createWithContext(parse.getReader());
                    }
                    break;
                }
                modifiers.add(contextData);
                contextData = child;
            }
            Map<String, ParsedArgument<ClientCommandSource, ?>> argsMap = contextData.getArguments();
            if (commandStr.startsWith("give") || commandStr.startsWith("minecraft:give")) {
                return handleClientSideGiveCommand(argsMap, command);
            }
        } catch (CommandSyntaxException e) {
            Debug.chat(getErrorMessage(e));
        } catch (Throwable e) {
            Debug.chat(Text.literal("Internal Error!").formatted(Formatting.RED), e);
        }
        return false;
    }

    private boolean handleClientSideGiveCommand(
            Map<String, ParsedArgument<ClientCommandSource, ?>> argsMap, String command) throws CommandSyntaxException {
        if (enableGive.get()) {
            if (mc.player.isCreative()) {
                Debug.chat(Text.literal("尝试在客户端执行give指令").formatted(Formatting.GREEN));
                ParsedArgument<ClientCommandSource, ?> entityArgument = argsMap.get("targets");
                EntitySelector entitySelector = (EntitySelector) entityArgument.getResult();
                StringRange range = entityArgument.getRange();
                if (entitySelector.isSenderOnly()
                        || Objects.equals(
                                mc.player.getNameForScoreboard(),
                                command.substring(range.getStart(), range.getEnd()))) {
                    ItemStackArgument itemStack =
                            (ItemStackArgument) argsMap.get("item").getResult();
                    int count = argsMap.containsKey("count")
                            ? (Integer) argsMap.get("count").getResult()
                            : 1;
                    ItemStack itemStackToGive = itemStack.createStack(count, false);
                    InvTasks.creativeGive(itemStackToGive, count);
                    Debug.chat(Text.literal("命令执行成功！").formatted(Formatting.GREEN));
                    return true;
                } else {
                    Debug.chat(Text.literal("你选中了其他生物,指令转向服务端执行!").formatted(Formatting.YELLOW));
                    return false;
                }
            } else {
                Debug.chat(Text.literal("你启用了客户端指令的功能,但是你并不是创造模式!").formatted(Formatting.YELLOW));
                return false;
            }
        } else {
            Debug.chat(Text.literal("尝试在客户端执行give指令,但是你没有启用客户端give指令").formatted(Formatting.RED));
            return false;
        }
    }

    private static Text getErrorMessage(CommandSyntaxException e) {
        Text message = Texts.toText(e.getRawMessage());
        String context = e.getContext();

        return context != null
                ? Text.translatable("command.context.parse_error", message, e.getCursor(), context)
                : message;
    }
}
