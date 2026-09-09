package me.matl114.hacks.modules.task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import me.matl114.events.CombatListener;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.CombatPlayer;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.*;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CollectionUtils;
import me.matl114.utils.InventoryUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.world.World;

public class EventCommand extends BaseModule {
    public static EventCommand INSTANCE;

    public EventCommand() {
        super("EventCommand");
        bindFlag(enable);
        INSTANCE = this;
    }

    public final ModulePath cmd = makePath(Configs.MISC_CONFIG, "event-command");

    public final FlagRef enable = flagBuilder(cmd.addEnable()).build();
    public final KeyBindRef hotkey =
            toggleHotkey(cmd.addHotkey(), new MultiKeyBind(), cmd.addEnable()).build();

    private final List<String> ARGUMENTS = List.of(
            "player",
            "pos",
            "x",
            "y",
            "z",
            "world",
            "pitch",
            "yaw",
            "pop_cnt",
            "target",
            "target_x",
            "target_y",
            "target_z",
            "target_pop_cnt");
    public final NBTRef<PrimitivePairList<DispatchData<EventType>, StringFormat>> eventMap = builder(
                    cmd.add("event-map"), PrimitivePairList.<DispatchData<EventType>, StringFormat>parameter())
            .defaultValue(new PrimitivePairList<>(
                    "widget.event-command.event-type",
                    "widget.event-command.command",
                    NBTTypes.DISPATCH_DATA_TYPE.cast(),
                    NBTTypes.STRING_FORMAT_TYPE,
                    Optional.of(createTemplate()),
                    Optional.of(new StringFormat(ARGUMENTS, "")),
                    List.of()))
            .build();

    private static final String KEY_TOTEM_MIN = "widget.event-command.key.totem-min";
    private static final String KEY_TOTEM_MAX = "widget.event-command.key.totem-max";
    private static final String KEY_POP_TOTEM_MIN = "widget.event-command.key.pop-totem-min";
    private static final String KEY_POP_TOTEM_MAX = "widget.event-command.key.pop-totem-max";
    private static final String KEY_SELF_HEALTH_MIN = "widget.event-command.key.self-health-min";
    private static final String KEY_SELF_HEALTH_MAX = "widget.event-command.key.self-health-max";

    private static final String KEY_TARGET_HEALTH_MIN = "widget.event-command.key.target-health-min";
    private static final String KEY_TARGET_HEALTH_MAX = "widget.event-command.key.target-health-max";
    private static final String KEY_DELAY = "widget.event-command.key.delay";

