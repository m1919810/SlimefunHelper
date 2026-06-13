package me.matl114.utils.commands.params.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.InputArgument;
import me.matl114.utils.commands.params.types.ExecuteRotation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec2f;
import org.jetbrains.annotations.Nullable;

public class RotationArgumentType extends AbstractArgumentType<ExecuteRotation>
        implements ArgumentType<ExecuteRotation> {
    public RotationArgumentType(String argsName) {
        super(argsName);
    }

    @Nullable
    @Override
    public InputArgument<ExecuteRotation> consume(
            CommandExecution execution, List<InputArgument<?>> args, ArgumentReader reader) {
        if (reader.hasNext()) {
            int startIndex = reader.cursor();
            String pitchStr = reader.next();
            ExecuteRotation rotation = null;
            if (reader.hasNext()) {
                String yawStr = reader.next();
                try {
                    rotation = parse(pitchStr, yawStr);
                    return new RotationArgumentResult(Optional.ofNullable(rotation), this, reader, startIndex);
                } catch (Throwable ignored) {
                }
            }
            reader.setCursor(startIndex);
            return new RotationArgumentResult(Optional.ofNullable(defaultValue), this, reader, startIndex);
        } else {
            return new RotationArgumentResult(Optional.ofNullable(defaultValue), this, reader, reader.cursor());
        }
    }

    public boolean isPartOfRotation(String str) {
        if (str.startsWith("~")) {
            str = str.substring(1);
        }
        if (str.startsWith("-")) {
            str = str.substring(1);
        }
        try {
            if (str.isEmpty()) return true;
            Float.parseFloat(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public Stream<String> getTab(CommandExecution sender, List<InputArgument<?>> args) {
        return Stream.concat(super.getTab(sender, args), this.tabCompleteArguments(sender, args));
    }

    protected ExecuteRotation parse(String pitchStr, String yawStr) {
        int flag = 0;
        float pitch = 0.0F;
        float yaw = 0.0F;

        if (pitchStr.startsWith("~")) {
            flag |= 1;
            pitch = parseFloatAfterPrefix(pitchStr, "~");
        } else {
            pitch = Float.parseFloat(pitchStr);
        }
        if (Float.isNaN(pitch)) return null;

        if (yawStr.startsWith("~")) {
            flag |= 2;
            yaw = parseFloatAfterPrefix(yawStr, "~");
        } else {
            yaw = Float.parseFloat(yawStr);
        }
        if (Float.isNaN(yaw)) return null;

        return flag == 0 ? ExecuteRotation.fixed(pitch, yaw) : ExecuteRotation.relative(flag, pitch, yaw);
    }

    protected float parseFloatAfterPrefix(String s, String prefix) {
        String num = s.substring(prefix.length());
        if (num.isEmpty()) {
            return 0.0F;
        }
        return Float.parseFloat(num);
    }

    public Stream<String> tabCompleteArguments(CommandExecution sender, List<InputArgument<?>> args) {
        InputArgument<?> lastArg = args.get(args.size() - 1);
        if (lastArg instanceof RotationArgumentResult rotationResult) {
            String[] rangeArgs = rotationResult.getParsedArgument();
            int len = rangeArgs.length;
            if (len == 0 || rangeArgs[0].isEmpty()) {
                Vec2f rotation = currentRotation(sender);
                return Stream.of(ExecuteRotation.fixed(rotation.x, rotation.y), ExecuteRotation.relative(3, 0.0F, 0.0F))
                        .map(ExecuteRotation::asString);
            } else {
                boolean show = true;
                for (var i = 0; i < len; ++i) {
                    show &= isPartOfRotation(rangeArgs[i]);
                }
                if (!show) {
                    return Stream.empty();
                }
                int leftArg = 2 - len;
                Vec2f rotation = currentRotation(sender);
                String[] p1 = {"%.1f".formatted(rotation.x), "%.1f".formatted(rotation.y)};
                String[] p2 = {"~", "~"};
                if ((rangeArgs[len - 1].isEmpty())) {
                    int includeLen = leftArg + 1;
                    return Stream.of(p1, p2).map(s -> String.join(" ", Arrays.copyOfRange(s, 2 - includeLen, 2)));
                } else {
                    return Stream.of(p1, p2).map(s -> {
                        List<String> strs = new ArrayList<>();
                        strs.add(rangeArgs[len - 1]);
                        for (int i = 2 - leftArg; i < 2; ++i) {
                            strs.add(s[i]);
                        }
                        return String.join(" ", strs);
                    });
                }
            }
        }
        return Stream.empty();
    }

    private Vec2f currentRotation(CommandExecution sender) {
        PlayerEntity executor = sender == null ? null : sender.getExecutor();
        return executor == null ? new Vec2f(0.0F, 0.0F) : new Vec2f(executor.getPitch(), executor.getYaw());
    }
}
