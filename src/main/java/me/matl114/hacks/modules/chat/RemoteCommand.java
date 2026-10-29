package me.matl114.hacks.modules.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.ChatRecv;
import me.matl114.gui.basic.*;
import me.matl114.gui.interfaces.ElementFactory;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.utils.RemoteCommandExecution;
import me.matl114.hacks.utils.config.StringFormat;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.Tasks;
import me.matl114.managers.command.MainCommand;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.ListRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.Ref;
import me.matl114.managers.config.Refs;
import me.matl114.managers.config.StringRef;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.collections.MutableRecord;
import me.matl114.utils.commands.commandGroup.AbstractMainCommand;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TaskSubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.interruption.ArgumentException;
import me.matl114.utils.commands.interruption.DispatchFailureError;
import me.matl114.utils.commands.interruption.PermissionDenyError;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.versioned.api.VRecord;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class RemoteCommand extends BaseModule {
    public static RemoteCommand INSTANCE;
    private static final int MAX_FEEDBACK_COMMAND_LENGTH = 128;
    private static final int MAX_FEEDBACK_TEXT_BYTES = 16_384;
    private static final int SESSION_EXPIRE_LENGTH = 1200;
    private static final SecureRandom SESSION_ID_RANDOM = new SecureRandom();

    private static final StringFormat DEFAULT_SEND_FORMAT =
            new StringFormat(List.of("target", "command"), "/minecraft:msg {target} {command}");

    private static final Data DEFAULT_DATA = new Data(true, false, true, List.of(), List.of());

    private static final List<String> CONFIG_KEYS =
            List.of("allow-control", "auto-control", "whitelist-enabled", "allowed-controller-ids", "controlled-ids");

    private static final Codec<Data> CONFIG_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("role").forGetter(data -> Optional.<String>empty()),
                    Codec.BOOL.optionalFieldOf("allow-control").forGetter(data -> Optional.of(data.allowControl())),
                    Codec.BOOL.optionalFieldOf("auto-control").forGetter(data -> Optional.of(data.autoControl())),
                    Codec.BOOL
                            .optionalFieldOf("whitelist-enabled", DEFAULT_DATA.whitelistEnabled())
                            .forGetter(Data::whitelistEnabled),
                    Codec.STRING
                            .listOf()
                            .optionalFieldOf("allowed-controller-ids", DEFAULT_DATA.allowedControllerIds())
                            .forGetter(Data::allowedControllerIds),
                    Codec.STRING
                            .listOf()
                            .optionalFieldOf("controlled-ids", DEFAULT_DATA.controlledIds())
                            .forGetter(Data::controlledIds))
            .apply(
                    instance,
                    (legacyRole, allowControl, autoControl, whitelistEnabled, allowedControllerIds, controlledIds) ->
                            new Data(
                                    allowControl.orElseGet(() ->
                                            legacyRole.map("CONTROLLED"::equals).orElse(true)),
                                    autoControl.orElseGet(() ->
                                            legacyRole.map("CONTROLLER"::equals).orElse(false)),
                                    whitelistEnabled,
                                    allowedControllerIds,
                                    controlledIds)));

    private static final Codec<LegacySettings> LEGACY_SETTINGS_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                            StringFormat.TYPE
                                    .typeCodec()
                                    .optionalFieldOf("send-format", DEFAULT_SEND_FORMAT)
                                    .forGetter(LegacySettings::sendFormat),
                            Codec.STRING
                                    .optionalFieldOf("command-prefix", "/!!")
                                    .forGetter(LegacySettings::commandPrefix),
                            Codec.INT
                                    .optionalFieldOf("send-interval-ticks", 20)
                                    .forGetter(LegacySettings::sendIntervalTicks),
                            Codec.STRING
                                    .listOf()
                                    .optionalFieldOf("temporary-friends", List.of())
                                    .forGetter(LegacySettings::temporaryFriends))
                    .apply(instance, LegacySettings::new));

    private final FileStorage fileStorage = FileManager.getInstance().getInternalStorage("remote-command.nbt");
    private final LegacySettings legacySettings = fileStorage.read(
            LEGACY_SETTINGS_CODEC, () -> new LegacySettings(DEFAULT_SEND_FORMAT, "/!!", 20, List.of()));

    public final ModulePath root = makePath(Configs.CHAT_CONFIG, "remote-command");
    public final NBTRef<StringFormat> sendFormat = builder(root.add("send-format"), StringFormat.class)
            .defaultValue(legacySettings.sendFormat())
            .build();
    public final StringRef commandPrefix = builder(root.add("command-prefix"), StringRef.TYPE)
            .defaultValue(legacySettings.commandPrefix())
            .build();
    public final IntRef sendIntervalTicks = builder(root.add("send-interval-ticks"), IntRef.TYPE)
            .defaultValue(Math.max(1, legacySettings.sendIntervalTicks()))
            .validator(ticks -> ticks > 0)
            .build();
    public final ListRef cooperator = builder(root.add("cooperator"), ListRef.TYPE)
            .defaultValue(legacySettings.temporaryFriends())
            .build();

    private Data data = fileStorage.read(CONFIG_CODEC, () -> DEFAULT_DATA);
    private final Deque<String> queuedRemoteMessages = new ArrayDeque<>();
    private final Map<Integer, RemoteSession> sessions = new ConcurrentHashMap<>();
    private final TimerExecutor remoteMessageTickExecutor = new TimerExecutor();

    public final AbstractMainCommand REMOTE = new AbstractMainCommand();

    public RemoteCommand() {
        super("RemoteCommand");
        INSTANCE = this;
        registerInternalCommands();
    }

    public boolean allowsControl() {
        return data.allowControl();
    }

    public boolean automaticallyControlsOthers() {
        return data.autoControl();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getChatMessageReceive(), this::onChatReceive, Integer.MAX_VALUE);
        registerListener(Listener.getPostTick(), this::onPostTick);
        registerListener(Listener.getServerLeavePoint(), event -> queuedRemoteMessages.clear());
        registerCommandBootstrap(this::onLocalCommandBootstrap);
    }

    @Override
    public void addCustomWidgets(
            Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank, ElementFactory factory) {
        super.addCustomWidgets(acceptor, dx, dy, dblank, factory);
        MutableRecord mutable = MutableRecord.of(CONFIG_KEYS, data);
        for (var component : mutable.getComponents()) {
            String key = component.getFirst();
            Ref<?> ref = Refs.wrapInstance(component.getSecond());
            ref.addUpdateListener(updated -> updateConfig(mutable, key, updated));
            acceptor.accept(ref.createKeyValue("widget.remote-command." + key)
                    .generateKeyValueInput(
                            factory, 0, dblank, indexWidth, blankWidth, dx - indexWidth - blankWidth, dy));
        }
        acceptor.accept(factory.createTitle("widget.remote-command.command", 0, dblank, dx, dy));
    }

    private void onChatReceive(Event<ChatRecv> event) {
        if (event.isCancelled()) {
            return;
        }
        RemoteCommandExecution execution = createExecution(event.context());
        if (execution != null && executeCommand(event.context().text(), execution)) {
            event.cancel();
        }
    }

    private RemoteCommandExecution createExecution(ChatRecv recv) {
        if (mc.player == null) {
            return null;
        }
        String senderName = recv.senderName().filter(name -> !name.isBlank()).orElseGet(() -> recv.senderProfile()
                .map(VRecord::getName)
                .filter(name -> !name.isBlank())
                .orElse(null));
        String senderId =
                recv.senderProfile().map(VRecord::getId).map(UUID::toString).orElse(null);
        senderName = resolvePlayerName(senderName, senderId);
        if ((senderName == null || senderName.isBlank()) && senderId == null) {
            return null;
        }
        if (senderName != null && senderName.equalsIgnoreCase(VRecord.getName(mc.player.getGameProfile()))) {
            return null;
        }
        if (senderId != null && senderId.equalsIgnoreCase(mc.player.getUuid().toString())) {
            return null;
        }

        List<String> permissions = permissionsFor(senderName, senderId);
        String targetName = senderName;
        return new RemoteCommandExecution(
                mc.player,
                recv.senderProfile().orElse(null),
                senderName,
                permissions,
                data.allowControl() ? message -> sendFeedback(targetName, message) : null);
    }

    private List<String> permissionsFor(String senderName, String senderId) {
        if (data.allowControl()
                && (!data.whitelistEnabled()
                        || matchesConfiguredId(data.allowedControllerIds(), senderName, senderId))) {
            return List.of(
                    RemoteCommandExecution.COMMON_PERMISSION,
                    RemoteCommandExecution.TEAMMATE_PERMISSION,
                    RemoteCommandExecution.CONTROLLER_PERMISSION);
        }
        if (isCooperator(senderName)
                || (data.autoControl() && matchesConfiguredId(data.controlledIds(), senderName, senderId))) {
            return List.of(RemoteCommandExecution.COMMON_PERMISSION, RemoteCommandExecution.TEAMMATE_PERMISSION);
        }
        return List.of();
    }

    private boolean isCooperator(String playerName) {
        TargetSelector targetSelector = TargetSelector.INSTANCE;
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        return containsPlayerName(cooperator.get(), playerName)
                || (targetSelector != null && targetSelector.isFriend(playerName));
    }

    private void onPostTick() {
        if (checkNull()) return;
        expireSessionCallbacks();
        if (!queuedRemoteMessages.isEmpty()) {
            remoteMessageTickExecutor.run(Math.max(1, sendIntervalTicks.get()), () -> {
                String message = queuedRemoteMessages.pollFirst();
                if (message != null) {
                    ChatTasks.sayMessage(message, false);
                }
            });
        }
    }

    public int createSessionId() {
        int sessionId;
        RemoteSession session = new RemoteSession(Tasks.getTick() + SESSION_EXPIRE_LENGTH);
        do {
            sessionId = 1 + SESSION_ID_RANDOM.nextInt(Integer.MAX_VALUE - 1);
        } while (sessions.putIfAbsent(sessionId, session) != null);
        return sessionId;
    }

    public void addSessionCallback(int sessionId, IntConsumer callback) {
        RemoteSession session = sessions.get(sessionId);
        if (session == null || session.callback != null) {
            throw new IllegalArgumentException("Unknown or already registered remote session ID: " + sessionId);
        }
        session.callback = Objects.requireNonNull(callback);
    }

    private void expireSessionCallbacks() {
        int currentTick = Tasks.getTick();
        sessions.forEach((id, session) -> {
            if (currentTick >= session.expireTick) {
                sessions.remove(id, session);
            }
        });
    }

    private void cancelSessionCallback(int sessionId) {
        sessions.remove(sessionId);
    }

    private boolean completeSessionCallback(int sessionId, int retVal) {
        RemoteSession session = sessions.remove(sessionId);
        if (session == null) {
            return false;
        }
        IntConsumer callback = session.callback;
        if (callback != null) {
            try {
                callback.accept(retVal);
            } catch (Throwable error) {
                Debug.info("Remote session callback failed: ", error);
            }
        }
        return true;
    }

    private boolean handleAck(int sessionId, int retVal) {
        return completeSessionCallback(sessionId, retVal);
    }

    private boolean handleCooperatorAddRequest(CommandExecution sender, int sessionId) {
        if (!(sender instanceof RemoteCommandExecution remoteSender)) {
            return false;
        }
        String senderName = remoteSender.getSenderName();
        if (senderName == null || senderName.isBlank()) {
            return false;
        }
        Text prompt = Text.translatable("message.remote-command.cooperator-request", senderName)
                .append(" ")
                .append(ChatTasks.createCallbackText(() -> {
                    addCooperator(senderName);
                    sendSessionAck(sender, sessionId, 1);
                    Debug.chat(Text.translatable("message.remote-command.cooperator-added", senderName));
                }));
        Debug.chat(prompt);
        return true;
    }

    private boolean handleCooperatorRemoval(CommandExecution sender, int sessionId) {
        if (!(sender instanceof RemoteCommandExecution remoteSender)) {
            return false;
        }
        String senderName = remoteSender.getSenderName();
        if (senderName == null || senderName.isBlank()) {
            return false;
        }
        boolean removed = removeCooperator(senderName);
        sendSessionAck(sender, sessionId, removed ? 1 : 0);
        if (removed) {
            Debug.chat(Text.translatable("message.remote-command.cooperator-removed", senderName));
        }
        return true;
    }

    private void sendSessionAck(CommandExecution sender, int sessionId, int retVal) {
        if (sender instanceof RemoteCommandExecution remoteSender) {
            sendFormattedMessage(remoteSender.getSenderName(), "ack " + sessionId + " " + retVal);
        }
    }

    private boolean addCooperator(String playerName) {
        if (containsPlayerName(cooperator.get(), playerName)) {
            return false;
        }
        List<String> cooperators = new ArrayList<>(cooperator.get());
        cooperators.add(playerName);
        cooperator.set(List.copyOf(cooperators));
        return true;
    }

    private boolean removeCooperator(String playerName) {
        List<String> cooperators = new ArrayList<>(cooperator.get());
        boolean removed = cooperators.removeIf(name -> name.equalsIgnoreCase(playerName));
        if (removed) {
            cooperator.set(List.copyOf(cooperators));
        }
        return removed;
    }

    private boolean containsPlayerName(List<String> names, String playerName) {
        return names.stream().anyMatch(name -> name.equalsIgnoreCase(playerName));
    }

    private void onLocalCommandBootstrap(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.mainBuilder().name("remote_command").build();
        main.subBuilder(SubCommand.treeBuilder())
                .name("remote")
                .post(this::registerLocalRemoteSubCommands)
                .complete();
    }

    private void registerLocalRemoteSubCommands(TreeSubCommand remote) {
        remote.subBuilder(SubCommand.taskBuilder())
                .name("add")
                .helper("widget.remote-command.command.add.help")
                .arg(SimpleCommandArgs.argumentBuilder().name("player").build())
                .post(task -> task.executor((sender, args, reader) -> {
                    String playerName = readCooperatorTarget(sender, args, reader, "add");
                    if (playerName != null) {
                        requestCooperator(sender, playerName);
                    }
                    return true;
                }))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("run")
                .helper("widget.remote-command.command.run.help")
                .post(task -> task.executor((sender, args, reader) -> {
                    String vanillaCommand = reader.getRemainingArgStr().trim();
                    if (vanillaCommand.isEmpty()) {
                        sender.sendMessage("&cUsage: remote run <command>");
                        return true;
                    }
                    if (!sendRemoteCommand("remote " + vanillaCommand)) {
                        sender.sendMessage("&cNo online controlled endpoint is available.");
                    }
                    return true;
                }))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("send")
                .helper("widget.remote-command.command.send.help")
                .arg(SimpleCommandArgs.argumentBuilder().name("user").build())
                .post(task -> task.executor((sender, args, reader) -> {
                    String user = args.nextArgOrDefault(() -> null);
                    String command = reader.getRemainingArgStr().trim();
                    if (user == null || user.isBlank() || command.isEmpty()) {
                        sender.sendMessage("&cUsage: remote send <user> <command>");
                        return true;
                    }
                    if (!sendFormattedMessage(user, command)) {
                        sender.sendMessage("&cCould not send the remote command.");
                    }
                    return true;
                }))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("remove")
                .helper("widget.remote-command.command.remove.help")
                .arg(SimpleCommandArgs.argumentBuilder().name("player").build())
                .post(task -> task.executor((sender, args, reader) -> {
                    String playerName = readCooperatorTarget(sender, args, reader, "remove");
                    if (playerName != null) {
                        removeCooperator(playerName);
                        int sessionId = createSessionId();
                        addSessionCallback(sessionId, retVal -> {
                            if (retVal > 0) {
                                sender.sendMessage(
                                        Text.translatable("message.remote-command.cooperator-removed", playerName));
                            } else {
                                sender.sendMessage("&eThe remote player had no matching cooperator.");
                            }
                        });
                        if (!sendFormattedMessage(playerName, "removecoop " + sessionId)) {
                            cancelSessionCallback(sessionId);
                            sender.sendMessage("&cCould not send the cooperator removal request.");
                        }
                    }
                    return true;
                }))
                .complete();
    }

    private String readCooperatorTarget(
            CommandExecution sender, ArgumentInputStream args, ArgumentReader reader, String action) {
        String playerName = args.nextArgOrDefault(() -> null);
        if (playerName == null || playerName.isBlank() || reader.hasNext()) {
            sender.sendMessage("&cUsage: remote " + action + " <player>");
            return null;
        }
        if (mc.player == null || playerName.equalsIgnoreCase(VRecord.getName(mc.player.getGameProfile()))) {
            sender.sendMessage("&cYou cannot use this command for yourself.");
            return null;
        }
        return playerName;
    }

    private TaskSubCommand createAckCommand() {
        return SubCommand.taskBuilder()
                .name("ack")
                .arg(SimpleCommandArgs.argumentBuilder().name("session").build())
                .arg(SimpleCommandArgs.argumentBuilder().name("result").build())
                .post(builder -> builder.executor((sender, streamArgs, argsReader) -> {
                    int sessionId = streamArgs.nextInt();
                    int retVal = streamArgs.nextInt();
                    return handleAck(sessionId, retVal);
                }))
                .build();
    }

    private void requestCooperator(CommandExecution sender, String playerName) {
        int sessionId = createSessionId();
        addSessionCallback(sessionId, retVal -> {
            if (retVal > 0) {
                addCooperator(playerName);
                sender.sendMessage(Text.translatable("message.remote-command.cooperator-added", playerName));
            } else {
                sender.sendMessage("&cThe cooperator request was not accepted.");
            }
        });
        if (sendFormattedMessage(playerName, "addcoop " + sessionId)) {
            sender.sendMessage(Text.translatable("message.remote-command.cooperator-request-sent", playerName));
        } else {
            cancelSessionCallback(sessionId);
            sender.sendMessage("&cCould not send the cooperator request.");
        }
    }

    private String extractCommandText(Text text) {
        String prefix = commandPrefix.get();
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }
        String message = ChatUtils.textToPlainString(text);
        int prefixIndex = message.indexOf(prefix);
        return prefixIndex < 0
                ? null
                : message.substring(prefixIndex + prefix.length()).trim();
    }

    private boolean executeCommand(Text text, CommandExecution execution) {
        String commandText = extractCommandText(text);
        if (commandText == null || commandText.isBlank()) {
            return false;
        }
        String[] args = commandText.split("\\s+");
        try {
            return REMOTE.onCustomCommand(execution, new ArgumentReader(args));
        } catch (PermissionDenyError ignored) {
            // Remote permission denials are intentionally silent.
            return false;
        } catch (DispatchFailureError ignored) {
            return false;
        } catch (ArgumentException exception) {
            exception.handleAbort(execution, REMOTE);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    public boolean sendRemoteCommand(String commandText) {
        if (!data.autoControl()
                || mc.player == null
                || mc.getNetworkHandler() == null
                || commandText == null
                || commandText.isBlank()) {
            return false;
        }

        for (String controlledId : data.controlledIds()) {
            if (controlledId == null || controlledId.isBlank()) {
                continue;
            }
            PlayerListEntry target = findOnlinePlayer(controlledId);
            if (target == null) {
                continue;
            }
            String targetName = VRecord.getName(target.getProfile());
            if (mc.player.getUuid().equals(VRecord.getId(target.getProfile()))) {
                continue;
            }
            return sendFormattedMessage(targetName, commandText);
        }
        return false;
    }

    public boolean sendRemoteCommandTo(String targetName, String commandText) {
        return commandText != null && !commandText.isBlank() && sendFormattedMessage(targetName, commandText);
    }

    private void sendFeedback(String targetName, Text message) {
        if (targetName == null || targetName.isBlank() || message == null) {
            return;
        }
        String encoded = encodeFeedback(message);
        if (encoded == null) {
            return;
        }
        String commandText = "feedback " + encoded;
        if (commandText.length() > MAX_FEEDBACK_COMMAND_LENGTH) {
            encoded = encodeFeedback(Text.translatable("message.remote-command.feedback-too-lang")
                    .formatted(Formatting.RED));
            if (encoded == null) {
                return;
            }
            commandText = "feedback " + encoded;
            if (commandText.length() > MAX_FEEDBACK_COMMAND_LENGTH) {
                return;
            }
        }
        sendFormattedMessage(targetName, commandText);
    }

    private String encodeFeedback(Text message) {
        String json = ChatUtils.textToJsonString(message);
        return json == null
                ? null
                : Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private Text decodeFeedback(String encoded) {
        byte[] json;
        try {
            json = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (json.length > MAX_FEEDBACK_TEXT_BYTES) {
            return null;
        }
        return ChatUtils.textFromJsonString(new String(json, StandardCharsets.UTF_8));
    }

    private boolean sendFormattedMessage(String targetName, String commandText) {
        String prefix = commandPrefix.get();
        String format = prefix == null ? "" : prefix;
        if (format.isBlank() || targetName == null || targetName.isBlank()) {
            return false;
        }
        String command = commandText.startsWith(format)
                ? commandText
                : format.startsWith("/") && commandText.startsWith(format.substring(1))
                        ? format + commandText.substring(format.length() - 1)
                        : format + commandText;
        String message = sendFormat.get().format(Map.of("target", targetName, "command", command));
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return false;
        }
        queuedRemoteMessages.addLast(message);
        return true;
    }

    private void registerInternalCommands() {
        TreeSubCommand remoteSubCommands = REMOTE.mainBuilder().name("remote").build();
        remoteSubCommands.registerSub(createRemoteCommand());
        remoteSubCommands.registerSub(createFeedbackCommand());
        remoteSubCommands.registerSub(createAckCommand());
        remoteSubCommands
                .subBuilder(SubCommand.taskBuilder())
                .name("addcoop")
                .arg(SimpleCommandArgs.argumentBuilder().name("session").build())
                .post(task -> task.executor((sender, args, reader) -> {
                    int sessionId = args.nextInt();
                    return !reader.hasNext() && handleCooperatorAddRequest(sender, sessionId);
                }))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("removecoop")
                .arg(SimpleCommandArgs.argumentBuilder().name("session").build())
                .post(task -> task.executor((sender, args, reader) -> {
                    int sessionId = args.nextInt();
                    return !reader.hasNext() && handleCooperatorRemoval(sender, sessionId);
                }))
                .complete();
    }

    public boolean runVanillaCommand(RemoteCommandExecution sender, String vanillaCommand) {
        if (!data.allowControl()
                || sender == null
                || vanillaCommand == null
                || vanillaCommand.isBlank()
                || mc.player == null) {
            return false;
        }
        String command = vanillaCommand.startsWith("/") ? vanillaCommand : "/" + vanillaCommand;
        CommandExecution.sender(mc.player).sendMessage("&7Sending vanilla command: " + command);
        ChatTasks.sayMessage(command, false);
        return true;
    }

    private TaskSubCommand createRemoteCommand() {
        return SubCommand.taskBuilder()
                .name("remote")
                .permission(RemoteCommandExecution.CONTROLLER_PERMISSION + ".remote")
                .helper("widget.remote-command.command.remote.help")
                .post(builder -> builder.executor((sender, streamArgs, argsReader) -> {
                    if (!data.allowControl() || !(sender instanceof RemoteCommandExecution remoteSender)) {
                        return false;
                    }
                    String vanillaCommand = argsReader.getRemainingArgStr().trim();
                    if (vanillaCommand.isEmpty()) {
                        sender.sendMessage("&cUsage: remote <command>");
                        return true;
                    }
                    runVanillaCommand(remoteSender, vanillaCommand);
                    return true;
                }))
                .build();
    }

    private TaskSubCommand createFeedbackCommand() {
        return SubCommand.taskBuilder()
                .name("feedback")
                .permission(RemoteCommandExecution.COMMON_PERMISSION + ".feedback")
                .helper("widget.remote-command.command.feedback.help")
                .post(builder -> builder.executor((sender, streamArgs, argsReader) -> {
                    String[] values = argsReader.getRemainingArgs();
                    if (values.length != 1 || !data.autoControl()) {
                        return true;
                    }
                    Text feedback = decodeFeedback(values[0]);
                    if (feedback != null) {
                        logSub("Remote", feedback);
                    }
                    return true;
                }))
                .build();
    }

    private String resolvePlayerName(String playerName, String playerId) {
        if (playerName != null && !playerName.isBlank()) {
            return playerName;
        }
        if (playerId != null && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = findOnlinePlayer(playerId);
            if (entry != null) {
                return VRecord.getName(entry.getProfile());
            }
        }
        return playerName;
    }

    private boolean matchesConfiguredId(List<String> configuredIds, String playerName, String playerId) {
        String resolvedId = playerId;
        if (resolvedId == null && playerName != null && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = findOnlinePlayer(playerName);
            if (entry != null) {
                UUID id = VRecord.getId(entry.getProfile());
                resolvedId = id == null ? null : id.toString();
            }
        }
        for (String configuredId : configuredIds) {
            if (configuredId != null
                    && ((playerName != null && configuredId.equalsIgnoreCase(playerName))
                            || (resolvedId != null && configuredId.equalsIgnoreCase(resolvedId)))) {
                return true;
            }
        }
        return false;
    }

    private PlayerListEntry findOnlinePlayer(String id) {
        if (mc.getNetworkHandler() == null) {
            return null;
        }
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String playerName = VRecord.getName(entry.getProfile());
            Optional<UUID> playerId = Optional.ofNullable(VRecord.getId(entry.getProfile()));
            if ((playerName != null && playerName.equalsIgnoreCase(id))
                    || playerId.map(uuid -> uuid.toString().equalsIgnoreCase(id))
                            .orElse(false)) {
                return entry;
            }
        }
        return null;
    }

    private synchronized void updateConfig(MutableRecord mutable, String fieldName, Object value) {
        mutable.replaceMap(MutableRecord.of(CONFIG_KEYS, data));
        mutable.set(fieldName, value instanceof List<?> list ? List.copyOf(list) : value);
        data = mutable.toRecord(Data.class);
        fileStorage.write(CONFIG_CODEC, data);
    }

    public record Data(
            boolean allowControl,
            boolean autoControl,
            boolean whitelistEnabled,
            List<String> allowedControllerIds,
            List<String> controlledIds) {}

    private record LegacySettings(
            StringFormat sendFormat, String commandPrefix, int sendIntervalTicks, List<String> temporaryFriends) {}

    private static final class RemoteSession {
        private final int expireTick;
        private volatile IntConsumer callback;

        private RemoteSession(int expireTick) {
            this.expireTick = expireTick;
        }
    }
}
