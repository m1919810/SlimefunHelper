package me.matl114.hacks.modules.move;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.matl114.events.Event;
import me.matl114.hacks.ACPostTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.modules.extra.Tests;
import me.matl114.managers.Configs;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

public class MovTest extends BaseModule implements LegalMovementManager.MovementModifier {
    public static LegalMovementManager.DelegateMovementModifier instance;

    public MovTest() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            // register at here for the first time
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public KeyBindRef keyBindRef = hotkey(Configs.HOTKEY_CONFIG, Tests.TEST_HOTKEY_2)
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::onMovTest))
            .build();

    public void onMovTest() {
        Debug.chat("On Mov Test");
        int entity;
        if (mc.crosshairTarget instanceof EntityHitResult entityHitResult) {
            entity = entityHitResult.getEntity().getId();
        } else {

            List<Entity> entities = new ArrayList<>();
            for (var et : mc.world.getEntities()) {
                if (et != mc.player) {
                    entities.add(et);
                }
            }
            entities.sort(Comparator.comparingDouble(s -> s.squaredDistanceTo(mc.player)));
            if (!entities.isEmpty()) {
                entity = entities.get(0).getId();
            } else {
                entity = mc.player.getId() - 1;
            }
        }
        ACPostTasks.addPostTransactionAction(han -> {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            mc.interactionManager.sendSequencedPacket(mc.world, (seq) -> {
                return new PlayerInteractEntityC2SPacket(
                        entity,
                        true,
                        new PlayerInteractEntityC2SPacket.InteractAtHandler(Hand.MAIN_HAND, mc.player.getPos()));
            });
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        });
    }

    @Override
    public int priority() {
        // the least important shit
        return 10000000;
    }

    @Override
    public boolean mayModifyRotation() {
        return false;
    }

    @Override
    public boolean mayModifyPos() {
        return false;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return true;
    }
}
