package me.matl114.hacks.modules.move;

import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.events.Event;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.client.network.ClientPlayerEntity;

public class Sprint extends BaseModule implements LegalMovementManager.MovementModifier {
    public static final String[] MOVE_AUTO_TOGGLE_SPRINT = {"move-speed","sprint", "legal-auto-sprint"};
    public static final String[] MOVE_ALL_DIRECTION_SPRINT = {"move-speed", "sprint", "all-direction-sprint"};
    public static final String[] MOVE_SPRINT_BYPASS_MODE = {"move-speed", "sprint", "bypass-mode"};

    public static LegalMovementManager.DelegateMovementModifier instance;
    //todo; add speed modify to here
    //todo: 顶头跑 here
    public Sprint() {
        if(instance == null){
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            //register at here for the first time
            MovTasks.PLAYER_PIPELINE_ROT.addMovementModifierFactory(()-> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final FlagRef autoSprintLegal = flagBuilder(Configs.MOV_CONFIG, MOVE_AUTO_TOGGLE_SPRINT)
        .build();

    public final FlagRef directionalSprint = flagBuilder(Configs.MOV_CONFIG, MOVE_ALL_DIRECTION_SPRINT)
        .build();

    public final EnumRef<Configs.BypassMode> directionalSprintMode = builder(Configs.MOV_CONFIG, MOVE_SPRINT_BYPASS_MODE, Configs.BypassMode.class)
        .defaultValue(Configs.BypassMode.NO_BYPASS)
        .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getGameTick(), this::onTick);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    public boolean enableSprintDirectionalThisTick = false;
    //todo add pitchyaw pipeline-- do it later
    //check if we can speedup using moveFoward+sideway
    //no use: sidewaywalk no faster than foward, but jump-sprint much faster


    @Override
    public void onEnableModule() {
        super.onEnableModule();
        enableSprintDirectionalThisTick = false;
        workRotationThisTick = false;
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        enableSprintDirectionalThisTick = false;
        workRotationThisTick = false;
    }

    public void onTick(Event<ClientPlayerEntity> event){
        if(autoSprintLegal.get() && mc.currentScreen == null ){
            if(!mc.options.sprintKey.isPressed()){
                Debug.chat("[Legal Sprint] toggle sprint on (ctrl)");
                mc.options.sprintKey.setPressed(true);
            }
        }
    }



    boolean workRotationThisTick;
    @Override
    public int priority() {
        //the least important shit
        return 10000000;
    }

    @Override
    public boolean mayModifyRotation() {
        return directionalSprint.get() && !mc.player.input.hasForwardMovement() && directionalSprintMode.getValue() != Configs.BypassMode.NO_BYPASS;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
//                if(player.getVelocity().horizontalLength() > 0.05)
//                Debug.info("check vc", player.getVelocity().horizontalLength());

        // Debug.info(player.input.movementForward);
        if(directionalSprint.get() && (player.input.playerInput.backward() && !player.input.playerInput.forward()) && !(player.isTouchingWater() && !player.isSubmergedInWater()) && !(player.horizontalCollision && !player.collidedSoftly)){
            //give the ticket
            enableSprintDirectionalThisTick = true;

            //}

        }
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if(directionalSprint.get() && (player.input.playerInput.backward() && !player.input.playerInput.forward()) && player.isSprinting()){

            if(directionalSprintMode.getValue() == Configs.BypassMode.BYPASS_GRIM){
                workRotationThisTick = true;
//                            float yaw = EntityUtils.rotationToYaw(walkingWay);
//                        Debug.info(walkingWay);
//                        Debug.info(yaw);
                //turn around to bypass ,movingAround

                EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
            }

            //}

        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        enableSprintDirectionalThisTick = false;
        if(!enabledThisTick)return true;
        if(workRotationThisTick){

            movementManagerEvent.context.playerStatus.restoreRotation();
        }
        return true;
    }


    public void onModulePreset(Event<EventContainer<ModulePreset>> event){
        ModulePreset preset = event.context().getValue();
        switch (preset){
            case HACKING, VANILLA -> {
                directionalSprintMode.set(Configs.BypassMode.NO_BYPASS);
            }
            default -> {
                directionalSprintMode.set(Configs.BypassMode.BYPASS_GRIM);
            }
        }
    }

}
