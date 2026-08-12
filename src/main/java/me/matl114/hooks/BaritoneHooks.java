package me.matl114.hooks;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.event.events.ChatEvent;
import baritone.api.event.events.RotationMoveEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.*;
import baritone.api.utils.BetterBlockPos;
import baritone.process.ElytraProcess;
import baritone.process.elytra.ElytraBehavior;
import java.awt.*;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import me.matl114.events.Event;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.annotations.Cancelable;
import me.matl114.events.annotations.ExtraArgs;
import me.matl114.events.annotations.Modifiable;
import me.matl114.events.channels.EventChannel;
import me.matl114.hacks.utils.config.*;
import me.matl114.hacks.utils.move.goal.*;
import me.matl114.hacks.utils.move.goal.GoalNear;
import me.matl114.hooks.impl.baritone.*;
import me.matl114.utils.config.ValueAccessor;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;

public abstract class BaritoneHooks implements IHooks {

    private static BaritoneHooks instance;

    public static BaritoneHooks getInstance() {
        if (instance == null) {
            try {
                try {
                    instance = new MeteorBaritoneImpl();
                } catch (Throwable e) {
                    instance = new UnknownBaritoneImpl();
                }
            } catch (Throwable e) {
                instance = new Default();
            }
        }
        return instance;
    }

    public static List<BlockPos> currentNetherElytraPath = List.of();

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public abstract boolean handleCommand(String command);

    public abstract Map<String, ValueAccessor<?>> getAllSettings();

    public abstract <T> ValueAccessor<T> getSetting(String name);

    public abstract boolean isBaritoneElytraProcessing();

    public abstract boolean isBaritonePathing();

    public abstract void setBaritoneNetherPathSupplier(Supplier<List<BlockPos>> blockPos);

    public abstract void updateBaritoneNetherPath();

    public final List<BlockPos> getCurrentNetherPath() {
        return currentNetherElytraPath;
    }

    public abstract void setBaritoneCurrentElytraDestination(@Nullable BlockPos pos);

    public abstract void setBaritoneCurrentPathingDestination(@Nullable BlockPos pos);

    public abstract Vec2f getBaritoneCurrentMoveRot(ClientPlayerEntity player);

    public abstract void setBaritoneCurrentGoal(IPathGoal goal);

    public abstract boolean isBaritoneGoalPathingActive();

    public abstract void cancelBaritone();

    public abstract String getCommandPrefix();

    public abstract boolean isBaritoneAPISupported();

    public abstract boolean isBaritoneVersionSupported();

    @Getter
    @Cancelable
    @ExtraArgs({BaritoneLanding.class})
    public static final EventChannel<BaritoneFuture> landingEvent = new EventChannel<>();

    @Getter
    @Broadcast
    public static final EventChannel<BlockPos> elytraPathingEvent = new EventChannel<>();

    @Getter
    @Modifiable
    public static final EventChannel<Vec2f> moveRotEvent = new EventChannel<>();

    public abstract static class AbstractBaritoneVersion extends BaritoneHooks {
        final Settings settings;
        final Map<String, ValueAccessor<?>> settingsMap = new LinkedHashMap<>();
        final ValueAccessor<String> prefix;

        public void onMoveRot(RotationMoveEvent event) {
            if (event.getType() == RotationMoveEvent.Type.MOTION_UPDATE && !moveRotEvent.isEmpty()) {
                Vec2f vec2f = new Vec2f(event.getPitch(), event.getYaw());
                var eventMe = new Event<>(vec2f, false, true);
                moveRotEvent.handleValue(eventMe);
                if (vec2f != eventMe.context) {
                    event.setPitch(eventMe.context.x);
                    event.setYaw(eventMe.context.y);
                }
            }
        }

        public AbstractBaritoneVersion() {
            Class<?> checkClass = BaritoneAPI.class;
            settings = BaritoneAPI.getSettings();
            buildMap();
            prefix = getSetting("prefix");
            BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getGameEventHandler()
                    .registerEventListener(new AbstractGameEventListener() {
                        @Override
                        public void onPlayerRotationMove(RotationMoveEvent rotationMoveEvent) {
                            AbstractBaritoneVersion.this.onMoveRot(rotationMoveEvent);
                        }
                    });
        }

