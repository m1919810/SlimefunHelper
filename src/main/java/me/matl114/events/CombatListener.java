package me.matl114.events;

import lombok.Getter;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.channels.EventChannel;
import me.matl114.events.impl.CombatPlayer;

public class CombatListener {
    // combat death
    @Getter
    @Broadcast
    public static final EventChannel<CombatPlayer> playerPopTotem = new EventChannel<>();

    @Getter
    @Broadcast
    public static final EventChannel<CombatPlayer> playerDeathInfo = new EventChannel<>();

    @Getter
    @Broadcast
    public static final EventChannel<CombatPlayer> playerEnterVisualRange = new EventChannel<>();

    @Getter
    @Broadcast
    public static final EventChannel<CombatPlayer> playerLeaveVisualRange = new EventChannel<>();
}
