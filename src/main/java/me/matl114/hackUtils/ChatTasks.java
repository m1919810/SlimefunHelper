package me.matl114.hackUtils;

import com.google.common.collect.Streams;

import com.mojang.brigadier.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.*;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import lombok.Getter;
import me.matl114.ModConfig;
import me.matl114.access.*;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.invcache.InventoryViewScreen;
import me.matl114.gui.other.ChatLikeInputSubScreen;
import me.matl114.hackUtils.modules.move.NoFallModule;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.*;
import me.matl114.utils.UtilClass.*;
import me.matl114.utils.UtilClass.Event;
import me.matl114.utils.UtilClass.commands.*;
import me.matl114.utils.UtilClass.commands.CommandContext;
import me.matl114.utils.UtilClass.interruptions.LogicalError;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.visitor.NbtTextFormatter;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static me.matl114.managers.Configs.*;

public class ChatTasks {
    public static void init(){

    }
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Config.IntRef period= Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_PERIOD);
    private static final Config.StringRef message = Configs.CHAT_CONFIG.getString(Configs.CHAT_HELPER_CACHE);
    private static final Config.IntRef multiple= Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_MULTIPLE);
    private static final AtomicInteger counter= new AtomicInteger(0);

    public static void onAutoChatStart(){
        if(counter.getAndIncrement() >period.get()){
            counter.set(0);
            for (int i=0;i<multiple.get();i++){
                sendMessage(message.getValue(),true);
            }
        }
    }
    //modified from @ChatScreen.class
    public static void sendMessage(String chatText, boolean addToHistory) {
        if(MinecraftClient.getInstance().player!=null && MinecraftClient.getInstance().player.networkHandler!=null){
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

    public static void onAutoChatStop(){
        counter.set(0);
    }
    @Getter
    private static final LimitedSpeedExecutor chatExecutor=new LimitedSpeedExecutor(new Config.IntRef(5));
    public static void sendDelayChatMessage(Text text){
        chatExecutor.addDelayedExecuteTask(()->mc.player.sendMessage(text));
    }

    private static Config.StringRef stored=Configs.CHAT_CONFIG.getString(Configs.CHAT_HELPER_CACHE);
    public static void initChatScreen(Screen screen){
    }
   private static final   Predicate<CommandSource> requirement = (val)->true;
    private static final Command<CommandSource> success = (val)->Command.SINGLE_SUCCESS;




   private static void addOurCommandNodesInRoot(RootCommandNode<CommandSource> node){
        //try add deop command
       //fix: plugin give commands
       CommandNode<CommandSource> give = node.getChild("minecraft:give");
       LiteralCommandNode<CommandSource> giveCommand;
       if (give == null) {
           giveCommand = new LiteralCommandNode<>(
               "minecraft:give",
               null,
               requirement,
               null,
               null,
               false
           );
           node.addChild(giveCommand);
       }else {
           giveCommand = (LiteralCommandNode<CommandSource>) give;
       }
       if(node.getChild("give") == null){
           LiteralCommandNode<CommandSource> mcGiveCommand = new LiteralCommandNode<>(
               "give",
               null,
               requirement,
               giveCommand,
               null,
               false
           );
           node.addChild(mcGiveCommand);
        }
        if(give == null){
            CommandRegistryAccess commandRegistryAccess = CommandRegistryAccess.of(
                ItemStackUtils.delegate(),
                FeatureFlags.DEFAULT_ENABLED_FEATURES
            );


            ArgumentCommandNode<CommandSource, EntitySelector> targetArgument = new ArgumentCommandNode<>(
                "targets",
                EntityArgumentType.players(),
                null,
                requirement,
                null,
                null,
                false,
                //use default because if "minecraft:give" node is absent, then we definitely have no permission of requesting this
                null
            );
            giveCommand.addChild(targetArgument);
            ArgumentCommandNode<CommandSource, ItemStackArgument> itemArgument = new ArgumentCommandNode<>(
                "item",
                ItemStackArgumentType.itemStack(commandRegistryAccess),
                success,
                requirement,
                null,
                null,
                false,
                null
            );
            targetArgument.addChild(itemArgument);
            ArgumentCommandNode<CommandSource, Integer> countAmount = new ArgumentCommandNode<>(
                "count",
                IntegerArgumentType.integer(1),
                success,
                requirement,
                null,
                null,
                false,
                null
            );
            itemArgument.addChild(countAmount);
        }


   }
   private static void reloadVanillaClientCommand(){

   }
   private static void onClientCommandReload(CommandDispatcher<CommandSource> dispatcher){
        RootCommandNode<CommandSource> root = dispatcher.getRoot();
        if(root != null && EXECUTE_GIVE_CLIENTSIDE.get()){
            addOurCommandNodesInRoot(root);
        }
   }

   private static final Config.FlagRef combineMessage = CHAT_CONFIG.getBoolean(CHAT_HELPER_COMBINE_SAME_CHAT);
   private static final String formatCombinedMessage = " &r&7&l[x&a%d&7&l]";
   private static final Pattern matcherCombinedMessageSuffix = Pattern.compile("^\\s*?\\[x(\\d*?)\\]$");
   public static void onAddMessageCombineSameMessage(Event<ChatHudLine> textEvent){
       if (!combineMessage.get()){
           return;
       }
       ChatHudLine line = textEvent.context();
        Text text = line.content();
        //use translated
        String rawString = ChatUtils.getOrderedTextString(text.asOrderedText());
//       rawString = rawString.replaceAll("§.", "");
        ChatHud hud = mc.inGameHud.getChatHud();
       int amount = 0;
       if(hud != null){
           //fixme: shit, they may split lines in list
           //fixme: shit, color and formats EVERYWHERE!
           var visibleHistory = ChatHudAccess.of(hud).getVisibleLines();
           ListIterator<ChatHudLine.Visible> lineIterator  = visibleHistory.listIterator();
           List<OrderedText> textList = new ArrayList<>();
           while (lineIterator.hasNext()){
               var visible = lineIterator.next();
               textList.add(0, visible.content());
               String rawLine1 = ChatUtils.getOrderedTextString(textList.toArray(OrderedText[]::new));
               //remove all fucking shits
               if(rawLine1.length() > rawString.length() + 10 + formatCombinedMessage.length()){
                   break;
               }
               if(rawLine1.startsWith(rawString)){
                   String suffix = rawLine1.substring(rawString.length());
                   if(suffix.isEmpty()){
                       //absolutely equals
                       amount += 1;
                       lineIterator.remove();
                       while (lineIterator.hasPrevious()){
                           lineIterator.previous();
                           lineIterator.remove();
                       }
//                       do {
//                           lineIterator.remove();
//                       }while (lineIterator.hasPrevious());
                       textList.clear();
                   }else {
                       //check if with suffix
                       Matcher matcher = matcherCombinedMessageSuffix.matcher(suffix);
                       if(matcher.find()){
                           try{
                               //combine amount and remove line
                               amount += Integer.parseInt(matcher.group(1));
                               lineIterator.remove();
                               while (lineIterator.hasPrevious()){
                                   lineIterator.previous();
                                   lineIterator.remove();
                               }
                               textList.clear();
                           }catch (Throwable e){
                               //break combine
                               continue;
                           }
                       }
                   }
               }else {
                   //break combine
                   continue;
               }
           }
       }
       if (amount > 0) {

            String newLineLegacy = formatCombinedMessage.formatted( amount + 1);
            MutableText newLine = ChatUtils.copyText(text);
            newLine.append(ChatUtils.stringToText(newLineLegacy));
            textEvent.context(new ChatHudLine(line.creationTick(), newLine, line.signature(), line.indicator()));
       }
   }
   private static final Config.FlagRef saveWhenCloseExit = CHAT_CONFIG.getBoolean(CHAT_HELPER_ADD_HISTORY_WHE_CLOSE);
   public static void onChatInputSaveOnClose(Event<Screen> chatScreenSave){
       if(!saveWhenCloseExit.get())return;
       if(chatScreenSave.context() instanceof ChatScreen chat){
           String chatInput = ChatScreenAccess.of(chat).getInputWidget().getText();
           //ignore two default input
           if(!chatInput.isEmpty() && !Objects.equals("/", chatInput)){
               if(mc.inGameHud != null){
                   mc.inGameHud.getChatHud().addToMessageHistory(chatInput);
               }
           }
       }
   }

   private static final Config.FlagRef doNotSendMeaninglessMessage = CHAT_CONFIG.getBoolean(CHAT_HELPER_DO_NOT_SEND_EMPTY_MESSAGE);
    public static void onChatInputSend(Event<String> stringEvent){
        if(!doNotSendMeaninglessMessage.get())return;
        String value = stringEvent.context();
        //ignore meaningless shit, do not addToMessageHistory
        if(value.isEmpty() || Objects.equals(value, "/")
            || Objects.equals(value, "!!") || Objects.equals(value, "/!!")
        ){
            stringEvent.cancel();
        }
    }

    public static SubScreenWidget createChatInputWidget(HandledScreen<?> screen){
        HandledScreenAccess access = HandledScreenAccess.of(screen);
        return new ChatLikeInputSubScreen(access.getScreenX() + 2 , access.getScreenY() + access.getScreenBackgroundY() + (screen instanceof CreativeInventoryScreen ? 40 : 10), access.getScreenBackgroundX() - 4, 12, (str)-> {
            if(str != null && !str.isEmpty() && !Objects.equals(str, "/")){
                //do not let blanks or / shits into it
                ChatTasks.sendMessage(str, true);
            }
        });
    }


    static {
        Tasks.registerGameTask((player -> {
            if(HotKeys.getSimpleToggleManager().getState(HotKeys.AUTO_CHAT)){
                ChatTasks.onAutoChatStart();
            }else {
                ChatTasks.onAutoChatStop();
            }
        }));
        Tasks.registerGameTask(player -> {
            chatExecutor.reset();
        });
        //use default command registry access

        Listener.getCommandReloadPoint().registerHandler(ChatTasks::onClientCommandReload);
        Listener.getMessageAddToVisiblePoint().registerHandler(ChatTasks::onAddMessageCombineSameMessage);
        Listener.getClientScreenClose().registerHandler(ChatTasks::onChatInputSaveOnClose);
        Listener.getChatScreenSendInput().registerHandler(ChatTasks::onChatInputSend);
    }

    static{
        Listener.getChatEntryPoint().registerHandler(ChatTasks::parseClientCommand);
    }
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
                case "module" -> Tasks.scheduleDelayed(HackModules.getManager()::reloadModules, 1);
                default -> Debug.chat("不支持的参数类型: " + re);
            }
            return true;
        }
        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("openmenu")
                .helper("<page:default guide> 打开模组的特殊界面")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("page")
                        .select(List.of(
                            "guide","rtype","vanilla","saved", "itemedit", "invcache", "config"
                        ), "guide")
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onOpenMenu)))
                .complete();
        }
        public void onOpenMenu(ArgumentInputStream s){
            var re = s.nextArg();
            String var;
            if(re != null){
                var= re;
            }else {
                var = "guide";
            }
            switch (var){
                case "rtype" -> Tasks.scheduleDelayed(SlimefunTasks::handleClickRtypeIcon, 1);
                case "vanilla" -> Tasks.scheduleDelayed( SlimefunTasks::handleClickCraftTableIcon, 1);
                case "saved"->Tasks.scheduleDelayed( SlimefunTasks::handleClickSaveItemIcon,1);
                case "itemedit" -> Tasks.scheduleDelayed(ItemEditTasks::openEditor, 1);
                case "invcache" -> Tasks.scheduleDelayed(InvTasks::openInventoryCacheScreen, 1);
                case "config" -> Tasks.scheduleDelayed(InvTasks::openConfigNewStyleScreen, 1);
                default -> Tasks.scheduleDelayed(SlimefunTasks::handleClickGuideIcon,1);
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
                    Tasks.scheduleDelayed(InvTasks::openConfigNewStyleScreen, 1);
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
                .name("list")
                .helper("<resource> 查看某些资源的值")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("resource")
                        .select(List.of(
                            "hotkeys"
                        ), "hotkeys")
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onListResource)))
                .complete();
        }
        public void onListResource(ArgumentInputStream s){
            var re = s.nextNonnull();
            switch (re){
                case "hotkeys" -> {
                    Debug.chat(Text.literal("当前的快捷键注册表:").formatted(Formatting.GREEN));
                    HotKeys.getHotkeysMap().forEach((i,j)->{
                        Debug.chat("功能:", i, ", 快捷键:",j);
                    });
                }
                default -> Debug.chat("不存在的资源项",re);
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
                    SlimefunTasks.handleAutoEnable();
                }
                case "reload" ->{
                    SlimefunTasks.reloadData();
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
                    Tasks.DEBUG_PACKET_IN = s.nextBoolean();
                }
                case "packet-out"->{
                    Tasks.DEBUG_PACKET_OUT = s.nextBoolean();
                }
                case "log-to-chat"->{
                    Debug.DEBUG_LOG_TO_CHAT = s.nextBoolean();
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

        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("resource")
                .helper("<id> <filter:\"\"> 查看某些原版重要数据")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("id")
                        .select(List.of("world", "command", "seed", "plugins", "version"))
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
            String val = re.nextNonnull();
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
                        Text.literal(  "当前绑定种子: ").append(MineTasks.isCurrentWorldSeedInputExist()? ChatUtils. getDisplayedLong(MineTasks.getCurrentWorldSeedInput()): Text.literal("暂未输入"))
                    );
                    onResource0(val, datas);
                }
                case "plugins" -> {
                    //todo: add tabing /version as a plan , then appending command namespace
                    Debug.chat(Text.literal("导出Command Namespace获取的数据:").formatted(Formatting.GREEN));
                    datas = Tasks.getServerCommands().stream()
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
                    Tasks.getServerPluginResources().thenAccept((list)->{
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
                .name("resource")
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
                Tasks.scheduleDelayed(()->RenderTasks.setScreenSleeping(level), 1);
            }else {
                Debug.chat("使用sleep confirm 确认进入睡眠模式, 进入睡眠模式后可以按 "+ ModConfig.getFuncHotKeys(HotKeys.WAKE_UP_SCREEN)+" 键离开");
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
                .arg(createX("x"))
                .arg(createY("y"))
                .arg(createZ("z"))
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("far")
                        .bool(false)
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onTp)))
                .complete();
        }
        private SimpleCommandArgs.Argument createX(String name){
            return SimpleCommandArgs.argumentBuilder()
                .name(name)
                .tabCompletor(p -> Stream.of("%.2f %.2f %.2f".formatted(p.getX(), p.getY(), p.getZ())))
                .select(List.of("~ ~ ~", "^ ^ ^"))
                .defaultValue("~")
                .build();
        }
        private SimpleCommandArgs.Argument createY(String name){
             return SimpleCommandArgs.argumentBuilder()
                .name(name)
                .tabCompletor(p -> Stream.of("%.2f %.2f".formatted(p.getY(), p.getZ())))
                .select(List.of("~ ~", "^ ^"))
                .defaultValue("~")
                .build() ;
        }
        private SimpleCommandArgs.Argument createZ(String name){
            return SimpleCommandArgs.argumentBuilder()
                .name("z")
                .tabCompletor(p -> Stream.of("%.2f".formatted(p.getZ())))
                .select(List.of("~", "^"))
                .defaultValue("~")
                .build();
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
                .name("tpa")
                .post(
                    s -> s.subBuilder(SubCommand.taskBuilder())
                        .name("to")
                        .helper("<coord> 自动传送旅行")
                        .arg(createX("x"))
                        .arg(createY("y"))
                        .arg(createZ("z"))
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

        {
            main.subBuilder(SubCommand.taskBuilder())
                .name("info")
                .helper("<information> <user> 查看某项信息")
                .arg(
                    SimpleCommandArgs.argumentBuilder()
                        .name("information")
                        .select( List.of("death", "spawn", "nbt", "inventory","ender", "plist", "team", "pentry"))
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
            String info = re.nextNonnull();
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
                        .select( List.of("vanilla", "hacking", "ac-common", "ac-grim", "ac-vulcan", "ac-matrix"))
                        .build()
                )
                .post(e -> e.executor(CommandContext.run(this::onPreset)))
                .complete();
        }

        public void onPreset(ArgumentInputStream re){
            String preset = re.nextNonnull();

            switch (preset) {
                case "vanilla" -> {
                    //disable all hacks
                    //todo: may complete it later
                }
                case "hacking"->{
                    configureNoACEnvHacks();
                }
                case "ac-common"->{
                    configureCommonACHacks();
                }
                case "ac-grim"->{
                    configureCommonACHacks();
                    configureGrimACEnvHacks();
                }
                case "ac-vulcan"->{
                    configureCommonACHacks();
                    configureVulcanEnvHacks();
                    //
                }
                case "ac-matrix" ->{
                    configureCommonACHacks();
                    configureMatrixEnvHacks();
                }
                default -> {
                    return ;
                }
            }
            Config.launchSaveTasks();
            Debug.info("已经加载", preset, "配置预设");
        }
        //todo: add Event to this
        private void configureNoACEnvHacks(){
            //NO FALL
            MOV_CONFIG.setValueNoNew(true, NoFallModule.MOVE_NOFALL);
            //NO SLOW
            MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_SNEAK);
            MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_SLOW);
            MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_USEITEM);
            MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_FRAC);
            MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_IN);
            MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_SPECIAL);
            //flight
            MOV_CONFIG.setValueNoNew(true, MOVE_FLIGHT_ANTIKICK);
            //sprint
            MOV_CONFIG.setValueNoNew(true, MOVE_AUTO_TOGGLE_SPRINT);

            MOV_CONFIG.setValueNoNew(BypassMode.NO_BYPASS, MOVE_SPRINT_BYPASS_MODE);
