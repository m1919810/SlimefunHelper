package me.matl114.hackUtils.modules.move;

import me.matl114.access.KeyBindAccess;
import me.matl114.hackUtils.MovTasks;
import me.matl114.hackUtils.Tasks;
import me.matl114.hackUtils.modules.BaseModule;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.UtilClass.Event;
import me.matl114.utils.UtilClass.LegalMovementManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.util.math.Vec3d;

public class CreativeFlight extends BaseModule implements LegalMovementManager.MovementModifier {
    private static LegalMovementManager.DelegateMovementModifier instance;
    public CreativeFlight() {
        bindFlag(canFly);
        if(instance == null){
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(()-> instance);
        }
        instance.setDelegate(this::cast);
    }
    public final Config.FlagRef canFly = HotKeys.getToggleFlag(HotKeys.TOGGLE_FLIGHT, false)
        ;
    public final Config.FlagRef doAntiKick = Configs.MOV_CONFIG
        .builder(Boolean.class)
        .path(Configs.MOVE_FLIGHT_ANTIKICK)
        .defaultValue(true)
        .build()
        ;
    public final Config.FlagRef overrideFlySpeed = Configs.MOV_CONFIG
        .builder(Boolean.class)
        .path(Configs.MOVE_SPEED_OVERRIDE_FLY)
        .defaultValue(false)
        .build()
        ;
    public boolean serverSideCanFly = false;

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        if (mc.player != null) {
            serverSideCanFly = mc.player.getAbilities().allowFlying;
        }
    }

    public void onDisableModule() {
        super.onDisableModule();
        if (mc.player != null) {
            //cancel fly when disable
            mc.player.getAbilities().allowFlying = serverSideCanFly;
            if(mc.player.getAbilities().flying && !serverSideCanFly) {
                mc.player.getAbilities().flying = false;
            }
        }
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketListenerPoint(PlayerAbilitiesS2CPacket.class), this::onAbility);
        registerListener(Listener.getPacketListenerPoint(UpdatePlayerAbilitiesC2SPacket.class), this::onAbilityUpdate);
    }

    public void onAbility(Event<PlayerAbilitiesS2CPacket> event){
        var packet1 = event.context();
        serverSideCanFly = packet1.allowFlying();
        if(mc.player != null){
            PlayerAbilities abilities = mc.player.getAbilities();
            //abilities.allowFlying = abilities.allowFlying;
            abilities.creativeMode = packet1.isCreativeMode();
            abilities.invulnerable = packet1.isInvulnerable();
            if(!isActive()){
                abilities.allowFlying = packet1.allowFlying();
            }
            if(!overrideFlySpeed.get()){
                abilities.setFlySpeed(packet1.getFlySpeed());
            }
            abilities.setWalkSpeed(packet1.getWalkSpeed());
            //mc.player.getAbilities().flying = isFly;
        }else{
            //sometimes the player hasn't enter the game, because this is accepted in async thread, so run main
            Tasks.scheduleDelayed(()->{
                onAbility(event);
            },1);
        }
        event.cancel();
    }

    public void onAbilityUpdate(Event<UpdatePlayerAbilitiesC2SPacket> event){
        if(isActive() && !serverSideCanFly){
            event.cancel();
        }
    }


    @Override
    public boolean mayModifyPos() {
        return false;
    }

    @Override
    public boolean mayModifyRotation() {
        return false;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if( HotKeys.getHotkeyToggleManager().getState(HotKeys.TOGGLE_FLIGHT)){
            if( !player.getAbilities().allowFlying ){
                player.getAbilities().allowFlying = true;
            }
            if(doAntiKick.get()){
                antiKick(player);
            }
        }else {
            player.getAbilities().allowFlying = serverSideCanFly;
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return true;
    }

    //server constant
    private static final int antiKickPeriod = 60;
    private static final double antiKickOffset = 0.032D;
    //antikick module
    private int antiKickCount = 0;
    private double antiKickOffset0 ;
    private boolean escapeMotionReset = false;
    private double preservedLastMotion = 0.0D;
    private boolean waitingForServerResponse;

    public void antiKick(ClientPlayerEntity player){
        if(MovTasks.seenAsFloating()){
            antiKickCount++;
        }else {
            antiKickCount = 0;
        }
        if(antiKickCount > antiKickPeriod){
            antiKickCount = 0;
            escapeMotionReset = false;
            preservedLastMotion = player.getVelocity().y;
            setMotionY(- antiKickOffset);
            //randomly fall down twice
            waitingForServerResponse = true; //Tasks.getTickRandom()%3 == 0;
            antiKickOffset0 = antiKickOffset - 0.008;
            return;
        }
        if( !escapeMotionReset){
            if(waitingForServerResponse){
                setMotionY(- antiKickOffset);
                antiKickOffset0 += antiKickOffset - 0.008;
                //there is no fucking packet for response
                waitingForServerResponse = false;

                //continue fall down til server respond
            }else {
                setMotionY( antiKickOffset0 + preservedLastMotion - 0.0);
                antiKickOffset0 = 0D;
                preservedLastMotion = 0.0D;
                Tasks.scheduleDelayed(this::restoreKeyPresses,1);
                //set end
                escapeMotionReset = true;
            }
        }

//        }

    }
    private void setMotionY(double motionY)
    {

        mc.options.sneakKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, motionY, velocity.z);
    }

    private  void restoreKeyPresses()
    {
        //bugfix when shift click in screen, this key is reset to fall
        if(mc.currentScreen == null){

            KeyBindAccess.of(mc.options.jumpKey).resetKeyState();
            KeyBindAccess.of(mc.options.sneakKey).resetKeyState();
        }

    }
}
