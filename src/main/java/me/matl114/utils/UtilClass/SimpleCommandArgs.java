package me.matl114.utils.UtilClass;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class SimpleCommandArgs {
    public static class Argument implements TabProvider{
        @Getter
        private final String argsName;
        public HashSet<String> argsAlias;
        @Getter
        @Setter
        private String defaultValue=null;
        public Supplier<List<String>> tabCompletor=List::of;
        public Argument(String argsName){
            this.argsName = argsName;
            this.argsAlias = new HashSet<>();
            argsAlias.add(argsName);
            argsAlias.add(argsName.toLowerCase());
            argsAlias.add(argsName.toUpperCase());
            argsAlias.add(argsName.substring(0,1));
            argsAlias.add(argsName.substring(0, 1).toLowerCase());
            argsAlias.add(argsName.substring(0, 1).toUpperCase());
        }
        public boolean isAlias(String arg){
            return argsAlias.contains(arg);
        }
        public List<String> getTab(){
            return tabCompletor.get();
        }
    }

    public static class SimpleCommandInputStream {
        public SimpleCommandInputStream(SimpleCommandArgs.Argument[] args, Map<SimpleCommandArgs.Argument,String> argsMap){
            this.arguments = args;
            this.argsMap = argsMap;
        }
        SimpleCommandArgs.Argument[] arguments;
        Map<SimpleCommandArgs.Argument, String> argsMap;
        int i = 0;
        public boolean hasNext(){
            return i < arguments.length;
        }
        public SimpleCommandArgs.Argument nextArgument(){
            return arguments[i++];
        }

        public String nextArg() {
            if(hasNext()){
                SimpleCommandArgs.Argument arg = nextArgument();
                return argsMap.computeIfAbsent(arg, SimpleCommandArgs.Argument::getDefaultValue);
            }else {
                throw new RuntimeException("Illegal to access undeclared argument");
            }
        }


        public int nextInt() {
            if(hasNext()){
                SimpleCommandArgs.Argument arg = nextArgument();
                String val = argsMap.computeIfAbsent(arg, SimpleCommandArgs.Argument::getDefaultValue);
                if(val == null){
                    throw new ValueAbsentError(arg);
                }
                return TabExecutor.gint(val, arg);
            }else {
                throw new RuntimeException("Illegal to access undeclared argument");
            }
        }


        public boolean nextBoolean() {
            if(hasNext()){
                SimpleCommandArgs.Argument arg = nextArgument();
                String val = argsMap.computeIfAbsent(arg, SimpleCommandArgs.Argument::getDefaultValue);
                if(val == null){
                    throw new ValueAbsentError(arg);
                }
                return TabExecutor.gbool(val, arg);
            }else {
                throw new RuntimeException("Illegal to access undeclared argument");
            }
        }


        public double nextDouble() {
            if(hasNext()){
                SimpleCommandArgs.Argument arg = nextArgument();
                String val = argsMap.computeIfAbsent(arg, SimpleCommandArgs.Argument::getDefaultValue);
                if(val == null){
                    throw new ValueAbsentError(arg);
                }
                return TabExecutor.gdouble(val, arg);
            }else {
                throw new RuntimeException("Illegal to access undeclared argument");
            }
        }


        public float nextFloat() {
            if(hasNext()){
                SimpleCommandArgs.Argument arg = nextArgument();
                String val = argsMap.computeIfAbsent(arg, SimpleCommandArgs.Argument::getDefaultValue);
                if(val == null){
                    throw new ValueAbsentError(arg);
                }
                return TabExecutor.gfloat(val, arg);
            }else {
                throw new RuntimeException("Illegal to access undeclared argument");
            }
        }


        public String nextNonnull() {
            if(hasNext()){
                SimpleCommandArgs.Argument arg = nextArgument();
                String val = argsMap.computeIfAbsent(arg, SimpleCommandArgs.Argument::getDefaultValue);

                if(val == null){
                    throw new ValueAbsentError(arg);
                }
                return val;
            }else {
                throw new RuntimeException("Illegal to access undeclared argument");
            }
        }

        @Nullable

        public List<String> getTabComplete(){
            for(int i=0;i<=arguments.length;i++){
                if(i==arguments.length ||argsMap.get(arguments[i])==null){
                    if(i==0){
                        return null;
                    }
                    final int index=i-1;
                    List<String> tablist=arguments[index].tabCompletor.get();
                    tablist=tablist==null?List.of():tablist;
                    return tablist.stream().filter(s->s.contains(argsMap.get(arguments[index]))).toList();
                }
            }
            return null;
        }
    }
    
    @Getter
    Argument[] args;
    public SimpleCommandArgs(String... args){
        this.args= Arrays.stream(args).map(Argument::new).toArray(Argument[]::new);
    }
    public void setDefault(String arg,String defaultValue){
        for(Argument a : args){
            if(a.argsName.equals(arg)){
                a.defaultValue=defaultValue;
            }
        }
    }
    public void setTabCompletor(String arg,Supplier<List<String>> tabCompletor){
        for(Argument a : args){
            if(a.argsName.equals(arg)){
                a.tabCompletor=tabCompletor;
            }
        }
    }
    public Pair<SimpleCommandInputStream,String[]> parseInputStream(String[] input){
        List<String> commonArgs=new ArrayList<>();
        final HashMap<Argument,String> argsMap=new HashMap<>();
        Iterator<String > iter= Arrays.stream(input).iterator();
        while(iter.hasNext()){
            String arg=iter.next();
            if(arg.startsWith("-")){
                Argument selected=null;
                String trueName = arg.replaceFirst("^-+","");
                for(Argument a:args){
                    if(a.isAlias(trueName)){
                        selected=a;
                        break;
                    }
                }
                if(selected!=null){
                    if(arg.startsWith("--")){
                        // --args inputValue
                        if(iter.hasNext()){
                            String arg2=iter.next();

                            argsMap.put(selected,arg2);
                        }
                    }else{
                        //-f -v means boolean
                        argsMap.put(selected,"true");
                    }

                }
                else {
                    //输入了一个无效参数 加入commonArgs
                    commonArgs.add(arg);
                }
            }
            else {
                commonArgs.add(arg);
            }
        }
        for(Argument a:args){
            if(!argsMap.containsKey(a)){
                if(!commonArgs.isEmpty()){
                    argsMap.put(a,commonArgs.remove(0));
                }
            }
        }
        return new Pair<>(new SimpleCommandInputStream(args, argsMap) ,commonArgs.toArray(String[]::new));
    }
}
