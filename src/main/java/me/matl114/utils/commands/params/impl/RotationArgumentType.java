package me.matl114.utils.commands.params.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import me.matl114.hacks.MovTasks;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.InputArgument;
import me.matl114.utils.commands.params.types.EntitySelector;
import me.matl114.utils.commands.params.types.ExecutePos;
import me.matl114.utils.commands.params.types.ExecuteRotation;
import org.jetbrains.annotations.Nullable;

public class RotationArgumentType extends AbstractArgumentType<ExecuteRotation>
        implements ArgumentType<ExecuteRotation> {
    private static final List<String> TYPE_TABS = List.of("look", "pos", "entity");

    public RotationArgumentType(String argsName) {
        super(argsName);
    }

    @Override
    public Stream<String> getTab(CommandExecution sender, List<InputArgument<?>> args) {
        return Stream.concat(super.getTab(sender, args), filterTab(TYPE_TABS.stream(), args));
    }

    @Nullable
    @Override
    public InputArgument<ExecuteRotation> consume(
            CommandExecution execution, List<InputArgument<?>> args, ArgumentReader reader) {
        if (!reader.hasNext()) {
            return new RotationArgumentResult(null, this, reader, reader.cursor(), false);
        }
        int startIndex = reader.cursor();
        String head = reader.next();
        ExecuteRotation rotation = switch (head) {
            case "look" -> ExecuteRotation.look();
            case "pos" -> parsePos(execution, args, reader);
            case "entity" -> parseEntity(execution, args, reader);
            default -> parseFixed(head, reader);
        };
        if (rotation == null) {
            reader.setCursor(startIndex);
            return new RotationArgumentResult(null, this, reader, startIndex, false);
        }
        return new RotationArgumentResult(rotation, this, reader, startIndex, true);
    }

    private ExecuteRotation parsePos(CommandExecution execution, List<InputArgument<?>> args, ArgumentReader reader) {
        int startIndex = reader.cursor();
        var posResult = new MovTasks.TpaAndPosArgumentType("rotation_pos").consume(execution, args, reader);
        if (posResult == null || !posResult.isParseSuccess() || posResult.result() == null) {
            reader.setCursor(startIndex);
            return null;
        }
        return ExecuteRotation.pos(posResult.result());
    }

    private ExecuteRotation parseEntity(CommandExecution execution, List<InputArgument<?>> args, ArgumentReader reader) {
        int startIndex = reader.cursor();
        var entityResult = new EntityArgumentType("rotation_entity").consume(execution, args, reader);
        if (entityResult == null || !entityResult.isParseSuccess() || entityResult.result() == null) {
            reader.setCursor(startIndex);
            return null;
        }
        return ExecuteRotation.entity(entityResult.result());
    }

    private ExecuteRotation parseFixed(String yawRaw, ArgumentReader reader) {
        if (!reader.hasNext()) {
            return null;
        }
        String pitchRaw = reader.next();
        try {
            return ExecuteRotation.fixed(Float.parseFloat(yawRaw), Float.parseFloat(pitchRaw));
        } catch (Throwable ignored) {
            return null;
        }
    }
}