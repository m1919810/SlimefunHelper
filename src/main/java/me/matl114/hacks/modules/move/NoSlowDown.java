package me.matl114.hacks.modules.move;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.PlayerInteractEntityC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.ACPostTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.versioned.api.VDataFlag;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class NoSlowDown extends BaseModule implements LegalMovementManager.MovementModifier {
    public static final String[] NO_SLOW_DOWN_SNEAK = {"move-speed", "no-slowdown", "when-sneak"};
    public static final String[] NO_SLOW_DOWN_USEITEM = {"move-speed", "no-slowdown", "when-use-item"};
    //    public static final String[] NO_SLOW_DOWN_BLOCK_FRAC = {"move-speed","no-slowdown", "when-walk-on-block"};
    public static final String[] NO_SLOW_DOWN_BLOCK_SLOW = {"move-speed", "no-slowdown", "when-with-block"};
    public static final String[] NO_SLOW_DOWN_BLOCK_FRAC = {"move-speed", "no-slowdown", "when-on-block"};
    public static final String[] NO_SLOW_DOWN_BLOCK_IN = {"move-speed", "no-slowdown", "when-in-block"};
    public static final String[] NO_SLOW_DOWN_BLOCK_SPECIAL = {"move-speed", "no-slowdown", "when-special-block"};
    public static final String[] FAKE_SNEAK = {"move-speed", "no-slowdown", "fake-sneak"};
    public static final String[] FAKE_SNEAK_HOTKEY = {"move-speed", "no-slowdown", "fake-sneak-hotkey"};
    public static final String[] FAKE_SNEAK_MODE = {"move-speed", "no-slowdown", "fake-sneak-mode"};
    public static final String[] FAKE_SNEAK_STATUS = {"move-speed", "fake-sneak-status"};
    public static final String[] FAKE_SNEAK_STATUS_MODE = {"move-speed", "no-slowdown", "fake-sneak-status-mode"};

    public static LegalMovementManager.DelegateMovementModifier instance;

    public NoSlowDown() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            // register at here for the first time
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(this::newMovementInstance);
        }
        instance.setDelegate(this::cast);
    }

    private LegalMovementManager.DelegateMovementModifier newMovementInstance() {
        resetPlayer();
        return instance;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(Listener.getEntityTrackDataUpdate().getChannel(EntityType.PLAYER), this::onServerSyncSneak);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class), this::onInteractSend);
    }

    public final FlagRef sneak =
            flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_SNEAK).build();

    public final FlagRef useItem =
            flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_USEITEM).build();

    public final FlagRef blockSlow =
            flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_BLOCK_SLOW).build();

    public final FlagRef blockFrac =
            flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_BLOCK_FRAC).build();

    public final FlagRef blockIn =
            flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_BLOCK_IN).build();

    public final FlagRef blockSpecial =
            flagBuilder(Configs.MOV_CONFIG, NO_SLOW_DOWN_BLOCK_SPECIAL).build();

    public final FlagRef enableFakeSneak =
            flagBuilder(Configs.MOV_CONFIG, FAKE_SNEAK).build();

    public final KeyBindRef keyBindRef = toggleHotkey(
                    Configs.MOV_CONFIG, FAKE_SNEAK_HOTKEY, new MultiKeyBind(), FAKE_SNEAK)
            .build();

    public final EnumRef<Configs.BypassMode> fakeSneakBypass = builder(
                    Configs.MOV_CONFIG, FAKE_SNEAK_MODE, Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

    public final KeyBindRef fakeStatus = hotkey(Configs.MOV_CONFIG, FAKE_SNEAK_STATUS)
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::onSneakStatus))
            .build();

    public final EnumRef<Configs.BypassMode> fakeStatusBypass = builder(
                    Configs.MOV_CONFIG, FAKE_SNEAK_STATUS_MODE, Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        ModulePreset preset = event.context().getValue();
        switch (preset) {
            case HACKING -> {
                sneak.set(true);
                useItem.set(true);
                blockSlow.set(true);
                blockFrac.set(true);
                blockIn.set(true);
                blockSpecial.set(true);
            }
            default -> {
                sneak.set(false);
                useItem.set(false);
                blockSlow.set(false);
                blockFrac.set(false);
                blockIn.set(false);
                blockSpecial.set(false);
            }
        }
    }

    public void resetPlayer() {
        sneakStatus = false;
    }

    boolean sneakStatus = false;

    public void onSneakStatus() {
        if (mc.player == null) return;
        if (sneakStatus) {
            sneakStatus = false;
            ClientPlayerAccess.of(mc.player).resyncSneak();
            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.getLastPlayerInput()));
            Debug.chat("[NoSlow] 取消当前伪造潜行状态");
        } else {
            Entity entity;
            boolean canBypass;
            if (mc.crosshairTarget instanceof EntityHitResult entityHitResult) {
                entity = entityHitResult.getEntity();
                canBypass = true;
            } else {

                List<Entity> entities = new ArrayList<>();
                for (var et : mc.world.getEntities()) {
                    if (et != mc.player) {
                        entities.add(et);
                    }
                }
                entities.sort(Comparator.comparingDouble(s -> s.squaredDistanceTo(mc.player)));
                if (!entities.isEmpty()) {
                    entity = entities.get(0);
                    canBypass = false;
                } else {
                    entity = null;
                    canBypass = false;
                }
            }
            if (canBypass || !fakeStatusBypass.get().hasAc()) {
                int id = entity == null ? mc.player.getId() - 1 : entity.getId();
                PlayerInputUtils.Input input = PlayerInputUtils.of(mc.player.input);
                // to trigger plugin events
                input.sneak(true).sendPlayerInputPacket();
                input.sneak(false).sendPlayerInputPacket();
                mc.interactionManager.sendSequencedPacket(mc.world, (seq) -> {
                    return new PlayerInteractEntityC2SPacket(
                            id,
                            true,
                            new PlayerInteractEntityC2SPacket.InteractAtHandler(Hand.MAIN_HAND, mc.player.getPos()));
                });
                sneakStatus = true;
                Debug.chat("[NoSlow] 成功伪造状态");
            } else {
                // out of interact range
                if (entity == null
                        || entity.getBoundingBox().squaredMagnitude(mc.player.getEyePos())
                                > MathUtils.s2(mc.player.getEntityInteractionRange() + 0.5)) {
                    Debug.chat("[NoSlow] 当前模式下需要一个实体以交互");
                    return;
                }
                Entity target = Objects.requireNonNull(entity);
                ClientPlayerAccess.of(mc.player)
                        .getLegalMovementManager()
                        .addMovementModifier(new LegalMovementManager.MovementModifier() {
                            Vec3d velocity;

                            @Override
                            public int priority() {
                                return PRIORITY_LOW;
                            }

                            @Override
                            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                                ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
                                // step back our position
                                velocity = args.getVelocity();

                                Vec3d eyePos = target.getEyePos();
                                Vec3d targetPos = target.getPos();
                                Vec3d attackOffsetted =
                                        targetPos.add(eyePos.subtract(targetPos).multiply(0.8));
                                Vec3d cacheDirection = attackOffsetted
                                        .subtract(args.getEyePos())
                                        .normalize();
                                movementManagerEvent.context.pushImportantRotation(true, true);
                                EntityUtils.setEntityRotationSafe(args, cacheDirection);
                                // restore velocity after collide
                                args.setVelocity(velocity);
                            }

                            @Override
                            public boolean postModify(
                                    Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                                if (!enabledThisTick) {
                                    // rare,,, maybe
                                    return false;
                                }
                                PlayerInputUtils.Input input = PlayerInputUtils.of(mc.player.input);

                                ACPostTasks.addPostTransactionAction(han -> {
                                    // to trigger plugin events
                                    input.sneak(true).sendPlayerInputPacket();
                                    input.sneak(false).sendPlayerInputPacket();
                                    mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                                    mc.interactionManager.sendSequencedPacket(mc.world, (seq) -> {
                                        return new PlayerInteractEntityC2SPacket(
                                                target.getId(),
                                                true,
                                                new PlayerInteractEntityC2SPacket.InteractAtHandler(
                                                        Hand.MAIN_HAND, mc.player.getPos()));
                                    });
                                    mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                                    Debug.chat("[NoSlow] 成功伪造状态");
                                    sneakStatus = true;
                                });
                                movementManagerEvent.context.playerStatus.restoreRotation();
                                // return do not kept
                                return false;
                            }
                        });
            }
        }
    }

    public boolean shouldNoSlowSneak() {
        return sneak.get()
                && (!lastPredictWasSneakEdge || !fakeSneakBypass.get().hasAc());
    }

    public boolean shouldFakeSneakStatus() {
        return (sneakStatus || (enableFakeSneak.get() && checkSneakSpeed())) && mc.player.isOnGround();
    }

    private boolean checkSneakSpeed() {
        return mc.player.getAttributeValue(EntityAttributes.SNEAKING_SPEED) < 0.9F;
    }

    public void onInteractSend(Event<PlayerInteractEntityC2SPacket> interactPacket) {
        if (sneakStatus) {
            PlayerInteractEntityC2SPacket packet = interactPacket.context();
            if (!packet.isPlayerSneaking()) {
                PlayerInteractEntityC2SPacketAccess.of(packet).setPlayerSneaking(true);
            }
            //            else{
            //                if(packet.isPlayerSneaking()){
            //                    interactPacket.context(new PlayerInteractEntityC2SPacket(packet.entityId, false,
            // packet.type));
            //                }
            //            }
        }
    }

    public void onServerSyncSneak(Event<DataTracker.SerializedEntry<?>> event) {
        if (event.isCancelled()) {
            return;
        }
        if (sneakStatus && event.getArgs(0) instanceof ClientPlayerEntity player && player == mc.player) {
            var val = event.context();
            if (val.id() == VDataFlag.ID_FLAGS) {
                byte data = (byte) val.value();
                boolean sneakFlag = (data & (1 << VDataFlag.SNEAKING_FLAG_INDEX)) != 0;
                if (!sneakFlag) {
                    sneakStatus = false;
                    Debug.chat("[NoSlow] 伪造的潜行状态被重置了");
                }
            }
        }
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
        if (shouldNoSlowSneak()) {
            PlayerInputUtils.of(args.input)
                    .sneak(mc.options.sneakKey.isPressed())
                    .applyInput(args.input);
        }
    }

    BlockPos cachedPos;
    Vec3d cachedVelocity;

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
        if (shouldFakeSneakStatus()) {
            // totally shit, the sneak flag is override with playerInput,
            // fuck ojng
            // we move it to InputTick
            if (args.isSneaking()) {
                // we tend to make this work
                // add flag to remove calculation noSlow
                // use supporting plate here
                if (cachedPos != null) {
                    BlockPos supportingPos = cachedPos;
                    Vec3d velocity = args.getVelocity();
                    Vec3d vec3d = args.getPos();
                    Vec3d vec3dSupportingBlock = vec3d.subtract(0, 0.500001F, 0);
                    BlockPos underBlock = BlockPos.ofFloored(vec3dSupportingBlock);
                    BlockState state = mc.world.getBlockState(underBlock);
                    if (state.isAir() || !state.isFullCube(mc.world, underBlock)) {
                        double delta = 0.1F;
                        double xmin = supportingPos.getX() - delta;
                        double zmin = supportingPos.getZ() - delta;
                        double xmax = supportingPos.getX() + 1 + delta;
                        double zmax = supportingPos.getZ() + 1 + delta;
                        boolean xrange = (vec3d.x > xmin && vec3d.x < xmax);
                        boolean zrange = vec3d.z > zmin && vec3d.z < zmax;
                        if (!xrange || !zrange) {
                            Vec3d supportingPosCenter = supportingPos.toCenterPos();
                            boolean directionX = vec3d.x < supportingPosCenter.x;
                            boolean directionZ = vec3d.z < supportingPosCenter.z;

                            if (((!xrange) && directionX == (velocity.x < 0))
                                    || (!zrange) && directionZ == (velocity.z < 0)
                                    || (!xrange && !zrange)) {
                                this.lastPredictWasSneakEdge = true;
                            }
                        }
                    }
                }
                cachedPos = args.getVelocityAffectingPos();
                //                PlayerInputUtils.Input input = PlayerInputUtils.of(args.input);
                //                Vec3d movementInput = new Vec3d(input.sidewaysSpeed(), input.upwardSpeed(),
                // input.forwardSpeed());
                //                cachedVelocity = EntityUtils.movementInputToVelocity(movementInput, 1.0F,
                // args.getYaw());

                //                if (false) {
                //                    PlayerInputUtils.Input pinput = PlayerInputUtils.of(args.input.playerInput);
                //
                //                    // fake input as preTick
                //                    Vec3d velocity =
                // movementManagerEvent.context.playerStatus.calculateLastMoveVelocity(
                //                            pinput.forwardSpeed(), pinput.sidewaysSpeed(), pinput.jump());
                //                    if (velocity.horizontalLengthSquared() < 1e-7) {
                //                        if (lastSneakingPos != null
                //                                && lastSneakingPos.subtract(args.getPos()).length() < 1e-4) {
                //                            lastPredictWasSneakEdge = true;
                //
                //                            // Debug.chat("Edge history");
                //                        }
                //                    }
                //                    if (!this.lastPredictWasSneakEdge) {
                //                        Vec3d vec3dSimulation = ((Entity) args).adjustMovementForSneaking(velocity,
                // MovementType.SELF);
                //                        lastPredictWasSneakEdge =
                //                                Math.abs(vec3dSimulation.subtract(velocity).horizontalLengthSquared())
                // > 0;
                //                        if (lastPredictWasSneakEdge) {
                //                            lastSneakingPos = args.getPos();
                //                            // Debug.chat("Edge");
                //                        }
                //                    }
                //                }

                // Math.abs(vec3dSimulation.subtract(velocity).horizontalLengthSquared()) > 1e-7;
            }
        }
    }

    boolean lastPredictWasSneakEdge = false;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
        if (shouldFakeSneakStatus()) {
            // do not sync sneak status
            //            if(lastPredictWasSneakEdge){
            //
            //            }
            if (!lastPredictWasSneakEdge && args.isSneaking()) {
                // in lower version,
                // ClientPlayerAccess.of(args).setLastSneakFlag(args.isSneaking());
                PlayerInputUtils.of(args.input).sneak(false).applyInput(args.input);
            }
            if (lastPredictWasSneakEdge) {
                ClientPlayerAccess.of(mc.player).resyncSneak();
            }

            // args.setOnGround(true);
            // only consider on ground to avoid jump
            //
            //            if(args.isOnGround()){
            //                if(lastPredictWasSneakEdge){
            //
            //                    // met edge
            //                    movementManagerEvent.context.playerStatus.restorePos();
            //                    PlayerInput input = args.input.playerInput;
            //                    //stop input packets
            //                    args.input.playerInput =
            // PlayerInputUtils.of(input).forward(true).backward(true).left(true).right(true).toPlayerInput();
            //                }
            //
            //            }

        } else {
            //            Vec3d realMovement = args.getPos().subtract(movementManagerEvent.context.playerStatus.pos);
            //            if(Math.abs(realMovement.horizontalLengthSquared()) >0){
            //                Debug.chat("move");
            //            }
        }
        lastPredictWasSneakEdge = false;
    }

    /**
     * [05:52:11] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.0057478763745 63.5 197.50643412620974 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:52:12] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.0057478763745 63.5 197.50643412620974 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:52:13] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.0057478763745 63.5 197.50643412620974 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:52:14] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.0057478763745 63.5 197.50643412620974 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.90775312429543 63.5 197.50541941599815 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.75625323136643 63.5 197.50385067394666 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.7255395281401 63.5 197.50197943047544 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] [GrimAC] matl114 failed Simulation (vl:78.0): .071609 /gl 140
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Pos Resync [187.67,63.50,197.50]
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] [AC] 反作弊回弹! tp号:-2062579318
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos_rot 187.67353428021917 63.5 197.50299414068706 , Pitch: 75.00008 , Yaw: 450.598 , onGround: false
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.67353428021917 63.42162607581029 197.50299414068706 , Pitch: 0.0 , Yaw: 0.0 , onGround: false
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Pos Resync [187.63,63.42,197.50]
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] [AC] 反作弊回弹! tp号:-1300224046
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos_rot 187.6283697276468 63.42159999847412 197.502526473473 , Pitch: 75.00008 , Yaw: 450.598 , onGround: false
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.58411106972696 63.266378122138896 197.5020793759112 , Pitch: 0.0 , Yaw: 0.0 , onGround: false
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.52423674017348 63.035860678843875 197.50146957508343 , Pitch: 0.0 , Yaw: 0.0 , onGround: false
     * [05:52:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.45015214902384 62.731553578492104 197.50071171427942 , Pitch: 0.0 , Yaw: 0.0 , onGround: false
     */

    /**
     * [05:53:14] [Render thread/INFO] (Minecraft) [System] [CHAT] matl114: 2
     * [05:53:14] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.34316275067525 63.5 197.62313465356922 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:15] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.34316275067525 63.5 197.62313465356922 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:16] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.34316275067525 63.5 197.62313465356922 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:17] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.34316275067525 63.5 197.62313465356922 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:18] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.34316275067525 63.5 197.62313465356922 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:18] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.31376528296994 63.5 197.6235208029856 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:18] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.29771426373847 63.5 197.62373164059144 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:18] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.28895040622015 63.5 197.6238467579376 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.28416533945935 63.5 197.6239096120159 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.25476787175404 63.5 197.62429576143228 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.20931938481726 63.5 197.62489274845447 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.15510704036214 63.5 197.62560485282282 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.0961096291462 63.5 197.62637981126946 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 188.03449957117542 63.5 197.62718908804686 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.97146300791078 63.5 197.62801710263503 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.90764757266524 63.5 197.62885534806904 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.84340687326872 63.5 197.62969917954555 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.7789339796188 63.5 197.6305460610016 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.6313946077467 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.6322440637397 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.63309401618213 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.63394423968597 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.63479461118936 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.6356450635005 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.6364955599327 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.637346080455 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:19] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.63819661413046 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.6390471549876 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.6398976997659 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.64074824668515 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.6415987947734 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.64244934349986 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.64329989257484 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.64415044184008 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.64500099120923 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.6458515406351 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.64670209009194 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.64755263956567 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.64840318904865 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.64925373853666 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.71433430789182 63.5 197.64971813861106 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.70379451186733 63.5 197.6498565837358 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69803978256954 63.5 197.6499321747827 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 63.42159999847412 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: false
     * [05:53:20] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 63.26636799395752 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: false
     * [05:53:21] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 63.03584062504456 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: false
     * [05:53:21] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.731523797587016 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: false
     * [05:53:21] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.35489329934836 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: false
     * [05:53:21] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:22] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:23] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:24] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:25] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:26] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:27] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:28] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:29] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:30] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:31] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     * [05:53:32] [Render thread/INFO] (Minecraft) [System] [CHAT] Send minecraft:move_player_pos 187.69489770000797 62.0 197.6499734474991 , Pitch: 0.0 , Yaw: 0.0 , onGround: true
     */
    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return true;
    }
}
