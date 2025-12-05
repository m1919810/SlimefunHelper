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
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.*;
import me.matl114.utils.UtilClass.*;
import me.matl114.utils.UtilClass.Event;
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

import static me.matl114.utils.UtilClass.TabExecutor.*;
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
    private static AbstractMainCommand REGISTERED_COMMANDS;
    private static final Map<String, Supplier<AbstractMainCommand>> COMMAND_FACTORY = new HashMap<>();
    public static void reloadAllCommand(){
        REGISTERED_COMMANDS = new SlimefunHelperMainCommand();
        Debug.chat("SfHelper Command Successfully reloaded");
    }
    public static class SlimefunHelperMainCommand extends AbstractMainCommand{
        SubCommand mainCommand = genMainCommand("");

        SubCommand reloadCommand = new SubCommand("reload", genArgument("what"),"!!reload <what: default main> 重载指令实例"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst().nextNonnull();
                switch (re){
                    case "main"->Tasks.scheduleDelayed(ChatTasks::reloadAllCommand,1);
                    case "vanilla" -> Tasks.scheduleDelayed(ChatTasks::reloadVanillaClientCommand, 1);
                    default -> Debug.chat("不支持的参数类型: " + re);
                }
                return true;
            }
        }
            .setEnum("what","main",List.of("vanilla","main"))
            .register(this);
        SubCommand helpCommand = new SubCommand("help", genArgument("subcommand", "!!help <optional> 获得帮助")){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst().nextArg();
                if(re != null){
                    SubCommand command1 = getSubCommand(re);
                    if(command1 != null){
                        SlimefunHelperMainCommand.this.sendMessage(var1,"&a"+command1.getHelp());
                        return true;
                    }
                    return false;
                }else {
                    showHelpCommand(var1);
                    return false;
                }
            }
        }
            .setTabCompletor("subcommand", this::getDisplayedSubCommand)
            .register(this);
        SubCommand openMenuCommand = new SubCommand("open", genArgument("page"),"!!open <page:default guide> 打开模组的特殊界面"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst().nextArg();
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
                    case "config" -> Tasks.scheduleDelayed(InvTasks::openSelectScreen, 1);
                    default -> Tasks.scheduleDelayed(SlimefunTasks::handleClickGuideIcon,1);
                }
                Debug.chat(Text.literal("成功打开界面").formatted(Formatting.GREEN));
                return true;
            }
        }
            .setEnum("page", "guide",List.of(
                "guide","rtype","vanilla","saved", "itemedit", "invcache", "config"
            ))
            .register(this);
        SubCommand configCommand = new SubCommand("config", genArgument(),"!!config 打开配置文件界面"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                Tasks.scheduleDelayed(InvTasks::openSelectScreen, 1);
                Debug.chat(Text.literal("成功打开配置文件界面").formatted(Formatting.GREEN));
                return true;
            }
        }
            .register(this);
        SubCommand listResourceCommand = new SubCommand("list",genArgument("resource"),"!!list <resource> 查看某些资源的值"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst().nextNonnull();
                switch (re){
                    case "hotkeys" -> {
                        Debug.chat(Text.literal("当前的快捷键注册表:").formatted(Formatting.GREEN));
                        HotKeys.getHotkeysMap().forEach((i,j)->{
                            Debug.chat("功能:", i, ", 快捷键:",j);
                        });
                    }
                    default -> Debug.chat("不存在的资源项",re);
                }
                return true;
            }

        }
            .setEnum("resource", List.of("hotkeys"))
            .register(this)
            ;

        SubCommand taskCommand = new SubCommand("task", genArgument("taskid"),"!!task <taskid> <args> 运行内置任务"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4);
                String val = re.getFirst().nextNonnull();
                String[] extraArg = re.getSecond();

                try{
                    Tasks.runSpecialTask(val, extraArg);
                }catch (Throwable e){
                    Debug.chat("运行Task出现错误!:",e.getMessage());
                    Debug.info(e);
                }
                return true;
            }
        }
            .setEnum("taskid", Tasks.getSpecialTaskName())
            .register(this);

        SubCommand asyncTaskCommand = new SubCommand("asynctask", genArgument("taskid"),"!!asynctask <taskid> <args> 运行内置任务"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4);
                String val = re.getFirst().nextNonnull();
                String[] extraArg = re.getSecond();
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
        }
            .register(this);

        SubCommand recipesDataCommand = new SubCommand("recipes", genArgument("action"),"!!recipes <action> 管理配方系统"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re= parseInput(var4).getFirst().nextNonnull();
                switch (re){
                    case "enable"->{
                        SlimefunTasks.handleAutoEnable();
                    }
                    case "reload" ->{
                        SlimefunTasks.reloadData();
                    }
                }
                return true;
            }
        }
            .setEnum("action","enable",List.of("reload","enable"))
            .register(this);

        SubCommand debugCommand = new SubCommand("debug", genArgument("debug","state"), "!!debug <debug> <state> 切换调试项"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
                String debug = re.nextNonnull();
                switch (debug){
                    case "packet-in"->{
                        Tasks.DEBUG_PACKET_IN = gbool(re.nextNonnull());
                    }
                    case "packet-out"->{
                        Tasks.DEBUG_PACKET_OUT = gbool(re.nextNonnull());
                    }
                    case "log-to-chat"->{
                        Debug.DEBUG_LOG_TO_CHAT = gbool(re.nextNonnull());
                    }
                }
                return true;
            }
        }
            .setEnum("debug", List.of("packet-in", "packet-out", "log-to-chat"))
            .setEnum("state", List.of("false", "true"))
            .register(this);

        SubCommand listRegistry = new SubCommand("registry", genArgument("id", "filter"), "!!registry <id> <filter: \"\"> 查看原版注册表"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
                Identifier identifier = Identifier.tryParse(re.nextNonnull());
                RegistryKey registryKey = RegistryKey.ofRegistry(identifier);
                Registry result = (Registry) ItemStackUtils.registry().getOptional(registryKey).orElse(null);
                if(result != null){
                    Debug.chat(Text.literal(identifier.toString() + "所拥有的注册项:").formatted(Formatting.GREEN));
                    String filter = re.nextNonnull();
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
                return true;
            }
        }
            .setTabCompletor("id", ()->ItemStackUtils.registry().streamAllRegistryKeys().map(RegistryKey::getValue).map(i-> "minecraft".equals(i.getNamespace())? i.getPath(): i.toString()).toList())
            .setDefault("filter","")
            .register(this);

        SubCommand listData = new SubCommand("resource", genArgument("id", "filter"), "!!resource <id> 查看某些原版重要数据"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
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
                    }
                    case "command"->{
                       datas = mc.getNetworkHandler().getCommandDispatcher().getRoot().getChildren()
                            .stream()
                            .map(CommandNode::getName)
                            .filter(u->u.contains(filter))
                            .sorted(String::compareTo)
                            .toList();
                    }
                    case "seed" ->{
                        datas = List.of(
                           Text.literal( "服务端加密种子: ").append(ChatUtils. getDisplayedLong(mc.world.getBiomeAccess().seed)),
                            Text.literal(  "当前绑定种子: ").append(MineTasks.isCurrentWorldSeedInputExist()? ChatUtils. getDisplayedLong(MineTasks.getCurrentWorldSeedInput()): Text.literal("暂未输入"))
                        );
                    }
                    case "plugins" -> {
                        //todo: add tabing /version as a plan , then appending command namespace
                        datas = mc.getNetworkHandler().getCommandDispatcher().getRoot().getChildren()
                            .stream()
                            .map(CommandNode::getName)
                            .map(n -> {
                                var sp = n.split(":");
                                return  sp.length >=2 ? sp[0] : null;
                            })
                            .filter(Objects::nonNull)
                            .filter(u->u.contains(filter))
                            .distinct()
                            .sorted(String::compareTo)
                            .toList();
                    }
