package me.matl114.utils.UtilClass;

import me.matl114.hackUtils.MovTasks;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public class LegalMovementManager implements ProgressWrapper<ClientPlayerEntity>{
    final List<MovementModifier> hacks = new ArrayList<>();
    List<MovementModifier> currentTickEnableHacks = new ArrayList<>();
    public EntityMovementStatus<ClientPlayerEntity> playerStatus;
    public EntityMovementStatus<ClientPlayerEntity> playerPostHackStatus;

    public void addMovementModifier(MovementModifier movementModifier){
        int p = movementModifier.priority();
        int index = 0;
        while (index < hacks.size() && hacks.get(index).priority() <= p) {
            index++;
        }

        hacks.add(index, movementModifier);
    }

    public boolean yawModified() {
        ClientPlayerEntity player = playerStatus.entity;

        // 获取安全的角度（处理NaN等异常情况）
        float currentYaw = EntityUtils.getSafeYaw(player, playerStatus.yaw);
        float previousYaw = EntityUtils.getSafeYaw(player, playerPostHackStatus.yaw);

        // 计算两个角度之间的最小差值（处理360度环绕）
        float diff = Math.abs(currentYaw - previousYaw );
        float wrappedDiff = Math.min(diff, 360.0f - diff);

        // 设置一个合理的阈值（例如5度）
        return wrappedDiff > 2.0f;
    }
    public boolean pitchModified(){
        return Math.abs(EntityUtils.getSafePitch( playerStatus.pitch )- EntityUtils.getSafePitch( playerPostHackStatus.pitch) ) > 2.0F;
    }

    @Override
    public void preProgress(ClientPlayerEntity args) {
        this.playerStatus = new EntityMovementStatus<>(args);
        this.currentTickEnableHacks = new ArrayList<>();
        //start new tick, removing contents and replace with new
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, false, false);
        for(var hack : hacks){
            if(hack.shouldApply(movementManagerEvent)){
                this.currentTickEnableHacks.add(hack);
            }
        }
        this.playerPostHackStatus = new EntityMovementStatus<>(args);

    }

    public void postInputTick(){
        if(this.playerStatus  == null){
            //illegal status
            return ;
        }
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, false, false);
        for (var hack : this.currentTickEnableHacks){
            hack.applyAfterInputTick(movementManagerEvent);
        }
    }

    //invoke before the boat packets
    public boolean preInputProgress(ClientPlayerEntity args){
        if(this.playerStatus  == null){
            //illegal status
            return true;
        }
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, true, false);
        for (var hack : this.currentTickEnableHacks){
            hack.applyBeforeInputPacketModify(movementManagerEvent);
        }
        return !movementManagerEvent.isCancelled();
    }
    //invoke before the movement packets
    public boolean preMovementProgress(ClientPlayerEntity args){
        if(this.playerStatus  == null){
            //illegal status
            return true;
        }
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, true, false);
        for (var hack : this.currentTickEnableHacks){
            hack.applyBeforeMovementPacketModify(movementManagerEvent);
        }
        return !movementManagerEvent.isCancelled();
    }
    //after player tick
    @Override
    public void postProgress(ClientPlayerEntity args) {
        if(this.playerStatus  == null){
            //illegal status
           return;
        }
        Event<LegalMovementManager> movementManagerEvent = new Event<>(this, true, false);
        Iterator<MovementModifier> iter = this.hacks.iterator();
        int goingCurrentTickEnables = 0;
        while (iter.hasNext()){
            var hack = iter.next();
            //in same seq, so match one by one
            boolean enabled = goingCurrentTickEnables < this.currentTickEnableHacks.size() && this.currentTickEnableHacks.get(goingCurrentTickEnables) == hack;
            if(enabled){
                goingCurrentTickEnables += 1;
            }
            if(!hack.postModify(movementManagerEvent, enabled)){
                iter.remove();
            }
        }
        return;
    }

    @Override
    public final boolean stillWrap(ClientPlayerEntity args) {
        return true;
    }

    //functions:
    public void tryCorrectMovementInput(){

        if(yawModified()){
            ClientPlayerEntity player = playerStatus.entity;
            float originYaw = playerStatus.yaw;
            //rotated
            float diff = EntityUtils.getSafeYawDiff(originYaw, player.getYaw());
            Input input = player.input;
            float forwardSpeed ;
            float sidewaySpeed;
            boolean w,a,s,d;
            if(diff < 22.5 && diff >= -22.5){
                //do nothing
                return;
            }else if(diff < 67.5 && diff >= 22.5){
                //turn to
                forwardSpeed = (input.movementForward - input.movementSideways);
                sidewaySpeed = (input.movementForward + input.movementSideways);
            }else if(diff >= 67.5 && diff < 90.0F + 22.5F){
                forwardSpeed = - input.movementSideways;
                sidewaySpeed = input.movementForward;
            }else if(diff >= 90.0F + 22.5F && diff < 90.0F + 67.5F){
                forwardSpeed = (- input.movementForward - input.movementSideways);
                sidewaySpeed = (input.movementForward - input.movementSideways) ;
            }else if(diff >= 90.0F + 67.5F || diff < -90.0F - 67.5F){
                forwardSpeed = -input.movementForward;
                sidewaySpeed = -input.movementSideways;
            }else if (diff >= -90.0F - 67.5F && diff < -90.0F - 22.5F){
                forwardSpeed = (-input.movementForward + input.movementSideways);
                sidewaySpeed = (-input.movementForward - input.movementSideways) ;
            }else if(diff >= -90.0F - 22.5F && diff < -90.0F + 22.5F){
                forwardSpeed = input.movementSideways;
                sidewaySpeed = -input.movementForward;
            }else if(diff >= -90.0F + 22.5F && diff < -22.5F){
                forwardSpeed = (input.movementForward + input.movementSideways);
                sidewaySpeed = (- input.movementForward + input.movementSideways) ;
            }else {
                return;
            }
            //sync values
            input.movementForward = forwardSpeed > 0 ? 1.0F :(forwardSpeed <0 ? -1.0F : 0.0F);
            input.movementSideways =  sidewaySpeed > 0 ? 1.0F :(sidewaySpeed < 0 ? -1.0F : 0.0F);
            input.pressingForward = input.movementForward > 0;
            input.pressingBack = input.movementForward < 0;
            input.pressingLeft = input.movementSideways > 0;
            input.pressingRight = input.movementSideways < 0;
            Debug.chat("reset input", originYaw, player.getYaw(), input.movementForward, input.movementSideways);
        }

    }


    public static interface MovementModifier extends Comparable<MovementModifier>{
        default int priority(){
            return 0;
        }

        default boolean mayModify(){
            return mayModifyPos() || mayModifyRotation();
        }

        default boolean mayModifyPos(){
            return false;
        }

        default boolean mayModifyRotation(){
            return false;
        }

        default boolean conflictCheck(LegalMovementManager movementManager){
            if(mayModifyPos() && movementManager.currentTickEnableHacks.stream().anyMatch(MovementModifier::mayModifyPos)){
                return false;
            }
            if(mayModifyRotation() && movementManager.currentTickEnableHacks.stream().anyMatch(MovementModifier::mayModifyRotation)){
                return false;
            }
            return true;
        }

        //return if available for current tick modify
        default boolean shouldApply(Event<LegalMovementManager> movementManagerEvent){
            //two modifying hacks may clash with each other, so we
            preTick(movementManagerEvent);
            LegalMovementManager movementManager = movementManagerEvent.context();
            if (!conflictCheck(movementManager)) {
                return false;
            }
            applyPreTickModify(movementManagerEvent);
            return true;
        }

        default void preTick(Event<LegalMovementManager> movementManagerEvent){

        }

        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent);

        default void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent){

        }

        default void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent){

        }

        default void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent){

        }

        //return for removal after player tick
        //return if this hack is still valid, if return false, we will remove it from hack list
        //tick both
        //
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick);
        default int compareTo(MovementModifier var1){
            return this.priority() - var1.priority();
        }


    }

    public static class ModifierPipeline implements MovementModifier{

        public ModifierPipeline(int p){
            this.priority = p;
        }

        private final int priority;
        private final List<Supplier<MovementModifier>> factories = new ArrayList<>();
        private final List<MovementModifier> pipeline = new ArrayList<>();
        public void resetForNewPlayer(){
            pipeline.clear();
            for (var factory : factories){
                MovementModifier movementModifier = factory.get();
                addPipelineInternal(movementModifier);
            }
        }
        private void addPipelineInternal(MovementModifier movementModifier){
            int p = movementModifier.priority();
            int index = 0;
            while (index < pipeline.size() && pipeline.get(index).priority() <= p) {
                index++;
            }

            pipeline.add(index, movementModifier);
        }
        //NOTE: we should assume that pipeline modifiers do not conflict with each other
        //NOTE: the pipeline's checkConflict will NOT be INVOKED
        //NOTE: the modifers in pipeline will not be removed
        //NOTE: the pipeline will not be removed
        public void addMovementModifierFactory(Supplier<MovementModifier> movementModifier){
           factories.add(movementModifier);
           addPipelineInternal(movementModifier.get());

        }
        @Override
        public int priority() {
            return  priority;
        }

        @Override
        public boolean mayModifyPos() {
            for (var pi :pipeline){
                if(pi.mayModifyPos()){
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mayModifyRotation() {
            for (var pi :pipeline){
                if(pi.mayModifyRotation()){
                    return true;
                }
            }
            return false;
        }

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            for (var re: pipeline){
                re.applyPreTickModify(movementManagerEvent);
            }
        }

        @Override
        public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            for (var re: pipeline){
                re.applyBeforeMovementPacketModify(movementManagerEvent);
            }
        }

        @Override
        public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            for (var re: pipeline){
                re.applyBeforeInputPacketModify(movementManagerEvent);
            }
        }

        @Override
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            for (var re: pipeline){
                re.postModify(movementManagerEvent, enabledThisTick);
            }
            return true;
        }
    }

}
