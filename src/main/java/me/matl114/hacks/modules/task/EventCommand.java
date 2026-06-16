package me.matl114.hacks.modules.task;

import java.util.List;
import java.util.function.Consumer;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.EnumPrimitiveList;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.world.World;

public class EventCommand extends BaseModule {

    public EventCommand() {}

    public final ModulePath cmd = makePath(Configs.MISC_CONFIG, "event-command");

    public final FlagRef enable = flagBuilder(cmd.addEnable()).build();
    public final KeyBindRef hotkey =
            toggleHotkey(cmd.addHotkey(), new MultiKeyBind(), cmd.addEnable()).build();

    public final NBTRef<EnumPrimitiveList<EventType, String>> eventMap = builder(
                    cmd.add("event-map"),
                    NBTType.<EnumPrimitiveList<EventType, String>>parameter(EnumPrimitiveList.class))
            .defaultValue(new EnumPrimitiveList<>(EventType.class, NBTTypes.STRING_TYPE, List.of()))
            .build();

    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPostHandlePoint().getChannel(PlayerRespawnS2CPacket.class), this::onRespawn);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldChange);
        registerListener(Listener.getGameJoinPoint(), this::onGameJoin);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(EntityStatusS2CPacket.class), this::onTriggerTotem);
    }

    public void onEventType(EventType type, Consumer<String> commandSender) {
        eventMap.get().forEach((eventType, s) -> {
            if (eventType == type) {
                commandSender.accept(s);
            }
        });
    }

    private void executeDelayed(String s) {
        ChatTasks.sayMessage(s, false);
    }

    public void onRespawn(Event<PlayerRespawnS2CPacket> eventRespawn) {
        if (enable.get()) {
            var respawn = eventRespawn.context();
            // death
            if (respawn.flag() == 0 || respawn.flag() == 1) {
                onEventType(EventType.RESPAWN, this::executeDelayed);
            }
        }
    }

    public void onWorldChange(Event<World> event) {
        if (enable.get()) {
            onEventType(EventType.WORLD_CHANGE, this::executeDelayed);
        }
    }

    public void onGameJoin(Event<ClientPlayerEntity> event) {
        if (enable.get()) {
            onEventType(EventType.JOIN_GAME, this::executeDelayed);
        }
    }

    public void onTriggerTotem(Event<EntityStatusS2CPacket> event) {
        if (checkNull()) return;
        if (enable.get()
                && event.context.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING
                && event.context.getEntity(mc.world) == mc.player) {
            onEventType(EventType.TRIGGER_TOTEM, this::executeDelayed);
        }
    }

    public enum EventType implements ConfigEnum {
        NONE,
        RESPAWN,
        WORLD_CHANGE,
        JOIN_GAME,
        TRIGGER_TOTEM;

        @Override
        public String getConfigEnumType() {
            return "event_command_event_type";
        }
    }
}