    private DispatchData<EventType> createTemplate() {
        return new DispatchData<>(
                EventType.NONE,
                Map.of(
                        EventType.SELF_TRIGGER_TOTEM,
                                new RecordData(CollectionUtils.ofOrdered(
                                        KEY_TOTEM_MIN, 0,
                                        KEY_TOTEM_MAX, 999,
                                        KEY_POP_TOTEM_MIN, 0,
                                        KEY_POP_TOTEM_MAX, 999)),
                        EventType.OTHER_TRIGGER_TOTEM,
                                new RecordData(CollectionUtils.ofOrdered(
                                        KEY_TOTEM_MIN, 0,
                                        KEY_TOTEM_MAX, 999,
                                        KEY_POP_TOTEM_MIN, 0,
                                        KEY_POP_TOTEM_MAX, 999)),
                        EventType.SELF_DEATH,
                                new RecordData(CollectionUtils.ofOrdered(
                                        KEY_TOTEM_MIN, 0,
                                        KEY_TOTEM_MAX, 999,
                                        KEY_POP_TOTEM_MIN, 0,
                                        KEY_POP_TOTEM_MAX, 999)),
                        EventType.OTHER_DEATH,
                                new RecordData(CollectionUtils.ofOrdered(
                                        KEY_TOTEM_MIN, 0,
                                        KEY_TOTEM_MAX, 999,
                                        KEY_POP_TOTEM_MIN, 0,
                                        KEY_POP_TOTEM_MAX, 999)),
                        EventType.OTHER_ENTER_VISUAL_RANGE,
                                new RecordData(CollectionUtils.ofOrdered(
                                        KEY_TOTEM_MIN,
                                        0,
                                        KEY_TOTEM_MAX,
                                        999,
                                        KEY_POP_TOTEM_MIN,
                                        0,
                                        KEY_POP_TOTEM_MAX,
                                        999,
                                        KEY_SELF_HEALTH_MIN,
                                        0.0D,
                                        KEY_SELF_HEALTH_MAX,
                                        999.0D,
                                        KEY_TARGET_HEALTH_MIN,
                                        0.0D,
                                        KEY_TARGET_HEALTH_MAX,
                                        999.0D)),
                        EventType.OTHER_LEAVE_VISUAL_RANGE,
                                new RecordData(CollectionUtils.ofOrdered(
                                        KEY_TOTEM_MIN,
                                        0,
                                        KEY_TOTEM_MAX,
                                        999,
                                        KEY_POP_TOTEM_MIN,
                                        0,
                                        KEY_POP_TOTEM_MAX,
                                        999,
                                        KEY_SELF_HEALTH_MIN,
                                        0.0D,
                                        KEY_SELF_HEALTH_MAX,
                                        999.0D,
                                        KEY_TARGET_HEALTH_MIN,
                                        0.0D,
                                        KEY_TARGET_HEALTH_MAX,
                                        999.0D)),
                        EventType.TICK,
                                new RecordData(CollectionUtils.ofOrdered(
                                        KEY_DELAY, 40,
                                        KEY_TOTEM_MIN, 0,
                                        KEY_TOTEM_MAX, 999,
                                        KEY_POP_TOTEM_MIN, 0,
                                        KEY_POP_TOTEM_MAX, 999,
                                        KEY_SELF_HEALTH_MIN, 0.0D,
                                        KEY_SELF_HEALTH_MAX, 999.0D)),
                        EventType.WORLD_CHANGE,
                                new RecordData(CollectionUtils.ofOrdered(
                                        KEY_TOTEM_MIN, 0,
                                        KEY_TOTEM_MAX, 999,
                                        KEY_SELF_HEALTH_MIN, 0,
                                        KEY_SELF_HEALTH_MAX, 0))));
    }

    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldChange);
        registerListener(Listener.getGameJoinPoint(), this::onGameJoin);
        registerListener(CombatListener.getPlayerPopTotem(), this::onTriggerTotem);
        registerListener(CombatListener.getPlayerDeathInfo(), this::onDeath);
        registerListener(CombatListener.getPlayerEnterVisualRange(), this::onEnterVisualRange);
        registerListener(CombatListener.getPlayerLeaveVisualRange(), this::onLeftVisualRange);
        registerListener(Listener.getPostGameTick(), this::onPostTick);
    }

    private boolean testTotem(RecordData data) {
        double cnt = InventoryUtils.computePlayerInventory(
                (v) -> v.getItem() == Items.TOTEM_OF_UNDYING ? (double) v.getCount() : null, false);
        return cnt >= data.<Integer>get(KEY_TOTEM_MIN, 0) && cnt <= data.<Integer>get(KEY_TOTEM_MAX, Integer.MAX_VALUE);
    }

    private boolean testPopTotem(RecordData data, PlayerEntity player) {
        int popCnt = PlayerStateManager.INSTANCE.getPlayerPopCount(player);
        return popCnt >= data.<Integer>get(KEY_POP_TOTEM_MIN, 0)
                && popCnt <= data.<Integer>get(KEY_POP_TOTEM_MAX, Integer.MAX_VALUE);
    }

    private boolean testSelfHealth(RecordData data) {
        double health = mc.player.getHealth();
        return health >= data.get(KEY_SELF_HEALTH_MIN, Double.MIN_VALUE)
                && health <= data.get(KEY_SELF_HEALTH_MAX, Double.MAX_VALUE);
    }

    private boolean testTargetHealth(RecordData data, PlayerEntity player) {
        double health = player.getHealth();
        return health >= data.get(KEY_TARGET_HEALTH_MIN, Double.MIN_VALUE)
                && health <= data.get(KEY_TARGET_HEALTH_MAX, Double.MAX_VALUE);
    }

    private boolean testDelay(RecordData data) {
        Integer intValue = data.get(KEY_DELAY);
        if (intValue == null) {
            return true;
        } else {
            return Tasks.getTick() % intValue == 0;
        }
    }

    public void onEventType(EventType type, Consumer<StringFormat> commandSender) {
        eventMap.get().list().forEach((s) -> {
            if (s.getFirst().getType() == type) {
                commandSender.accept(s.getSecond());
            }
        });
    }

    public void onEvent(EventType type, PlayerEntity target) {
        onEventType(
                type,
                (recordData) -> {
                    return testDelay(recordData)
                            && testTotem(recordData)
                            && testPopTotem(recordData, target != null ? target : mc.player)
                            && testSelfHealth(recordData)
                            && (target == null || (testTargetHealth(recordData, target)));
                },
                target == null ? this::executeDelayed : (s) -> executeDelayed(s, target));
    }

    public void onEventType(EventType type, Predicate<RecordData> predicate, Consumer<StringFormat> commandSender) {
        eventMap.get().list().forEach((s) -> {
            if (s.getFirst().getType() == type && predicate.test(s.getFirst().getDispatch())) {
                commandSender.accept(s.getSecond());
            }
        });
    }

    private HashMap<String, String> createContext() {
        return new HashMap<>(Map.of(
                "player", mc.player.getNameForScoreboard(),
                "pos", "%.2f %.2f %.2f".formatted(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                "x", "%.2f".formatted(mc.player.getX()),
                "y", "%.2f".formatted(mc.player.getY()),
                "z", "%.2f".formatted(mc.player.getZ()),
                "pitch", "%.2f".formatted(mc.player.getPitch()),
                "yaw", "%.2f".formatted(mc.player.getYaw()),
                "world", mc.world.getRegistryKey().getValue().getPath(),
                "pop_cnt", String.valueOf(PlayerStateManager.INSTANCE.getPlayerPopCount(mc.player))));
    }

    private void appendTargetContext(HashMap<String, String> context, PlayerEntity player) {
        context.put("target", player.getNameForScoreboard());
        context.put("target_x", "%.2f".formatted(player.getX()));
        context.put("target_y", "%.2f".formatted(player.getY()));
        context.put("target_z", "%.2f".formatted(player.getZ()));
        context.put("target_pop_cnt", String.valueOf(PlayerStateManager.INSTANCE.getPlayerPopCount(player)));
    }

    private void executeDelayed(StringFormat s) {
        executeDelayed(s, createContext());
    }

    private void executeDelayed(StringFormat s, PlayerEntity target) {
        var context = createContext();
        appendTargetContext(context, target);
        executeDelayed(s, context);
    }

    private void executeDelayed(StringFormat s, Map<String, String> map) {
        ChatTasks.sayMessage(s.format(map), false);
    }

    //    public void onDeath(Event<CombatPlayer> eventRespawn) {
    //        if (enable.get()) {
    //            var respawn = eventRespawn.context();
    //            // death
    //            if (respawn.flag() == 0 || respawn.flag() == 1) {
    //                onEventType(EventType.RESPAWN, this::executeDelayed);
    //            }
    //        }
    //    }
    //
    public void onWorldChange(Event<World> event) {
        if (enable.get()) {
            onEvent(EventType.WORLD_CHANGE, null);
        }
    }
    //
    public void onGameJoin(Event<ClientPlayerEntity> event) {
        if (enable.get()) {
            onEvent(EventType.JOIN_GAME, null);
        }
    }

    public void onPostTick(Event<ClientPlayerEntity> eventPost) {
        if (enable.get()) {
            onEvent(EventType.TICK, null);
        }
    }

    public void onTriggerTotem(Event<CombatPlayer> event) {
        if (checkNull()) return;
        if (enable.get()) {
            PlayerEntity pl = event.context.player();
            if (pl == mc.player) {
                onEvent(EventType.SELF_TRIGGER_TOTEM, null);
            } else {
                onEvent(EventType.OTHER_TRIGGER_TOTEM, pl);
            }
        }
    }

    public void onDeath(Event<CombatPlayer> event) {
        if (checkNull()) return;
        if (enable.get()) {
            PlayerEntity pl = event.context.player();
            if (pl == mc.player) {
                onEvent(EventType.SELF_DEATH, null);
            } else {
                onEvent(EventType.OTHER_DEATH, pl);
            }
        }
    }

    public void onEnterVisualRange(Event<CombatPlayer> event) {
        if (enable.get() && event.context.player() != mc.player) {
            onEvent(EventType.OTHER_ENTER_VISUAL_RANGE, event.context.player());
        }
    }

    public void onLeftVisualRange(Event<CombatPlayer> event) {
        if (enable.get() && event.context.player() != mc.player) {
            onEvent(EventType.OTHER_LEAVE_VISUAL_RANGE, event.context.player());
        }
    }

    //
    //    public void onTriggerTotem(Event<EntityStatusS2CPacket> event) {
    //        if (checkNull()) return;
    //        if (enable.get()
    //                && event.context.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING
    //                && event.context.getEntity(mc.world) == mc.player) {
    //            onEventType(EventType.TRIGGER_TOTEM, this::executeDelayed);
    //        }
    //    }

    public enum EventType implements ConfigEnum {
        NONE,
        WORLD_CHANGE,
        JOIN_GAME,
        TICK,
        SELF_TRIGGER_TOTEM,
        OTHER_TRIGGER_TOTEM,
        SELF_DEATH,
        OTHER_DEATH,
        OTHER_ENTER_VISUAL_RANGE,
        OTHER_LEAVE_VISUAL_RANGE;

        @Override
        public String getConfigEnumType() {
            return "event_command_event_type";
        }
    }
}
