package me.matl114.hacks.modules.combat;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Tasks;
import me.matl114.versioned.api.VItem;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.Vec3d;

public class Spear extends BaseModule {
    public Spear() {}

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onSpearRelease);
    }

    public void onSpearRelease(Event<PlayerActionC2SPacket> packetEvent) {
        if (packetEvent.isCancelled()) return;
        if (false
                && packetEvent.context().getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM
                && VItem.getInstance().isSpear(mc.player.getActiveItem())) {
            Entity target = CombatTasks.getTargetSelector().searchAttackEntity(80, true);

            if (target != null) {
                Vec3d tagretPos = CombatTasks.getPositionPredict().predictPosition(target);
                Vec3d fromTo =
                        mc.player.getPos().subtract(tagretPos).normalize().multiply(3);
                fromTo = tagretPos.add(fromTo);
                MovTasks.MovingContext ctx = MovTasks.createPlayerMovContext();
                var lst = MovTasks.generateTpSequence(ctx, fromTo, true);

                MovTasks.scheduleMoveSequence(
                        ctx, lst.stream().map(MovTasks::createNotOnGround).toList(), false, true);
                packetEvent.cancel();
                MovTasks.doingTp = true;
                Tasks.scheduleDelayed(() -> MovTasks.doingTp = false, 3);
                Tasks.scheduleDelayed(() -> Listener.sendPacketNoEvents(packetEvent.context()), 20);
            }
        }
    }
}