        private void buildMap() {
            for (Settings.Setting re : settings.allSettings) {
                String name = re.getName();
                ValueAccessor accessor = ValueAccessor.of(() -> re.value, (va) -> re.value = va);
                settingsMap.put(name, accessor);
            }
            for (var field : settings.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                String string = field.getName();
                try {
                    Object value = field.get(settings);
                    if (value instanceof Settings.Setting<?> setting) {
                        var re = setting.value;
                        if (re == null) {
                            settingsMap.remove(string);
                        } else if (re instanceof Boolean || re instanceof Number || re instanceof String) {
                            continue;
                        } else if (re instanceof Color) {
                            Settings.Setting<Color> colorValue = (Settings.Setting<Color>) setting;
                            ValueAccessor<?> accessor = ValueAccessor.of(
                                    () -> new WrapColor(colorValue.value),
                                    (v) -> colorValue.value = new Color(v.asRGB()));
                            settingsMap.put(string, accessor);
                        } else if (setting.value instanceof List) {
                            Type listType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                            Type type = ((ParameterizedType) listType).getActualTypeArguments()[0];
                            if (type == Block.class) {
                                Settings.Setting<List<Block>> blockValue = (Settings.Setting<List<Block>>) setting;
                                ValueAccessor<EntrySet<Block>> accessor = ValueAccessor.of(
                                        () -> new EntrySet<>(Registries.BLOCK, blockValue.value),
                                        (lst) -> blockValue.value = lst.list());
                                settingsMap.put(string, accessor);
                            } else if (type == Item.class) {
                                Settings.Setting<List<Item>> blockValue = (Settings.Setting<List<Item>>) setting;
                                ValueAccessor<EntrySet<Item>> accessor = ValueAccessor.of(
                                        () -> new EntrySet<>(Registries.ITEM, blockValue.value),
                                        (lst) -> blockValue.value = lst.list());
                                settingsMap.put(string, accessor);
                            } else {
                                settingsMap.remove(string);
                            }
                        } else if (setting.value instanceof Map) {
                            // ...
                            if (setting == settings.buildValidSubstitutes || setting == settings.buildSubstitutes) {
                                Settings.Setting<Map<Block, List<Block>>> settingMapList =
                                        (Settings.Setting<Map<Block, List<Block>>>) setting;
                                ValueAccessor<PrimitiveMap<Holder<Block>, PrimitiveList<Holder<Block>>>> wtf =
                                        ValueAccessor.of(
                                                () -> {
                                                    Map<Block, List<Block>> map = settingMapList.value;
                                                    Map<Holder<Block>, PrimitiveList<Holder<Block>>> map2 =
                                                            map.entrySet().stream()
                                                                    .collect(Collectors.toMap(
                                                                            s -> Holder.of(
                                                                                    Registries.BLOCK, s.getKey()),
                                                                            s -> new PrimitiveList<>(
                                                                                    NBTTypes.HOLDER_TYPE.cast(),
                                                                                    s.getValue().stream()
                                                                                            .map(sss -> Holder.of(
                                                                                                    Registries.BLOCK,
                                                                                                    sss))
                                                                                            .toList(),
                                                                                    Holder.of(Registries.BLOCK, null)),
                                                                            (k, v) -> v));
                                                    return new PrimitiveMap<>(
                                                            NBTTypes.HOLDER_TYPE.cast(),
                                                            NBTTypes.PRIMITIVE_LIST_TYPE.cast(),
                                                            map2,
                                                            Holder.of(Registries.BLOCK, null),
                                                            new PrimitiveList<>(
                                                                    NBTTypes.HOLDER_TYPE.cast(),
                                                                    List.of(),
                                                                    Holder.of(Registries.BLOCK, null)));
                                                },
                                                (v) -> {
                                                    Map<Holder<Block>, PrimitiveList<Holder<Block>>> map3 = v.map();
                                                    settingMapList.value = map3.entrySet().stream()
                                                            .filter(s ->
                                                                    s.getKey().entry() != null)
                                                            .collect(Collectors.toMap(
                                                                    s -> s.getKey()
                                                                            .entry(),
                                                                    s -> s.getValue().list().stream()
                                                                            .filter(ss -> ss.entry() != null)
                                                                            .map(Holder::entry)
                                                                            .toList(),
                                                                    (k, v2) -> v2));
                                                });
                                settingsMap.put(string, wtf);
                            } else {
                                settingsMap.remove(string);
                            }
                        } else {
                            settingsMap.remove(string);
                        }
                    }
                } catch (Throwable e) {
                    settingsMap.remove(string);
                }
            }
        }

