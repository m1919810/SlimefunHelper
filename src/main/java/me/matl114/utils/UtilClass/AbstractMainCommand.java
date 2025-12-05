package me.matl114.utils.UtilClass;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Streams;
import lombok.Getter;

import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

public abstract class AbstractMainCommand implements ComplexCommandExecutor, InterruptionHandler {
    @Getter
    private LinkedHashSet<SubCommand> subCommands = new LinkedHashSet<>();
    private SubCommand mainInternal;

    protected void sendMessage(ClientPlayerEntity sender, String message) {
        sender.sendMessage(ChatUtils.stringToText(message));
    }

    public SubCommand getSubCommand(String name) {
        for(SubCommand command:subCommands){
            if(command.getName().equalsIgnoreCase(name)){
                return command;
            }
        }return null;
    }
    public List<String> getDisplayedSubCommand(){
        return this.subCommands.stream().filter(SubCommand::isVisiable).map(SubCommand::getName).toList();
    }
    protected SubCommand genMainCommand(String name){
        mainInternal =  new SubCommand(name,genArgument("_operation"),"")
            .setTabCompletor("_operation",this::getDisplayedSubCommand);
        return mainInternal;
    }

    public void registerSub(SubCommand command) {
        this.subCommands.add(command);
    }
    public void registerSubMain(String name, AbstractMainCommand command){
        new SubCommand(name, genArgument(),name + " 查看子指令组 "+ name +" 的相关信息" ).setCommandExecutor(command).register(this);
    }
    private SubCommand getMainInternal() {

        return mainInternal;
    }
    public SubCommand getMainCommand() {
        return getMainInternal();
    }
    public String getMainName(){
        return getMainInternal().getName();
    }
    public boolean onCommand(ClientPlayerEntity var1, String var3, String[] var4){
        if(var4.length>=1){
            SubCommand command=getSubCommand(var4[0]);
            if(command != null ){
                //add permission check

                String[] elseArg= Arrays.copyOfRange(var4,1,var4.length);
                try{
                    return command.getExecutor().onCommand(var1,var4[0],elseArg);
                }catch (ArgumentException e){
                    e.handleAbort(var1, this);
                    return false;
                }
            }
        }
        showHelpCommand(var1);
        return false;
    }



    public void handleTypeError(ClientPlayerEntity sender, String argument, TypeError.BaseArgumentType type, String input){
        if(argument != null){
            sender.sendMessage(ChatUtils.stringToText( "&c类型错误:参数\""+ argument+"\"需要输入一个"+type.getDisplayNameZHCN()+",但是输入了:" + input));
        }else {
            sender.sendMessage(ChatUtils.stringToText( "&c类型错误: 需要输入一个" + type.getDisplayNameZHCN()+",但是输入了:" + input));
        }
    }
    public void handleValueAbsent(ClientPlayerEntity sender, String argument){
        sender.sendMessage(ChatUtils.stringToText("&c值缺失: 并未输入参数\"" + argument + "\"的值"));
    }
    public void handleLogicalError(ClientPlayerEntity sender, String fullMessage){
        sender.sendMessage(ChatUtils.stringToText( "&c执行该指令时出现逻辑错误: "+ fullMessage));
    }


    public void showHelpCommand(ClientPlayerEntity sender){
        sendMessage(sender,"&a/%s 全部指令大全".formatted(getMainName()));
        for(SubCommand cmd:subCommands){
            for (String help:cmd.getHelp()){
                sendMessage(sender,"&a"+help);
            }
        }
    }
    public List<String> onTabComplete(ClientPlayerEntity var1, String var3, String[] var4){
        //add permission check

        var re=getMainCommand().parseInput(var4);
        if(re.getSecond().length==0){
            List<String> provider=re.getFirst().getTabComplete();
            return provider==null?new ArrayList<>():provider;
        }else{
            SubCommand subCommand= getSubCommand(re.getFirst().nextArg());
            if(subCommand!=null ){
                String[] elseArg=re.getSecond();
                return subCommand.onTabComplete(var1,var3,elseArg);
            }
        }
        return new ArrayList<>();
    }

    public static SimpleCommandArgs genArgument(String... args){
        return new SimpleCommandArgs(args);
    }
    public static Supplier<List<String>> numberSupplier(){
        return ()->List.of("0","1","16","114514","2147483647");
    }
    public static Supplier<List<String>> floatSupplier(){
        return ()->List.of("0.0","1.0","3.14159","1.57079","6.283185");
    }
    public Supplier<List<String>> subCommandsSupplier(){
        return this::getDisplayedSubCommand;
    }
    public static Supplier<List<String>> playerNameSupplier(){
        return ()-> Streams.concat(Stream.of(MinecraftClient.getInstance().player.getName().toString()), MinecraftClient.getInstance().getNetworkHandler().getPlayerList().stream().map(pl->pl.getDisplayName().toString())).toList();
    }

}