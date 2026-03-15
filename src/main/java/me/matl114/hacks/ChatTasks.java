package me.matl114.hacks;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.Getter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.gui.invcache.InventoryViewScreen;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.chat.*;
import me.matl114.hacks.modules.combat.Attack;
import me.matl114.hacks.modules.combat.BowEnhance;
import me.matl114.hacks.modules.combat.ProjectileEnhance;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.task.RepeatTask;
import me.matl114.utils.*;
import me.matl114.utils.commands.CommandUtils;
import me.matl114.utils.commands.commandGroup.*;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.impl.DispatchArgumentType;
import me.matl114.utils.commands.params.impl.PosArgumentType;
import me.matl114.utils.commands.params.types.ExecutePos;
import me.matl114.utils.tasks.LimitedSpeedExecutor;
import me.matl114.versioned.api.VEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.visitor.NbtTextFormatter;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class ChatTasks {
    public static void init() {}

    @Getter
    @ApiMethod
    public static final ModuleGroup moduleManager = new ModuleGroup("Chat");

    @Getter
    public static ChatExtra chatExtra;

    @Getter
    public static ChatTools chatTools;

    @Getter
    public static ClientSideCommand clientSideCommand;

    @Getter
    public static ChatCombine chatCombine;

    @Getter
    public static InGuiChatBox inGuiChatBox;

    private static void initModules(ModuleManager m) {
        chatExtra = new ChatExtra().register(m);

        chatTools = new ChatTools().register(m);

        clientSideCommand = new ClientSideCommand().register(m);

        chatCombine = new ChatCombine().register(m);
        inGuiChatBox = new InGuiChatBox().register(m);
    }

    static {
        moduleManager.registerFactories(ChatTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
    // ========================================== utilities ========================================
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // modified from @ChatScreen.class
    public static void sayMessage(String chatText, boolean addToHistory) {
        if (MinecraftClient.getInstance().player != null
                && MinecraftClient.getInstance().player.networkHandler != null) {
            chatText = getChatExtra().normalizeSendText(chatText);
            // in world
            if (addToHistory) {
                MinecraftClient.getInstance().inGameHud.getChatHud().addToMessageHistory(chatText);
            }
            if (chatText.startsWith("/")) {
                MinecraftClient.getInstance().player.networkHandler.sendChatCommand(chatText.substring(1));
            } else {
                MinecraftClient.getInstance().player.networkHandler.sendChatMessage(chatText);
            }
        }
    }

    @Getter
    private static final LimitedSpeedExecutor chatExecutor = new LimitedSpeedExecutor(new IntRef(5));

    public static void sendDelayChatMessage(Text text) {
        chatExecutor.addDelayedExecuteTask(() -> mc.inGameHud.getChatHud().addMessage(text));
    }

    static {
        Tasks.registerGameTask(player -> {
            chatExecutor.reset();
        });
        Listener.getChatSend().registerHandler(ChatTasks::parseClientCommand);
    }

    // ====================================== client commands ========================================
    private static SlimefunHelperMainCommand REGISTERED_COMMANDS;
    private static final List<Consumer<SlimefunHelperMainCommand>> COMMAND_BOOTSTRAPS = new ArrayList<>();

    public static void reloadAllCommand() {
        REGISTERED_COMMANDS = new SlimefunHelperMainCommand();
        Debug.chat("SfHelper Command Successfully reloaded");
    }

    public static class SlimefunHelperMainCommand extends AbstractMainCommand {
        TreeSubCommand main = mainBuilder().name("").build();

        // SubCommand mainCommand = genMainCommand("");
        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("reload")
                    .helper("<what: default main> 重载模块")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("what")
                            .select(List.of("command", "module"), "command")
                            .build())
                    .post(e -> e.executor(CommandContext.run(SlimefunHelperMainCommand.this::onReload)))
                    .complete();
        }

        public boolean onReload(ArgumentInputStream args) {
            var re = args.nextNonnullString();
            switch (re) {
                case "command" -> Tasks.scheduleDelayed(ChatTasks::reloadAllCommand, 1);
                    // case "vanilla" -> Tasks.scheduleDelayed(ChatTasks::reloadVanillaClientCommand, 1);
                case "module" -> {
                    CompletableFuture.runAsync(() -> mc.execute(HackModules::reloadModuleGroups));
                }
                default -> Debug.chat("不支持的参数类型: " + re);
            }
            return true;
        }

        List<String> pageType =
                List.of("guide", "rtype", "vanilla", "saved", "itemedit", "invcache", "config", "scanner");

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("openmenu")
                    .helper("<page:default guide> 打开模组的特殊界面")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("page")
                            .select(pageType, "guide")
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onOpenMenu)))
                    .complete();
        }

        public void onOpenMenu(ArgumentInputStream s) {
            switch (s.nextSelect(pageType)) {
                case "rtype" -> Tasks.scheduleDelayed(SlimefunTasks.getSlimefunGuide()::openCraftTypeMenu, 1);
                case "vanilla" -> Tasks.scheduleDelayed(SlimefunTasks.getSlimefunGuide()::openVanillaRecipesMenu, 1);
                case "saved" -> Tasks.scheduleDelayed(SlimefunTasks.getSlimefunGuide()::openSaveItemMenu, 1);
                case "itemedit" -> Tasks.scheduleDelayed(InvTasks::openEditorForPlayer, 1);
                case "invcache" -> Tasks.scheduleDelayed(InvTasks::openInventoryCacheScreen, 1);
                case "config" -> Tasks.scheduleDelayed(MainTasks::openConfigNewStyleScreen, 1);
                case "scanner" -> Tasks.scheduleDelayed(ExtraTasks.getServerScanner()::openScannerScreen, 1);
                default -> Tasks.scheduleDelayed(SlimefunTasks.getSlimefunGuide()::openMainGuideMenu, 1);
            }
            Debug.chat(Text.literal("成功打开界面").formatted(Formatting.GREEN));
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("config")
                    .helper("<operation:default open> 配置文件操作")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("operation")
                            .select(List.of("open", "reload"), "open")
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onConfig)))
                    .complete();
        }

        public void onConfig(ArgumentInputStream s) {
            String next = s.nextNonnull();
            switch (next) {
                case "open" -> {
                    Tasks.scheduleDelayed(MainTasks::openConfigNewStyleScreen, 1);
                    Debug.chat(Text.literal("成功打开配置文件界面").formatted(Formatting.GREEN));
                }
                case "reload" -> {
                    Tasks.scheduleDelayed(Config::reloadAll, 1);
                    Debug.chat(Text.literal("成功重载配置文件").formatted(Formatting.GREEN));
                }
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("task")
                    .helper("<taskid> <args> 运行内置任务")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("taskid")
                            .tabSupplier(() -> MainTasks.getSpecialTaskName().stream())
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onTask)))
                    .complete();
        }

        public boolean onTask(PlayerEntity player, ArgumentInputStream s, ArgumentReader reader) {

            String val = s.nextNonnull();
            String[] extraArg = reader.getRemainingArgs();

            try {
                MainTasks.runSpecialTask(val, extraArg);
            } catch (Throwable e) {
                Debug.chat("运行Task出现错误!:", e.getMessage());
                Debug.info(e);
            }
            return true;
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("asynctask")
                    .helper("<taskid> <args> 运行内置任务")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("taskid")
                            .tabSupplier(() -> MainTasks.getSpecialTaskName().stream())
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onAsyncTask)))
                    .complete();
        }

        public boolean onAsyncTask(PlayerEntity player, ArgumentInputStream s, ArgumentReader reader) {
            String val = s.nextNonnull();
            String[] extraArg = reader.getRemainingArgs();
            CompletableFuture.runAsync(() -> {
                try {
                    MainTasks.runSpecialTask(val, extraArg);
                } catch (Throwable e) {
                    Debug.chat("运行Task出现错误!:", e.getMessage());
                    Debug.info(e);
                }
            });
            return true;
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("recipes")
                    .helper("<action:default enable> 管理配方系统")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("action")
                            .select(List.of("reload", "enable"), "enable")
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onRecipe)))
                    .complete();
        }

        public void onRecipe(ArgumentInputStream s) {
            switch (s.nextNonnullString()) {
                case "enable" -> {
                    SlimefunTasks.getSlimefunGuide().handleAutoEnable();
                }
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("debug")
                    .helper("<debug> <state> 调试项开关")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("debug")
                            .select(List.of("packet-in", "packet-out", "log-to-chat"))
                            .build())
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("state")
                            .bool()
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onDebugState)))
                    .complete();
        }

        public void onDebugState(ArgumentInputStream s) {
            var debug = s.nextNonnullString();
            switch (debug) {
                case "packet-in" -> {
                    ExtraTasks.getPacketDebugger().debugIn.set(s.nextBoolean());
                }
                case "packet-out" -> {
                    ExtraTasks.getPacketDebugger().debugOut.set(s.nextBoolean());
                }
                case "log-to-chat" -> {
                    ExtraTasks.DEBUG_INTO_CHAT = s.nextBoolean();
                }
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("registry")
                    .helper("<id> <filter:\"\"> 查看原版注册表")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("id")
                            .tabSupplier(() -> ItemStackUtils.registry()
                                    .streamAllRegistryKeys()
                                    .map(RegistryKey::getValue)
                                    .map(i -> "minecraft".equals(i.getNamespace()) ? i.getPath() : i.toString()))
                            .build())
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("filter")
                            .select("<namespace_filter>:<path_filter>")
                            .defaultValue("")
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onListRegistry)))
                    .complete();
        }

        public void onListRegistry(ArgumentInputStream re) {
            Identifier identifier = Identifier.tryParse(re.nextNonnull());
            RegistryKey registryKey = RegistryKey.ofRegistry(identifier);
            Registry result = (Registry)
                    ItemStackUtils.registry().getOptional(registryKey).orElse(null);
            if (result != null) {
                String filter = re.nextNonnull();
                Debug.chat(Text.literal(identifier.toString() + "所拥有的注册项:").formatted(Formatting.GREEN));
                Identifier filterId = Identifier.tryParse(filter);
                boolean namespace = filter.contains(":");
                for (var id : result.getKeys()) {
                    Identifier identifier1 = ((RegistryKey) id).getValue();
                    String val = identifier1.getPath();
                    if (filterId == null
                            || (val.contains(filterId.getPath())
                                    && (!namespace || identifier1.getNamespace().contains(filterId.getNamespace())))) {
                        Debug.chat(identifier1);
                    }
                }
            } else {
                Debug.chat(Text.literal("不存在的注册表: " + identifier).formatted(Formatting.RED));
            }
        }

        List<String> resourceTypes = List.of("world", "command", "seed", "plugins", "version");

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("resource")
                    .helper("<id> <filter:\"\"> 查看某些原版重要数据")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("id")
                            .select(resourceTypes)
                            .build())
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("filter")
                            .select("<namespace_filter>:<path_filter>")
                            .defaultValue("")
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onResource)))
                    .complete();
        }

        public void onResource(ArgumentInputStream re) {
            String val = re.nextSelect(resourceTypes);
            String filter = re.nextNonnull();
            Identifier filterId = Identifier.tryParse(filter);
            boolean namespace = filter.contains(":");
            List datas = new ArrayList<>();
            switch (val) {
                case "world" -> {
                    datas = mc.getNetworkHandler().getWorldKeys().stream()
                            .map(RegistryKey::getValue)
                            .filter(u -> filterId == null
                                    || (u.getPath().contains(filterId.getPath())
                                            && (!namespace || u.getNamespace().contains(filterId.getNamespace()))))
                            .toList();
                    onResource0(val, datas);
                }
                case "command" -> {
                    datas = mc.getNetworkHandler().getCommandDispatcher().getRoot().getChildren().stream()
                            .map(CommandNode::getName)
                            .filter(u -> u.contains(filter))
                            .sorted(String::compareTo)
                            .toList();
                    onResource0(val, datas);
                }
                case "seed" -> {
                    datas = List.of(
                            Text.literal("服务端加密种子: ")
                                    .append(ChatUtils.getDisplayedLong(mc.world.getBiomeAccess().seed)),
                            Text.literal("当前绑定种子: ")
                                    .append(
                                            MineTasks.getSeedOre().hasCurrentSeed()
                                                    ? ChatUtils.getDisplayedLong(MineTasks.getSeedOre()
                                                            .getCurrentSeed())
                                                    : Text.literal("暂未输入")));
                    onResource0(val, datas);
                }
                case "plugins" -> {
                    Debug.chat(Text.literal("导出Command Namespace获取的数据:").formatted(Formatting.GREEN));
                    datas = ClientUtils.getServerCommands().stream()
                            .map(n -> {
                                var sp = n.split(":");
                                return sp.length >= 2 ? sp[0] : null;
                            })
                            .filter(Objects::<String>nonNull)
                            .filter(u -> ((String) u).contains(filter))
                            .distinct()
                            .sorted(String::compareTo)
                            .toList();
                    onResource0(val, datas);
                    Debug.chat(Text.literal("导出Version Tab获取的数据:").formatted(Formatting.GREEN));
                    ClientUtils.getServerPluginResources().thenAccept((list) -> {
                        onResource0(
                                val,
                                list.stream()
                                        .map(str -> str.toLowerCase(Locale.ROOT))
                                        .filter(u -> u.contains(filter))
                                        .distinct()
                                        .sorted(String::compareTo)
                                        .toList());
                    });
                }
                    //                    case "gamerule"->{
                    //                        datas = mc.world.getGameRules().toNbt().entries.entrySet().stream()
                    //                            .map(entry-> entry.getKey()+ ":" + entry.getValue().asString())
                    //                            .filter(u-> u.contains(filter))
                    //                            .toList();
                    //                    }
                default -> {
                    Debug.chat(Text.literal("不支持的资源: " + val).formatted(Formatting.RED));
                }
            }
        }

        private void onResource0(String name, List datas) {
            Debug.chat(Text.literal(name + "所拥有的数据:").formatted(Formatting.GREEN));
            for (var identifier1 : datas) {
                Debug.chat(identifier1);
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("sleep")
                    .helper("<level> <confirm> 进入睡眠状态")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("level")
                            .intValue()
                            .build())
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("confirm")
                            .dispatchLastArg((str) -> {
                                int val = str.getInt();
                                if (val > 0) {
                                    return Stream.of("confirm");
                                } else {
                                    return Stream.of("第一个参数请输入正整数");
                                }
                            })
                            .defaultValue("")
                            .build())
                    .arg(SimpleCommandArgs.argumentBuilder().name("display").build())
                    .post(e -> e.executor(CommandContext.run(this::onSleep)))
                    .complete();
        }

        public void onSleep(ArgumentInputStream re) {
            int level = re.nextClampedInt(1, 3);
            if (level != 1 && level != 2) {
                Debug.chat("请输入范围内的数字: 1~2");
                return;
            }
            String val = re.nextNonnull();
            String val2 = re.nextArg();
            if ("confirm".equals(val)) {
                Tasks.scheduleDelayed(() -> RenderTasks.getSleepMode().setCustomScreenSleeping(level, val2), 1);
            } else {
                Debug.chat("使用sleep confirm 确认进入睡眠模式, 进入睡眠模式后可以按 "
                        + RenderTasks.getSleepMode().getWakeupButton() + " 键离开");
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("debug-render")
                    .helper("<task> <state> 调试渲染功能")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("task")
                            .select(List.of("collision", "combat", "bow-aim", "standing", "debug-tick"))
                            .build())
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("state")
                            .dispatchLastArg(s -> onDebugRenderTab(s.nonnullResultAsString()))
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onDebugRender)))
                    .complete();
        }

        public void onDebugRender(ArgumentInputStream re) {
            String task = re.nextNonnull();
            switch (task) {
                case "collision" -> RenderTasks.DEBUG_RENDER_COLLISION = re.nextBoolean();
                case "standing" -> RenderTasks.DEBUG_RENDER_STANDING = re.nextBoolean();
                case "combat" -> RenderTasks.DEBUG_RENDER_COMBAT = re.nextBoolean();
                case "bow-aim" -> RenderTasks.DEBUG_RENDER_BOWAIM = re.nextBoolean();
                case "debug-tick" -> RenderTasks.DEBUG_TICK = re.nextClampedInt(0, Integer.MAX_VALUE);
                default -> Debug.chat("没有调试项:", task);
            }
        }

        public Stream<String> onDebugRenderTab(String type) {
            return switch (type) {
                case "collision", "combat", "bow-aim", "standing" -> CommandUtils.bools().stream();
                case "debug-tick" -> CommandUtils.numbers().stream();
                default -> Stream.empty();
            };
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("tp")
                    .helper("<x> <y> <z> [-far] 执行模拟tp行为")
                    .arg(SimpleCommandArgs.argumentBuilder(PosArgumentType::new)
                            .name("position")
                            .build())
                    .post(e -> e.executor(this::onTp))
                    .complete();
        }

        public boolean onTp(CommandExecution p, ArgumentInputStream re, ArgumentReader reader) {
            ExecutePos executePos = re.nextArg();
            if (executePos != null) {
                Vector3d vector3d = executePos.getPosition(p);
                onTpa(new Vec3d(vector3d.x, vector3d.y, vector3d.z));
            } else {
                sendMessage(p, "输入了无效坐标!");
            }
            return true;
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("tpa")
                    .helper("<target> 传送到特殊目标位置")
                    .arg(SimpleCommandArgs.argumentBuilder(MovTasks.TpaArgumentType::new)
                            .name("tpa_target")
                            .build())
                    .post(e -> e.executor(this::onTpa))
                    .complete();
        }

        public boolean onTpa(CommandExecution var1, ArgumentInputStream streamArgs, ArgumentReader argsReader) {
            ExecutePos pos = streamArgs.nextArg();
            if (pos != null) {
                Vector3d vector3d = pos.getPosition(var1);
                onTpa(new Vec3d(vector3d.x, vector3d.y, vector3d.z));
            } else {
                sendMessage(var1, "输入了无效目标位置!");
            }
            return true;
        }

        public void onTpa(Vec3d pos) {
            MovTasks.executeTp(pos, 320, true, true);
        }

        {
            main.subBuilder(SubCommand.treeBuilder())
                    .name("travel")
                    .post(s -> s.subBuilder(SubCommand.taskBuilder())
                            .name("to")
                            .helper("<coord> 自动传送旅行")
                            .arg(SimpleCommandArgs.argumentBuilder(MovTasks.TpaAndPosArgumentType::new)
                                    .name("target")
                                    .build())
                            .post(e -> e.executor(this::onTravelTo))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("cancel")
                            .helper("中断传送旅行")
                            .post(e -> e.executor(CommandContext.run(this::onTravelCancel)))
                            .complete())
                    .complete();
        }

        public boolean onTravelTo(CommandExecution var1, ArgumentInputStream streamArgs, ArgumentReader argsReader) {
            ExecutePos pos = streamArgs.nextArg();
            if (pos != null) {
                Vector3d vector3d = pos.getPosition(var1);
                onTravel(var1.getExecutor(), new Vec3d(vector3d.x, vector3d.y, vector3d.z));
            }
            return true;
        }

        public void onTravel(PlayerEntity var1, Vec3d parsedCoord) {
            if (travelTask == null) {
                if (parsedCoord == null) return;
                travelTask = new RepeatTask(20, 2) {
                    Vec3d pos0 = parsedCoord;
                    final ClientPlayerEntity currentPlayer = mc.player;
                    final long startingTime = System.currentTimeMillis();
                    final Vec3d startPos = mc.player.getPos();

                    public void cancel() {
                        super.cancel();
                        MovTasks.doingTp = false;
                    }

                    private boolean finish() {
                        if (travelTask != this
                                || mc.player != currentPlayer
                                || mc.player.getPos().subtract(pos0).horizontalLengthSquared() < 900) {
                            Debug.chat("当前travel task已完成或者终止");
                            long usedSec = (System.currentTimeMillis() - startingTime) / 1000L;
                            Debug.info("using time", usedSec);
                            if (mc.player != null) {
                                double len = mc.player.getPos().distanceTo(startPos);
                                Debug.chat("时间开销:", usedSec, "s, 运行距离: ", len, ", 平均速度: ", len / usedSec, "m/s");
                                // send signal to reset distance
                                mc.player.setOnGround(false);

                                ClientPlayerAccess.of(mc.player)
                                        .setForceNoFall(true); // .fallDistance = MovTasks.FORCE_RESET_DISTANCE;
                            }

                            travelTask = null;
                            cancel();
                            return true;
                        } else {
                            return false;
                        }
                    }

                    private boolean move(Vec3d delta) {

                        if (delta.length() == 0) {
                            MovTasks.moveToWithPackets(mc.player.getPos(), null);
                            return false;
                        } else {
                            MovTasks.moveToWithPackets(mc.player.getPos().add(delta), Boolean.TRUE);
                            return finish();
                        }
                    }

                    int tickCNT = 0;
                    long lastTick;
                    //                                Vec3d vec3d = Vec3d.ZERO;
                    @Override
                    public boolean runTask() {
                        if (mc.player == null) return false;
                        MovTasks.doingTp = false;
                        mc.player.setOnGround(false);
                        tickCNT += 1;
                        //                                    Debug.info("distance ", vec3d, mc.player.getPos());
                        if (mc.player.getY() < mc.world.getBottomY() + mc.world.getHeight() + 64) {
                            MovTasks.farawayMove(new Vec3d(0, 128, 0), true);
                        } else {
                            // fixme error in boat, desync boat position
                            Vec3d towards = pos0.subtract(mc.player.getPos());

                            Vec3d towardsHorizontal = new Vec3d(towards.x, 0, towards.z).normalize();
                            //                                            if(move(Vec3d.ZERO)){
                            //                                                return true;
                            //                                            }
                            if (move(towardsHorizontal.multiply(9.9).add(0, -0.3, 0))) {
                                return true;
                            }
                            if (move(towardsHorizontal.multiply(9.9).add(0, -0.3, 0))) {
                                return true;
                            }
                            if (tickCNT % 3 == 0) {
                                if (move(towardsHorizontal.multiply(9.9).add(0, -0.3, 0))) {
                                    return true;
                                }
                            }
                        }
                        MovTasks.doingTp = true;
                        //                                    this.vec3d = mc.player.getPos();
                        return false;
                    }
                };
                Tasks.scheduleTask(travelTask);
            } else {
                Debug.chat("上一个travel task仍旧在执行,使用travel cancel取消");
            }
        }

        public void onTravelCancel() {
            if (travelTask != null) {
                travelTask.cancel();
                travelTask = null;
            }
        }

        public static RepeatTask travelTask;

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("mark")
                    .helper("<type> [extra] 标注一个位置为临时缓存位置")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("type")
                            .select(List.of("player", "camera", "this", "pos", "target", "cross", "clear"), "camera")
                            .build())
                    .arg(new DispatchArgumentType<Object>("extra")
                            .registerArgumentDispatcher(
                                    0,
                                    "pos",
                                    SimpleCommandArgs.argumentBuilder(PosArgumentType::new)
                                            .name("dispatch_pos")
                                            .build())
                            .registerArgumentDispatcher(
                                    0,
                                    "target",
                                    SimpleCommandArgs.argumentBuilder(MovTasks.TpaArgumentType::new)
                                            .name("dispatch_tpa")
                                            .build())
                            .registerArgumentDispatcher(
                                    0,
                                    "player",
                                    SimpleCommandArgs.argumentBuilder()
                                            .name("dispatch_player")
                                            .tabSupplier(() -> EntityUtils.getWorldPlayerNames(false))
                                            .build())
                            .registerDispatcher(
                                    (p, args) -> true,
                                    SimpleCommandArgs.argumentBuilder()
                                            .name("dispatch_default")
                                            .build()))
                    .post(e -> e.executor(this::onMark))
                    .complete();
        }

        public boolean onMark(CommandExecution var1, ArgumentInputStream re, ArgumentReader reader) {
            String type = re.nextNonnull();
            Vec3d pos;
            PlayerEntity sender = var1.getExecutorPlayer();
            switch (type) {
                case "this" -> pos = sender.getPos();
                case "camera" -> pos = RenderUtils.getCameraEntityPos();
                case "cross" -> pos = mc.crosshairTarget.getPos();
                case "player" -> {
                    String var = re.nextNonnull();
                    Entity player = EntityUtils.getPlayerByName(var);
                    if (player != null) {
                        pos = player.getPos();
                    } else {
                        sendMessage(var1, Text.literal("找不到实体或者玩家: " + var).formatted(Formatting.RED));
                        return true;
                    }
                }
                case "pos" -> {
                    ExecutePos executePos = re.nextArg();
                    if (executePos != null) {
                        var vcd3 = executePos.getPosition(var1);
                        pos = new Vec3d(vcd3.x, vcd3.y, vcd3.z);
                    } else {
                        sendMessage(var1, Text.literal("无效的坐标").formatted(Formatting.RED));
                        return true;
                    }
                }
                case "target" -> {
                    ExecutePos executePos = re.nextArg();
                    if (executePos != null) {
                        var vcd3 = executePos.getPosition(var1);
                        pos = new Vec3d(vcd3.x, vcd3.y, vcd3.z);
                    } else {
                        sendMessage(var1, Text.literal("无效的特殊位置").formatted(Formatting.RED));
                        return true;
                    }
                }
                case "clear" -> {
                    MovTasks.MARK = null;
                    return true;
                }
                default -> {
                    sendMessage(var1, Text.literal("不存在的mark类型: " + type).formatted(Formatting.RED));
                    return true;
                }
            }
            MovTasks.MARK = pos;
            Debug.chat("标记成功: ", ChatUtils.getDisplayedLocationDouble(pos));
            RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                            new RenderTasks.BoxObject(sender.dimensions.getBoxAt(MovTasks.MARK), Color.GREEN))
                    .setAutoStop(() -> MovTasks.MARK != pos));
            return true;
        }

        List<String> infoTypes =
                List.of("death", "spawn", "nbt", "inventory", "ender", "plist", "team", "pentry", "waypoint", "server");

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("info")
                    .helper("<information> <user> 查看某项信息")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("information")
                            .select(infoTypes)
                            .build())
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("user")
                            .dispatchLast(this::onInfoTab)
                            .select("#me")
                            .defaultValue("#me")
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onInfo)))
                    .complete();
        }

        public Stream<String> onInfoTab(String string) {
            return switch (string) {
                case "nbt", "inventory", "ender" -> EntityUtils.getWorldPlayerNames(true);
                case "pentry", "team" -> getPlayerListNames();
                case "waypoint" -> getWaypointNames();
                default -> Stream.empty();
            };
        }

        public Stream<String> getPlayerListNames() {
            return mc.getNetworkHandler().getPlayerList().stream()
                    .map(PlayerListEntry::getProfile)
                    .map(GameProfile::getName);
        }

        public Stream<String> getWaypointNames() {

            return Stream.empty();
        }

        public void onInfo(ArgumentInputStream re) {
            String info = re.nextSelect(infoTypes);
            PlayerEntity entity;
            String user = re.nextNonnull();
            entity = Objects.equals("#me", user) ? mc.player : EntityUtils.getPlayerByName(user);
            if (entity != null) {
                Debug.chat("Information about player : ", entity.getNameForScoreboard());
            }
            switch (info) {
                case "death" -> {
                    if (entity != null) {
                        var death = entity.getLastDeathPos();
                        if (death.isPresent()) {
                            var deathpoint = death.get();
                            var world = deathpoint.dimension();
                            Debug.chat(
                                    "Last Death Point [World:",
                                    world.getValue(),
                                    ",Pos:",
                                    ChatUtils.getDisplayedLocationDouble(Vec3d.of(deathpoint.pos())),
                                    "]");
                        } else {
                            Debug.chat("Last Death Point Not Present");
                        }
                    } else {
                        Debug.chat("找不到玩家", user);
                    }
                }
                case "spawn" -> {
                    Debug.chat("当前世界的出生点:");
                    GlobalPos pos = GlobalPos.create(mc.world.getRegistryKey(), mc.world.getSpawnPos());
                    Debug.chat(
                            "World Spawn Point [World:",
                            pos.dimension().getValue(),
                            ",Pos:",
                            ChatUtils.getDisplayedLocationDouble(Vec3d.of(pos.pos())),
                            "]");
                    //                        if(entity != null){
                    //                           // mc.player.spawn
                    //                        }else{
                    //                            Debug.chat("找不到玩家", user);
                    //                        }
                }
                case "nbt" -> {
                    if (entity != null) {
                        var comp = VEntity.saveEntityNbt(entity);
                        comp.remove("Inventory");
                        comp.remove("EnderItems");
                        Debug.chat(new NbtTextFormatter("").apply(comp));
                    } else {
                        Debug.chat("找不到玩家", user);
                    }
                }
                case "inventory" -> {
                    if (entity != null) {
                        PlayerInventory enderInventory = entity.getInventory();
                        Tasks.scheduleDelayed(
                                () -> {
                                    ScreenAccess.of(new InventoryViewScreen(
                                                    enderInventory,
                                                    Text.literal("背包预览 - " + entity.getNameForScoreboard()),
                                                    new ItemStack(Items.CHEST)))
                                            .openFromCurrent();
                                },
                                2);

                    } else {
                        Debug.chat("找不到玩家", user);
                    }
                }
                case "ender" -> {
                    if (entity != null) {
                        EnderChestInventory enderInventory = entity.getEnderChestInventory();
                        Tasks.scheduleDelayed(
                                () -> {
                                    ScreenAccess.of(new InventoryViewScreen(
                                                    enderInventory,
                                                    Text.literal("末影箱预览 - " + entity.getNameForScoreboard()),
                                                    new ItemStack(Items.ENDER_CHEST)))
                                            .openFromCurrent();
                                },
                                2);

                    } else {
                        Debug.chat("找不到玩家", user);
                    }
                }
                case "plist" -> {
                    Debug.chat(Text.literal("当前可视的玩家列表").formatted(Formatting.GREEN));
                    mc.getNetworkHandler().getPlayerList().stream()
                            .sorted(Comparator.comparing(e -> e.getProfile().getName()))
                            .map(entry -> {
                                var val = Text.literal("%-16s (Display: "
                                                .formatted(entry.getProfile().getName()))
                                        .append(
                                                entry.getDisplayName() == null
                                                        ? Text.literal("null")
                                                        : entry.getDisplayName())
                                        .append(Text.literal(", GameMode: "
                                                + entry.getGameMode().name() + ")"));
                                Debug.info(val);
                                return val;
                            })
                            .forEach(Debug::chat);
                }
                case "team" -> {
                    String user0 = Objects.equals(user, "#me") ? mc.player.getNameForScoreboard() : user;
                    PlayerListEntry entry =
                            MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(user0);
                    if (entry != null) {
                        Team team = entry.getScoreboardTeam();
                        if (team != null) {
                            Debug.chat("该玩家所在Team: ", team.getName());
                            Debug.chat(
                                    Text.literal("展示名称: ").formatted(Formatting.GRAY),
                                    team.getDisplayName() == null ? "" : team.getDisplayName());
                            Debug.chat(
                                    Text.literal("前缀: ").formatted(Formatting.GRAY),
                                    team.getPrefix() == null ? "" : team.getPrefix());
                            Debug.chat(
                                    Text.literal("后缀: ").formatted(Formatting.GRAY),
                                    team.getSuffix() == null ? "" : team.getSuffix());
                            Debug.chat(
                                    Text.literal("颜色: ").formatted(Formatting.GRAY),
                                    team.getColor() == null ? "" : team.getColor());
                            Debug.chat(Text.literal("友伤: ").formatted(Formatting.GRAY), team.isFriendlyFireAllowed());
                            Debug.chat(
                                    Text.literal("显示隐身队友: ").formatted(Formatting.GRAY),
                                    team.shouldShowFriendlyInvisibles());
                            Debug.chat(Text.literal("队员列表:").formatted(Formatting.GRAY));
                            Debug.chat(Text.literal("-------------------").formatted(Formatting.GREEN));
                            for (var str : team.getPlayerList()) {
                                Debug.chat(str);
                            }
                        } else {
                            Debug.chat("该玩家没有Team");
                        }
                    } else {
                        Debug.chat("找不到玩家", user);
                    }
                }
                case "pentry" -> {
                    String user0 = Objects.equals(user, "#me") ? mc.player.getNameForScoreboard() : user;
                    PlayerListEntry entry =
                            MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(user0);
                    if (entry != null) {
                        Debug.chat("查询到PlayerEntry");
                        Debug.chat(
                                Text.literal("名字: ").formatted(Formatting.GRAY),
                                entry.getProfile().getName());
                        Debug.chat(
                                Text.literal("UUID: ").formatted(Formatting.GRAY),
                                ChatUtils.getClickCopyTargetText(
                                                entry.getProfile().getId().toString())
                                        .formatted(Formatting.GREEN));
                        Debug.chat(
                                Text.literal("Property: ").formatted(Formatting.GRAY),
                                ChatUtils.getHoverShowText(
                                        "[点击查看具体数据]",
                                        List.of(Text.literal(entry.getProfile()
                                                .getProperties()
                                                .toString()))));
                        Debug.chat(
                                Text.literal("GameMode: ").formatted(Formatting.GRAY),
                                entry.getGameMode().name());
                        Debug.chat(
                                Text.literal("DisplayName: ").formatted(Formatting.GRAY),
                                entry.getDisplayName() == null ? Text.literal("null") : entry.getDisplayName());
                        List<Text> texts = new ArrayList<>();
                        texts.add(Text.literal("Latency: " + entry.getLatency()));
                        texts.add(Text.literal("MessageVerifier: " + entry.getMessageVerifier()));
                        texts.add(Text.literal("SkinTextures: " + entry.getSkinTextures()));
                        texts.add(Text.literal("Session: " + entry.getSession()));
                        Debug.chat(
                                Text.literal("More: ").formatted(Formatting.GRAY),
                                ChatUtils.getHoverShowText("[点击查看具体数据]", texts));
                    } else {
                        Debug.chat("该玩家没有PlayerEntry");
                    }
                }
                case "server" -> {
                    Debug.chat("当前服务器:");
                    String ip = CommonUtils.getServerName();
                    Debug.chat(
                            ChatUtils.getClickCopyTargetText(ip).formatted(Formatting.GREEN),
                            "|",
                            mc.world.getRegistryKey().getValue());
                }
                case "waypoint" -> {
                    Debug.chat("当前版本并不支持waypoint查询");
                }
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("preset")
                    .helper("<preset> 加载配置文件预设")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("preset")
                            .enumValue(ModulePreset.class)
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onPreset)))
                    .complete();
        }

        public void onPreset(ArgumentInputStream re) {
            ModulePreset preset1 = re.nextEnum(ModulePreset.class);
            Listener.getCustomListener()
                    .handleValue(new Event<>(new EventContainer<>(ModulePreset.class, preset1), false, false));
            //
            Debug.info("已经加载", preset1.name(), "配置预设");
            Config.launchSaveTasks();
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                    .name("toggle")
                    .helper("<toggle> <state> 针对某些配置项进行快捷切换")
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("toggle")
                            .select(List.of("tp-attack", "bow-tp-attack", "mace-attack", "pearl-tp"))
                            .build())
                    .arg(SimpleCommandArgs.argumentBuilder()
                            .name("state")
                            .select(List.of("on", "off", "switch"), "switch")
                            .build())
                    .post(e -> e.executor(CommandContext.run(this::onToggle)))
                    .complete();
        }
        // todo: rewrite toggle command, add custom keybind command
        public void onToggle(ArgumentInputStream re) {
            String toggle = re.nextNonnull();
            String state = re.nextNonnull();
            int stateCode =
                    switch (state) {
                        case "on" -> 1;
                        case "off" -> 2;
                        case "switch" -> 0;
                        default -> 0;
                    };
            switch (toggle) {
                case "tp-attack" -> {
                    Attack attack = CombatTasks.getAttack();
                    if (stateCode == 0) {
                        attack.enableTp.set(!attack.enableTp.get());
                    } else if (stateCode == 1) {
                        attack.enableTp.set(true);
                        if (attack.tpRange.get() < 0) {
                            attack.tpRange.set(-attack.tpRange.get());
                        }
                    } else if (stateCode == 2) {
                        attack.enableTp.set(false);
                    }
                }
                case "bow-tp-attack" -> {
                    BowEnhance attack = CombatTasks.getBowEnhance();
                    if (stateCode == 0) {
                        attack.enableTp.set(!attack.enableTp.get());
                    } else if (stateCode == 1) {
                        attack.enableTp.set(true);
                        if (attack.tpDistance.get() < 0) {
                            attack.tpDistance.set(-attack.tpDistance.get());
                        }
                    } else if (stateCode == 2) {
                        attack.enableTp.set(false);
                    }
                }
                case "pearl-tp" -> {
                    ProjectileEnhance attack = CombatTasks.getProjectileEnhance();
                    if (stateCode == 0) {
                        attack.enableTp.set(!attack.enableTp.get());
                    } else if (stateCode == 1) {
                        attack.enableTp.set(true);
                        if (attack.tpDistance.get() < 0) {
                            attack.tpDistance.set(-attack.tpDistance.get());
                        }
                    } else if (stateCode == 2) {
                        attack.enableTp.set(false);
                    }
                }
                case "mace-attack" -> {
                    Attack attack = CombatTasks.getAttack();
                    if (stateCode == 0) {
                        attack.enableMace.set(!attack.enableMace.get());
                    } else if (stateCode == 1) {
                        attack.enableMace.set(true);
                        if (attack.maceHeight.get() < 0) {
                            attack.maceHeight.set(-attack.maceHeight.get());
                        }
                    } else if (stateCode == 2) {
                        attack.enableMace.set(false);
                    }
                }
            }
            Config.launchSaveTasks();
        }

        // todo not complete

        // todo more command
        // todo add facing/ targeting command
        {
            if (COMMAND_BOOTSTRAPS != null) {
                COMMAND_BOOTSTRAPS.forEach(s -> s.accept(this));
            }
        }

        public void registerAsSubCommand(String dispatchName, AbstractMainCommand main) {
            this.registerSub(new DelegateSubCommand(dispatchName, main.getMainCommand()));
        }

        public AbstractMainCommand reload() {
            return new SlimefunHelperMainCommand();
        }
    }

    // our client commands
    public static void parseClientCommand(Event<String> commandEvent) {
        String command = commandEvent.context();
        if (command.startsWith("!!")) {
            dispatchClientCommand(command.substring(2));
            commandEvent.cancel();
            return;
        } else if (command.startsWith("/!!")) {
            dispatchClientCommand(command.substring(3));
            commandEvent.cancel();
            return;
        }
    }

    public static CompletableFuture<Suggestions> tabCompleteClientCommand(String command, int cursorAt) {
        if (command.startsWith("!!")) {
            return dispatchTabComplete(command.substring(2), cursorAt - 2, false);
        } else if (command.startsWith("/!!")) {
            return dispatchTabComplete(command.substring(3), cursorAt - 3, true);
        }
        return null;
    }
    //    public static ParseResults<CommandSource> addParseToVanillaCommands(ParseResults<CommandSource> originResult,
    // StringReader reader){
    //
    //        if(command.startsWith("/")){
    //            CompletableFuture<Suggestions> sugg = parseVanillaComandsTab(command.substring(1), cursorAt - 1);
    //            if(sugg != null)return sugg;
    //        }
    //    }

    public static CompletableFuture<Suggestions> dispatchTabComplete(String command, int cursorAt, boolean withPrefix) {
        if (cursorAt < 0) {
            // handle !!
            return null;
        }
        String trueCommand = command.substring(0, cursorAt);
        int lastBlank = -1;
        int prefixLen = 2 + (withPrefix ? 1 : 0);
        StringRange tabCompleteRange;
        List<String> args = new ArrayList<>();
        while (true) {
            int nextBlank = trueCommand.indexOf(" ", lastBlank + 1);
            if (nextBlank == -1) {
                args.add(trueCommand.substring(lastBlank + 1));
                tabCompleteRange = new StringRange(prefixLen + lastBlank + 1, prefixLen + trueCommand.length());
                break;
            }
            args.add(trueCommand.substring(lastBlank + 1, nextBlank));
            lastBlank = nextBlank;
        }
        List<String> tabList = callTabCompletion(args.toArray(String[]::new));
        List<Suggestion> suggestionList =
                tabList.stream().map(i -> new Suggestion(tabCompleteRange, i)).toList();
        Suggestions suggestions = new Suggestions(tabCompleteRange, suggestionList);
        return CompletableFuture.completedFuture(suggestions);
    }

    public static List<String> callTabCompletion(String[] command) {
        if (mc.player != null) {
            List<String> val = REGISTERED_COMMANDS.onTabComplete(mc.player, "", command);
            if (val != null && !val.isEmpty()) {
                return val;
            }
        }
        return List.of();
    }

    public static void dispatchClientCommand(String command) {
        if (mc.player != null) {
            String[] args = command.split(" ");
            if (args.length == 0) return;
            try {
                if (REGISTERED_COMMANDS.onCommand(mc.player, "", args)) {
                    return;
                }
            } catch (Throwable e) {
                Debug.chat("Unexpected Error occurred :", e.getMessage());
                Debug.info(e);
            }
        }
    }

    public static void registerSubCommands(String name, Supplier<AbstractMainCommand> commandSupplier) {
        registerCommandBootstrap((main) -> {
            main.registerAsSubCommand(name, commandSupplier.get());
        });
    }

    public static void registerCommandBootstrap(Consumer<SlimefunHelperMainCommand> bootStrap) {
        COMMAND_BOOTSTRAPS.add(bootStrap);
        if (REGISTERED_COMMANDS != null) {
            bootStrap.accept(REGISTERED_COMMANDS);
        }
    }

    static {
        REGISTERED_COMMANDS = new SlimefunHelperMainCommand();
    }
}
