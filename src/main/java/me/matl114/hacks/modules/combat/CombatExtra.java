package me.matl114.hacks.modules.combat;

import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;

public class CombatExtra extends BaseModule {
    public static CombatExtra INSTANCE;

    public CombatExtra() {
        super("CombatExtra");
        INSTANCE = this;
    }

    public final ModulePath combat = makePath(Configs.COMBAT_CONFIG, "attack");

    public final DoubleRef range =
            doubleBuilder(combat.add("att-range")).defaultValue(0.0D).build();

    public final DoubleRef boatAttackRange =
            doubleBuilder(combat.add("boat-reach-range")).defaultValue(0.0D).build();

    public final FlagRef shieldPredict =
            flagBuilder(combat.add("shielding-setback-log")).build();

    public final FlagRef useAttack = flagBuilder(combat.add("shielding-attack")).build();

    public final FlagRef rideAttack = flagBuilder(combat.add("riding-attack")).build();

    public final FlagRef noCooldown = flagBuilder(combat.add("cancel-interval")).build();

    private boolean ridingBypass(Entity entity) {
        return entity.hasVehicle();
    }

    public double getAttackAtTargetRange(Entity entity) {
        double d = ridingBypass(mc.player) || ridingBypass(entity) ? boatAttackRange.get() : range.get();
        return mc.player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE) + d;
    }

    public double getAttackRange() {
        double d = ridingBypass(mc.player) ? boatAttackRange.get() : range.get();
        return mc.player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE) + d;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(EntityTrackerUpdateS2CPacket.class), this::onShieldSetback);
        registerListener(
                Listener.getPacketPoint().getChannel(CooldownUpdateS2CPacket.class), this::asyncUpdateShieldCooldown);
        registerListener(Listener.getAttackAction(), this::onUseAttackNoSlow);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    public int shieldExceptionspam = 0;

    public void onUseAttackNoSlow(Event<HitResult> event) {
        //        if (shieldAttack.get() && mc.player.isUsingItem()) {
        //            MovTasks.getNoSlowDown().setPreAttackUseTick();
        //        }
    }

    public void onShieldSetback(Event<EntityTrackerUpdateS2CPacket> trackerUpdateS2CPacketEvent) {
        if (trackerUpdateS2CPacketEvent.isCancelled()) {
            return;
        }
        var trackerUpdateS2CPacket = trackerUpdateS2CPacketEvent.context();
        if (shieldPredict.get()
                && mc.player != null
                && trackerUpdateS2CPacket.id() == mc.player.getId()
                && mc.player.isUsingItem()
                && VItem.getInstance().isShield(mc.player.getActiveItem())
                && !mc.player.getItemCooldownManager().isCoolingDown(mc.player.getActiveItem())) {
            // shield not in cooldown
            // block shield from
            for (var trackerUpdate : trackerUpdateS2CPacket.trackedValues()) {
                // the ordinal  of LIVING FLAGS in LivingEntity, may vary with versionsl pls check
                if (trackerUpdate.id() == VDataFlag.ID_LIVING_FLAGS) {
                    byte byteValue = ((Number) trackerUpdate.value()).byteValue();
                    boolean bl = (byteValue & (1 << VDataFlag.USING_ITEM_FLAG_INDEX)) > 0;
                    Hand hand = (byteValue & (1 << VDataFlag.OFFHAND_ACTIVE_FLAG_INDEX)) > 0
                            ? Hand.OFF_HAND
                            : Hand.MAIN_HAND;
                    // cooldown should be ok,
                    // the only position the server disable shield correctly should be cooldown
                    // so we kick it back
                    if (!bl && hand == mc.player.getActiveHand()) {
                        // using shield , but banned
                        if (shieldExceptionspam + 4 < Tasks.getTick()) {
                            shieldExceptionspam = Tasks.getTick();
                            Debug.chat(Text.literal("[AC] 阻挡异常盾牌禁用").formatted(Formatting.RED));
                        }
                        // trackerUpdateS2CPacketEvent.cancel();
                    }
                }
            }
        }
    }

    public void asyncUpdateShieldCooldown(Event<CooldownUpdateS2CPacket> packetEvent) {
        if (packetEvent.isCancelled()) {
            return;
        }
        CooldownUpdateS2CPacket packet = packetEvent.context();
        if (packet.cooldown() > 0) {
            try {
                synchronized (CombatExtra.class) {
                    // async update, synchronize to protect concurrent cooldown update,
                    mc.player.getItemCooldownManager().set(packet.cooldownGroup(), packet.cooldown());
                    //                if(mc.player.isUsingItem() && mc.player.getActiveItem().getItem() == shield){
                    //
                    //                }
                    // consume packet
                    packetEvent.cancel();
                }

            } catch (Throwable e) {
                // any exception

            }
        }
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        switch (event.context.getValue()) {
            case HACKING -> {
                range.set(3.0);
                boatAttackRange.set(3.0);
            }
            case AC_GRIM, AC_GRIM_LEGACY -> {
                range.set(0.0);
                boatAttackRange.set(3.0);
            }
            default -> {
                range.set(0.0);
                boatAttackRange.set(0.0);
            }
        }
    }
}
