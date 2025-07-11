package me.matl114.hackUtils;

import com.mojang.brigadier.*;
import com.mojang.brigadier.arguments.ArgumentType;
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
import lombok.val;
import me.matl114.ModConfig;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.renders.RenderMain;
import me.matl114.utils.Debug;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.UtilClass.AbstractMainCommand;
import me.matl114.utils.UtilClass.CancellableEntryPoint;
import me.matl114.utils.UtilClass.LimitedSpeedExecutor;
import me.matl114.utils.UtilClass.SubCommand;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.*;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ChatTasks {
    public static void init(){

    }
    private static MinecraftClient mc = MinecraftClient.getInstance();
    private static AtomicInteger period= Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_PERIOD);
    private static Config.StringRef message = Configs.CHAT_CONFIG.getString(Configs.CHAT_HELPER_CACHE);
    private static AtomicInteger multiple= Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_MULTIPLE);
    private static AtomicInteger counter= new AtomicInteger(0);

    public static void onAutoChatStart(){
        if(counter.getAndIncrement()>period.get()){
            counter.set(0);
            for (int i=0;i<multiple.get();i++){
                sendMessage(message.get(),true);
            }
        }
    }
    //modified from @ChatScreen.class
    public static void sendMessage(String chatText, boolean addToHistory) {
        if(MinecraftClient.getInstance().player!=null&&MinecraftClient.getInstance().player.networkHandler!=null){
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
    private static final LimitedSpeedExecutor chatExecutor=new LimitedSpeedExecutor(new AtomicInteger(5));
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

    }
    @Getter
    private static final CancellableEntryPoint<MutableObject<String>> chatEntryPoint = new CancellableEntryPoint<>();
    static{
        chatEntryPoint.registerHandler(i->{
            return !parseClientCommand(i.getValue());
        });
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
                    case "main"->Tasks.scheduleDelayed(ChatTasks::reloadAllCommand,2);
                    case "vanilla" -> Tasks.scheduleDelayed(ChatTasks::reloadVanillaClientCommand, 2);
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
                    Tasks.runTask(val, extraArg);
                }catch (Throwable e){
                    Debug.chat("运行Task出现错误!:",e.getMessage());
                    Debug.info(e);
                }
                return true;
            }
        }
            .register(this);

        SubCommand asyncTaskCommand = new SubCommand("asynctask", genArgument("taskid"),"!!asynctask <taskid> <args> 运行内置任务"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                var re = parseInput(var4);
                String val = re.getFirst().nextNonnull();
                String[] extraArg = re.getSecond();
                CompletableFuture.runAsync(()->{
                    try{
                        Tasks.runTask(val, extraArg);
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

        SubCommand renderDebugCommand = new SubCommand("render", genArgument(), "!!render 进行debug"){
            @Override
            public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4) {
                RenderMain.modelDebug();
                return true;
            }
        }
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
                            .toList();
                    }
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
            .setEnum("id", List.of("world", "command"))
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
                    Tasks.scheduleDelayed(()->RenderTasks.setScreenSleeping(level), 2);
                }else {
                    Debug.chat("使用sleep confirm 确认进入睡眠模式, 进入睡眠模式后可以按 "+ ModConfig.getFuncHotKeys(HotKeys.WAKE_UP_SCREEN)+" 键离开");
                }
                return true;
            }
        }
            .setDefault("confirm","")
            .setInt("level")
            .register(this);

        {

            if(COMMAND_FACTORY != null){
                COMMAND_FACTORY.forEach(((string, commandSupplier) -> this.registerSubMain(string, commandSupplier.get())));
            }
        }


        public AbstractMainCommand reload() {
            return new SlimefunHelperMainCommand();
        }
    }


    public static boolean parseClientCommand(String command){
        if(command.startsWith("!!")){
            dispatchClientCommand(command.substring(2));
            return true;
        }else if(command.startsWith("/!!")){
            dispatchClientCommand(command.substring(3));
            return true;
        }else if(command.startsWith("/")){
            if(parseVanillaCommands(command.substring(1))){
                return true;
            }
            return checkCommandLength(command);
        }
        return checkMessageLength(command);
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
            if(REGISTERED_COMMANDS.onCommand(mc.player, "", args)){
                return;
            }
        }
    }
    public static void registerSubCommands(String name, Supplier<AbstractMainCommand> commandSupplier){
        COMMAND_FACTORY.put(name, commandSupplier);
        if(REGISTERED_COMMANDS != null){
            REGISTERED_COMMANDS.registerSubMain(name, commandSupplier.get());
        }
    }

    private static final AtomicBoolean EXECUTE_GIVE_CLIENTSIDE = Configs.CHAT_CONFIG.getBoolean(Configs.CHAT_HELPER_CLIENT_GIVE);
    private static final AtomicInteger MESSAGE_LENGTH = Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_CHECK_MESSAGE_LENGTH);
    private static final AtomicInteger COMMAND_LENGTH = Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_CHECK_COMMAND_LENGTH);
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
