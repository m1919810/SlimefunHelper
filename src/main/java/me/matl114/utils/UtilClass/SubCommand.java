package me.matl114.utils.UtilClass;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import net.minecraft.client.network.ClientPlayerEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class SubCommand implements TabExecutor {
    @Nullable
    @Override
    public List<String> onTabComplete(ClientPlayerEntity commandSender, String s, String[] elseArg) {
        if(executor!=this){
            return executor.onTabComplete(commandSender, s, elseArg);
        }else {
            var tab=this.parseInput(elseArg).getFirst();
            if(tab!=null){
                return tab.getTabComplete();
            }else {
                return List.of();
            }
        }
    }

    public interface SubCommandCaller{
        public void registerSub(SubCommand command);
    }
    @Getter
    String[] help;
    SimpleCommandArgs template;
    @Getter
    String name;
    @Getter
    TabExecutor executor=this;
    boolean hide = false;
    public boolean onCommand(ClientPlayerEntity var1,String var3, String[] var4){
        return true;
    }
    public SubCommand(String name,SimpleCommandArgs argsTemplate,String... help){
        this.name = name;
        this.template=argsTemplate;
        this.help = help;
    }
    public SubCommand(String name,SimpleCommandArgs argsTemplate,List<String> help){
        this(name,argsTemplate,help.toArray(String[]::new));
    }
    public SubCommand hide(){
        this.hide=true;
        return this;
    }
    public boolean isVisiable(){
        return !this.hide;
    }
    public SubCommand register(SubCommandCaller caller){
        caller.registerSub(this);
        return this;
    }
    @Nonnull
    public Pair<SimpleCommandArgs.SimpleCommandInputStream,String[]> parseInput(String[] args){
        return template.parseInputStream(args);
    }

    public CommandArgumentMap parseArgument(String[] args){
        return new CommandArgumentMap(TabExecutor.parseArguments(args, this.template.getArgs()));
    }
    public SubCommand setDefault(String arg,String val){
        this.template.setDefault(arg,val);
        return this;
    }
    public SubCommand setInt(String arg){
        setDefault(arg, "0");
        setTabCompletor(arg, AbstractMainCommand.numberSupplier());
        return this;
    }
    public SubCommand setInt(String arg, int val){
        setDefault(arg, String.valueOf(val));
        setTabCompletor(arg, AbstractMainCommand.numberSupplier());
        return this;
    }
    public SubCommand setFloat(String arg){
        setDefault(arg, "0.0");
        setTabCompletor(arg, AbstractMainCommand.floatSupplier());
        return this;
    }
    public SubCommand setFloat(String arg, float val){
        setDefault(arg, String.valueOf(val));
        setTabCompletor(arg, AbstractMainCommand.floatSupplier());
        return this;
    }

    public SubCommand setEnum(String arg, Collection<String> enumValues){
        List<String> enums = enumValues.stream().toList();
        setTabCompletor(arg,()->enums);
        return this;
    }
    public SubCommand setEnum(String arg, String defaultValue, Collection<String> enumValues){
        List<String> enums = enumValues.stream().toList();
        setDefault(arg, defaultValue);
        setTabCompletor(arg,()->enums);
        return this;
    }
    public SubCommand setTabCompletor(String arg, Supplier<List<String>> completions){
        this.template.setTabCompletor(arg,completions);
        return this;
    }
    public SubCommand setCommandExecutor(TabExecutor executor){
        this.executor=executor;
        return this;
    }
}