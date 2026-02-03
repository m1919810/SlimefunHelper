package me.matl114.hacks.modules.combat;

import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.ACPostTasks;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.events.Event;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BowEnhance extends BaseModule {
    public static final String TOGGLE_AUTO_AIM = "bow-aim";
    public static final String[] BOW_ENHANCE = {"hotkeys-toggle", "bow-aim"};

    public static final String[] BOW_AIM = {"bow-att", "aim-enable"};

    public static final String[] BOW_TP = {"bow-att", "tp-enable"};

    public static final String[] TARGETING = {"bow-att", "targeting-mode"};

    public static final String[] TP_ACCELERATE = {"bow-att", "tp-accelerate"};

    public static final String[] TP_EXACT = {"bow-att", "tp-accelerate-exact-tp"};

    public static final String[] LOW_VERSION = {"bow-att", "version-lower-than-121"};

    public static final String[] RENDER_TARGET = {"bow-att", "render-target"};

    public BowEnhance() {
        bindFlag(enable);
    }
    public FlagRef enable = toggle(BOW_ENHANCE)
        .build();

    public KeyBindRef hotkey = toggleHotkey(BOW_ENHANCE, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_H))
        .build();

    public FlagRef enableAim = flagBuilder(Configs.COMBAT_CONFIG, BOW_AIM)
        .build();

    public FlagRef enableTp = flagBuilder(Configs.COMBAT_CONFIG, BOW_TP)
        .build();

    public EnumRef<Configs.LegalInteractMode> mode = builder(Configs.COMBAT_CONFIG, TARGETING, Configs.LegalInteractMode.class)
        .defaultValue(Configs.LegalInteractMode.USEITEM_PACKET)
        .build();

    public DoubleRef tpDistance = builder(Configs.COMBAT_CONFIG, TP_ACCELERATE, DoubleRef.TYPE)
        .defaultValue(150.0D)
        .build();

    public FlagRef enhanceTp = flagBuilder(Configs.COMBAT_CONFIG, TP_EXACT)
        .build();

    public FlagRef lowVersion = flagBuilder(Configs.COMBAT_CONFIG, LOW_VERSION)
        .build();

    public FlagRef renderTarget = flagBuilder(Configs.COMBAT_CONFIG, RENDER_TARGET)
        .build();

    public boolean canTp(){
        return enableTp.get() && tpDistance.get() > 1E-7;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onBowAction);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRenderAimTarget);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    public void onBowAction(Event<PlayerActionC2SPacket> actionEvent){
        if(actionEvent.isCancelled()) return;
        if(!enable.get())return;
        var actionPacket = actionEvent.context();
        if(actionPacket.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM && mc.player != null ){
            //delay tp do not run BowAction logic and let it go
            //may not using item anymore

            //ret
            if(! mc.player.isUsingItem()){
                return;
            }
            //run main logic
            ItemStack stack = mc.player.getActiveItem();
            //only consider BowItem
            if(stack.isEmpty())return;

           if((stack.getItem() instanceof BowItem) || (stack.getItem() instanceof TridentItem)) {
               float velocity;
               boolean searchEntity = enableAim.get();
               Entity targetEntity;
               if(searchEntity){
                   Entity entity = CombatTasks.targetSelector.searchAimableEntity(stack.getItem() instanceof BowItem);
                   if(entity != null){

                       // calculate lerp by speed
                       targetEntity = entity;
                   }else{


                       targetEntity = null;
                   }
               }else{
                   targetEntity = null;
               }
                //goes accelerate with bowTP
                if (stack.getItem() instanceof BowItem) {
                    velocity = (72000 - mc.player.getItemUseTimeLeft()) / 20F;
                    velocity = (velocity * velocity + velocity * 2) / 3;
                    if (velocity > 1)
                        velocity = 1;
                    velocity =  (velocity * 3.0F) ;
                } else if (stack.getItem() instanceof TridentItem) {
                    velocity = 2.5F;
                } else {
                    //whatever
                    velocity = 3.0F;
                }
                switch (mode.get()) {
                    case MOVEMENT -> bowActionMovement(actionEvent, targetEntity, velocity);
                    case DELAY_MOVEMENT -> bowActionDelayMovement(actionEvent, targetEntity, velocity);
                    case USEITEM_PACKET -> bowActionInteractItem(actionEvent, targetEntity, velocity);
                }
           }

        }
    }

    public void onRenderAimTarget(Event<MatrixStack> stackE){
        var stack = stackE.context;
        if(enable.get() && enableAim.get() && renderTarget.get() && mc.player != null && mc.player.isUsingItem()){
            float tickDelta = (Float) stackE.extraArgs[0];
            ItemStack itemInUse = mc.player.getActiveItem();
            if(!itemInUse.isEmpty() && (itemInUse.getItem() instanceof RangedWeaponItem || itemInUse.getItem() instanceof TridentItem)){
                RenderUtils.startDrawVirtual(stack);
                try{
                    Entity entity = CombatTasks.getTargetSelector().searchAimableEntity(itemInUse.getItem() instanceof BowItem);
                    if(entity != null){
                        RenderUtils.setAsShaderColor(Color.GREEN, 0.25F);
                        Box box = RenderUtils.getLerpedBox(entity, tickDelta);
                        RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), box.getMinPos(), box.getMaxPos());
                    }
                }finally {
                    RenderUtils.stopDrawVirtual(stack);
                }
            }
        }
    }

    public void bowActionMovement(Event<PlayerActionC2SPacket> event, Entity entity, float initialVelocity){
        Vec2f playerPitchYaw = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        var facing = entity == null ? mc.player.getRotationVector().normalize() : CombatTasks.getPositionPredict().predictAimPositionForEntity(entity, 3600000).subtract(mc.player.getEyePos());
        Entity nowMePointingTheEntity = (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY)? ((EntityHitResult)mc.crosshairTarget).getEntity() : null;
        if(nowMePointingTheEntity != null && nowMePointingTheEntity.getEntityPos().squaredDistanceTo(mc.player.getEyePos()) > 50){
            nowMePointingTheEntity = null;
        }
        boolean makeAim = enableAim.get();
        float finalVelocity = initialVelocity;
        make_movements:
        {
            if(canTp()){
                //add movements to accelerate the projectile
                boolean exactTp = enhanceTp.get();
                double range = tpDistance.get();
                Vec3d facingNorm = facing.normalize();
                Vec3d oppositeFacing = Vec3d.ZERO.subtract(facingNorm);
                Vec3d finalMove = Vec3d.ZERO;
                Vec3d currentPlayerPos = mc.player.getEntityPos();
                boolean shouldResetRotation = true;
                test_tp_position:
                {
                    // optimize the collision check by caching List of Boxes
                    MovTasks.CollisionContext context = new MovTasks.CollisionCache(mc.player, currentPlayerPos, currentPlayerPos.add( oppositeFacing.multiply(range + 1.0d)), true);
                    double test = range;
                    for (; test > 10.0D; test -= 1.0D){
                        if(exactTp){
                            Vec3d oppositeMultiply = oppositeFacing.multiply(test);
                            if(MovTasks.validMoveTo(context, currentPlayerPos.add(oppositeMultiply), Vec3d.ZERO.subtract(oppositeMultiply))){
                                finalMove = oppositeMultiply;
                                break test_tp_position;
                            }
                        }else{
                            if(MovTasks.validMoveToAndBack(context, currentPlayerPos, oppositeFacing.multiply(test))){
                                finalMove = oppositeFacing.multiply(test);
                                break test_tp_position;
                            }
                        }
                    }
                    // t < 10
                    //check again
                    test = 10.0D;
                    for (; test > 0.0D; test -= 0.5D){
                        Vec3d oppositeMultiply = oppositeFacing.multiply(test);
                        if(exactTp){
                            if(MovTasks.validMoveTo(context, currentPlayerPos.add(oppositeMultiply), Vec3d.ZERO.subtract(oppositeMultiply))){
                                finalMove = oppositeMultiply;
                                break test_tp_position;
                            }
                        }else{
                            if(MovTasks.validMoveToAndBack(context, currentPlayerPos, oppositeMultiply)){
                                finalMove = oppositeMultiply;
                                break test_tp_position;
                            }
                        }
                        Vec3d oppoHorizontal = new Vec3d(oppositeMultiply.x, 0.0d, oppositeMultiply.z);
                        Vec3d simulateMove = context.simulateMovement(mc.player, currentPlayerPos, oppoHorizontal);
                        if(MovTasks.validMovementAsServer(oppoHorizontal, simulateMove)){
                            Vec3d simulateDownMove = context.simulateMovement(mc.player, currentPlayerPos.add(simulateMove), new Vec3d(0, oppositeMultiply.y, 0));
                            Vec3d wholeMovement = simulateMove.add(simulateDownMove);
                            //y does not matter , xz matters
                            if(MovTasks.validMoveTo(context, currentPlayerPos.add(wholeMovement), wholeMovement.multiply(-1))){
                                finalMove = wholeMovement;
                                break test_tp_position;
                            }
                        }
                    }
                    // should strengthen move when test < 10,
                }
                if(finalMove.lengthSquared() > 1E-4){
                    //随便写的阈值 速度太快不需要转向
                    double velocity = finalMove.length();
                    finalVelocity += velocity;
                    shouldResetRotation = velocity < 10d;
                    java.util.List<Vec3d> tpSequence = MovTasks.generateTpSequence(currentPlayerPos, currentPlayerPos.add(finalMove), false, 161, true);
                    if(!tpSequence.isEmpty()){
                        Vec2f redirectTarget = null;
                        Debug.chat(Text.literal("[Bow TP] Projectile Velocity Simulate %.2f".formatted(finalMove.length())).formatted(Formatting.GREEN));
                        List<MovTasks.MovInfo> movements = new ArrayList<>();
                        int size = tpSequence.size();
                        for (int i=0; i< size; ++i){
                            movements.add(i == 0 ? MovTasks.MovInfo.createNotOnGround(tpSequence.get(i)) : MovTasks.MovInfo.create(tpSequence.get(i)));
                        }
                        if(shouldResetRotation){
                            //need test
                            redirectTarget =  CombatTasks.calculatePitchYawPredict(finalVelocity, finalMove, facing);
                            if(Float.isNaN(redirectTarget.x) || Float.isInfinite(redirectTarget.x)){
                                //unreachable target via aim
                                Debug.chat("[Bow Aim] Arrow failed to reach the target");
                                shouldResetRotation = false;
                            }
                        }
                        movements.add(shouldResetRotation ? new MovTasks.MovInfo(currentPlayerPos.add(0, 9E-8, 0), null, true, redirectTarget):  MovTasks.MovInfo.create(currentPlayerPos.add(0, 9E-8, 0)));


                        //todo: check if there is any problem
                        MovTasks.scheduleFarawayMoveInternal(movements, false, MovTasks.MovingContext.create(currentPlayerPos), true);

                        MovTasks.setupAutoResync(mc.player.getEntityPos() , 10);
                        //disable later autoAim because we have sent the pitchYaw
                        makeAim = false;
                        break make_movements;
                    }

                    //send packets to simulate movements
                }
                Debug.chat(Text.literal("[Bow TP] Projectile Velocity fail to simulate"));
            }
        }
        if(makeAim && entity != null && entity != nowMePointingTheEntity){

            //add use item feature

            Vec2f red =  CombatTasks.calculatePitchYawPredict(finalVelocity, Vec3d.ZERO, facing);
            if(Float.isNaN(red.x) || Float.isInfinite(red.x) || Float.isNaN(red.y) || Float.isInfinite(red.y)){
                Debug.chat("[Bow Aim] Arrow failed to reach the target");
            }else{
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(red.y, red.x, mc.player.isOnGround()));
            }


        }


        mc.player.setPitch(playerPitchYaw.x);
        mc.player.setYaw(playerPitchYaw.y);

    }

    //todo: should check
    public void bowActionDelayMovement(Event<PlayerActionC2SPacket> event, Entity entity, float initialVelocity){
        //it is from a delayed packet, or, I can fire it without event
        if(canTp()){
            Debug.chat("[BowEh] Arrow Velocity Simulate not enabled in Legal Mode");
        }
        event.cancel();
        PlayerActionC2SPacket delayedPacket = event.context();
        ClientPlayerAccess.of(mc.player).getLegalMovementManager()
            .addMovementModifier(
                new LegalMovementManager.MovementModifier() {
                    @Override
                    public int priority() {
                        return -10000000;
                    }
                    @Override
                    public boolean mayModifyRotation() {
                        return true;
                    }
                    @Override
                    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                        ClientPlayerEntity player = movementManagerEvent.context().playerStatus.entity;
                        Vec3d targetAt = CombatTasks.getPositionPredict().predictAimPositionForEntity(entity, initialVelocity);
                        Vec3d targetAtFacing = targetAt.subtract(player.getEyePos());
                        Vec2f pitchYaw = CombatTasks.calculatePitchYawPredict((float) (initialVelocity),  player.getVelocity(), targetAtFacing);
                        if(Float.isNaN(pitchYaw.x) || Float.isInfinite(pitchYaw.x) || Float.isNaN(pitchYaw.y) || Float.isInfinite(pitchYaw.y)){
                            Debug.chat("[Bow Aim] Arrow failed to reach the target");
                            return;
                        }
                        EntityUtils.setEntityPitchSafe(player, pitchYaw.x);
                        EntityUtils.setEntityYawSafe(player, pitchYaw.y);
                    }

                    @Override
                    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                        if(enabledThisTick)
                            movementManagerEvent.context().playerStatus.restoreRotation();
                        //add post packets
                        //mc.getNetworkHandler().sendPacket(actionPacket);
                        if(true)
                            ACPostTasks.addPostTransactionAction((handler)->{
                                Listener.sendPacketNoEvents(handler.getConnection(), delayedPacket);
                            });
                        return false;
                    }
                }
            );
    }

    public void bowActionInteractItem(Event<PlayerActionC2SPacket> event, Entity entity, float initialVelocity){
        Vec2f playerPitchYaw = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        var facing = entity == null ? mc.player.getRotationVector().normalize() : CombatTasks.getPositionPredict().predictAimPositionForEntity(entity, 3600000).subtract(mc.player.getEyePos());
        Entity nowMePointingTheEntity = (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY)? ((EntityHitResult)mc.crosshairTarget).getEntity() : null;
        if(nowMePointingTheEntity != null && nowMePointingTheEntity.getEntityPos().squaredDistanceTo(mc.player.getEyePos()) > 50){
            nowMePointingTheEntity = null;
        }
        boolean makeAim = enableAim.get();
        float finalVelocity = initialVelocity;
        make_movements:
        {
            if(canTp()){
                //add movements to accelerate the projectile
                boolean exactTp = enhanceTp.get();
                double range = tpDistance.get();
                Vec3d facingNorm = facing.normalize();
                Vec3d oppositeFacing = Vec3d.ZERO.subtract(facingNorm);
                Vec3d finalMove = Vec3d.ZERO;
                Vec3d currentPlayerPos = mc.player.getEntityPos();
                boolean shouldResetRotation = true;
                test_tp_position:
                {
                    // optimize the collision check by caching List of Boxes
                    MovTasks.CollisionContext context = new MovTasks.CollisionCache(mc.player, currentPlayerPos, currentPlayerPos.add( oppositeFacing.multiply(range + 1.0d)), true);
                    double test = range;
                    for (; test > 10.0D; test -= 1.0D){
                        if(exactTp){
                            Vec3d oppositeMultiply = oppositeFacing.multiply(test);
                            if(MovTasks.validMoveTo(context, currentPlayerPos.add(oppositeMultiply), Vec3d.ZERO.subtract(oppositeMultiply))){
                                finalMove = oppositeMultiply;
                                break test_tp_position;
                            }
                        }else{
                            if(MovTasks.validMoveToAndBack(context, currentPlayerPos, oppositeFacing.multiply(test))){
                                finalMove = oppositeFacing.multiply(test);
                                break test_tp_position;
                            }
                        }
                    }
                    // t < 10
                    //check again
                    test = 10.0D;
                    for (; test > 0.0D; test -= 0.5D){
                        Vec3d oppositeMultiply = oppositeFacing.multiply(test);
                        if(exactTp){
                            if(MovTasks.validMoveTo(context, currentPlayerPos.add(oppositeMultiply), Vec3d.ZERO.subtract(oppositeMultiply))){
                                finalMove = oppositeMultiply;
                                break test_tp_position;
                            }
                        }else{
                            if(MovTasks.validMoveToAndBack(context, currentPlayerPos, oppositeMultiply)){
                                finalMove = oppositeMultiply;
                                break test_tp_position;
                            }
                        }
                        Vec3d oppoHorizontal = new Vec3d(oppositeMultiply.x, 0.0d, oppositeMultiply.z);
                        Vec3d simulateMove = context.simulateMovement(mc.player, currentPlayerPos, oppoHorizontal);
                        if(MovTasks.validMovementAsServer(oppoHorizontal, simulateMove)){
                            Vec3d simulateDownMove = context.simulateMovement(mc.player, currentPlayerPos.add(simulateMove), new Vec3d(0, oppositeMultiply.y, 0));
                            Vec3d wholeMovement = simulateMove.add(simulateDownMove);
                            //y does not matter , xz matters
                            if(MovTasks.validMoveTo(context, currentPlayerPos.add(wholeMovement), wholeMovement.multiply(-1))){
                                finalMove = wholeMovement;
                                break test_tp_position;
                            }
                        }
                    }
                    // should strengthen move when test < 10,
                }
                if(finalMove.lengthSquared() > 1E-4){
                    //随便写的阈值 速度太快不需要转向
                    double velocity = finalMove.length();
                    finalVelocity += velocity;
                    java.util.List<Vec3d> tpSequence = MovTasks.generateTpSequence(currentPlayerPos, currentPlayerPos.add(finalMove), false, 161, true);
                    if(!tpSequence.isEmpty()){
                        Debug.chat(Text.literal("[Bow TP] Projectile Velocity Simulate %.2f".formatted(finalMove.length())).formatted(Formatting.GREEN));
                        List<MovTasks.MovInfo> movements = new ArrayList<>();
                        int size = tpSequence.size();
                        for (int i=0; i< size; ++i){
                            movements.add(i == 0 ? MovTasks.MovInfo.createNotOnGround(tpSequence.get(i)) : MovTasks.MovInfo.create(tpSequence.get(i)));
                        }

                        movements.add(MovTasks.MovInfo.create(currentPlayerPos.add(0, 9E-8, 0)));

                        //todo: check if there is any problem
                        MovTasks.scheduleFarawayMoveInternal(movements, false, MovTasks.MovingContext.create(currentPlayerPos), true);

                        MovTasks.setupAutoResync(mc.player.getEntityPos() , 10);
                        //disable later autoAim because we have sent the pitchYaw
                        break make_movements;
                    }

                    //send packets to simulate movements
                }
                Debug.chat(Text.literal("[Bow TP] Projectile Velocity fail to simulate"));
            }
        }
        if(makeAim && entity != null && entity != nowMePointingTheEntity){
            Vec2f red = CombatTasks.calculatePitchYawPredict(finalVelocity, mc.player.getVelocity(), facing);
            if(Float.isNaN(red.x) || Float.isInfinite(red.x) || Float.isNaN(red.y) || Float.isInfinite(red.y)){
                Debug.chat("[Bow Aim] Arrow failed to reach the target");
            }else{
                mc.interactionManager.sendSequencedPacket(mc.world, (s)->{
                    return new PlayerInteractItemC2SPacket(mc.player.getActiveHand(), s, red.y, red.x);
                });
            }
        }


        mc.player.setPitch(playerPitchYaw.x);
        mc.player.setYaw(playerPitchYaw.y);

    }
    public void onModulePreset(Event<EventContainer<ModulePreset>> event){
        ModulePreset preset = event.context().getValue();
        switch (preset){
            case HACKING, VANILLA -> {
                if(tpDistance.get() < 0){
                    tpDistance.set(-tpDistance.get());
                }
                if(mode.get() == Configs.LegalInteractMode.DELAY_MOVEMENT){
                    mode.set(Configs.LegalInteractMode.MOVEMENT);
                }
            }
            default -> {
                if(tpDistance.get() > 0){
                    tpDistance.set(-tpDistance.get());
                }
                if(mode.get() == Configs.LegalInteractMode.MOVEMENT){
                    mode.set(Configs.LegalInteractMode.DELAY_MOVEMENT);
                }
            }
        }
    }




}
