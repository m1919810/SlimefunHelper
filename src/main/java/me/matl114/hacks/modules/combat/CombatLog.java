package me.matl114.hacks.modules.combat;

import java.util.Objects;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;

public class CombatLog extends BaseModule {
    public CombatLog() {}

    public ModulePath combatInfo = makePath(Configs.COMBAT_CONFIG, "combat-info");

    public ModulePath combatLog = combatInfo.add("combat-log");
    public final FlagRef enable = flagBuilder(combatLog.addEnable()).build();
    public final FlagRef enableHit = flagBuilder(combatLog.add("log-hit")).build();

    public final StringRef logFormat = builder(combatLog.add("log-hit-format"), StringRef.TYPE)
            .defaultValue("&c[Combat]&f You hit %s")
            .build();

    public final FlagRef enableSmash = flagBuilder(combatLog.add("log-smash")).build();

    public final StringRef logSmashFormat = builder(combatLog.add("log-smash-format"), StringRef.TYPE)
            .defaultValue("&c[Combat]&f You smash %s")
            .build();

    public final FlagRef enableKinetic =
            flagBuilder(combatLog.add("log-kinetic")).build();

    public final StringRef logKineticFormat = builder(combatLog.add("log-kinetic-format"), StringRef.TYPE)
            .defaultValue("&c[Combat]&f You spear %s")
            .build();

    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onEntityDamage);
    }

    public void onEntityDamage(Event<EntityDamageS2CPacket> e) {
        if (checkNull()) return;
        if (enable.get()
                && e.context.sourceCauseId() == mc.player.getId()
                && mc.world.getEntityById(e.context.entityId()) instanceof PlayerEntity otherPlayer) {
            var source = e.context.sourceType().getKey().orElse(null);
            if (enableSmash.get()) {
                if (Objects.equals(source, DamageTypes.MACE_SMASH)) {
                    // we trigger a mace smash
                    Debug.chat(
                            ChatUtils.stringToText(logSmashFormat.get().formatted(otherPlayer.getNameForScoreboard())));
                    return;
                }
            }
            if (enableKinetic.get()) {
                if (Objects.equals(source, DamageTypes.SPEAR)) {
                    // we trigger a mace smash
                    Debug.chat(ChatUtils.stringToText(
                            logKineticFormat.get().formatted(otherPlayer.getNameForScoreboard())));
                    return;
                }
            }
            if (enableHit.get()) {
                Debug.chat(ChatUtils.stringToText(logFormat.get().formatted(otherPlayer.getNameForScoreboard())));
                return;
            }
        }
    }
}
