package me.matl114.utils.commands.params.impl;

import java.util.List;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.types.EntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class EntityArgumentResult extends AbstractArgumentResult<EntitySelector> {
    private final String rawString;

    public EntityArgumentResult(
            EntitySelector selector,
            ArgumentType<EntitySelector> type,
            ArgumentReader reader,
            int startIndex,
            boolean parseSuccess) {
        super(selector, type, reader, startIndex);
        this.parseSuccess = parseSuccess;
        this.rawString = selector == null ? null : String.join(" ", reader.getArgsInRange(startIndex, endIndex));
    }

    @Override
    public String resultAsString() {
        return rawString;
    }

}