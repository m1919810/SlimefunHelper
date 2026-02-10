package me.matl114.hacks.modules.combat;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.Tasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

public class CombatExtra extends BaseModule {
    public static final String[] COMBAT_INTERVEL = {"attack", "cancel-interval"};
    public static final String[] COMBAT_RIDING = {"attack", "riding-attack"};
    public static final String[] ATTACK_RANGE = {"attack", "att-range"};
    public static final String[] COMBAT_SHIELDING = {"attack", "shielding-attack"};
    public static final String[] COMBAT_AUTOSHIELD = {"attack", "no-grim-shield-setback"};

    public final DoubleRef range = builder(Configs.COMBAT_CONFIG, ATTACK_RANGE, DoubleRef.TYPE)
            .defaultValue(0.0D)
            .build();

    public final FlagRef shieldPredict = builder(Configs.COMBAT_CONFIG, COMBAT_AUTOSHIELD, Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef shieldAttack =
            flagBuilder(Configs.COMBAT_CONFIG, COMBAT_SHIELDING).build();

    public final FlagRef rideAttack =
            flagBuilder(Configs.COMBAT_CONFIG, COMBAT_RIDING).build();

    public final FlagRef noCooldown =
            flagBuilder(Configs.COMBAT_CONFIG, COMBAT_INTERVEL).build();

    public double getAttackRange() {
        double d = range.get();
        return mc.player.getAttributeValue(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE) + d;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(EntityTrackerUpdateS2CPacket.class), this::onShieldSetback);
        registerListener(
                Listener.getPacketPostSendPoint().getChannel(HandSwingC2SPacket.class), this::onShieldSetbackPredict);
        registerListener(
                Listener.getPacketPoint().getChannel(CooldownUpdateS2CPacket.class), this::asyncUpdateShieldCooldown);
    }

    public int shieldExceptionspam = 0;

    public void onShieldSetback(Event<EntityTrackerUpdateS2CPacket> trackerUpdateS2CPacketEvent) {
        if (trackerUpdateS2CPacketEvent.isCancelled()) {
            return;
        }
        var trackerUpdateS2CPacket = trackerUpdateS2CPacketEvent.context();
        if (shieldPredict.get()
                && mc.player != null
                && trackerUpdateS2CPacket.id() == mc.player.getId()
                && VItem.getInstance().isShield(mc.player.getActiveItem())
                && !mc.player
                        .getItemCooldownManager()
                        .isCoolingDown(mc.player.getActiveItem().getItem())) {
            // shield not in cooldown
            // block shield from
            for (var trackerUpdate : trackerUpdateS2CPacket.trackedValues()) {
                // the ordinal  of LIVING FLAGS in LivingEntity, may vary with versionsl pls check
                if (trackerUpdate.id() == VDataFlag.ID_LIVING_FLAGS) {
                    byte byteValue = (byte) trackerUpdate.value();
                    boolean bl = ((Byte) byteValue & VDataFlag.USING_ITEM_FLAG_INDEX) > 0;
                    Hand hand = ((Byte) byteValue & VDataFlag.OFFHAND_ACTIVE_FLAG_INDEX) > 0
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
                        mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                            return new PlayerInteractItemC2SPacket(
                                    hand, sequence, mc.player.getYaw(), mc.player.getPitch());
                        });
                        trackerUpdateS2CPacketEvent.cancel();
                    }
                }
            }
        }
    }

    public void onShieldSetbackPredict(Event<HandSwingC2SPacket> packet) {
        if (shieldPredict.get()
                && mc.player.isUsingItem()
                && VItem.getInstance().isShield(mc.player.getActiveItem())
                && !mc.player
                        .getItemCooldownManager()
                        .isCoolingDown(mc.player.getActiveItem().getItem())) {
            mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                return new PlayerInteractItemC2SPacket(
                        mc.player.getActiveHand(), sequence, mc.player.getYaw(), mc.player.getPitch());
            });
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
                    mc.player.getItemCooldownManager().set(packet.item(), packet.cooldown());
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
}
