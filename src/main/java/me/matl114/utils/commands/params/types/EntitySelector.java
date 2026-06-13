package me.matl114.utils.commands.params.types;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import me.matl114.utils.commands.params.api.CommandExecution;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public interface EntitySelector {
    List<Entity> resolve(CommandExecution execution);

    String asString();

    default Entity random(CommandExecution execution) {
        List<Entity> entities = resolve(execution).stream()
                .filter(entity -> entity != null && !entity.isRemoved())
                .toList();
        return entities.isEmpty()
                ? null
                : entities.get(ThreadLocalRandom.current().nextInt(entities.size()));
    }

    default Entity first(CommandExecution execution) {
        return resolve(execution).stream()
                .filter(entity -> entity != null && !entity.isRemoved())
                .findFirst()
                .orElse(null);
    }

    default Vec3d pos(CommandExecution execution) {
        Entity entity = random(execution);
        return entity == null ? null : entity.getPos();
    }
}