//                        MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_OVERRIDE_WALK);
//                        MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_OVERRIDE_FLY);
            MOV_CONFIG.save();
            COMBAT_CONFIG.setValueNoNew(false, COMBAT_LEGAL_MOD);
            COMBAT_CONFIG.setValueNoNew(false, COMBAT_BOW_AIM_LEGALLY);
            Config.DoubleRef currentTpRange = COMBAT_CONFIG.getDouble(COMBAT_TP_REACH);
            if (currentTpRange.get() < 0) {
                currentTpRange.set(-currentTpRange.get());
            }
            Config.DoubleRef currentTpBowRange = COMBAT_CONFIG.getDouble(COMBAT_PROJECTILE_TP);
            if (currentTpBowRange.get() < 0) {
                currentTpBowRange.set(-currentTpBowRange.get());
            }
            COMBAT_CONFIG.save();
//                        MINE_CONFIG.setValueNoNew(false, MINE_BYPASS_FAST_BREAK_CHECK);
            MINE_CONFIG.setValueNoNew(BypassMode.NO_BYPASS, MINE_BYPASS_FAST_BREAK_BYPASS_MODE);
            MINE_CONFIG.save();
            if (!HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FLIGHT)) {
                HotKeys.getHotkeyToggleManager().getToggle(HotKeys.TOGGLE_FLIGHT).run();
                ;
            }

            INTERACT_CONFIG.setValueNoNew(false, INTERACT_SCAFFOLD_LEGAL);
            INTERACT_CONFIG.save();
        }
        private void configureGrimACEnvHacks(){
            //grimac mode of nofall works
            MOV_CONFIG.setValueNoNew(NoFallModule.NofallBypassMode.BYPASS_GRIM, NoFallModule.MOVE_NOFALL_MODE);
            MOV_CONFIG.save();
            MINE_CONFIG.setValueNoNew(BypassMode.BYPASS_GRIM, MINE_BYPASS_FAST_BREAK_BYPASS_MODE);
            MINE_CONFIG.save();
        }
        private void configureVulcanEnvHacks(){
            //vanilla nofall can bypass vulcan

            //vanilla kill can bypass vulcan
            COMBAT_CONFIG.setValueNoNew(false, COMBAT_LEGAL_MOD);
            COMBAT_CONFIG.setValueNoNew(false, COMBAT_BOW_AIM_LEGALLY);
            COMBAT_CONFIG.save();
        }
        private void configureMatrixEnvHacks(){
            //todo: wait to test

        }
        private void configureCommonACHacks(){
            MOV_CONFIG.setValueNoNew(NoFallModule.NofallBypassMode.LAZY_MODE, NoFallModule. MOVE_NOFALL_MODE);
            MOV_CONFIG.setValueNoNew(true, NoFallModule. MOVE_NOFALL);
            //noslow
            MOV_CONFIG.setValueNoNew(false, MOVE_SPEED_NO_SLOW_DOWN_SNEAK);
            MOV_CONFIG.setValueNoNew(false, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_SLOW);
            MOV_CONFIG.setValueNoNew(false, MOVE_SPEED_NO_SLOW_DOWN_USEITEM);
            MOV_CONFIG.setValueNoNew(false, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_FRAC);
            MOV_CONFIG.setValueNoNew(false, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_IN);
            MOV_CONFIG.setValueNoNew(false, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_SPECIAL);
            //sprint
            MOV_CONFIG.setValueNoNew(true, MOVE_AUTO_TOGGLE_SPRINT);
            //todo: test features
            MOV_CONFIG.setValueNoNew(false, MOVE_DISABLE_SETBACK_VELOCITY_RESET);

            MOV_CONFIG.setValueNoNew(false, MOVE_SPEED_OVERRIDE_WALK);
            MOV_CONFIG.setValueNoNew(false, MOVE_SPEED_OVERRIDE_FLY);
            MOV_CONFIG.setValueNoNew(BypassMode.BYPASS_GRIM, MOVE_SPRINT_BYPASS_MODE);
            MOV_CONFIG.save();
            COMBAT_CONFIG.setValueNoNew(true, COMBAT_LEGAL_MOD);
            COMBAT_CONFIG.setValueNoNew(true, COMBAT_BOW_AIM_LEGALLY);
            Config.DoubleRef currentTpRange = COMBAT_CONFIG.getDouble(COMBAT_TP_REACH);
            if(currentTpRange.get() > 0){
                currentTpRange.set(- currentTpRange.get());
            }
            Config.DoubleRef currentTpBowRange = COMBAT_CONFIG.getDouble(COMBAT_PROJECTILE_TP);
            if(currentTpBowRange.get() > 0){
                currentTpBowRange.set(- currentTpBowRange.get());
            }

            COMBAT_CONFIG.save();

            if(HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FLIGHT)){
                HotKeys.getHotkeyToggleManager().getToggle(HotKeys.TOGGLE_FLIGHT).run();;
            }
            //mines
            MINE_CONFIG.setValueNoNew(BypassMode.NO_BYPASS, MINE_BYPASS_FAST_BREAK_BYPASS_MODE);
            MINE_CONFIG.save();

            // interact
            INTERACT_CONFIG.setValueNoNew(true, INTERACT_SCAFFOLD_LEGAL);
            INTERACT_CONFIG.save();
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
                    Config.DoubleRef value = COMBAT_CONFIG.getDouble(COMBAT_TP_REACH);
                    if(stateCode == 0){
                        value.set(- value.get());
                    }else if(stateCode == 1){
                        value.set(Math.abs(value.get()));
                    }else if(stateCode == 2){
                        value.set(- Math.abs(value.get()));
                    }
                }
                case "bow-tp-attack"->{
                    Config.FlagRef value = COMBAT_CONFIG.getBoolean(COMBAT_BOW_TP_TOGGLE);
                    value.set(!value.get());
//                        Config.DoubleRef value = COMBAT_CONFIG.getDouble(COMBAT_PROJECTILE_TP);
//                        if(stateCode == 0){
//                            value.set(- value.get());
//                        }else if(stateCode == 1){
//                            value.set(Math.abs(value.get()));
//                        }else if(stateCode == 2){
//                            value.set(- Math.abs(value.get()));
//                        }
                }
                case "pearl-tp"->{
                    Config.FlagRef value = COMBAT_CONFIG.getBoolean(COMBAT_PEARL_TP);
                    value.set(!value.get());
                }
                case "mace-attack"->{
                    Config.DoubleRef value = COMBAT_CONFIG.getDouble(COMBAT_MACE_HACK);
                    if(stateCode == 0){
                        value.set(- value.get());
                    }else if(stateCode == 1){
                        value.set(Math.abs(value.get()));
                    }else if(stateCode == 2){
                        value.set(- Math.abs(value.get()));
                    }
                }
            }
            COMBAT_CONFIG.save();
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
        }else if(command.startsWith("/")){
            if(parseVanillaCommands(command.substring(1))){
                commandEvent.cancel();
                return;
            }
            if(checkCommandLength(command)){
                commandEvent.cancel();
            }
            return;
        }
        if(checkMessageLength(command)){
            commandEvent.cancel();
        }
    }

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

    private static final Config.FlagRef EXECUTE_GIVE_CLIENTSIDE = Configs.CHAT_CONFIG.getBoolean(Configs.CHAT_HELPER_CLIENT_GIVE);
    private static final Config.IntRef MESSAGE_LENGTH = Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_CHECK_MESSAGE_LENGTH);
    private static final Config.IntRef COMMAND_LENGTH = Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_CHECK_COMMAND_LENGTH);
    private static CommandManager manager;
    public static CommandManager getVanillaCommandManager(){
        if(manager == null){
            manager = new CommandManager(CommandManager.RegistrationEnvironment.ALL, CommandManager.createRegistryAccess(BuiltinRegistries.createWrapperLookup()));
        }
        return manager;
    }
    static {
        Listener.getServerDisconnectPoint().registerHandler((v)->{
            manager = null;
        });

    }
    private static boolean checkCommandLength(String command){
        if(MESSAGE_LENGTH.get() >0 && command.length() > COMMAND_LENGTH.get()){
            Debug.chat(Text.literal("你的输入内容太长了! %d / %d".formatted(command.length(), COMMAND_LENGTH.get())).formatted(Formatting.RED));
            if(!EXECUTE_GIVE_CLIENTSIDE.get() &&(command.startsWith("/give") || command.startsWith("/minecraft:give"))){
                Debug.chat(Text.literal("可以在配置文件中启用客户端/give指令来执行长指令"));
            }
            return true;
        }
        return false;
    }
    private static boolean checkMessageLength(String command){
        if(MESSAGE_LENGTH.get() >0 && command.length() > MESSAGE_LENGTH.get()){
            Debug.chat(Text.literal("你的输入内容太长了! %d / %d".formatted(command.length(),MESSAGE_LENGTH.get())).formatted(Formatting.RED));
            return true;
        }
        return false;
    }
    private static boolean parseVanillaCommands(String command){
        if(EXECUTE_GIVE_CLIENTSIDE.get()){
            if(command.startsWith("minecraft:give") || command.startsWith("give")) {
                if(mc.player.isCreative() ){
                    //parse command for give command
                    Debug.chat(Text.literal("尝试在客户端执行/give指令").formatted(Formatting.GREEN));
                    return dispatchVanillaCommand(command);
                }else {
                    Debug.chat(Text.literal("你启用了客户端/give指令的功能,但是你并不是创造模式!").formatted(Formatting.YELLOW));
                }
            }
        }
        return false;
    }
    private static CompletableFuture<Suggestions> parseVanillaComandsTab(String command, int cursorAt){
        if(EXECUTE_GIVE_CLIENTSIDE.get()){
            if(command.startsWith("minecraft:give") || command.startsWith("give")) {
                return dispatchVanillaTabComplete(command, cursorAt);
            }
        }
        return null;
    }
    private static ResultConsumer<CommandSource> consumer = (c, s, r) -> {
    };
    private static CompletableFuture<Suggestions> dispatchVanillaTabComplete(String command, int cursorAt){
        if(mc.player == null)return null;
        return null;
    }
    private static boolean dispatchVanillaCommand(String command){
       // Debug.info(command);
        if(mc.player == null)return false;
        mc.player.setClientPermissionLevel(4);
        try{
            ParseResults<CommandSource> parse = mc.getNetworkHandler().getCommandDispatcher().parse(command, mc.player.getCommandSource());
            if (parse.getReader().canRead()) {
                if (parse.getExceptions().size() == 1) {
                    throw parse.getExceptions().values().iterator().next();
                } else if (parse.getContext().getRange().isEmpty()) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader());
                } else {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(parse.getReader());
                }
            }

            final String commandStr = parse.getReader().getString();
            final CommandContextBuilder<CommandSource> originalBuilder = parse.getContext();
            //flatten this
            List<CommandContextBuilder<CommandSource>> modifiers = new ArrayList<>();
            CommandContextBuilder<CommandSource> contextData = originalBuilder;
            while (true){
                CommandContextBuilder<CommandSource> child = contextData.getChild();
                if(child == null){
                    if(contextData.getCommand() ==null){
                        consumer.onCommandComplete(originalBuilder.build(commandStr), false, 0);
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader());
                    }
                    break;
                }
                modifiers.add(contextData);
                contextData = child;
            }
            Map<String, ParsedArgument<CommandSource, ?>> argsMap = contextData.getArguments();
            if(commandStr.startsWith("give") || commandStr.startsWith("minecraft:give")){
                return handleClientSideGiveCommand(argsMap, command);
            }
        }catch (CommandSyntaxException e){
            Debug.chat(getErrorMessage(e));
        }catch (Throwable e){
            Debug.chat(Text.literal("Internal Error!").formatted(Formatting.RED) ,e);
        }
        return false;
    }

    private static boolean handleClientSideGiveCommand(Map<String, ParsedArgument<CommandSource, ?>> argsMap, String command) throws CommandSyntaxException{

        ParsedArgument<CommandSource, ?> entityArgument = argsMap.get("targets");
        EntitySelector entitySelector = (EntitySelector) entityArgument.getResult();
        StringRange range = entityArgument.getRange();
        if(entitySelector.isSenderOnly() || Objects.equals( mc.player.getNameForScoreboard(), command.substring(range.getStart(), range.getEnd()))){
            ItemStackArgument itemStack = (ItemStackArgument) argsMap.get("item").getResult();
            int count = argsMap.containsKey("count") ? (Integer)argsMap.get("count").getResult(): 1;
            ItemStack itemStackToGive = itemStack.createStack(count, false);
            InvTasks.creativeGive(itemStackToGive, count);
            Debug.chat(Text.literal("命令执行成功！").formatted(Formatting.GREEN));
            return true;
        }else{
            Debug.chat(Text.literal("你选中了其他生物,指令转向服务端执行!").formatted(Formatting.YELLOW));
            return false;
        }
    }

    private static Text getErrorMessage(CommandSyntaxException e) {
        Text message = Texts.toText(e.getRawMessage());
        String context = e.getContext();

        return context != null ? Text.translatable("command.context.parse_error", message, e.getCursor(), context) : message;
    }




    static {
        REGISTERED_COMMANDS = new SlimefunHelperMainCommand();
    }
}
