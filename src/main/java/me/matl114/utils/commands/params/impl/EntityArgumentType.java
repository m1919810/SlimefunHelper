package me.matl114.utils.commands.params.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.api.ArgumentType;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.InputArgument;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

public class EntityArgumentType extends AbstractArgumentType<EntityArgumentResult.EntitySelector>
        implements ArgumentType<EntityArgumentResult.EntitySelector> {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final List<String> SELECTOR_TABS = List.of("@s", "@p", "@a", "@e");

    public EntityArgumentType(String argsName) {
        super(argsName);
    }

    @Override
    public Stream<String> getTab(CommandExecution sender, List<InputArgument<?>> args) {
        return Stream.concat(super.getTab(sender, args), filterTab(getEntityTabs(), args));
    }

    @Nullable
    @Override
    public InputArgument<EntityArgumentResult.EntitySelector> consume(
            CommandExecution execution, List<InputArgument<?>> args, ArgumentReader reader) {
        if (!reader.hasNext()) {
            return new EntityArgumentResult(null, this, reader, reader.cursor(), false);
        }
        int startIndex = reader.cursor();
        String raw = reader.next();
        EntityArgumentResult.EntitySelector selector = parse(raw);
        if (selector == null) {
            reader.setCursor(startIndex);
            return new EntityArgumentResult(null, this, reader, startIndex, false);
        }
        return new EntityArgumentResult(selector, this, reader, startIndex, true);
    }

    public static Stream<String> getEntityTabs() {
        Stream<String> players = mc.world != null ? EntityUtils.getWorldPlayerNames(false) : Stream.empty();
        Stream<String> crosshair = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY
                ? Stream.of(((EntityHitResult) mc.crosshairTarget).getEntity().getUuidAsString())
                : Stream.empty();
        return Stream.of(SELECTOR_TABS.stream(), players, crosshair).flatMap(Function.identity());
    }

    public static EntityArgumentResult.EntitySelector parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return switch (raw) {
            case "@s" -> new Selector(raw, EntityArgumentType::selfEntity);
            case "@p" -> new Selector(raw, EntityArgumentType::nearestPlayer);
            case "@a" -> new Selector(raw, EntityArgumentType::allPlayers);
            case "@e" -> new Selector(raw, EntityArgumentType::allEntities);
            default -> new Selector(raw, execution -> resolveNamedOrUuid(raw));
        };
    }

    private static List<Entity> selfEntity(CommandExecution execution) {
        Entity executor = execution.getExecutor();
        return executor == null || executor.isRemoved() ? List.of() : List.of(executor);
    }

    private static List<Entity> nearestPlayer(CommandExecution execution) {
        if (mc.world == null) {
            return List.of();
        }
        var center = execution.getExecutor() == null ? null : execution.getExecutor().getPos();
        if (center == null) {
            return mc.world.getPlayers().stream().map(Entity.class::cast).findFirst().stream().toList();
        }
        return mc.world.getPlayers().stream()
                .min(Comparator.comparingDouble(player -> player.getPos().squaredDistanceTo(center)))
                .map(Entity.class::cast)
                .stream()
                .toList();
    }

    private static List<Entity> allPlayers(CommandExecution execution) {
        return mc.world == null ? List.of() : mc.world.getPlayers().stream().map(Entity.class::cast).toList();
    }

    private static List<Entity> allEntities(CommandExecution execution) {
        return mc.world == null ? List.of() : StreamSupport.stream(mc.world.getEntities().spliterator(), false).toList();
    }

    private static List<Entity> resolveNamedOrUuid(String raw) {
        if (mc.world == null) {
            return List.of();
        }
        Entity entity = null;
        if (raw.length() > 16) {
            try {
                UUID uuid = UUID.fromString(raw);
                entity = mc.world.getEntityLookup().get(uuid);
            } catch (Throwable ignored) {
            }
        }
        if (entity == null) {
            entity = EntityUtils.getPlayerByName(raw);
        }
        return entity == null ? List.of() : List.of(entity);
    }

    public record Selector(String raw, Function<CommandExecution, List<Entity>> resolver)
            implements EntityArgumentResult.EntitySelector {
        @Override
        public List<Entity> resolve(CommandExecution execution) {
            return resolver.apply(execution).stream()
                    .filter(entity -> entity != null && !entity.isRemoved())
                    .toList();
        }

        @Override
        public String asString() {
            return raw;
        }
    }
}