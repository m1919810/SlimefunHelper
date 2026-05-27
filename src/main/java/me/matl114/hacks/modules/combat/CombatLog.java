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
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.registry.RegistryKey;

public class CombatLog extends BaseModule {
    public CombatLog() {
        bindFlag(enable);
    }

    public ModulePath combatInfo = makePath(Configs.COMBAT_CONFIG, "combat-info");

    public ModulePath combatLog = combatInfo.add("combat-log");
    public final FlagRef enable = flagBuilder(combatLog.addEnable()).build();
    public final FlagRef enableMeHitOther =
            flagBuilder(combatLog.add("log-me-hit-other")).build();
    public final FlagRef enableOtherHitMe =
            flagBuilder(combatLog.add("log-other-hit-me")).build();
    public final FlagRef enableHit = flagBuilder(combatLog.add("log-hit")).build();

    public final StringRef logFormat = builder(combatLog.add("log-hit-format-str"), StringRef.TYPE)
            .defaultValue("&c[Combat]&f %s hit %s, type: %s")
            .build();

    public final FlagRef enableSmash = flagBuilder(combatLog.add("log-smash")).build();

    public final StringRef logSmashFormat = builder(combatLog.add("log-smash-format-str"), StringRef.TYPE)
            .defaultValue("&c[Combat]&f %s smash %s")
            .build();

    public final FlagRef enableKinetic =
            flagBuilder(combatLog.add("log-kinetic")).build();

    public final StringRef logKineticFormat = builder(combatLog.add("log-kinetic-format-str"), StringRef.TYPE)
            .defaultValue("&c[Combat]&f %s spear %s")
            .build();

    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(EntityDamageS2CPacket.class), this::onEntityDamage);
    }

    public void onEntityDamage(Event<EntityDamageS2CPacket> e) {
        if (checkNull()) return;
        if (enable.get()
                && e.context.sourceCauseId() == mc.player.getId()
                && mc.world.getEntityById(e.context.entityId()) instanceof PlayerEntity otherPlayer
                && enableMeHitOther.get()) {
            var source = e.context.sourceType().getKey().orElse(null);
            logDamage("you", otherPlayer.getNameForScoreboard(), source);
        } else if (enable.get()
                && e.context.entityId() == mc.player.getId()
                && mc.world.getEntityById(e.context.sourceCauseId()) instanceof PlayerEntity otherPlayer
                && enableOtherHitMe.get()) {
            var source = e.context.sourceType().getKey().orElse(null);
            logDamage(otherPlayer.getNameForScoreboard(), "you", source);
        }
    }

    public void logDamage(String from, String to, RegistryKey<DamageType> source) {
        if (enableSmash.get()) {
            if (Objects.equals(source, DamageTypes.MACE_SMASH)) {
                // we trigger a mace smash
                Debug.chat(ChatUtils.stringToText(logSmashFormat.get().formatted(from, to)));
                return;
            }
        }
        if (enableKinetic.get()) {
            if (Objects.equals(source, DamageTypes.SPEAR)) {
                // we trigger a mace smash
                Debug.chat(ChatUtils.stringToText(logKineticFormat.get().formatted(from, to)));
                return;
            }
        }
        if (enableHit.get()) {
            Debug.chat(ChatUtils.stringToText(logFormat
                    .get()
                    .formatted(
                            from,
                            to,
                            source == null ? "null" : source.getValue().getPath())));
            return;
        }
    }
}
