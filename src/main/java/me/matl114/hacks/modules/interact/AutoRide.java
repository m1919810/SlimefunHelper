package me.matl114.hacks.modules.interact;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.combat.CombatExtra;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.MathUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;

import javax.swing.*;

public class AutoRide extends BaseModule {
    public AutoRide() {

    }
    public final ModulePath autoRide = makePath(Configs.INTERACT_CONFIG, "interaction-tweaks.auto-ride");
    public final FlagRef enable = flagBuilder(autoRide.addEnable())
        .build();

    public final KeyBindRef hotkey = moduleEntry(autoRide.addHotkey(), new MultiKeyBind(), autoRide.addEnable())
        .build();

    public final IntRef cd = intBuilder(autoRide.add("interact-cooldown"))
        .defaultValue(4)
        .build();
    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::handleInput);
    }
    Entity lastEntity;
    int cd1 = 0;
    public void handleInput(Event<Void> event){
        if(enable.get() && !mc.player.hasVehicle()){
            if(++cd1 > cd.get()){
                cd1 = 0;
                double reach = CombatExtra.INSTANCE.getAttackRange();
                Box box = mc.player.getBoundingBox().expand(reach + 3, reach + 3, reach + 3);
                var entites = mc.world.getOtherEntities(mc.player, box, re -> re instanceof VehicleEntity);
                Entity selected = null ;
                for (var re : entites){
                    if(re == lastEntity){
                        selected = re;
                    }
                }
                if(selected == null){
                    for (var re : entites){
                        if(re.getBoundingBox().squaredMagnitude(mc.player.getEyePos()) < MathUtils.s2(reach)){
                            selected = re;
                        }
                    }
                }
                lastEntity = selected;
                if(selected != null){
                    var boxxx = lastEntity.getBoundingBox();
                    var re = new EntityHitResult(lastEntity, boxxx.getCenter().add(0, boxxx.getLengthY() / 2,0));
                    InteractUtils.simulateInteract(re);
                }
            }
        }
    }
}
