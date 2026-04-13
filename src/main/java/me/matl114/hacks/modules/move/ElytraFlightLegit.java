package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.entity.LegalMovementManager;

public class ElytraFlightLegit extends BaseModule implements LegalMovementManager.MovementModifier {
    public static final String[] ENABLE = makePath("elytra.elytra-flight-legit.enable");
    public static final String[] SPEED = makePath("elytra.elytra-flight-legit.speed");
    public static final String[] MODE = makePath("elytra.elytra-flight-legit.mode");

    public final FlagRef enable = flagBuilder(Configs.MOV_CONFIG, ENABLE).build();
    //    public final DoubleRef speed = builder(Configs.MOV_CONFIG, SPEED, DoubleRef.TYPE)
    //        .defaultValue(1.0)
    //        .build();
    //    public final EnumRef<ElytraFlight.ElytraMode> controlMode = builder(Configs.MOV_CONFIG, MODE,
    // ElytraFlight.ElytraMode.class)
    //        .defaultValue(ElytraFlight.ElytraMode.CONTROL)
    //        .build();

    public static LegalMovementManager.DelegateMovementModifier instance;
    // we remove it from these modules, we may add back later
    public ElytraFlightLegit() {
        bindFlag(enable);
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        // 空实现，不添加任何逻辑
    }

    /**
     * 判断是否应该启用合法飞行
     * @return true 如果本模块启用且 ElytraFlight 模块未启用
     */
    public boolean isEnableLegitFly() {
        return enable.get() && !MovTasks.getElytraFlight().enable.get();
    }

    int counter = 0;
    boolean workThisTick = false;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        //        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        //        if(isEnableLegitFly() && player.isFallFlying()) {
        //            if(!movementManagerEvent.context().hasImportantPitch()) {
        //                ++counter;
        //                workThisTick = true;
        //                player.setPitch(0.0F);
        //                if(counter %2 == 0){
        //                    EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
        //                }
        //
        //                //player.setVelocity(Vec3d.ZERO);
        ////                if(player.getVelocity().y < 0.0){
        ////                    player.setVelocity(player.getVelocity().add(0, 0.002, 0));
        ////                }
        //            }
        //        }
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        //        if(workThisTick){
        //            movementManagerEvent.context.playerStatus.restoreRotation();
        //            workThisTick = false;
        //        }
        return true;
    }
}
