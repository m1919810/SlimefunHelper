package me.matl114.utils.UtilClass.commands;

import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import me.matl114.utils.UtilClass.interruptions.TypeError;
import me.matl114.utils.UtilClass.interruptions.ValueAbsentError;
import me.matl114.utils.UtilClass.interruptions.ValueOutOfRangeError;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

public class ArgumentInputStream {
    @AllArgsConstructor
    @Accessors(fluent = true)
    @Setter
    @Getter
    public static class ArgumentReaderResult{
        //the index lies at the next index of result, where it just read the result, so the result is at top of alreadyRead arguments
        //or the index lies at the end of the reader, where the result is not found, anywhere
        public SimpleCommandArgs.Argument argument;public String result; public ArgumentReader reader; int index; boolean isDefault;
        public ArgumentReader getReaderAt(){
            return new ArgumentReader(reader).setCursor(index);
        }
        //if the default value is null and you're requiring a primitive type, then it is a ValueAbsentError , not a type error
        private void checkDefaultNullPrimitive(){
            if(isDefault && result == null){
                throw new ValueAbsentError(getReaderAt(), argument);
            }
        }
        public int getInt(){
            checkDefaultNullPrimitive();
            try {
                return  Integer.parseInt(result);
            } catch (Throwable e) {
                throw new TypeError(getReaderAt(),  argument, TypeError.BaseArgumentType.INT, result);
            }
        }

        public boolean getBoolean(){
            checkDefaultNullPrimitive();
            switch (result) {
                case "true":
                    return true;
                case "false":
                    return false;
                default:
                    throw new TypeError(getReaderAt(), argument, TypeError.BaseArgumentType.BOOLEAN, result);
            }
        }

        public float getFloat(){
            checkDefaultNullPrimitive();
            try {
                return Float.parseFloat(result);
            } catch (Throwable e) {
                throw new TypeError(getReaderAt(), argument, TypeError.BaseArgumentType.FLOAT, result);
            }
        }

        public double getDouble(){
            checkDefaultNullPrimitive();
            try {
                return Double.parseDouble(result);
            } catch (Throwable e) {
                throw new TypeError(getReaderAt(), argument, TypeError.BaseArgumentType.FLOAT, result);
            }
        }

        public int clampInt(int low, int highEx){
            int val = getInt();
            if(val >= low && val < highEx){
                return val;
            }else {
                throw new ValueOutOfRangeError(getReaderAt(), argument.getArgsName(), low, highEx, val);
            }
        }
        public float clampFloat(float low, float highEx){
            float val = getFloat();
            if(val >= low && val < highEx){
                return val;
            }else {
                throw new ValueOutOfRangeError(getReaderAt(), argument.getArgsName(), low, highEx, val);
            }
        }

        public double clampDouble(double low, double highEx){
            double val = getDouble();
            if(val >= low && val < highEx){
                return val;
            } else {
                throw new ValueOutOfRangeError(getReaderAt(), argument.getArgsName(),String.valueOf( low), String.valueOf(highEx), String.valueOf(val), TypeError.BaseArgumentType.FLOAT);
            }
        }

        public String nonnullResult(){
            if(result == null){
                throw new ValueAbsentError(getReaderAt(), argument.getArgsName());
            }else {
                return result;
            }
        }

    }
    public ArgumentInputStream(
        ArgumentReader reader, SimpleCommandArgs.Argument[] args, Map<SimpleCommandArgs.Argument, ArgumentReaderResult> argsMap) {
        this.reader = new ArgumentReader(reader);
        this.arguments = args;
        this.argsMap = argsMap;
    }
    ArgumentReader reader;
    SimpleCommandArgs.Argument[] arguments;
    Map<SimpleCommandArgs.Argument, ArgumentReaderResult> argsMap;
    int i = 0;

    public boolean hasNext() {
        return i < arguments.length;
    }

    public SimpleCommandArgs.Argument nextArgument() {
        return arguments[i++];
    }

    private ArgumentReaderResult createDefault(SimpleCommandArgs.Argument argument){
        return new ArgumentReaderResult(argument, argument.getDefaultValue(), reader, reader.cursor(), true);
    }
    @Nonnull
    public ArgumentReaderResult next() {
        if (hasNext()) {
            SimpleCommandArgs.Argument arg = nextArgument();
            return argsMap.computeIfAbsent(arg, this::createDefault);
        } else {
            throw new RuntimeException("Illegal to access undeclared argument");
        }
    }
    @Nullable
    public String nextArg() {
        return next().result();
    }


    public int nextInt() {
        return next().getInt();
    }

    public boolean nextBoolean() {
        return next().getBoolean();
    }

    public double nextDouble() {
        return next().getDouble();
    }

    public float nextFloat() {
        return next().getFloat();
    }


    public int nextClampedInt(int from, int toExclude) {
        return next().clampInt(from, toExclude);
    }


    public double nextClampedDouble(double from, double toExclu) {
        return next().clampDouble(from, toExclu);
    }

    public float nextClampedFloat(float from, float to) {
        return next().clampFloat(from, to);
    }
    @Nonnull
    public String nextNonnull() {
        return next().nonnullResult();
    }

    @Nullable public List<String> getTabComplete(PlayerEntity sender) {
        for (int i = 0; i <= arguments.length; i++) {
            if (i == arguments.length || argsMap.get(arguments[i]) == null) {
                if (i == 0) {
                    return null;
                }
                final int index = i - 1;
                List<String> tablist = arguments[index].tabCompletor.apply(sender);
                tablist = tablist == null ? List.of() : tablist;
                ArgumentReaderResult result = argsMap.get(arguments[index]);
                String resultResult = result == null ? null : result.result();
                return tablist.stream()
                    .filter(s -> resultResult != null && s.contains(resultResult))
                    .toList();
            }
        }
        return null;
    }
}
