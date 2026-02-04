package me.matl114.hacks.modules.move;

import me.matl114.events.Listener;
import me.matl114.hacks.Tasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.events.Event;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public class ElytraExtra extends BaseModule {
    public static final String[] MOVE_UNBREAKABLE_ELYTRA = {"move-safety", "unbreakable-elytra"};

    public ElytraExtra() {

    }

    public final FlagRef enableUnbreakableElytra = flagBuilder(Configs.MOV_CONFIG, MOVE_UNBREAKABLE_ELYTRA)
        .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerFallFlyingTick(), this::runElytraUnbreakable);
        registerListener(Listener.getEntityTrackDataUpdate(), this::handleEntityDataUpdate);
    }

    //TODO: fake elytra flight figure it out: NO USE, server player pose will not change
    //elytra unbreakable?

    //TODO: Elytra Mode: velocity control, fake creative flight, rewrite this elytra unbreakable
    //TODO: armor flight,
    private int fakeGlideTime = 0;
    private int fakeGlidePoseTime = 0;
    public void runElytraUnbreakable(Event<Integer> tickEvent){
        if(enableUnbreakableElytra.get() && tickEvent.context() >= 18){
            fakeGlideTime += 1;
            fakeGlidePoseTime += 1;
            if(mc.player != null && mc.player.isFallFlying()){
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
//                Debug.info("send stop glide");
            }
            Tasks.scheduleDelayed(()->{
                if(mc.player != null && mc.player.isFallFlying() && !mc.player.isOnGround()){
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
//                    Debug.info("send restart glide");
                }else{
//                    Debug.info("not glide anymore");
                }
            }, 3);

            tickEvent.context(0);
        }
    }
    public static boolean isUsable(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }

    //may cause fake gliding !!! must be careful
    public void handleEntityDataUpdate(Event<DataTracker.SerializedEntry<?>> serializedEntryMutableObject){
        if(serializedEntryMutableObject.isCancelled())return;
        //only when elytra unbreakable do
        if(enableUnbreakableElytra.get() && serializedEntryMutableObject.extraArgs().length > 0 && serializedEntryMutableObject.extraArgs()[0] instanceof ClientPlayerEntity player && player == mc.player && player.isGliding() && !player.isOnGround() && !player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.LEVITATION)){
            ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
            //do all the checks to avoid ghost gliding
            if (VItem.getInstance().canGlide(itemStack) && isUsable(itemStack)) {
                var val = serializedEntryMutableObject.context();

                if(val.id() == 0){
                    byte data = (byte) val.value();
                    if( (data & (1 << 7 )) == 0){
//                        Debug.info("[data]stop gliding");
                        if(fakeGlideTime > 0){
                            fakeGlideTime -= 1;
                            //cancel stop fallflying
                            // ;
                            serializedEntryMutableObject.context(new DataTracker.SerializedEntry(val.id(), val.handler(), (byte)(data | (1 << 7))));
                        }

                    }else{
//                        Debug.info("[data]start gliding");
                    }
                }else if(val.id() == 6){
                    //standing pose
                    EntityPose pose = (EntityPose) val.value();
                    if(fakeGlidePoseTime >0 && pose != EntityPose.GLIDING && player.getPose() == EntityPose.GLIDING){
                        //cancel pose sync
                        fakeGlidePoseTime -= 1;

                    }
                }
            }

        }
    }
}
