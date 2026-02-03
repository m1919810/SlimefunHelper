package me.matl114.hacks;

import com.mojang.brigadier.context.*;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import lombok.Getter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.EventContainer;
import me.matl114.gui.invcache.InventoryViewScreen;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.chat.AutoChat;
import me.matl114.hacks.modules.chat.ChatCombine;
import me.matl114.hacks.modules.chat.ChatExtra;
import me.matl114.hacks.modules.chat.ClientSideCommand;
import me.matl114.events.Listener;
import me.matl114.hacks.modules.combat.Attack;
import me.matl114.hacks.modules.combat.BowEnhance;
import me.matl114.hacks.modules.combat.ProjectileEnhance;
import me.matl114.managers.config.*;
import me.matl114.utils.*;
import me.matl114.events.Event;
import me.matl114.utils.commands.*;
import me.matl114.utils.commands.CommandContext;
import me.matl114.utils.interruptions.LogicalError;
import me.matl114.utils.tasks.LimitedSpeedExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.visitor.NbtTextFormatter;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ChatTasks {
    public static void init(){

    }
    @Getter
    @ApiMethod
    public static final ModuleGroup moduleManager = new ModuleGroup("Chat");
    @Getter
    public static ChatExtra chatExtra;
    @Getter
    public static AutoChat autoChat;
    @Getter
    public static ClientSideCommand clientSideCommand;
    @Getter
    public static ChatCombine chatCombine;

    private static void initModules(ModuleManager m){
        chatExtra = new ChatExtra()
            .register(m);

        autoChat = new AutoChat()
            .register(m);

        clientSideCommand = new ClientSideCommand()
            .register(m);

        chatCombine = new ChatCombine()
            .register(m);
    }
    static{
        moduleManager.registerFactories(ChatTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
    // ========================================== utilities ========================================
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    //modified from @ChatScreen.class
    public static void sayMessage(String chatText, boolean addToHistory) {
        if(MinecraftClient.getInstance().player!=null && MinecraftClient.getInstance().player.networkHandler!=null){
            chatText = getChatExtra().normalizeSendText(chatText);
            //in world
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
    private static final LimitedSpeedExecutor chatExecutor=new LimitedSpeedExecutor(new IntRef(5));
    public static void sendDelayChatMessage(Text text){
        chatExecutor.addDelayedExecuteTask(()->mc.player.sendMessage(text));
    }


    static {

        Tasks.registerGameTask(player -> {
            chatExecutor.reset();
        });
        Listener.getChatSend().registerHandler(ChatTasks::parseClientCommand);
    }

    // ====================================== client commands ========================================
    private static SlimefunHelperMainCommand REGISTERED_COMMANDS;
    private static final Map<String, Supplier<AbstractMainCommand>> COMMAND_FACTORY = new HashMap<>();
    public static void reloadAllCommand(){
        REGISTERED_COMMANDS = new SlimefunHelperMainCommand();
        Debug.chat("SfHelper Command Successfully reloaded");
    }
    public static class SlimefunHelperMainCommand extends AbstractMainCommand{
        TreeSubCommand main = mainBuilder()
            .name("")
            .build();

        //SubCommand mainCommand = genMainCommand("");
        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("reload")
                .helper("<what: default main> 重载模块")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("what")
                        .select(List.of("command", "module"), "command")
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(SlimefunHelperMainCommand.this::onReload)))
                .complete();
        }

        public boolean onReload(ArgumentInputStream args){
            var re = args.nextNonnull();
            switch (re){
                case "command"->Tasks.scheduleDelayed(ChatTasks::reloadAllCommand,1);
                //case "vanilla" -> Tasks.scheduleDelayed(ChatTasks::reloadVanillaClientCommand, 1);
                case "module" -> Tasks.scheduleDelayed(HackModules::reloadModuleGroups, 1);
                default -> Debug.chat("不支持的参数类型: " + re);
            }
            return true;
        }
        List<String> pageType = List.of(
            "guide","rtype","vanilla","saved", "itemedit", "invcache", "config"
        );
        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("openmenu")
                .helper("<page:default guide> 打开模组的特殊界面")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("page")
                        .select(pageType, "guide")
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onOpenMenu)))
                .complete();
        }
        public void onOpenMenu(ArgumentInputStream s){
            switch (s.nextSelect(pageType)){
                case "rtype" -> Tasks.scheduleDelayed(SlimefunTasks.getSlimefunGuide()::openCraftTypeMenu, 1);
                case "vanilla" -> Tasks.scheduleDelayed( SlimefunTasks.getSlimefunGuide()::openVanillaRecipesMenu, 1);
                case "saved"->Tasks.scheduleDelayed( SlimefunTasks.getSlimefunGuide()::openSaveItemMenu,1);
                case "itemedit" -> Tasks.scheduleDelayed(InvTasks::openEditorForPlayer, 1);
                case "invcache" -> Tasks.scheduleDelayed(InvTasks::openInventoryCacheScreen, 1);
                case "config" -> Tasks.scheduleDelayed(Tasks::openConfigNewStyleScreen, 1);
                default -> Tasks.scheduleDelayed(SlimefunTasks.getSlimefunGuide()::openMainGuideMenu,1);
            }
            Debug.chat(Text.literal("成功打开界面").formatted(Formatting.GREEN));
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("config")
                .helper("<operation:default open> 配置文件操作")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("operation")
                        .select(List.of(
                            "open", "reload"
                        ), "open")
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onConfig)))
                .complete();
        }

        public void onConfig(ArgumentInputStream s){
            String next = s.nextNonnull();
            switch (next){
                case "open" ->{
                    Tasks.scheduleDelayed(Tasks::openConfigNewStyleScreen, 1);
                    Debug.chat(Text.literal("成功打开配置文件界面").formatted(Formatting.GREEN));
                }
                case "reload" ->{
                    Tasks.scheduleDelayed(Config::reloadAll, 1);
                    Debug.chat(Text.literal("成功重载配置文件").formatted(Formatting.GREEN));
                }
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("task")
                .helper("<taskid> <args> 运行内置任务")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("taskid")
                        .tabSupplier(()-> Tasks.getSpecialTaskName().stream())
                        .build()
                )
                .post(e -> e.executor(this::onTask))
                .complete();
        }
        public boolean onTask(PlayerEntity player, ArgumentInputStream s, ArgumentReader reader){

            String val = s.nextNonnull();
            String[] extraArg = reader.getRemainingArgs();

            try{
                Tasks.runSpecialTask(val, extraArg);
            }catch (Throwable e){
                Debug.chat("运行Task出现错误!:",e.getMessage());
                Debug.info(e);
            }return true;
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("asynctask")
                .helper("<taskid> <args> 运行内置任务")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("taskid")
                        .tabSupplier(()-> Tasks.getSpecialTaskName().stream())
                        .build()
                )
                .post(e -> e.executor(this::onAsyncTask))
                .complete();
        }
        public boolean onAsyncTask(PlayerEntity player, ArgumentInputStream s, ArgumentReader reader){
            String val = s.nextNonnull();
            String[] extraArg = reader.getRemainingArgs();
            CompletableFuture.runAsync(()->{
                try{
                    Tasks.runSpecialTask(val, extraArg);
                }catch (Throwable e){
                    Debug.chat("运行Task出现错误!:",e.getMessage());
                    Debug.info(e);
                }
            });
            return true;
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("recipes")
                .helper("<action:default enable> 管理配方系统")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("action")
                        .select(List.of("reload","enable"), "enable")
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onRecipe)))
                .complete();
        }

        public void onRecipe(ArgumentInputStream s){
            switch (s.nextNonnull()){
                case "enable"->{
                    SlimefunTasks.getSlimefunGuide().handleAutoEnable();
                }

            }
        }
        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("debug")
                .helper("<debug> <state> 调试项开关")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("debug")
                        .select(List.of("packet-in", "packet-out", "log-to-chat"))
                        .build()
                )
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("state")
                        .bool()
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onDebugState)))
                .complete();
        }
        public void onDebugState(ArgumentInputStream s){
            var debug = s.nextNonnull();
            switch (debug){
                case "packet-in"->{
                    ExtraTasks.getPacketDebugger().debugIn.set(s.nextBoolean());
                }
                case "packet-out"->{
                    ExtraTasks.getPacketDebugger().debugOut.set(s.nextBoolean());
                }
                case "log-to-chat"->{
                    ExtraTasks.DEBUG_INTO_CHAT = s.nextBoolean();
                }
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("registry")
                .helper("<id> <filter:\"\"> 查看原版注册表")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("id")
                        .tabSupplier(()->ItemStackUtils.registry().streamAllRegistryKeys().map(RegistryKey::getValue).map(i-> "minecraft".equals(i.getNamespace())? i.getPath(): i.toString()))
                        .build()
                )
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("filter")
                        .defaultValue("")
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onListRegistry)))
                .complete();
        }
        public void onListRegistry(ArgumentInputStream re){
            Identifier identifier = Identifier.tryParse(re.nextNonnull());
            RegistryKey registryKey = RegistryKey.ofRegistry(identifier);
            Registry result = (Registry) ItemStackUtils.registry().getOptional(registryKey).orElse(null);
            if(result != null){
                String filter = re.nextNonnull();
                Debug.chat(Text.literal(identifier.toString() + "所拥有的注册项:").formatted(Formatting.GREEN));
                Identifier filterId = Identifier.tryParse(filter);
                boolean namespace = filter.contains(":");
                for (var id : result.getKeys()){
                    Identifier identifier1 = ((RegistryKey)id).getValue();
                    String val = identifier1.getPath();
                    if(filterId == null ||( val.contains(filterId.getPath()) && (!namespace || identifier1.getNamespace().contains(filterId.getNamespace())))){
                        Debug.chat(identifier1);
                    }
                }
            }else {
                Debug.chat(Text.literal("不存在的注册表: "+identifier).formatted(Formatting.RED));
            }
        }
        List<String> resourceTypes = List.of("world", "command", "seed", "plugins", "version");
        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("resource")
                .helper("<id> <filter:\"\"> 查看某些原版重要数据")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("id")
                        .select(resourceTypes)
                        .build()
                )
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("filter")
                        .defaultValue("")
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onResource)))
                .complete();
        }

        public void onResource(ArgumentInputStream re){
            String val = re.nextSelect(resourceTypes);
            String filter = re.nextNonnull();
            Identifier filterId = Identifier.tryParse(filter);
            boolean namespace = filter.contains(":");
            List datas = new ArrayList<>();
            switch (val){
                case "world"->{
                    datas = mc.getNetworkHandler().getWorldKeys().stream().map(RegistryKey::getValue)
                        .filter(u-> filterId == null ||( u.getPath().contains(filterId.getPath()) && (!namespace|| u.getNamespace().contains(filterId.getNamespace()))))
                        .toList();
                    onResource0(val, datas);
                }
                case "command"->{
                    datas = mc.getNetworkHandler().getCommandDispatcher().getRoot().getChildren()
                        .stream()
                        .map(CommandNode::getName)
                        .filter(u->u.contains(filter))
                        .sorted(String::compareTo)
                        .toList();
                    onResource0(val, datas);
                }
                case "seed" ->{
                    datas = List.of(
                        Text.literal( "服务端加密种子: ").append(ChatUtils. getDisplayedLong(mc.world.getBiomeAccess().seed)),
                        Text.literal(  "当前绑定种子: ").append(MineTasks.getSeedOre().hasCurrentSeed()? ChatUtils. getDisplayedLong(MineTasks.getSeedOre().getCurrentSeed()): Text.literal("暂未输入"))
                    );
                    onResource0(val, datas);
                }
                case "plugins" -> {
                    //todo: add tabing /version as a plan , then appending command namespace
                    Debug.chat(Text.literal("导出Command Namespace获取的数据:").formatted(Formatting.GREEN));
                    datas = ClientUtils.getServerCommands().stream()
                        .map(n -> {
                            var sp = n.split(":");
                            return  sp.length >=2 ? sp[0] : null;
                        })
                        .filter(Objects::<String>nonNull)
                        .filter(u-> ((String) u).contains(filter))
                        .distinct()
                        .sorted(String::compareTo)
                        .toList();
                    onResource0(val, datas);
                    Debug.chat(Text.literal("导出Version Tab获取的数据:").formatted(Formatting.GREEN));
                    ClientUtils.getServerPluginResources().thenAccept((list)->{
                        onResource0(val, list.stream()
                            .map(str -> str.toLowerCase(Locale.ROOT))
                            .filter(u->u.contains(filter))
                            .distinct()
                            .sorted(String::compareTo)
                            .toList()
                        );
                    });
                }
//                    case "gamerule"->{
//                        datas = mc.world.getGameRules().toNbt().entries.entrySet().stream()
//                            .map(entry-> entry.getKey()+ ":" + entry.getValue().asString())
//                            .filter(u-> u.contains(filter))
//                            .toList();
//                    }
                default -> {
                    Debug.chat(Text.literal("不支持的资源: "+val).formatted(Formatting.RED));
                }
            }

        }
        private void onResource0(String name, List datas){
            Debug.chat(Text.literal(name + "所拥有的数据:").formatted(Formatting.GREEN));
            for (var identifier1 : datas){
                Debug.chat(identifier1);
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("sleep")
                .helper("<level> <confirm> 进入睡眠状态")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("level")
                        .intValue()
                        .build()
                )
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("confirm")
                        .select(List.of("confirm"), "")
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onSleep)))
                .complete();
        }
        public void onSleep(ArgumentInputStream re){
            int level = re.nextClampedInt(1, 3);
            if(level != 1 && level != 2){
                Debug.chat("请输入范围内的数字: 1~2");
                return ;
            }
            String val = re.nextNonnull();
            if("confirm".equals(val)){
                Tasks.scheduleDelayed(()->RenderTasks.getSleepMode().setScreenSleeping(level), 1);
            }else {
                Debug.chat("使用sleep confirm 确认进入睡眠模式, 进入睡眠模式后可以按 "+ RenderTasks.getSleepMode().getWakeupButton() +" 键离开");
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("debug-render")
                .helper("<task> <state> 调试渲染功能")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("task")
                        .select(List.of("collision", "combat", "bow-aim", "debug-tick"))
                        .build()
                )
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("state")
                        .intValue()
                        .bool()
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onSleep)))
                .complete();
        }
        public void onDebugRender(ArgumentInputStream re){
            String task = re.nextNonnull();
            switch (task){
                case "collision"->RenderTasks.DEBUG_RENDER_COLLISION = re.nextBoolean();
                case "combat" -> RenderTasks.DEBUG_RENDER_COMBAT = re.nextBoolean();
                case "bow-aim"-> RenderTasks.DEBUG_RENDER_BOWAIM = re.nextBoolean();
                case "debug-tick" -> RenderTasks.DEBUG_TICK = re.nextClampedInt(0, Integer.MAX_VALUE);
                default -> Debug.chat("没有调试项:",task);
            }
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("tp")
                .helper("<x> <y> <z> [-far] 执行模拟tp行为")
                .arg(createX("x").build())
                .arg(createY("y").build())
                .arg(createZ("z").build())
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("far")
                        .bool(false)
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onTp)))
                .complete();
        }
        private SimpleCommandArgs.ArgumentBuilder createX(String name){
            return SimpleCommandArgs.argumentBuilder()
                .name(name)
                .tabCompletor(p -> Stream.of("%.2f %.2f %.2f".formatted(p.getX(), p.getY(), p.getZ())))
                .select(List.of("~ ~ ~", "^ ^ ^"))
                .defaultValue("~")
                ;
        }
        private SimpleCommandArgs.ArgumentBuilder createY(String name){
             return SimpleCommandArgs.argumentBuilder()
                .name(name)
                .tabCompletor(p -> Stream.of("%.2f %.2f".formatted(p.getY(), p.getZ())))
                .select(List.of("~ ~", "^ ^"))
                .defaultValue("~")
                 ;
        }
        private SimpleCommandArgs.ArgumentBuilder createZ(String name){
            return SimpleCommandArgs.argumentBuilder()
                .name("z")
                .tabCompletor(p -> Stream.of("%.2f".formatted(p.getZ())))
                .select(List.of("~", "^"))
                .defaultValue("~")
               ;
        }
        public void onTp(PlayerEntity p, ArgumentInputStream re){

            Vec3d parsedCoord = resolveCoord(p, re.next(), re.next(), re.next());
            boolean flag = re.nextBoolean();
            MovTasks.executeTp(parsedCoord, flag ? 2147483647: 128, true, true);
        }

        private Vec3d resolveCoord(Entity entity, ArgumentInputStream.ArgumentReaderResult argx, ArgumentInputStream.ArgumentReaderResult argy, ArgumentInputStream.ArgumentReaderResult argz){
            Vec3d parsedCoord;
            if(argx.nonnullResult().startsWith("^")){
                //use polar coord
                if(!(argy.nonnullResult().startsWith("^") && argz.nonnullResult().startsWith("^"))){
                    throw new LogicalError("Illegal format of look coordinate");
                }
                String xcoord = argx.nonnullResult();
                String ycoord = argy.nonnullResult();
                String zcoord = argz.nonnullResult();
                double x = xcoord.length() == 1 ? 0: CommandUtils.gdouble(xcoord.substring(1), argx.argument);
                double y = ycoord.length() == 1 ? 0: CommandUtils.gdouble(ycoord.substring(1), argy.argument);
                double z = zcoord.length() == 1 ? 0: CommandUtils.gdouble(zcoord.substring(1), argz.argument);
                parsedCoord = EntityUtils.lookCoordTooAbsolutePos(entity, x, y, z);
            }else {
                //use simple coord
                Vec3d pos = entity.getPos();
                double x = 0;
                double y = 0;
                double z = 0;
                String xcoord = argx.nonnullResult();
                String ycoord = argy.nonnullResult();
                String zcoord = argz.nonnullResult();
                if(xcoord.startsWith("~")){
                    x = pos.x;
                    xcoord = xcoord.substring(1);
                }
                if(!xcoord.isEmpty()){
                    x += CommandUtils.gdouble(xcoord, argx.argument);
                }
                if(ycoord.startsWith("~")){
                    y = pos.y;
                    ycoord = ycoord.substring(1);
                }
                if(!ycoord.isEmpty()){
                    y += CommandUtils.gdouble(ycoord, argy.argument);
                }
                if(zcoord.startsWith("~")){
                    z = pos.z;
                    zcoord = zcoord.substring(1);
                }
                if(!zcoord.isEmpty()){
                    z += CommandUtils.gdouble(zcoord, argz.argument);
                }

                parsedCoord = new Vec3d(x, y, z);
            }
            return parsedCoord;
        }
        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("tpa")
                .helper("<target> [-far] 传送到特殊目标位置")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("target")
                        .select(specialPositionType())
                        .tabSupplier(() -> mc.world != null ? EntityUtils.getWorldPlayerNames(false) : Stream.empty())
                        .tabSupplier(()-> (mc.crosshairTarget !=null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) ? Stream.of (((EntityHitResult)(mc.crosshairTarget)).getEntity().getUuidAsString()): Stream.empty())
                        .defaultValue("~")
                        .build()
                )

                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("far")
                        .bool(false)
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onTpa)))
                .complete();
        }
        public void onTpa(PlayerEntity var1, ArgumentInputStream re){
            String target = re.nextNonnull();
            Vec3d pos;
            if(target.startsWith("#")){
                //special target
                pos = specialPositions(target, var1);
                if(pos == null)return;
            }else {
                Entity entity = null;
                if(target.length() > 16){
                    try{
                        UUID uid = UUID.fromString(target);
                        entity = mc.world.getEntityLookup().get(uid);
                    }catch (Throwable e){
                    }
                }
                if(entity == null){
                    entity = EntityUtils.getPlayerByName(target);
                }
                if(entity == null){
                    var1.sendMessage(Text.literal("找不到实体或者玩家: " + target).formatted(Formatting.RED));
                    return;
                }
                pos = entity.getPos();
            }
            boolean flag = re.nextBoolean();
            MovTasks.executeTp(pos, flag ? 2147483647: 128, true, true);
        }
        public static Vec3d mark;
        private Vec3d specialPositions(String target, PlayerEntity var1){
            return switch (target.substring(1)){
                case "this"-> var1.getPos();
                case "near"-> {
                    var player = mc.world.getPlayers().stream().filter(m->m != var1).sorted(Comparator.comparingDouble(m -> m.getPos().squaredDistanceTo(var1.getPos()))).findFirst().orElse(null);
                    if (player == null){
                        var1.sendMessage(Text.literal("附近没有其他玩家!").formatted(Formatting.RED));
                        yield  null;
                    }else {
                        var1.sendMessage(Text.literal("找到附近的玩家: "+ player.getName()).formatted(Formatting.GREEN));
                    }
                    yield  player.getPos();
                }
                case "mark"-> {
                    if(mark != null){
                        Vec3d pos = Vec3d.ZERO.add(mark);
                        var1.sendMessage(Text.literal("使用记录坐标： ").append(ChatUtils. getDisplayedLocationDouble(pos)));
                        yield pos;
                    }else {
                        var1.sendMessage( Text.literal("暂未记录坐标!"));
                        yield null;
                    }
                }
                case "back" -> {
                    if(MovTasks.LAST_TP_FROM != null){
                        var1.sendMessage(Text.literal("使用上一个位置: ").append(ChatUtils. getDisplayedLocationDouble(MovTasks.LAST_TP_FROM)));
                        yield MovTasks.LAST_TP_FROM;
                    }
                    var1.sendMessage(Text.literal("找不到上一个位置"));
                    yield null;
                }
                case "desync" ->{
                    if(MovTasks.setBackLog.lastDesyncPos != null){
                        var1.sendMessage(Text.literal("使用上次客户端同步之前的位置").append(ChatUtils. getDisplayedLocationDouble(MovTasks.setBackLog.lastDesyncPos)));
                        yield MovTasks.setBackLog.lastDesyncPos;
                    }
                    var1.sendMessage(Text.literal("找不到上一次的客户端同步记录"));
                    yield null;
                }
                case "lasttp" -> {
                    if(MovTasks.LAST_TP_REQUEST != null){
                        var1.sendMessage(Text.literal("使用上一个TP请求: ").append(ChatUtils. getDisplayedLocationDouble(MovTasks.LAST_TP_REQUEST)));
                        yield MovTasks.LAST_TP_REQUEST;
                    }
                    var1.sendMessage(Text.literal("找不到上一个TP请求"));
                    yield null;
                }
                case "death" ->{
                    var b0 = var1.getLastDeathPos();
                    if(b0.isPresent() ){
                        if(Objects.equals(b0.get().dimension(), mc.world.getRegistryKey())){
                            yield b0.get().pos().toBottomCenterPos();
                        }else{
                            var1.sendMessage(Text.literal("上次死亡位置不在该世界"));
                        }
                    }else{
                        var1.sendMessage(Text.literal("暂未死亡历史记录"));
                    }
                    yield null;
                }
                default -> {
                    var1.sendMessage(Text.literal("不存在的特殊目标： "+ target));
                    yield null;
                }
            };
        }
        private List<String> specialPositionType(){
            return List.of("#mark","#near",  "#this", "#back", "#death", "#desync", "#lasttp");
        }
        {
            main.subBuilder(SubCommand.treeBuilder())
                .name("travel")
                .post(
                    s -> s.subBuilder(SubCommand.taskBuilder())
                        .name("to")
                        .helper("<coord> 自动传送旅行")
                        .arg(createX("x")
                            .select(specialPositionType())
                            .tabSupplier(() -> mc.world != null ? EntityUtils.getWorldPlayerNames(false) : Stream.empty())
                            .tabSupplier(()-> (mc.crosshairTarget !=null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) ? Stream.of (((EntityHitResult)(mc.crosshairTarget)).getEntity().getUuidAsString()): Stream.empty())
                            .build()
                        )
                        .arg(createY("y").build())
                        .arg(createZ("z").build())
                        .post(e -> e.executor(CommandContext.run(this::onTravel)))
                        .complete()
                        .subBuilder(SubCommand.taskBuilder())
                        .name("cancel")
                        .helper("中断传送旅行")
                        .post(e -> e.executor(CommandContext.run(this::onTravelCancel)))
                        .complete()
                )
                .complete();
        }
        public void onTravel(PlayerEntity var1, ArgumentInputStream re){

                    //fixme: add rot packets
            if(travelTask == null){
                var xcoord = re.next();
                Vec3d parsedCoord;
                if(xcoord.nonnullResult().startsWith("#")){
                    parsedCoord = specialPositions(xcoord.nonnullResult(), var1);
                }else{
                    parsedCoord = resolveCoord(var1, xcoord, re.next(), re.next());
                }
                if(parsedCoord == null)return ;
                travelTask = new Tasks.RepeatTimedTask(20, 2){
                    Vec3d pos0  = parsedCoord;
                    final ClientPlayerEntity currentPlayer = mc.player;
                    final long startingTime = System.currentTimeMillis();
                    final Vec3d startPos = mc.player.getPos();
                    public void cancel(){
                        super.cancel();
                        MovTasks.doingTp = false;
                    }
                    private boolean finish(){
                        if( travelTask != this || mc.player != currentPlayer || mc.player.getPos().subtract(pos0).horizontalLengthSquared() < 900){
                            Debug.chat("当前travel task已完成或者终止");
                            long usedSec = (System.currentTimeMillis() - startingTime)/1000L;
                            Debug.info("using time", usedSec);
                            if(mc.player != null){
                                double len = mc.player.getPos().distanceTo(startPos);
                                Debug.chat("时间开销:", usedSec, "s, 运行距离: ", len, ", 平均速度: ", len/usedSec ,"m/s");
                                //send signal to reset distance
                                mc.player.setOnGround(false);

                                ClientPlayerAccess.of(mc.player).setForceNoFall(true);//.fallDistance = MovTasks.FORCE_RESET_DISTANCE;
                            }

                            travelTask = null;
                            cancel();
                            return true;
                        }else {
                            return false;
                        }
                    }
                    private boolean move(Vec3d delta){

                        if(delta.length() == 0){
                            MovTasks.moveToWithPackets(mc.player.getPos(), null);
                            return false;
                        }else {
                            MovTasks.moveToWithPackets(mc.player.getPos().add(delta), Boolean.TRUE);
                            return finish();
                        }
                    }
                    int tickCNT = 0;
                    long lastTick ;
                    //                                Vec3d vec3d = Vec3d.ZERO;
                    @Override
                    public boolean runTask0() {
                        if(mc.player == null)return false;
                        MovTasks.doingTp = false;
                        mc.player.setOnGround(false);
                        tickCNT +=1;
//                                    Debug.info("distance ", vec3d, mc.player.getPos());
                        if(mc.player.getY() < mc.world.getTopY() + 64){
                            MovTasks.farawayMove(new Vec3d(0, 128, 0), true);
                        }else {
                            //fixme error in boat, desync boat position
                            Vec3d towards = pos0.subtract(mc.player.getPos());

                            Vec3d towardsHorizontal = new Vec3d(towards.x, 0, towards.z).normalize();
//                                            if(move(Vec3d.ZERO)){
//                                                return true;
//                                            }
                            if(move(towardsHorizontal.multiply(9.9).add(0, -0.3,0))){
                                return true;
                            }
                            if(move(towardsHorizontal.multiply(9.9).add(0, -0.3,0))){
                                return true;
                            }
                            if(tickCNT % 3 == 0){
                                if(move(towardsHorizontal.multiply(9.9).add(0, -0.3,0))){
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
            }else {
                Debug.chat("上一个travel task仍旧在执行,使用travel cancel取消");
            }
        }
        public void onTravelCancel(){
            if(travelTask != null){
                travelTask.cancel();
                travelTask = null;
            }
        }


        public static Tasks.RepeatTimedTask travelTask;
        //todo add elytra support
        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("mark")
                .helper("<type> [extra] 标注一个位置为临时缓存位置")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("type")
                        .select(List.of("player" ,"camera", "this", "cross", "clear"), "camera")
                        .build()
                )
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("extra")
                        .tabSupplier(()-> EntityUtils.getWorldPlayerNames(true))
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onMark)))
                .complete();
        }
        public void onMark(PlayerEntity var1, ArgumentInputStream re){
            String type = re.nextNonnull();
            Vec3d pos;
            switch (type){
                case "this"-> pos = var1.getPos();
                case "camera" -> pos = mc.player.getPos();
                case "cross" -> pos = mc.crosshairTarget.getPos();
                case "player" -> {
                    String var = re.nextNonnull();
                    Entity player = EntityUtils.getPlayerByName(var);
                    if(player != null){
                        pos = player.getPos();
                    }else {
                        var1.sendMessage(Text.literal("找不到实体或者玩家: " + var).formatted(Formatting.RED));
                        return ;
                    }
                }
                case "clear" -> {
                    mark = null;
                    return ;
                }
                default -> {
                    var1.sendMessage(Text.literal("不存在的mark类型: "+type).formatted(Formatting.RED));
                    return ;
                }
            }
            mark = pos;
            Debug.chat("标记成功: ", ChatUtils. getDisplayedLocationDouble(pos));
            RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(var1.dimensions.getBoxAt(mark), Integer.MAX_VALUE, Color.GREEN){
                @Override
                public boolean stillRender() {
                    return super.stillRender() && mark == pos;
                }
            });
        }
        List<String> infoTypes =  List.of("death", "spawn", "nbt", "inventory","ender", "plist", "team", "pentry");
        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("info")
                .helper("<information> <user> 查看某项信息")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("information")
                        .select(infoTypes)
                        .build()
                )
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("user")
                        .tabSupplier(()-> EntityUtils.getWorldPlayerNames(false))
                        .select("#me")
                        .defaultValue("#me")
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onInfo)))
                .complete();
        }

        public void onInfo(ArgumentInputStream re){
            String info = re.nextSelect(infoTypes);
            PlayerEntity entity;
            String user = re.nextNonnull();
            entity = Objects.equals("#me", user)? mc.player: EntityUtils.getPlayerByName(user);
            if(entity != null){
                Debug.chat("Information about player : ", entity.getNameForScoreboard());
            }
            switch (info){
                case "death"->{
                    if(entity != null){
                        var death = entity.getLastDeathPos();
                        if (death.isPresent()){
                            var deathpoint = death.get();
                            var world = deathpoint.dimension();
                            Debug.chat("Last Death Point [World:", world.getValue(), ",Pos:",ChatUtils. getDisplayedLocationDouble(Vec3d.of( deathpoint.pos())), "]");
                        }else{
                            Debug.chat("Last Death Point Not Present");
                        }
                    }else{
                        Debug.chat("找不到玩家", user);
                    }
                }
                case "spawn"->{
                    //todo: test if it works
                    Debug.chat("当前世界的出生点:");
                    GlobalPos pos = GlobalPos.create(mc.world.getRegistryKey(), mc.world.getSpawnPos());
                    Debug.chat("World Spawn Point [World:", pos.dimension().getValue(), ",Pos:", ChatUtils.getDisplayedLocationDouble(Vec3d.of(pos.pos())), "]");
//                        if(entity != null){
//                           // mc.player.spawn
//                        }else{
//                            Debug.chat("找不到玩家", user);
//                        }
                }
                case "nbt"->{
                    if(entity != null){
                        var comp = new NbtCompound();
                        entity.writeNbt(comp);
                        comp.remove("Inventory");
                        comp.remove("EnderItems");
                        Debug.chat(new NbtTextFormatter("").apply(comp));
                    }else{
                        Debug.chat("找不到玩家", user);
                    }
                }
                case "inventory" ->{
                    if(entity != null){
                        PlayerInventory enderInventory = entity.getInventory();
                        Tasks.scheduleDelayed(()->{
                            ScreenAccess.of(new InventoryViewScreen(enderInventory, Text.literal("背包预览 - "+ entity.getNameForScoreboard()), new ItemStack(Items.CHEST))).openFromCurrent();
                        }, 2);

                    }else{
                        Debug.chat("找不到玩家", user);
                    }
                }
                case "ender" ->{
                    if(entity != null){
                        EnderChestInventory enderInventory = entity.getEnderChestInventory();
                        Tasks.scheduleDelayed(()->{
                            ScreenAccess.of(new InventoryViewScreen(enderInventory, Text.literal("末影箱预览 - "+ entity.getNameForScoreboard()), new ItemStack(Items.ENDER_CHEST))).openFromCurrent();
                        }, 2);

                    }else{
                        Debug.chat("找不到玩家", user);
                    }
                }
                case "plist"->{
                    Debug.chat(Text.literal("当前可视的玩家列表").formatted(Formatting.GREEN));
                    mc.getNetworkHandler().getPlayerList().stream()
                        .sorted(Comparator.comparing(e -> e.getProfile().getName()))
                        .map(entry ->{
                            var val =  Text.literal(  "%-16s (Display: ".formatted(entry.getProfile().getName()) ).append(entry.getDisplayName() ==null ? Text.literal("null") : entry.getDisplayName()).append(Text.literal(", GameMode: " + entry.getGameMode().name() + ")"));
                            Debug.info(val);
                            return  val;
                        })
                        .forEach(Debug::chat);

                }
                case "team"->{
                    String user0 = Objects.equals(user, "#me") ? mc.player.getNameForScoreboard(): user;
                    PlayerListEntry entry = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(user0);
                    if(entry != null){
                        Team team = entry.getScoreboardTeam();
                        if(team != null){
                            Debug.chat("该玩家所在Team: ", team.getName());
                            Debug.chat(Text.literal("展示名称: ").formatted(Formatting.GRAY), team.getDisplayName() == null? "": team.getDisplayName());
                            Debug.chat(Text.literal("前缀: ").formatted(Formatting.GRAY), team.getPrefix() ==null? "": team.getPrefix());
                            Debug.chat(Text.literal("后缀: ").formatted(Formatting.GRAY), team.getSuffix() == null ? "": team.getSuffix());
                            Debug.chat(Text.literal("颜色: ").formatted(Formatting.GRAY), team.getColor() == null ? "": team.getColor());
                            Debug.chat(Text.literal("友伤: ").formatted(Formatting.GRAY), team.isFriendlyFireAllowed());
                            Debug.chat(Text.literal("显示隐身队友: ").formatted(Formatting.GRAY), team.shouldShowFriendlyInvisibles());
                            Debug.chat(Text.literal("队员列表:").formatted(Formatting.GRAY));
                            Debug.chat(Text.literal("-------------------").formatted(Formatting.GREEN));
                            for (var str: team.getPlayerList()){
                                Debug.chat(str);
                            }
                        }else {
                            Debug.chat("该玩家没有Team");
                        }
                    }else{
                        Debug.chat("找不到玩家", user);
                    }
                }
                case "pentry"->{
                    //todo entry information
                    String user0 = Objects.equals(user, "#me") ? mc.player.getNameForScoreboard(): user;
                    PlayerListEntry entry = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(user0);
                    if(entry != null){
                        Debug.chat("查询到PlayerEntry");
                        Debug.chat(Text.literal("名字: ").formatted(Formatting.GRAY), entry.getProfile().getName());
                        Debug.chat(Text.literal("UUID: ").formatted(Formatting.GRAY), ChatUtils.getClickCopyTargetText(entry.getProfile().getId().toString()).formatted(Formatting.GREEN));
                        Debug.chat(Text.literal("Property: ").formatted(Formatting.GRAY),  ChatUtils.getHoverShowText("[点击查看具体数据]", List.of(Text.literal(entry.getProfile().getProperties().toString()))) );
                        Debug.chat(Text.literal("GameMode: ").formatted(Formatting.GRAY), entry.getGameMode().name());
                        Debug.chat(Text.literal("DisplayName: ").formatted(Formatting.GRAY), entry.getDisplayName() == null ?  Text.literal("null") : entry.getDisplayName());
                        //todo need test
                        List<Text> texts = new ArrayList<>();
                        texts.add(Text.literal("Latency: " + entry.getLatency() ));
                        texts.add(Text.literal("MessageVerifier: " + entry.getMessageVerifier() ));
                        texts.add(Text.literal("SkinTextures: " + entry.getSkinTextures() ));
                        texts.add(Text.literal("Session: " + entry.getSession()));
                        Debug.chat(Text.literal("More: ").formatted(Formatting.GRAY), ChatUtils.getHoverShowText("[点击查看具体数据]", texts));
                    }else {
                        Debug.chat("该玩家没有PlayerEntry");
                    }
                }


            }
        }
        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("preset")
                .helper("<preset> 加载配置文件预设")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("preset")
                        .enumValue(ModulePreset.class)
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onPreset)))
                .complete();
        }

        public void onPreset(ArgumentInputStream re){
            ModulePreset preset1 = re.nextEnum(ModulePreset.class);
            Listener.getCustomListener().handleValue(new Event<>(new EventContainer<>(ModulePreset.class, preset1), false, false));
            //
            Debug.info("已经加载", preset1.name(), "配置预设");
            Config.launchSaveTasks();
        }

        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("toggle")
                .helper("<toggle> <state> 针对某些配置项进行快捷切换")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("toggle")
                        .select(List.of("tp-attack", "bow-tp-attack", "mace-attack", "pearl-tp"))
                        .build()
                )
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("state")
                        .select(List.of("on", "off", "switch"), "switch")
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onToggle)))
                .complete();
        }
        public void onToggle(ArgumentInputStream re){
            String toggle = re.nextNonnull();
            String state = re.nextNonnull();
            int stateCode = switch (state){
                case "on" ->1;
                case "off" ->2;
                case "switch"->0;
                default -> 0;
            };
            switch (toggle){
                case "tp-attack"->{
                    Attack attack = CombatTasks.getAttack();
                    if(stateCode == 0){
                        attack.enableTp.set( !attack.enableTp.get() );
                    }else if(stateCode == 1){
                        attack.enableTp.set(true);
                        if(attack.tpRange.get() <0){
                            attack.tpRange.set( - attack.tpRange.get());
                        }
                    }else if(stateCode == 2){
                        attack.enableTp.set(false);
                    }
                }
                case "bow-tp-attack"->{
                    BowEnhance attack = CombatTasks.getBowEnhance();
                    if(stateCode == 0){
                        attack.enableTp.set( !attack.enableTp.get() );
                    }else if(stateCode == 1){
                        attack.enableTp.set(true);
                        if(attack.tpDistance.get() <0){
                            attack.tpDistance.set( - attack.tpDistance.get());
                        }
                    }else if(stateCode == 2){
                        attack.enableTp.set(false);
                    }
                }
                case "pearl-tp"->{
                    ProjectileEnhance attack = CombatTasks.getProjectileEnhance();
                    if(stateCode == 0){
                        attack.enableTp.set( !attack.enableTp.get() );
                    }else if(stateCode == 1){
                        attack.enableTp.set(true);
                        if(attack.tpDistance.get() <0){
                            attack.tpDistance.set( - attack.tpDistance.get());
                        }
                    }else if(stateCode == 2){
                        attack.enableTp.set(false);
                    }
                }
                case "mace-attack"->{

                    Attack attack = CombatTasks.getAttack();
                    if(stateCode == 0){
                        attack.enableMace.set( !attack.enableMace.get() );
                    }else if(stateCode == 1){
                        attack.enableMace.set(true);
                        if(attack.maceHeight.get() <0){
                            attack.maceHeight.set( - attack.maceHeight.get());
                        }
                    }else if(stateCode == 2){
                        attack.enableMace.set(false);
                    }
                }
            }
            Config.launchSaveTasks();
        }

        //todo not complete


        //todo more command
        //todo add facing/ targeting command
        {

            if(COMMAND_FACTORY != null){
                COMMAND_FACTORY.forEach(((string, commandSupplier) -> this.registerAsSubCommand(string, commandSupplier.get())));
            }
        }
        public void registerAsSubCommand(String dispatchName, AbstractMainCommand main){
            this.registerSub(new DelegateSubCommand(dispatchName, main.getMainCommand()));
        }

        public AbstractMainCommand reload() {
            return new SlimefunHelperMainCommand();
        }
    }

    //our client commands
    public static void parseClientCommand(Event<String> commandEvent){
        String command = commandEvent.context();
        if(command.startsWith("!!")){
            dispatchClientCommand(command.substring(2));
            commandEvent.cancel();
            return;
        }else if(command.startsWith("/!!")){
            dispatchClientCommand(command.substring(3));
            commandEvent.cancel();
            return;
        }
    }

    //todo: make it a event
    public static CompletableFuture<Suggestions> tabCompleteClientCommand(String command, int cursorAt){
        if(command.startsWith("!!")){
            return dispatchTabComplete(command.substring(2), cursorAt -2, false);
        }else if(command.startsWith("/!!")){
            return dispatchTabComplete(command.substring(3), cursorAt -3,true);
        }
        return null;
    }