//                    case "gamerule"->{
//                        datas = mc.world.getGameRules().toNbt().entries.entrySet().stream()
//                            .map(entry-> entry.getKey()+ ":" + entry.getValue().asString())
//                            .filter(u-> u.contains(filter))
//                            .toList();
//                    }
                    default -> {
                        Debug.chat(Text.literal("不支持的资源: "+val).formatted(Formatting.RED));
                        return false;
                    }
                }
                Debug.chat(Text.literal(val + "所拥有的数据:").formatted(Formatting.GREEN));
                for (var identifier1 : datas){
                    Debug.chat(identifier1);
                }
                return true;
            }
        }
            .setEnum("id", List.of("world", "command", "seed", "plugins"))
            .setDefault("filter","")
            .register(this);

        SubCommand sleep = new SubCommand("sleep", genArgument("level","confirm"), "!!sleep <confirm> 进入睡眠状态"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
                int level = re.nextInt();
                if(level != 1 && level != 2){
                    Debug.chat("请输入范围内的数字: 1~2");
                    return true;
                }
                String val = re.nextNonnull();
                if("confirm".equals(val)){
                    Tasks.scheduleDelayed(()->RenderTasks.setScreenSleeping(level), 1);
                }else {
                    Debug.chat("使用sleep confirm 确认进入睡眠模式, 进入睡眠模式后可以按 "+ ModConfig.getFuncHotKeys(HotKeys.WAKE_UP_SCREEN)+" 键离开");
                }
                return true;
            }
        }
            .setDefault("confirm","")
            .setInt("level")
            .register(this);

        SubCommand renderDebug = new SubCommand("debug-render", genArgument("task", "state"), "!!debug-render <task> <state> 决定是否启用调试渲染功能"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
                String task = re.nextNonnull();
                String flag = re.nextNonnull();
                switch (task){
                    case "collision"->RenderTasks.DEBUG_RENDER_COLLISION = gbool(flag);
                    case "combat" -> RenderTasks.DEBUG_RENDER_COMBAT = gbool(flag);
                    case "bow-aim"-> RenderTasks.DEBUG_RENDER_BOWAIM = gbool(flag);
                    case "debug-tick" -> RenderTasks.DEBUG_TICK = gint(flag);
                    default -> Debug.chat("没有调试项:",task);
                }
               return true;
            }
        }
            .setEnum("task", List.of("collision", "combat", "bow-aim", "debug-tick"))
            .setTabCompletor("state", ()->List.of("true", "false"))
            .register(this);
        private Vec3d resolveCoord(Entity entity, String xcoord, String ycoord, String zcoord){
            Vec3d parsedCoord;
            if(xcoord.startsWith("^")){
                //use polar coord
                if(!(ycoord.startsWith("^") && zcoord.startsWith("^"))){
                    throw new LogicalError("Illegal format of look coordinate");
                }
                double x = xcoord.length() == 1 ? 0: TabExecutor.gdouble(xcoord.substring(1));
                double y = ycoord.length() == 1 ? 0: TabExecutor.gdouble(ycoord.substring(1));
                double z = zcoord.length() == 1 ? 0: TabExecutor.gdouble(zcoord.substring(1));
                parsedCoord = EntityUtils.lookCoordTooAbsolutePos(entity, x, y, z);
            }else {
                //use simple coord
                Vec3d pos = entity.getPos();
                double x = 0;
                double y = 0;
                double z = 0;
                if(xcoord.startsWith("~")){
                    x = pos.x;
                    xcoord = xcoord.substring(1);
                }
                if(!xcoord.isEmpty()){
                    x += TabExecutor.gdouble(xcoord);
                }
                if(ycoord.startsWith("~")){
                    y = pos.y;
                    ycoord = ycoord.substring(1);
                }
                if(!ycoord.isEmpty()){
                    y += TabExecutor.gdouble(ycoord);
                }
                if(zcoord.startsWith("~")){
                    z = pos.z;
                    zcoord = zcoord.substring(1);
                }
                if(!zcoord.isEmpty()){
                    z += TabExecutor.gdouble(zcoord);
                }

                parsedCoord = new Vec3d(x, y, z);
            }
            return parsedCoord;
        }
        SubCommand tpCommand = new SubCommand("tp", genArgument("x", "y", "z", "far"), "!!tp <x> <y> <z> [-far] 执行模拟tp行为"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
                String xcoord = re.nextNonnull();
                String ycoord = re.nextNonnull();
                String zcoord = re.nextNonnull();
                Vec3d parsedCoord = resolveCoord(var1, xcoord, ycoord, zcoord);
                boolean flag = re.nextBoolean();
                MovTasks.executeTp(parsedCoord, flag ? 2147483647: 128, true, true);
                return true;
            }
        }
            .setTabCompletor("x", ()->{
                return Streams.concat(Stream.of("%.2f %.2f %.2f".formatted(mc.player.getX(), mc.player.getY(), mc.player.getZ())), List.of("~ ~ ~", "^ ^ ^").stream()).toList();
            })
            .setDefault("x", "~")
            .setTabCompletor("y", ()->{
                return Streams.concat(Stream.of("%.2f %.2f".formatted(mc.player.getY(), mc.player.getZ())), List.of("~ ~", "^ ^").stream()).toList();
            })
            .setDefault("y", "~")
            .setTabCompletor("z", ()->{
                return Streams.concat(Stream.of("%.2f".formatted(mc.player.getZ())), List.of("~", "^").stream()).toList();
            })
            .setDefault("z", "~")
            .setTabCompletor("far",()->List.of("true", "false"))
            .setDefault("far", "false")
            .register(this);
        ;
        public static Vec3d mark;
        private Vec3d specialPositions(String target, ClientPlayerEntity var1){
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
                case "resync" ->{
                    if(MovTasks.LAST_RESYNC_POS != null){
                        var1.sendMessage(Text.literal("使用上次客户端同步之前的位置").append(ChatUtils. getDisplayedLocationDouble(MovTasks.LAST_RESYNC_POS)));
                        yield MovTasks.LAST_RESYNC_POS;
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
            return List.of("#mark","#near",  "#this", "#back", "#death", "#resync", "#lasttp");
        }

        SubCommand specialTp = new SubCommand("tpa", genArgument("target", "far"), "!!tpa <target> 传送到特殊目标位置"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
                String target = re.nextNonnull();
                Vec3d pos;
                if(target.startsWith("#")){
                    //special target
                    pos = specialPositions(target, var1);
                    if(pos == null)return true;
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
                        return true;
                    }
                    pos = entity.getPos();
                }
                boolean flag = re.nextBoolean();
                MovTasks.executeTp(pos, flag ? 2147483647: 128, true, true);
                return true;
            }
        }
            .setTabCompletor("target", ()->Stream.concat(Stream.concat(
                specialPositionType().stream(), mc.world == null? Stream.empty() : EntityUtils.getWorldPlayerNames(false)), mc.crosshairTarget !=null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY ? Stream.of (((EntityHitResult)(mc.crosshairTarget)).getEntity().getUuidAsString()): Stream.empty() ).toList())
            .setTabCompletor("far",()->List.of("true", "false"))
            .setDefault("far", "false")
            .register(this);
        public static Tasks.RepeatTimedTask travelTask;
        //todo add elytra support
        SubCommand farawayTravel = new SubCommand("travel", genArgument("mode", "x", "y", "z"), "!!travel <mode> <coord> 管理travel task"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
                String mode = re.nextNonnull();
                switch (mode){
                    case "to" ->{
                        //fixme: add rot packets
                        if(travelTask == null){
                            String xcoord = re.nextNonnull();
                            Vec3d parsedCoord;
                            if(xcoord.startsWith("#")){
                                parsedCoord = specialPositions(xcoord, var1);
                            }else{
                                String ycoord = re.nextNonnull();
                                String zcoord = re.nextNonnull();
                                parsedCoord = resolveCoord(var1, xcoord, ycoord, zcoord);
                            }
                            if(parsedCoord == null)return true;
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

//                                        if(move(towardsHorizontal.multiply(9.5).add(0, -0.3,0))){
//                                            return true;
//                                        }
//                                        if(move(towardsHorizontal.multiply(9.5).add(0, -0.3,0))){
//                                            return true;
//                                        }
//                                            move(Vec3d.ZERO);
//                                            move(Vec3d.ZERO);
//                                            // .multiply(9.5).add(0, -0.025, 0);
//                                            if (move(towardsHorizontal.multiply(19.5).add(0, -0.3,0))) {
//                                                return true;
//                                            }
//                                            move(Vec3d.ZERO);
//                                            if (move(towardsHorizontal.multiply(19.5).add(0, -0.3,0))) {
//                                                return true;
//                                            }

//                                            move(Vec3d.ZERO);
//                                            if (move(towardsHorizontal.multiply(9.5).add(0, 0.0,0))) {
//                                                return true;
//                                            }

//                                            tickCNT += 1;
//                                            if(tickCNT == 20){
//                                                Debug.info("last 20 tick",System.currentTimeMillis() - lastTick, "MS");
//                                                lastTick = System.currentTimeMillis();
//                                                tickCNT = 0;
//                                            }
//                                        if(tickCNT > 3){
//                                            tickCNT = 0;
//                                            if (move(towardsHorizontal)) {
//                                                return true;
//                                            }
//                                        }

                                            // plan A  1分16 (10000, 10000)
                                            //plan B 1分06
                                            //plan C 1分07
                                            //plan D 1分15 0回弹
                                            //plan E 1分10
                                            //plan F 1分12 5回弹
                                            //pln G 1分07 多回弹
//                                    for (int i=4; i< 10; ++ i )
//                                    {
//                                        if(move(towardsHorizontal)){
//                                            return true;
//                                        }
//                                    }
//                                    for (int i = 11; i< 15 ; ++i){
//                                        if(move(towardsHorizontal)){
//                                            return true;
//                                        }
//                                    }




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
                    case "cancel"->{
                        if(travelTask != null){
                            travelTask.cancel();
                            travelTask = null;
                        }
                    }
                }
                return true;
            }
        }
            .setEnum("mode", List.of("to", "cancel"))
            .setTabCompletor("x", ()->{
                return  Streams.concat(Stream.of("%.2f %.2f %.2f".formatted(mc.player.getX(), mc.player.getY(), mc.player.getZ())), List.of("~ ~ ~", "^ ^ ^").stream(), specialPositionType().stream()).toList();
            })
            .setDefault("x", "~")
            .setTabCompletor("y", ()->{
                return Streams.concat(Stream.of("%.2f %.2f".formatted(mc.player.getY(), mc.player.getZ())), List.of("~ ~", "^ ^").stream()).toList();
            })
            .setDefault("y", "~")
            .setTabCompletor("z", ()->{
                return Streams.concat(Stream.of("%.2f".formatted(mc.player.getZ())), List.of("~", "^").stream()).toList();
            })
            .setDefault("z", "~")
            .register(this);

        SubCommand markCommand = new SubCommand("mark", genArgument("type", "extra"), "!!mark <type> [extra] 标注一个位置为临时缓存位置"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
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
                            return true;
                        }
                    }
                    case "clear" -> {
                        mark = null;
                        return true;
                    }
                    default -> {
                        var1.sendMessage(Text.literal("不存在的mark类型: "+type).formatted(Formatting.RED));
                        return true;
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
                return true;
            }
        }
            .setDefault("type", "camera")
            .setTabCompletor("type", ()->List.of("player" ,"camera", "this", "cross", "clear"))
            .setTabCompletor("extra",()->Stream.concat(EntityUtils.getWorldPlayerNames(true), Stream.empty()).toList())
            .register(this);


        SubCommand infoCommand = new SubCommand("info", genArgument("information", "user"), "!!info <information> <user> 查看某项信息"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
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
                return true;
            }
        }
        //I'd like to know what team is player in
        //I'd like to know if I can get access to other player's data
            .setEnum("information", List.of("death", "spawn", "nbt", "inventory","ender", "plist", "team", "pentry"))
            .setDefault("user", "#me")
            .setTabCompletor("user", ()-> Stream.concat(EntityUtils.getWorldPlayerNames(false), Stream.of("#me")).toList())
            .register(this);

        SubCommand presetCommand = new SubCommand("preset", genArgument("preset"), "!!preset <preset> 加载配置文件预设"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
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
                    default -> {
                        return true;
                    }
                }
                Debug.info("已经加载", preset, "配置预设");
                return true;
            }
            private void configureNoACEnvHacks(){
                //NO FALL
                MOV_CONFIG.setValueNoNew(true, MOVE_NOFALL);
                //NO SLOW
                MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_SNEAK);
                MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_SLOW);
                MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_USEITEM);
                MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_FRAC);
                MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_IN);
                MOV_CONFIG.setValueNoNew(true, MOVE_SPEED_NO_SLOW_DOWN_BLOCK_SPECIAL);
                //sprint
                MOV_CONFIG.setValueNoNew(true, MOVE_AUTO_TOGGLE_SPRINT);

                MOV_CONFIG.setValueNoNew(BypassMode.NO_BYPASS, MOVE_SPRINT_BYPASS_MODE);
                MOV_CONFIG.setValueNoNew(BypassMode.NO_BYPASS, MOVE_NOFALL_MODE);
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
            }
            private void configureGrimACEnvHacks(){
                //grimac mode of nofall works
                MOV_CONFIG.setValueNoNew(true, MOVE_NOFALL);
                MOV_CONFIG.setValueNoNew(BypassMode.BYPASS_GRIM, MOVE_NOFALL_MODE);
                MOV_CONFIG.save();
                MINE_CONFIG.setValueNoNew(BypassMode.BYPASS_GRIM, MINE_BYPASS_FAST_BREAK_BYPASS_MODE);
                MINE_CONFIG.save();
            }
            private void configureVulcanEnvHacks(){
                //vanilla nofall can bypass vulcan
                MOV_CONFIG.setValueNoNew(true, MOVE_NOFALL);
                MOV_CONFIG.save();
                //vanilla kill can bypass vulcan
                COMBAT_CONFIG.setValueNoNew(false, COMBAT_LEGAL_MOD);
                COMBAT_CONFIG.setValueNoNew(false, COMBAT_BOW_AIM_LEGALLY);
                COMBAT_CONFIG.save();
            }
            private void configureCommonACHacks(){
                MOV_CONFIG.setValueNoNew(false, MOVE_NOFALL);
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
            }
        }
            .setEnum("preset", List.of("vanilla", "hacking", "ac-common", "ac-grim", "ac-vulcan"))
            .register(this);

        //todo not complete
        SubCommand fastToggleCommand = new SubCommand("toggle", genArgument("toggle", "state"), "!!toggle <toggle> 针对某些配置项进行快捷切换"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4).getFirst();
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
                return true;
            }
        }
            .setEnum("toggle", List.of("tp-attack", "bow-tp-attack", "mace-attack", "pearl-tp"))
            .setEnum("state", "switch", List.of("on", "off", "switch"))
            .register(this);


        //todo more command
        //todo add facing/ targeting command
        {

            if(COMMAND_FACTORY != null){
                COMMAND_FACTORY.forEach(((string, commandSupplier) -> this.registerSubMain(string, commandSupplier.get())));
            }
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
            List<String> val = REGISTERED_COMMANDS.onTabComplete(mc.player, "", command);
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
                if(REGISTERED_COMMANDS.onCommand(mc.player, "", args)){
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
            REGISTERED_COMMANDS.registerSubMain(name, commandSupplier.get());
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
