package me.matl114.matlib.utils.command.params.impl;

import me.matl114.matlib.utils.command.params.ArgumentReader;
import me.matl114.matlib.utils.command.params.api.ArgumentType;
import me.matl114.matlib.utils.command.params.api.InputArgument;
import me.matl114.matlib.utils.command.params.types.ExecutePos;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class PosArgumentType extends AbstractArgumentType<Optional<ExecutePos>> implements ArgumentType<Optional<ExecutePos>> {
    public PosArgumentType(String argsName) {
        super(argsName);
    }

    @Nullable
    @Override
    public InputArgument<Optional<ExecutePos>> consume(ArgumentReader reader) {
        if(reader.hasNext()){
            int startIndex = reader.cursor();
            String xStr = reader.next();
            ExecutePos pos = null;
            if(reader.hasNext()){
                String yStr = reader.next();
                if(reader.hasNext()){
                    String zStr = reader.next();
                    try{
                        pos = parse(xStr, yStr, zStr);
                    }catch (Throwable e){
                    }
                }
            }
            return new PosArgumentResult(Optional.ofNullable(pos), this, reader, startIndex);
        }else{
            return new PosArgumentResult(defaultValue, this, reader, reader.cursor());
        }
    }

    @Override
    public Stream<String> getTab(CommandSender sender, List<InputArgument<?>> args) {
        return Stream.concat( super.getTab(sender, args), this.tabCompleteArguments(sender, args));
    }

    private ExecutePos parse(String xStr, String yStr, String zStr) {
        // 检查是否采用视线坐标系（以 ^ 开头）
        if (xStr.startsWith("^")) {
            // 必须三个都以 ^ 开头
            if (!yStr.startsWith("^") || !zStr.startsWith("^")) {
                return null;
            }
            double x = parseDoubleAfterPrefix(xStr, "^");
            double y = parseDoubleAfterPrefix(yStr, "^");
            double z = parseDoubleAfterPrefix(zStr, "^");
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
                return null;
            }
            return new ExecutePos.RelativeRotateXYZ(new Vector3d(x, y, z));
        } else {
            // 普通相对/绝对坐标系
            int flag = 0;
            double x = 0, y = 0, z = 0;

            // 解析 X
            if (xStr.startsWith("~")) {
                flag |= 1;
                String num = xStr.substring(1);
                if (!num.isEmpty()) {
                    double val = Double.parseDouble(num);
                    if (Double.isNaN(val)) return null;
                    x = val;
                }
            } else {
                double val = Double.parseDouble(xStr);
                if (Double.isNaN(val)) return null;
                x = val;
            }

            // 解析 Y
            if (yStr.startsWith("~")) {
                flag |= 2;
                String num = yStr.substring(1);
                if (!num.isEmpty()) {
                    double val = Double.parseDouble(num);
                    if (Double.isNaN(val)) return null;
                    y = val;
                }
            } else {
                double val = Double.parseDouble(yStr);
                if (Double.isNaN(val)) return null;
                y = val;
            }

            // 解析 Z
            if (zStr.startsWith("~")) {
                flag |= 4;
                String num = zStr.substring(1);
                if (!num.isEmpty()) {
                    double val = Double.parseDouble(num);
                    if (Double.isNaN(val)) return null;
                    z = val;
                }
            } else {
                double val = Double.parseDouble(zStr);
                if (Double.isNaN(val)) return null;
                z = val;
            }

            return new ExecutePos.RelativeXYZ(flag, new Vector3d(x, y, z));
        }
    }
    /**
     * 去掉前缀后解析 double，空字符串视为 0.0
     */
    private double parseDoubleAfterPrefix(String s, String prefix) {
        String num = s.substring(prefix.length());
        if (num.isEmpty()) {
            return 0.0;
        }
        return Double.parseDouble(num);
    }


    public Stream<String> tabCompleteArguments(CommandSender sender, List<InputArgument<?>> args) {
        InputArgument<?> lastArg = args.get(args.size() - 1);
        if(lastArg instanceof PosArgumentResult posResult){
            String[] rangeArgs = posResult.getParsedArgument();
            int len = rangeArgs.length;
            if(len == 0 || rangeArgs[0].isEmpty()){
                return Stream.of(
                    new ExecutePos.Fixed(ExecutePos.getExecutorPos(sender)),
                    new ExecutePos.RelativeXYZ(7, new Vector3d(0,0,0)),
                    new ExecutePos.RelativeRotateXYZ(new Vector3d(0,0,0))
                ).map(ExecutePos::asString);
            }else{
                String firstArg = rangeArgs[0];
                int leftArg = 3 - len;

                if(firstArg.startsWith("^")){
                    boolean show = true;
                    for (var i = 1; i < len - 1; ++ i){
                        show &= rangeArgs[i].startsWith("^");
                    }
                    show &= rangeArgs[len - 1].startsWith("^") || rangeArgs[len - 1].isEmpty();
                    if(show){
                        String repeat = " ^".repeat(leftArg);
                        repeat =  ((rangeArgs[len - 1].isEmpty()) ? "^" : rangeArgs[len - 1]) + repeat;
                        return Stream.of(repeat);
                    }else{
                        return Stream.empty();
                    }
                }else {
                    boolean show = true;
                    for (var i = 1; i < len - 1; ++ i){
                        show &= !rangeArgs[i].startsWith("^");
                    }
                    if(show){
                        Vector3d exePos = ExecutePos.getExecutorPos(sender);
                        String exeX = "%.1f".formatted(exePos.x);
                        String exeY = "%.1f".formatted(exePos.y);
                        String exeZ = "%.1f".formatted(exePos.z);
                        String[] p1 = {exeX, exeY, exeZ};
                        String[] p2 = {"~", "~", "~"};
                        if((rangeArgs[len - 1].isEmpty())){
                            int includeLen = leftArg + 1;
                            return Stream.of(p1, p2).map(s -> String.join(" ", Arrays.copyOfRange(s, 3 - includeLen, 3)));
                        }else{

                            return Stream.of(p1, p2).map(s ->{
                                    List<String> strs = new ArrayList<>();
                                    strs.add(rangeArgs[len - 1]);
                                    for(int i = 3 - leftArg;  i < 3 ; ++i){
                                        strs.add(s[i]);
                                    };
                                    return String.join(" ", strs);
                                });
                        }
                    }else{
                        return Stream.empty();
                    }

                }
            }
        }
        return Stream.empty();
    }




}