        @Override
        public Map<String, ValueAccessor<?>> getAllSettings() {
            return Collections.unmodifiableMap(settingsMap);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean handleCommand(String command) {
            String pfx = BaritoneAPI.getSettings().prefix.value;
            command = command.startsWith(pfx) ? command : (pfx + command);
            ChatEvent var4 = new ChatEvent(command);
            IBaritone var3;
            if ((var3 = BaritoneAPI.getProvider().getBaritoneForPlayer(mc.player)) != null) {
                var3.getGameEventHandler().onSendChatMessage(var4);
                if (var4.isCancelled()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public <T> ValueAccessor<T> getSetting(String name) {
            return (ValueAccessor<T>) settingsMap.get(name);
        }

        public String getCommandPrefix() {
            return prefix == null ? "#" : prefix.getValue();
        }

        public boolean isBaritoneAPISupported() {
            return true;
        }

        @Override
        public boolean isBaritoneElytraProcessing() {
            return BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getElytraProcess()
                    .isActive();
        }

        public boolean isBaritonePathing() {
            return BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getPathingBehavior()
                    .isPathing();
        }

        @Override
        public Vec2f getBaritoneCurrentMoveRot(ClientPlayerEntity player) {
            RotationMoveEvent moveEvent =
                    new RotationMoveEvent(RotationMoveEvent.Type.MOTION_UPDATE, player.getYaw(), player.getPitch());
            BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onPlayerRotationMove(moveEvent);
            return new Vec2f(moveEvent.getPitch(), moveEvent.getYaw());
        }

        @Override
        public void setBaritoneCurrentElytraDestination(BlockPos pos) {
            if (pos != null) {
                BaritoneAPI.getProvider()
                        .getPrimaryBaritone()
                        .getElytraProcess()
                        .pathTo(pos);
            } else {
                BaritoneAPI.getProvider()
                        .getPrimaryBaritone()
                        .getElytraProcess()
                        .onLostControl();
            }
        }

        @Override
        public void cancelBaritone() {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }

        @Override
        public void updateBaritoneNetherPath() {
            BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().resetState();
        }

        @Override
        public void setBaritoneCurrentPathingDestination(BlockPos pos) {
            if (pos != null) {
                BaritoneAPI.getProvider()
                        .getPrimaryBaritone()
                        .getCustomGoalProcess()
                        .setGoalAndPath(new GoalBlock(pos));
            } else {
                BaritoneAPI.getProvider()
                        .getPrimaryBaritone()
                        .getCustomGoalProcess()
                        .onLostControl();
            }
        }

        @Override
        public void setBaritoneCurrentGoal(IPathGoal goal) {
            if (goal == null) {
                BaritoneAPI.getProvider()
                        .getPrimaryBaritone()
                        .getCustomGoalProcess()
                        .onLostControl();
            } else {
                BaritoneAPI.getProvider()
                        .getPrimaryBaritone()
                        .getCustomGoalProcess()
                        .setGoalAndPath(convertGoal(goal));
            }
        }

        public Goal convertGoal(IPathGoal goal) {
            return switch (goal) {
                case GoalBlockPos pos -> new GoalBlock(pos.pos());
                case GoalNear near -> new GoalNearManhattan(near.center(), near.radius());
                case GoalNearBlockPos near -> new GoalGetToBlock(near.pos());
                case GoalList list -> new GoalComposite(
                        list.goals().stream().map(this::convertGoal).toArray(Goal[]::new));
                case GoalFollow entity -> new GoalDynamicGoal(
                        entity.entity()::getPos,
                        0.3
                                + (entity.entity()
                                                .getDimensions(entity.entity().getPose())
                                                .width()
                                        / 2));
                case GoalDynamic dynamic -> new GoalDynamicGoal(dynamic.supplier(), dynamic.radius());
                case GoalDirection direction -> new GoalYawDirection(mc.player.getBlockPos(), direction.yaw());
            };
        }

        @Override
        public boolean isBaritoneGoalPathingActive() {
            return BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getCustomGoalProcess()
                    .isActive();
        }
    }

    public static class MeteorBaritoneImpl extends AbstractBaritoneVersion {

        public static Supplier<List<BlockPos>> netherPathSupplier;

        public MeteorBaritoneImpl() {
            Class<?> clazz = ElytraProcess.class;
            clazz = ElytraBehavior.class;
            clazz = BetterBlockPos.class;
        }

        @Override
        public boolean isBaritoneVersionSupported() {
            return true;
        }

        @Override
        public void setBaritoneNetherPathSupplier(Supplier<List<BlockPos>> blockPos) {
            netherPathSupplier = blockPos;
        }
    }

    public static class UnknownBaritoneImpl extends AbstractBaritoneVersion {
        public UnknownBaritoneImpl() {}

        @Override
        public void setBaritoneNetherPathSupplier(Supplier<List<BlockPos>> blockPos) {}

        @Override
        public boolean isBaritoneVersionSupported() {
            return false;
        }
    }

    public static class Default extends BaritoneHooks {

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public boolean handleCommand(String command) {
            return false;
        }

        @Override
        public Map<String, ValueAccessor<?>> getAllSettings() {
            return Map.of();
        }

        @Override
        public <T> ValueAccessor<T> getSetting(String name) {
            return null;
        }

        @Override
        public boolean isBaritoneElytraProcessing() {
            return false;
        }

        @Override
        public boolean isBaritonePathing() {
            return false;
        }

        @Override
        public void setBaritoneNetherPathSupplier(Supplier<List<BlockPos>> blockPos) {}

        @Override
        public void updateBaritoneNetherPath() {}

        @Override
        public void setBaritoneCurrentElytraDestination(BlockPos pos) {}

        @Override
        public void setBaritoneCurrentPathingDestination(BlockPos pos) {}

        @Override
        public Vec2f getBaritoneCurrentMoveRot(ClientPlayerEntity player) {
            return new Vec2f(player.getPitch(), player.getYaw());
        }

        @Override
        public void setBaritoneCurrentGoal(IPathGoal goal) {}

        @Override
        public boolean isBaritoneGoalPathingActive() {
            return false;
        }

        @Override
        public void cancelBaritone() {}

        @Override
        public String getCommandPrefix() {
            return "#";
        }

        @Override
        public boolean isBaritoneAPISupported() {
            return false;
        }

        @Override
        public boolean isBaritoneVersionSupported() {
            return false;
        }
    }
}
