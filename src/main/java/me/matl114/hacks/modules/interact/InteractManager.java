package me.matl114.hacks.modules.interact;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import me.matl114.accessors.hacks.KeyBindAccess;
import me.matl114.commands.MainCommand;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.InputArgument;
import me.matl114.utils.commands.params.impl.*;
import me.matl114.utils.commands.params.types.EntitySelector;
import me.matl114.utils.commands.params.types.ExecutePos;
import me.matl114.utils.commands.params.types.ExecuteRotation;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector2f;
import org.joml.Vector3d;

public class InteractManager extends BaseModule {
    public static InteractManager INSTANCE;

    private static final List<String> HAND_TABS = List.of("mainhand", "offhand");
    private static final List<String> SCHEDULE_TABS = List.of("once", "inf", "1", "4", "10", "20");
    private static final List<String> USE_TARGET_HEAD_TABS = List.of("look", "pos", "entity");
    private static final String USEITEM_HELP =
            "[hand|item_id] [once|inf|interval] [delay] <look|pos|entity> [pitch yaw|pos参数|@entity] 提交使用物品请求";
    private static final String ATTACK_HELP = "entity|block 提交攻击请求";
    private static final String ATTACK_ENTITY_HELP = "[hand|item_id] [once|inf|interval] [delay] <@entity> 提交实体攻击请求";
    private static final String ATTACK_BLOCK_HELP = "[hand|item_id] [once|inf|interval] [delay] <pos参数> 提交挖掘请求";
    private static final String HOLD_USEITEM_HELP =
            "[hand|item_id] [once|inf|interval] [delay] [release_ticks] 提交持续使用物品请求";
    private static final String LIST_HELP = "显示当前循环交互请求";
    private static final String CANCEL_HELP = "<id|all> 取消指定或全部循环交互请求";
    private static final String CLEAR_HELP = "取消全部循环交互请求";

    public final ModulePath module = makePath(Configs.INTERACT_CONFIG, "interaction-tweaks.interact-manager");

    public InteractManager() {
        INSTANCE = this;
    }

    public final FlagRef offhand = flagBuilder(module.add("offhand")).build();

    public final FlagRef swing = flagBuilder(module.add("swing-hand")).build();

    public final FlagRef disableLowVersionSpeedReset =
            flagBuilder(module.add("disable-low-version-speed-reset")).build();

    private final Map<String, InteractRequest> runningRequests = new LinkedHashMap<>();
    private long requestCounter = 0L;

    @Override
    public void registerAll() {
        super.registerAll();
        registerCommandBootstrap(this::bootstrapCommands);
        registerListener(Listener.getPreHandleInputEvents(), this::onInputEvent);
        registerListener(Listener.getServerLeavePoint(), this::onServerLeave);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(Listener.getPostHandleInputEvents(), this::onPostInputEvent);
    }