//    public static ParseResults<CommandSource> addParseToVanillaCommands(ParseResults<CommandSource> originResult, StringReader reader){
//
//        if(command.startsWith("/")){
//            CompletableFuture<Suggestions> sugg = parseVanillaComandsTab(command.substring(1), cursorAt - 1);
//            if(sugg != null)return sugg;
//        }
//    }

    public static CompletableFuture<Suggestions> dispatchTabComplete(String command, int cursorAt, boolean withPrefix){
        if(cursorAt < 0){
            //handle !!
            return null;
        }
        String trueCommand = command.substring(0, cursorAt);
        int lastBlank = -1 ;
        int prefixLen = 2 + (withPrefix?1:0);
        StringRange tabCompleteRange;
        List<String> args = new ArrayList<>();
        while(true){
            int nextBlank = trueCommand.indexOf(" ", lastBlank + 1);
            if(nextBlank == -1){
                args.add(trueCommand.substring(lastBlank + 1));
                tabCompleteRange = new StringRange(prefixLen + lastBlank +1, prefixLen + trueCommand.length());
                break;
            }
            args.add(trueCommand.substring(lastBlank + 1, nextBlank));
            lastBlank = nextBlank;

        }
        List<String> tabList = callTabCompletion(args.toArray(String[]::new));
        List<Suggestion> suggestionList = tabList.stream().map(i->new Suggestion(tabCompleteRange, i)).toList();
        Suggestions suggestions = new Suggestions(tabCompleteRange, suggestionList);
        return CompletableFuture.completedFuture(suggestions);
    }
    public static List<String> callTabCompletion(String[] command){
        if(mc.player != null){
            List<String> val = REGISTERED_COMMANDS.onTabComplete(mc.player, null, "", command);
            if(val != null && !val.isEmpty()){
                return val;
            }
        }
        return List.of();
    }

    public static void dispatchClientCommand(String command){
        if(mc.player != null){
            String[] args = command.split(" ");
            if(args.length == 0)return;
            try{
                if(REGISTERED_COMMANDS.onCommand(mc.player,null,  "", args)){
                    return;
                }
            }catch (Throwable e){
                Debug.chat("Unexpected Error occurred :", e.getMessage());
                Debug.info(e);
            }

        }
    }
    public static void registerSubCommands(String name, Supplier<AbstractMainCommand> commandSupplier){
        COMMAND_FACTORY.put(name, commandSupplier);
        if(REGISTERED_COMMANDS != null){
            REGISTERED_COMMANDS.registerAsSubCommand(name, commandSupplier.get());
        }
    }







    static {
        REGISTERED_COMMANDS = new SlimefunHelperMainCommand();
    }
}
