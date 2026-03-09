package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.versioned.api.VDataFlag;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public class ElytraExtra extends BaseModule {
    public static final String[] MOVE_UNBREAKABLE_ELYTRA = {"elytra", "unbreakable-elytra", "enable"};

    public static final String[] MOVE_ELYTRA_CHECK_PREIOD = {"elytra", "unbreakable-elytra", "period"};

    public static final String[] MOVE_ELYTRA_DELAY = {"elytra", "unbreakable-elytra", "delay"};

    public ElytraExtra() {}

    public final FlagRef enableUnbreakableElytra =
            flagBuilder(Configs.MOV_CONFIG, MOVE_UNBREAKABLE_ELYTRA).build();

    public final IntRef period = builder(Configs.MOV_CONFIG, MOVE_ELYTRA_CHECK_PREIOD, IntRef.TYPE)
            .defaultValue(16)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef delay = builder(Configs.MOV_CONFIG, MOVE_ELYTRA_DELAY, IntRef.TYPE)
            .defaultValue(3)
            .validator(Configs.INT_POSITIVE)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerFallFlyingTick(), this::runElytraUnbreakable);
        registerListener(Listener.getEntityTrackDataUpdate(), this::handleEntityDataUpdate);
    }

    // TODO: fake elytra flight figure it out: NO USE, server player pose will not change
    // elytra unbreakable?

    // TODO: Elytra Mode: velocity control, fake creative flight, rewrite this elytra unbreakable
    // TODO: armor flight,
    private int fakeGlideTime = 0;
    private int fakeGlidePoseTime = 0;
    // todo: check unbreakable flag
    // todo: 鞘翅甲飞
    // todo: 动量控制
    public void runElytraUnbreakable(Event<Integer> tickEvent) {
        if (enableUnbreakableElytra.get() && tickEvent.context() >= period.get()) {
            fakeGlideTime += 1;
            fakeGlidePoseTime += 1;
            if (mc.player != null && mc.player.isFallFlying()) {
                mc.getNetworkHandler()
                        .sendPacket(
                                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                //                Debug.info("send stop glide");
            }
            Tasks.scheduleDelayed(
                    () -> {
                        if (mc.player != null && mc.player.isFallFlying() && !mc.player.isOnGround()) {
                            mc.getNetworkHandler()
                                    .sendPacket(new ClientCommandC2SPacket(
                                            mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                            //                    Debug.info("send restart glide");
                        } else {
                            //                    Debug.info("not glide anymore");
                        }
                    },
                    delay.get());

            tickEvent.context(0);
        }
    }

    public static boolean isUsable(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }

    // may cause fake gliding !!! must be careful
    public void handleEntityDataUpdate(Event<DataTracker.SerializedEntry<?>> serializedEntryMutableObject) {
        if (serializedEntryMutableObject.isCancelled()) return;
        // only when elytra unbreakable do
        if (enableUnbreakableElytra.get()
                && serializedEntryMutableObject.extraArgs().length > 0
                && serializedEntryMutableObject.extraArgs()[0] instanceof ClientPlayerEntity player
                && player == mc.player
                && player.isFallFlying()
                && !player.isOnGround()
                && !player.isTouchingWater()
                && !player.hasStatusEffect(StatusEffects.LEVITATION)) {
            ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
            // do all the checks to avoid ghost gliding
            if (VItem.getInstance().canGlide(itemStack) && isUsable(itemStack)) {
                var val = serializedEntryMutableObject.context();

                if (val.id() == VDataFlag.ID_FLAGS) {
                    byte data = (byte) val.value();
                    if ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) == 0) {
                        //                        Debug.info("[data]stop gliding");
                        if (fakeGlideTime > 0) {
                            fakeGlideTime -= 1;
                            // cancel stop fallflying
                            // ;
                            serializedEntryMutableObject.context(new DataTracker.SerializedEntry(
                                    val.id(), val.handler(), (byte) (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                        }

                    } else {
                        //                        Debug.info("[data]start gliding");
                    }
                } else if (val.id() == VDataFlag.ID_POSE) {
                    // standing pose
                    EntityPose pose = (EntityPose) val.value();
                    if (fakeGlidePoseTime > 0
                            && pose != EntityPose.FALL_FLYING
                            && player.getPose() == EntityPose.FALL_FLYING) {
                        // cancel pose sync
                        fakeGlidePoseTime -= 1;
                    }
                }
            }
        }
    }
}