    boolean duringInput = false;

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        clearRunningRequests(null);
    }

    public void onServerLeave(Event<Void> event) {
        clearRunningRequests(null);
    }

    public void onWorldSwitch(Event<World> event) {
        clearRunningRequests(null);
    }

    int holdUseTick = -1;

    public void onInputEvent(Event<Void> event) {
        duringInput = true;
        if (checkNull()) return;
        var iter = runningRequests.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            var request = entry.getValue();
            Countdown countdown = request.countdown();
            if (countdown.canRun()) {
                if (countdown.countDown()) {
                    request.context().execute(this, mc.player);
                }
            } else {
                iter.remove();
            }
        }
        // hold use actions
        if (holdUseTick > 0) {
            holdUseTick--;
            mc.options.useKey.setPressed(true);
        } else if (holdUseTick == 0) {
            holdUseTick = -1;
            KeyBindAccess.of(mc.options.useKey).resetKeyState();
        }
    }

    public void onPostInputEvent(Event<Void> event) {
        duringInput = false;
    }

    private void bootstrapCommands(MainCommand mainCommand) {
        TreeSubCommand interact = mainCommand.mainBuilder().name("interact").build();
        interact.subBuilder(SubCommand.taskBuilder())
                .name("useitem")
                .helper(USEITEM_HELP)
                .arg(optionalHand("hand"))
                .arg(optionalTask("task"))
                .arg(optionalDelay("delay"))
                .arg(targetHeadArgument("target_type", USE_TARGET_HEAD_TABS))
                .arg(targetDispatchArgument(3, "target", true))
                .post(cmd -> cmd.executor((this::onUseItem)))
                .complete();
        interact.subBuilder(SubCommand.treeBuilder())
                .name("attack")
                .helper(ATTACK_HELP)
                .post(tree -> tree.subBuilder(SubCommand.taskBuilder())
                        .name("entity")
                        .helper(ATTACK_ENTITY_HELP)
                        .arg(optionalHand("hand"))
                        .arg(optionalTask("task"))
                        .arg(optionalDelay("delay"))
                        .arg(new EntityArgumentType("target"))
                        .post(cmd -> cmd.executor((this::onAttackEntity)))
                        .complete()
                        .subBuilder(SubCommand.taskBuilder())
                        .name("block")
                        .helper(ATTACK_BLOCK_HELP)
                        .arg(optionalHand("hand"))
                        .arg(optionalTask("task"))
                        .arg(optionalDelay("delay"))
                        .arg(new PosArgumentType("target"))
                        .post(cmd -> cmd.executor((this::onMineBlock)))
                        .complete())
                .complete();
        interact.subBuilder(SubCommand.taskBuilder())
                .name("holduseitem")
                .helper(HOLD_USEITEM_HELP)
                .arg(optionalHand("hand"))
                .arg(optionalTask("task"))
                .arg(optionalDelay("delay"))
                .arg(optionalHoldUse("release_ticks"))
                .post(cmd -> cmd.executor((this::onHoldUseItem)))
                .complete();

        TreeSubCommand interactMan =
                mainCommand.mainBuilder().name("interactman").build();
        interactMan
                .subBuilder(SubCommand.taskBuilder())
                .name("list")
                .helper(LIST_HELP)
                .post(cmd -> cmd.executor((this::onListRequests)))
                .complete();
        interactMan
                .subBuilder(SubCommand.taskBuilder())
                .name("cancel")
                .helper(CANCEL_HELP)
                .arg(SimpleCommandArgs.argumentBuilder()
                        .name("id")
                        .tabSupplier(() -> Stream.concat(Stream.of("all"), runningRequests.keySet().stream()))
                        .build())
                .post(cmd -> cmd.executor((this::onCancelRequest)))
                .complete();
        interactMan
                .subBuilder(SubCommand.taskBuilder())
                .name("clear")
                .helper(CLEAR_HELP)
                .post(cmd -> cmd.executor((this::onClearRequests)))
                .complete();
    }

    private OptionalArgumentType<String> optionalHand(String name) {
        return new OptionalArgumentType<>(
                name,
                handArgument(name),
                "mainhand",
                (execution, argument) -> isUseContextToken(argument.resultAsString()));
    }

    private ArgumentType<String> handArgument(String name) {
        return SimpleCommandArgs.argumentBuilder()
                .name(name)
                .tabCompletor((sender, args) -> useContextTabs(args))
                .build();
    }

    private OptionalArgumentType<String> optionalTask(String name) {
        return new OptionalArgumentType<>(
                name,
                SimpleCommandArgs.argumentBuilder()
                        .name(name)
                        .tabSupplier(SCHEDULE_TABS::stream)
                        .build(),
                "once",
                (execution, argument) -> isTaskToken(argument.resultAsString()));
    }

    private OptionalArgumentType<String> optionalDelay(String name) {
        return new OptionalArgumentType<>(
                name,
                SimpleCommandArgs.argumentBuilder().name(name).intValue().build(),
                "1",
                ((execution, inputArgument) -> parsePositiveInt(inputArgument.resultAsString()) != null));
    }

    private OptionalArgumentType<String> optionalHoldUse(String name) {
        return new OptionalArgumentType<>(
                name,
                SimpleCommandArgs.argumentBuilder().name(name).intValue().build(),
                "20",
                ((execution, inputArgument) -> parsePositiveInt(inputArgument.resultAsString()) != null));
    }

    private ArgumentType<String> targetHeadArgument(String name, List<String> allowedHeads) {
        return SimpleCommandArgs.argumentBuilder()
                .name(name)
                .select(allowedHeads)
                .build();
    }

    private ArgumentType<Object> targetDispatchArgument(int index, String name, boolean allowTarget) {
        DispatchArgumentType<Object> dispatch = new DispatchArgumentType<Object>(name)
                .registerArgumentDispatcher(index, "look", new RotationArgumentType(name + "_look"))
                .registerArgumentDispatcher(index, "pos", new MovTasks.TpaAndPosArgumentType(name + "_pos"));
        if (allowTarget) {
            dispatch.registerArgumentDispatcher(index, "entity", new EntityArgumentType(name + "_entity"));
        }
        return dispatch;
    }

    private Pair<UseContextSelector, Countdown> parseHandTaskContext(ArgumentInputStream streamArgs) {
        String handRaw = streamArgs.nextNonnullString();
        String taskRaw = streamArgs.nextNonnullString();
        int delay = streamArgs.nextInt();
        UseContextSelector hand = parseUseContextSelector(handRaw);
        Countdown task = parseInteractTask(taskRaw, delay);
        return Pair.of(hand, task);
    }

    private boolean checkNoRemainingArgs(ArgumentReader reader, CommandExecution context, String usage) {
        if (reader.hasNext()) {
            context.sendMessage("&c[Interact] 参数多余:" + reader.getRemainingArgStr());
            sendUsage(context, usage);
            return false;
        }
        return true;
    }

    private boolean onUseItem(CommandExecution context, ArgumentInputStream streamArgs, ArgumentReader reader) {
        if (!canSubmit(context)) return true;
        var htd = parseHandTaskContext(streamArgs);
        String typed = streamArgs.nextNonnullString();
        if (!USE_TARGET_HEAD_TABS.contains(typed)) {
            context.sendMessage("&c[Interact] 不存在的目标类型: " + typed);
            sendUsage(context, USEITEM_HELP);
            return true;
        }
        InputArgument<?> target = streamArgs.next();
        LookSupplier supplier = LookSupplier.of(target);
        if (supplier == null) {
            context.sendMessage("&c[Interact] 缺少或无效使用目标");
            sendUsage(context, USEITEM_HELP);
            return true;
        }
        if (!checkNoRemainingArgs(reader, context, USEITEM_HELP)) return true;
        return submitRequest(
                context,
                new InteractRequest(
                        nextRequestId("useitem"), htd.getSecond(), new UseItemContext(htd.getFirst(), supplier)));
    }

    private boolean onAttackEntity(CommandExecution context, ArgumentInputStream streamArgs, ArgumentReader reader) {
        if (!canSubmit(context)) return true;
        var htd = parseHandTaskContext(streamArgs);
        InputArgument<EntitySelector> targetArg = streamArgs.next();
        EntitySelector selector = targetArg.result();
        if (selector == null) {
            context.sendMessage("&c[Interact] 缺少或无效使用目标");
            sendUsage(context, USEITEM_HELP);
            return true;
        }
        if (!checkNoRemainingArgs(reader, context, USEITEM_HELP)) return true;
        return submitRequest(
                context,
                new InteractRequest(
                        nextRequestId("attack"), htd.getSecond(), new AttackContext(htd.getFirst(), selector)));
    }

    private boolean onMineBlock(CommandExecution context, ArgumentInputStream streamArgs, ArgumentReader reader) {
        if (!canSubmit(context)) return true;
        var htd = parseHandTaskContext(streamArgs);
        InputArgument<ExecutePos> targetArg = streamArgs.nextNonnull();
        ExecutePos selector = targetArg.result();
        if (selector == null) {
            context.sendMessage("&c[Interact] 缺少或无效使用目标");
            sendUsage(context, USEITEM_HELP);
            return true;
        }
        if (!checkNoRemainingArgs(reader, context, USEITEM_HELP)) return true;
        return submitRequest(
                context,
                new InteractRequest(nextRequestId("mine"), htd.getSecond(), new MineContext(htd.getFirst(), selector)));
    }

    private boolean onHoldUseItem(CommandExecution context, ArgumentInputStream streamArgs, ArgumentReader reader) {
        if (!canSubmit(context)) return true;
        var htd = parseHandTaskContext(streamArgs);
        int value = streamArgs.nextInt();
        if (!checkNoRemainingArgs(reader, context, USEITEM_HELP)) return true;
        return submitRequest(
                context,
                new InteractRequest(
                        nextRequestId("holduseitem"), htd.getSecond(), new HoldUseContext(htd.getFirst(), value)));
    }

    private boolean onListRequests(CommandExecution context, ArgumentInputStream streamArgs, ArgumentReader reader) {
        if (runningRequests.isEmpty()) {
            context.sendMessage("&e[Interact] 当前没有循环请求");
            return true;
        }
        context.sendMessage("&a[Interact] 当前循环请求:");
        runningRequests.values().forEach(request -> context.sendMessage("&7- " + request.id()));
        return true;
    }

    private boolean onCancelRequest(CommandExecution context, ArgumentInputStream streamArgs, ArgumentReader reader) {
        String rawId = streamArgs.nextNonnullString();
        if ("all".equalsIgnoreCase(rawId)) {
            int size = runningRequests.size();
            clearRunningRequests(null);
            context.sendMessage("&a[Interact] 已取消全部 " + size + " 个循环请求");
            return true;
        }
        String id = findRequestId(context, rawId);
        if (id == null) return true;
        InteractRequest removed = runningRequests.remove(id);
        if (removed == null) {
            context.sendMessage("&c[Interact] 找不到请求: " + rawId);
            return true;
        }
        context.sendMessage("&a[Interact] 已取消请求 " + removed.id());
        return true;
    }

    private boolean onClearRequests(CommandExecution context, ArgumentInputStream streamArgs, ArgumentReader reader) {
        int size = runningRequests.size();
        clearRunningRequests(context);
        context.sendMessage("&a[Interact] 已清空 " + size + " 个循环请求");
        return true;
    }

    private boolean submitRequest(CommandExecution context, InteractRequest request) {
        runningRequests.put(request.id(), request);
        context.sendMessage("&a[Interact] 已提交请求 " + request.id());
        return true;
    }

    private void clearRunningRequests(CommandExecution reporter) {
        if (runningRequests.isEmpty()) return;
        List<String> ids = List.copyOf(runningRequests.keySet());
        runningRequests.clear();
        if (reporter != null) {
            reporter.sendMessage("&a[Interact] 已清理 " + ids.size() + " 个循环请求");
        }
    }

    private boolean canSubmit(CommandExecution context) {
        if (mc.player == null || mc.world == null) {
            context.sendMessage("&c[Interact] 当前没有可用玩家或世界");
            return false;
        }
        return true;
    }

    private void sendUsage(CommandExecution context, String usage) {
        if (usage == null || usage.isBlank()) return;
        context.sendMessage("&7用法: " + usage);
    }

    private String nextRequestId(String type) {
        String prefix = type == null || type.isBlank() ? "task" : type;
        String id;
        do {
            id = prefix + "_" + (++requestCounter);
        } while (runningRequests.containsKey(id));
        return id;
    }

    private String findRequestId(CommandExecution context, String raw) {
        if (runningRequests.containsKey(raw)) {
            return raw;
        }
        List<String> matches = runningRequests.keySet().stream()
                .filter(id -> id.startsWith(raw))
                .toList();
        if (matches.isEmpty()) {
            context.sendMessage("&c[Interact] 找不到请求: " + raw);
            return null;
        }
        if (matches.size() > 1) {
            context.sendMessage("&c[Interact] 请求 ID 前缀不唯一: " + raw);
            return null;
        }
        return matches.get(0);
    }

    // Tab completions
    private static String currentToken(List<InputArgument<?>> args) {
        if (args.isEmpty()) return "";
        InputArgument<?> last = args.get(args.size() - 1);
        return last == null || last.tabbingString() == null ? "" : last.tabbingString();
    }

    private static Stream<String> useContextTabs(List<InputArgument<?>> args) {
        Stream<String> handTabs = HAND_TABS.stream();
        return Stream.concat(handTabs, itemStackTabs(currentToken(args))).distinct();
    }

    private static Stream<String> itemStackTabs(String token) {
        String raw = token == null ? "" : token;
        int metaStart = raw.indexOf('[');
        if (metaStart >= 0) {
            return potionMetaTabs(raw, metaStart);
        }
        Stream<String> itemIds = itemIdTabs();
        Item itemId = Registries.ITEM.get(Identifier.tryParse(token));
        if (itemId != Items.AIR && hasPotionComponent(itemId)) {
            return Stream.concat(itemIds, Stream.of(raw + "[")).distinct();
        }
        return itemIds;
    }

    private static Stream<String> itemIdTabs() {
        return Registries.ITEM.stream().map(Registries.ITEM::getId).flatMap(s -> Stream.of(s.toString(), s.getPath()));
    }

    private static Stream<String> potionMetaTabs(String raw, int metaStart) {
        if (raw.indexOf('[', metaStart + 1) >= 0 || raw.indexOf(']', metaStart + 1) >= 0) {
            return Stream.empty();
        }
        String itemRaw = raw.substring(0, metaStart);
        Item itemId = Registries.ITEM.get(Identifier.tryParse(itemRaw));
        if (itemId == Items.AIR || !hasPotionComponent(itemId)) return Stream.empty();
        String prefix = raw.substring(0, metaStart + 1);
        return potionIdTabs().map(id -> prefix + id + "]");
    }

    private static Stream<String> potionIdTabs() {
        return Registries.POTION.stream()
                .map(Registries.POTION::getId)
                .filter(Objects::nonNull)
                .flatMap(s -> Stream.of(s.toString(), s.getPath()));
    }

    private static boolean hasPotionComponent(Item itemId) {
        return itemId != null && itemId.getComponents().contains(DataComponentTypes.POTION_CONTENTS);
    }

    // Validations and parsers
    private static boolean isUseContextToken(String raw) {
        if (raw == null || raw.isBlank()) return false;
        return parseUseContextSelector(raw) != null;
    }

    private static UseContextSelector parseUseContextSelector(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw) {
            case "mainhand" -> UseContextSelector.fixed(Hand.MAIN_HAND);
            case "offhand" -> UseContextSelector.fixed(Hand.OFF_HAND);
            default -> UseContextSelector.item(parseItemStackSelector(raw));
        };
    }

    private static boolean isTaskToken(String raw) {
        if (raw == null || raw.isBlank()) return false;
        return parseInteractTask(raw, 0) != null;
    }

    private static Countdown parseInteractTask(String raw, int delay) {
        if (raw == null || raw.isBlank()) return new OnceCountdown(delay);
        String normalized = raw.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "once" -> new OnceCountdown(delay);
            case "inf", "infinite", "forever" -> new RepeatCountdown(delay);
            default -> {
                Integer interval = parsePositiveInt(raw);
                yield interval == null ? null : new RepeatCountdown(interval, delay);
            }
        };
    }

    private static ItemStackSelector parseItemStackSelector(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String itemRaw = raw;
        String metaRaw = null;
        int metaStart = raw.indexOf('[');
        if (metaStart >= 0) {
            if (!raw.endsWith("]") || metaStart == 0) return null;
            itemRaw = raw.substring(0, metaStart);
            metaRaw = raw.substring(metaStart + 1, raw.length() - 1);
            if (metaRaw.isBlank() || metaRaw.indexOf('[') >= 0 || metaRaw.indexOf(']') >= 0) return null;
        }
        Item item = Registries.ITEM.getOrEmpty(Identifier.tryParse(itemRaw)).orElse(null);
        if (item == null) return null;
        if (metaRaw != null) {
            Identifier id = Identifier.tryParse(metaRaw);
            if (Registries.POTION.containsId(id)) {
                RegistryEntry<Potion> potion = Registries.POTION.getEntry(Registries.POTION.get(id));
                return new ItemStackSelector(item, potion);
            }
        }
        return new ItemStackSelector(item, null);
    }

    private static Integer parsePositiveInt(String raw) {
        try {
            int val = Integer.parseInt(raw);
            return val > 0 ? val : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static CommandExecution currentExecution() {
        PlayerEntity player = mc.player;
        return player == null ? CommandExecution.EMPTY : CommandExecution.sender(player);
    }

    // Interaction request model
    public record InteractRequest(String id, Countdown countdown, InteractContext context) {}

    public interface Countdown {
        void cancel();

        boolean canRun();

        boolean countDown();
    }

    public abstract static class AbstractCountdown implements Countdown {
        final int delay;
        int currentDelay;

        public AbstractCountdown(int delay) {
            this.delay = delay;
            this.currentDelay = 0;
        }

        public boolean countDown() {
            if (++currentDelay >= delay) {
                return true;
            }
            return false;
        }
    }

    public static class OnceCountdown extends AbstractCountdown {
        boolean hasRun;

        public OnceCountdown(int delay) {
            super(delay);
        }

        @Override
        public void cancel() {
            hasRun = true;
        }

        @Override
        public boolean canRun() {
            return !hasRun;
        }

        @Override
        public boolean countDown() {
            if (hasRun) return false;
            if (super.countDown()) {
                hasRun = true;
                return true;
            }
            return false;
        }
    }

    public static class RepeatCountdown extends AbstractCountdown {
        private int repeatLeft;

        public RepeatCountdown(int intervalTicks, int delay) {
            super(delay);
            this.repeatLeft = intervalTicks;
        }

        public RepeatCountdown(int delay) {
            super(delay);
            this.repeatLeft = Integer.MAX_VALUE;
        }

        @Override
        public void cancel() {
            repeatLeft = 0;
        }

        @Override
        public boolean canRun() {
            return repeatLeft >= 0;
        }

        public boolean countDown() {
            if (super.countDown()) {
                repeatLeft--;
                return true;
            }
            return false;
        }
    }

    public interface InteractContext {
        String type();

        UseContextSelector hand();

        public void execute(InteractManager manager, PlayerEntity player);
    }

    public record AttackContext(UseContextSelector hand, EntitySelector entity) implements InteractContext {
        @Override
        public String type() {
            return "attack";
        }

        @Override
        public void execute(InteractManager manager, PlayerEntity player) {
            Entity entitySelect = entity.first(CommandExecution.sender(player));
            if (entitySelect == null) return;
            var entry = hand.getUseContext();
            if (entry != null) {
                Runnable runnable = InvExtra.INSTANCE.swapInventoryIndexToHand(entry.index());
                if (runnable != null) {
                    CombatTasks.getAttack().attackEntity(entitySelect);
                    runnable.run();
                }
            }
        }
    }

    public record MineContext(UseContextSelector hand, ExecutePos target) implements InteractContext {
        @Override
        public String type() {
            return "mine";
        }

        @Override
        public void execute(InteractManager manager, PlayerEntity player) {
            Vector3d vector3d = target.getPosition(CommandExecution.sender(player));
            BlockPos blockPos = new BlockPos((int) vector3d.x, (int) vector3d.y, (int) vector3d.z);
            var entry = hand.getUseContext();
            if (entry != null) {
                Runnable runnable = InvExtra.INSTANCE.swapInventoryIndexToHand(entry.index());
                if (runnable != null) {
                    mc.interactionManager.attackBlock(
                            blockPos, Direction.getFacing(mc.player.getEyePos().subtract(blockPos.toCenterPos())));
                    runnable.run();
                }
            }
        }
    }

    public record UseItemContext(UseContextSelector hand, LookSupplier target) implements InteractContext {
        @Override
        public String type() {
            return "useitem";
        }

        @Override
        public void execute(InteractManager manager, PlayerEntity player) {
            Vec2f supply = target.getLook(player);
            if (supply != null) {
                boolean shouldUseOffHand = InteractManager.shouldUseOffhandByDefault();
                var entry = hand.getUseContext();
                if (entry != null) {
                    var runnable = shouldUseOffHand
                            ? (InvExtra.INSTANCE.swapInventoryIndexToOffhand(entry.index()))
                            : InvExtra.INSTANCE.swapInventoryIndexToHand(entry.index());
                    if (runnable != null) {
                        Vec2f vec2f = new Vec2f(player.getPitch(), player.getYaw());
                        player.setPitch(supply.x);
                        player.setYaw(supply.y);
                        Hand hand = shouldUseOffHand ? Hand.OFF_HAND : Hand.MAIN_HAND;
                        var result = mc.interactionManager.interactItem(player, hand);
                        if (shouldSwingHandAfterUse()) {
                            InteractUtils.swingHandIfSuccess(result, hand);
                        }
                        player.setPitch(vec2f.x);
                        player.setYaw(vec2f.y);
                        runnable.run();
                    }
                }
            }
        }
    }

    @AllArgsConstructor
    public class HoldUseContext implements InteractContext {
        UseContextSelector hand;
        int releaseTicks;

        @Override
        public String type() {
            return "holduse";
        }

        @Override
        public UseContextSelector hand() {
            return hand;
        }

        @Override
        public void execute(InteractManager manager, PlayerEntity player) {
            var entry = hand.getUseContext();
            if (entry != null) {
                boolean offhand = (entry.index() == 40);
                var runnable = offhand
                        ? (InvExtra.INSTANCE.swapInventoryIndexToOffhand(entry.index()))
                        : InvExtra.INSTANCE.swapInventoryIndexToHand(entry.index());
                if (runnable != null) {
                    Hand hand = offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
                    var result = mc.interactionManager.interactItem(player, hand);
                    manager.holdUseTick = releaseTicks;
                    if (shouldSwingHandAfterUse()) {
                        InteractUtils.swingHandIfSuccess(result, hand);
                    }
                }
            }
        }
    }

    public interface UseContextSelector {
        IndexEntry<ItemStack> getUseContext();

        static UseContextSelector any() {
            return new AnyUseContextSelector();
        }

        static UseContextSelector fixed(Hand hand) {
            return new FixedUseContextSelector(hand);
        }

        static UseContextSelector item(ItemStackSelector itemStack) {
            return itemStack == null ? null : new ItemUseContextSelector(itemStack);
        }
    }

    public record AnyUseContextSelector() implements UseContextSelector {
        @Override
        public IndexEntry<ItemStack> getUseContext() {
            return currentHandContext(preferredHand());
        }
    }

    public record FixedUseContextSelector(Hand hand) implements UseContextSelector {
        @Override
        public IndexEntry<ItemStack> getUseContext() {
            return currentHandContext(normalizedHand(hand));
        }
    }

    public record ItemUseContextSelector(ItemStackSelector itemStack) implements UseContextSelector {
        @Override
        public IndexEntry<ItemStack> getUseContext() {
            return itemStack == null ? null : findMatchingItem(itemStack::matches, preferredHand());
        }
    }

    public record ItemStackSelector(Item item, RegistryEntry<Potion> potionType) {
        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty() || item == null) return false;
            if (!item.equals(stack.getItem())) return false;
            if (potionType == null) return true;
            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            return contents != null && contents.matches(potionType);
        }

        public String asString() {
            Identifier itemId = item == null ? null : Registries.ITEM.getId(item);
            return (itemId == null ? "itemstack" : itemId.toString())
                    + (potionType == null ? "" : "[" + potionType + "]");
        }
    }

    public interface LookSupplier {
        Vec2f getLook(PlayerEntity pl);

        static LookSupplier of(InputArgument<?> inputArgument) {
            if (inputArgument instanceof PosArgumentResult pos) {
                ExecutePos pos2 = pos.nonnullResult();
                return (pl) -> {
                    CommandExecution execution = CommandExecution.sender(pl);
                    Vector3d vector3d = pos2.getPosition(execution);
                    return EntityUtils.rotationToPitchYaw(
                            new Vec3d(vector3d.x(), vector3d.y(), vector3d.z()).subtract(pl.getEyePos()));
                };
            } else if (inputArgument instanceof RotationArgumentResult rot) {
                ExecuteRotation executeRotation = rot.nonnullResult();
                return (pl) -> {
                    CommandExecution execution = CommandExecution.sender(pl);
                    Vector2f vector2f = executeRotation.getRotation(execution);
                    return new Vec2f(vector2f.x(), vector2f.y());
                };
            } else if (inputArgument instanceof EntityArgumentResult result) {
                EntitySelector selector = result.nonnullResult();
                return (pl) -> {
                    Entity entity = selector.first(CommandExecution.sender(pl));
                    if (entity != null) {
                        return EntityUtils.rotationToPitchYaw(
                                entity.getBoundingBox().getCenter().subtract(mc.player.getEyePos()));
                    } else {
                        return new Vec2f(pl.getPitch(), pl.getYaw());
                    }
                };
            } else return null;
        }
    }

    private static Hand preferredHand() {
        return shouldUseOffhandByDefault() ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    private static Hand normalizedHand(Hand hand) {
        return hand == null ? Hand.MAIN_HAND : hand;
    }

    private static IndexEntry<ItemStack> currentHandContext(Hand hand) {
        if (mc.player == null) return null;
        Hand normalized = normalizedHand(hand);
        int index = normalized == Hand.OFF_HAND ? 40 : InventoryUtils.getSelectedSlot();
        return new IndexEntry<>(index, mc.player.getStackInHand(normalized));
    }

    private static boolean shouldUseOffhandByDefault() {
        return INSTANCE != null && INSTANCE.offhand.get();
    }

    private static boolean shouldSwingHandAfterUse() {
        return INSTANCE != null && INSTANCE.swing.get();
    }

    private static IndexEntry<ItemStack> findMatchingItem(Predicate<ItemStack> predicate, Hand preferredHand) {
        if (mc.player == null) return null;
        Hand normalized = normalizedHand(preferredHand);
        if (normalized == Hand.MAIN_HAND && predicate.test(mc.player.getStackInHand(Hand.MAIN_HAND))) {
            return currentHandContext(Hand.MAIN_HAND);
        }
        if (normalized == Hand.OFF_HAND && predicate.test(mc.player.getStackInHand(Hand.OFF_HAND))) {
            return currentHandContext(Hand.OFF_HAND);
        }
        IndexEntry<ItemStack> found =
                InventoryUtils.findPlayerItem(predicate, true, false, true, normalized == Hand.OFF_HAND);
        return found == null ? null : new IndexEntry<>(found.index(), found.val());
    }
}
