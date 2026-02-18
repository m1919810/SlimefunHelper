package me.matl114.hacks.modules.move;

import java.util.EnumMap;
import java.util.Locale;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.Tasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.utils.Debug;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.MaceItem;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;

public class NoFall extends BaseModule implements LegalMovementManager.MovementModifier {

    public static LegalMovementManager.DelegateMovementModifier instance;

    public NoFall() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            // register at here for the first time
            MovTasks.PLAYER_PIPELINE_POS.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public static final String[] MOVE_NOFALL = {"move-safety", "no-fall", "toggle"};
    public static final String[] MOVE_NOFALL_MODE = {"move-safety", "no-fall", "bypass-mode"};
    public static final String[] MOVE_NOFALL_SAFE_DISTANCE = {"move-safety", "no-fall", "safe-distance-modify"};

    public final FlagRef noFall = builder(Configs.MOV_CONFIG, Boolean.class)
            .path(MOVE_NOFALL)
            .defaultValue(false)
            .apply(this::bindFlag)
            .build();
    public final EnumRef<NofallBypassMode> noFallModel = builder(Configs.MOV_CONFIG, NofallBypassMode.class)
            .path(MOVE_NOFALL_MODE)
            .defaultValue(NofallBypassMode.NO_BYPASS)
            .build();

    public final IntRef noFallSafeDistance = builder(Configs.MOV_CONFIG, Integer.class)
            .path(MOVE_NOFALL_SAFE_DISTANCE)
            .defaultValue(0)
            .build();

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public boolean mayModifyPos() {
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initArguments();
        initDelegate();
    }

    private void initDelegate() {
        delegateMap.clear();
        delegateMap.put(NofallBypassMode.NO_BYPASS, new NoFallNoBypass(this));
        delegateMap.put(NofallBypassMode.LAZY_MODE, new NoFallLazy(this));
        delegateMap.put(NofallBypassMode.BYPASS_GRIM, new NoFallBypassGrim(this));
        delegateMap.put(NofallBypassMode.LAZY_BYPASS_GRIM, new NoFallLazyBypassGrim(this));
    }

    private void initArguments() {
        lastOnGroundHeight = Integer.MIN_VALUE;
        lastHeight = 0;
    }

    protected void onSetback(Event<MovTasks.MovInfo> setBackEvent) {
        getDelegate().onSetback(setBackEvent);
    }

    protected void onVcUpdate(Event<Vec3d> vc) {
        if (vc.getArgs(0) == mc.player) {
            if (getDelegate() instanceof NoFallLazyBypassGrim grimLazy) {
                grimLazy.onVelocity(vc);
            }
        }
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getTeleportConfirmResponsePoint(), this::onSetback);
        registerListener(Listener.getEntityClientVelocityUpdate(), this::onVcUpdate);
        registerListener(Listener.getPlayerInitConfiguration(), this::onPlayerInit);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
    }

    public void onPlayerInit(Event<ClientPlayerEntity> player) {
        initArguments();
        initDelegate();
    }

    public void onPlayerTickVelocity(Event<Vec3d> tickEvent) {
        if (getDelegate() instanceof NoFallLazyBypassGrim grimLazy) {}
    }

    protected <T extends NoFallDelegate> T getDelegate() {
        return (T) delegateMap.get(noFallModel.get());
    }
    // global status
    double lastOnGroundHeight = Integer.MIN_VALUE;
    double lastHeight;
    // current tick status
    boolean holdingMace = false;

    private static final int ENTITY_STAGE_INITIALIZING = 0;
    private static final int ENTITY_STAGE_ALIVE = 1;
    private static final int ENTITY_STAGE_ABSENT = 2;
    private static final int ENTITY_STAGE_INVULNERABLE = 3;
    int entityStage = 0;
    double safeDistance = 0;
    NoFallDelegate runningDelegate = null;
    // delegate map
    EnumMap<NofallBypassMode, LegalMovementManager.MovementModifier> delegateMap =
            new EnumMap<>(NofallBypassMode.class);

    private boolean unsafeFallDistance() {
        return lastHeight <= lastOnGroundHeight - safeDistance;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        runningDelegate = getDelegate();
        ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
        Vec3d pos = args.getPos();
        if (pos == null) {
            entityStage = ENTITY_STAGE_INITIALIZING;
            return;
        }
        // filter creative playerGaming

        if (args.getAbilities().invulnerable) {
            entityStage = ENTITY_STAGE_INVULNERABLE;
            return;
        }
        if (!args.isAlive()) {
            entityStage = ENTITY_STAGE_ABSENT;
            return;
        }
        entityStage = ENTITY_STAGE_ALIVE;
        holdingMace = args.getMainHandStack().getItem() instanceof MaceItem;
        lastHeight = args.getY();
        safeDistance = args.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE) + noFallSafeDistance.get();
        // reset
        if (args.isOnGround()
                || args.isTouchingWater()
                || args.getBlockStateAtPos().isOf(Blocks.BUBBLE_COLUMN)) {
            lastOnGroundHeight = lastHeight;
            runningDelegate.counter = 0;
        }
        // player fly up for a few blocks, also resets distance
        else if (lastHeight > lastOnGroundHeight) {
            lastOnGroundHeight = lastHeight;
            runningDelegate.counter = 0;
        }
        runningDelegate.applyPreTickModify(movementManagerEvent);
        // ret in this
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        if (entityStage == ENTITY_STAGE_ALIVE) {
            runningDelegate.applyAfterInputTick(movementManagerEvent);
        }
    }

    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        if (entityStage == ENTITY_STAGE_ALIVE) {
            runningDelegate.applyBeforeMovementPacketModify(movementManagerEvent);
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (entityStage == ENTITY_STAGE_ALIVE) {
            runningDelegate.postModify(movementManagerEvent, enabledThisTick);
            runningDelegate.runningThisTick = false;
        }
        return true;
    }

    public abstract static class NoFallDelegate implements LegalMovementManager.MovementModifier {
        NoFall module;
        int counter = 0;
        boolean noFallSetbackResponse = false;
        boolean runningThisTick = false;

        public NoFallDelegate(NoFall module) {
            this.module = module;
        }

        public void onSetback(Event<MovTasks.MovInfo> setBack) {
            noFallSetbackResponse = false;
        }
    }

    public static class NoFallNoBypass extends NoFallDelegate {

        public NoFallNoBypass(NoFall module) {
            super(module);
        }

        @Override
        public void onSetback(Event<MovTasks.MovInfo> event) {
            // todo: 何意味
            if (module.isActive()
                    && (event.context.oGroundOverride() == null
                            || noFallSetbackResponse != (boolean) event.context.oGroundOverride())) {
                var info = event.context();
                event.context(
                        new MovTasks.MovInfo(info.vec3d(), noFallSetbackResponse, false, info.rotationOverride()));
            }
            super.onSetback(event);
        }

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
            boolean forceNoFall = ClientPlayerAccess.of(args).isForceNoFall();
            if (module.isActive() || forceNoFall) {
                if (forceNoFall || module.unsafeFallDistance()) {
                    if (!module.holdingMace) {
                        runningThisTick = true;
                        // TODO LAZY MODE, only if we trigger not onground -> onground should we reset
                        counter = 0;
                        module.lastOnGroundHeight = args.getY();

                        args.setPosition(args.getPos().add(0, +1E-8, 0));
                        mc.getNetworkHandler()
                                .sendPacket(VPacket.newPositionAndOnGround(
                                        args.getX(),
                                        args.getY(),
                                        args.getZ(),
                                        !forceNoFall && args.isOnGround(),
                                        args.horizontalCollision));
                        noFallSetbackResponse = true;
                    }
                } else {
                    counter += 1;
                }
                if (forceNoFall) {
                    ClientPlayerAccess.of(args).setForceNoFall(false);
                }
                // ?
                if (counter > 100) {
                    // whatever , reset this flag
                    noFallSetbackResponse = false;
                }
            }
        }

        public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            // do nothing
        }

        @Override
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            // do nothing
            return true;
        }
    }

    public static class NoFallLazy extends NoFallDelegate {
        int noFallCnt = -1;
        boolean afterSetbackFlag = false;
        Boolean shouldApplyOnGroundReverseNextTick;

        public NoFallLazy(NoFall module) {
            super(module);
        }

        @Override
        public void onSetback(Event<MovTasks.MovInfo> setBack) {
            afterSetbackFlag = true;
            super.onSetback(setBack);
        }

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
            boolean forceNoFall = ClientPlayerAccess.of(args).isForceNoFall();

            if (forceNoFall) {
                runningThisTick = true;
                // TODO LAZY MODE, only if we trigger not onground -> onground should we reset
                counter = 0;
                module.lastOnGroundHeight = args.getY();

                args.setPosition(args.getPos().add(0, +1E-8, 0));
                mc.getNetworkHandler()
                        .sendPacket(VPacket.newPositionAndOnGround(
                                args.getX(), args.getY(), args.getZ(), false, args.horizontalCollision));
                noFallSetbackResponse = true;
                ClientPlayerAccess.of(args).setForceNoFall(false);
            } else if (module.isActive()) {
                counter += 1;
            }
            // ?
            if (counter > 100) {
                noFallSetbackResponse = false;
            }
        }
        // todo: copy the nofall position check from NoFallBypassGrimLazy

        public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            if (module.isActive()) {
                var entity = movementManagerEvent.context.playerStatus;
                // check Y after fall
                if (afterSetbackFlag || (entity.entity.getY() <= module.lastOnGroundHeight - module.safeDistance)) {
                    if (!runningThisTick) {
                        // apply only once

                        if (!entity.onGround && entity.entity.isOnGround()) {
                            afterSetbackFlag = false;
                            runningThisTick = true;
                            counter = 0;
                            // use history y
                            module.lastOnGroundHeight = entity.pos.getY();
                            mc.getNetworkHandler()
                                    .sendPacket(VPacket.newPositionAndOnGround(
                                            entity.pos.getX(),
                                            entity.pos.getY() + 1E-8,
                                            entity.pos.getZ(),
                                            false,
                                            entity.horizontalCollision));
                            // todo: 测试终止横向动量 减少grimac发包
                            //                                    entity.entity.setPos(entity.pos.getX(),
                            // entity.entity.getY() , entity.pos.getZ());
                            noFallSetbackResponse = true;

                            return;
                        }
                    }
                }
            }
        }

        @Override
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            if (shouldApplyOnGroundReverseNextTick != null) {
                if (!runningThisTick) {
                    movementManagerEvent.context.playerStatus.entity.setOnGround(shouldApplyOnGroundReverseNextTick);
                }
                shouldApplyOnGroundReverseNextTick = null;
            }
            if (runningThisTick) {
                // shouldApplyOnGroundReverseNextTick = movementManagerEvent.context.playerStatus.entity.isOnGround();
            }
            return true;
        }
    }

    public static class NoFallBypassGrim extends NoFallDelegate {
        boolean canDoJump = false;
        boolean doJump = false;
        boolean nofallWaitSetbackFlag = false;
        int waitTimeout = 0;

        public NoFallBypassGrim(NoFall module) {
            super(module);
        }

        @Override
        public void onSetback(Event<MovTasks.MovInfo> event) {
            nofallWaitSetbackFlag = false;
            super.onSetback(event);
        }

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            if (canDoJump) {
                //                    mc.player.addVelocityInternal(new Vec3d(0, 8, 0));
                if (!nofallWaitSetbackFlag) {
                    // a nofall packet comes
                    doJump = true;
                    canDoJump = false;
                    //                        Debug.info("trigger jump tick");
                    mc.player.setOnGround(true);
                    // TODO 1.21.2+ may need this, check code then
                    mc.options.jumpKey.setPressed(true);
                    //                        Vec3d vc = mc.player.getVelocity();
                    //                        mc.player.setVelocity(vc.x, 0.1, vc.z);
                } else {
                    // should not send onGround
                    // do not send pos
                    waitTimeout += 1;
                    if (waitTimeout >= 2) {
                        waitTimeout = 0;
                        canDoJump = false;
                        nofallWaitSetbackFlag = false;
                        mc.player.setOnGround(true);
                    } else {
                        mc.player.setOnGround(false);
                    }
                }
            }
            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
            boolean forceNoFall = ClientPlayerAccess.of(args).isForceNoFall();
            if (module.isActive() || forceNoFall) {
                if (forceNoFall || module.unsafeFallDistance()) {
                    if (!args.isOnGround()) {
                        runningThisTick = true;
                    }
                    // resync lastOnGroundHeigth in this method
                    counter = 0;
                } else if (args.isOnGround()) {
                    // todo check if this is at risk

                    module.lastOnGroundHeight = module.lastHeight;
                } else {
                    counter += 1;
                }
                if (counter >= 100) {
                    noFallSetbackResponse = false;
                }
            }
        }

        public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            if (canDoJump) {
                // wait for set back packets to do jump
                movementManagerEvent.cancel();
                // restore pos
                movementManagerEvent.context.playerStatus.restorePos();
                return;
            }
            if (module.isActive()) {
                if (runningThisTick) {
                    //                    movementManagerEvent.cancel();
                    //                    movementManagerEvent.context().playerStatus.restorePos();
                    ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                    //                    if(waitingForSetback && waitForSetbackId == waitForSetBack){
                    //                        waitTimeout += 1;
                    //                        if(waitTimeout >= 5){
                    //                            waitingForSetback = false;
                    //                            player.fallDistance = 0.0f;
                    //                            lastOnGroundHeight = player.getY();
                    //                            return;
                    //                        }else{
                    //                            movementManagerEvent.cancel();
                    //                            movementManagerEvent.context.playerStatus.restorePos();
                    //                            return;
                    //                        }
                    //                    }
                    if (player.isOnGround()) {
                        // onGround
                        // collide on ground should be
                        runningThisTick = true;

                        //                        player.setPos(player.getX(), player.getY() + 5E-2, player.getZ());
                        //                        Debug.info(player.getVelocity());
                        //                        player.setPos(player.getX(), player.getY() + 1E-8, player.getZ());

                        //                        player.setOnGround(false);
                        // cancel , do not restore pos
                        movementManagerEvent.cancel();

                        mc.getNetworkHandler().sendPacket(VPacket.newOnGroundOnly(true, player.horizontalCollision));
                        // 包吃住 不要过
                        noFallSetbackResponse = false;
                        ClientPlayerAccess.of(player).setForceNoFall(false);
                        module.lastOnGroundHeight = player.getY();

                        nofallWaitSetbackFlag = true;
                        canDoJump = true;
                        waitTimeout = 0;
                        runningThisTick = false;
                        //
                        // mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(player.getX(),
                        // player.getY(), player.getZ(),false));

                    } else {
                        runningThisTick = false;
                    }
                }
            }
        }

        @Override
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            if (canDoJump) return true;
            ClientPlayerEntity player = movementManagerEvent.context().playerStatus.entity;
            if (doJump) {

                mc.options.jumpKey.setPressed(false);
                doJump = false;
                player.setOnGround(false);
            }

            return true;
        }
    }
    // tested on mc.loyisa.cn server, 2026.1.14
    // also , many problems are here
    // not stable
    // stable when no horizontal velocity exists landing <- what the fuck
    // see log analysis for more information
    // todo: grim code analysis
    // todo: can not work in 1.21.11
    public static class NoFallLazyBypassGrim extends NoFallDelegate {
        int lastResyncTime = -1;
        int lastNoFall = -1;
        boolean afterSetbackFlag;
        Vec3d lastNoFallPos = null;
        Boolean shouldApplyOnGroundReverseNextTick;
        boolean duplicatingNoFall = false;
        int duplicateCount = 0;
        // left for usage
        boolean flag1;
        boolean flag2;
        int cnt1;
        int cnt2;
        Vec3d lastSetBackPos;
        int duplicateSetback = 0;
        private static final int latency = 10;

        public NoFallLazyBypassGrim(NoFall module) {
            super(module);
            this.afterSetbackFlag = true;
            this.duplicateCount = 0;
        }

        @Override
        public void onSetback(Event<MovTasks.MovInfo> setBack) {
            afterSetbackFlag = true;
            lastResyncTime = Tasks.getTick();
            Vec3d vc3d = setBack.context.vec3d();
            // our movements are 1E-8, mc movements 1E-7
            boolean thisduplicateSetback = lastSetBackPos != null && lastSetBackPos.squaredDistanceTo(vc3d) < 1E-12;
            if (thisduplicateSetback) {
                duplicateSetback += 1;
                if (duplicateSetback > 3) {
                    duplicateSetback = 0;
                    duplicateCount = 100;
                }
            } else {
                duplicateSetback = 0;
            }
            lastSetBackPos = vc3d;
            super.onSetback(setBack);
        }

        public void onVelocity(Event<Vec3d> playerVec) {
            if (module.isActive() && Tasks.getTick() <= lastNoFall + latency) {
                Vec3d vc3d = playerVec.context();
                if (vc3d.y < 0.0) {
                    playerVec.context(new Vec3d(vc3d.x, 0.0D, vc3d.z));
                }
                // 不知道为什么
                // 总之他起作用 我们先别动她
            }
        }

        public void onPlayerVelocityTick(Event<Vec3d> pv) {}

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
            boolean forceNoFall = ClientPlayerAccess.of(args).isForceNoFall();
            if (forceNoFall) {
                runningThisTick = true;
                // TODO LAZY MODE, only if we trigger not onground -> onground should we reset
                counter = 0;
                module.lastOnGroundHeight = args.getY();

                args.setPosition(args.getPos().add(0, +1E-8, 0));
                mc.getNetworkHandler()
                        .sendPacket(VPacket.newPositionAndOnGround(
                                args.getX(), args.getY(), args.getZ(), false, args.horizontalCollision));
                noFallSetbackResponse = true;
                ClientPlayerAccess.of(args).setForceNoFall(false);
            } else if (module.isActive()) {
                counter += 1;
            }
            // ?
            if (counter > 100) {
                noFallSetbackResponse = false;
            }
        }

        @Override
        public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
            if (module.isActive() && duplicateCount >= 1) {
                // do not make velocity input
                // Debug.info("check input");
                var input = movementManagerEvent.context.playerStatus.entity.input;
                input.movementForward = 0;
                input.movementSideways = 0;
            }
        }

        public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            if (module.isActive()) {
                var entity = movementManagerEvent.context.playerStatus;
                // check Y after fall
                duplicatingNoFall = false;
                if (lastNoFallPos != null) {
                    // near
                    if (Math.abs(lastNoFallPos.y - entity.entity.getY()) < 1e-2
                            && entity.entity.getPos().squaredDistanceTo(lastNoFallPos) < 1
                            && lastNoFall + latency >= Tasks.getTick()) {
                        duplicatingNoFall = true;
                    }
                }
                boolean shouldCheck;
                if (duplicatingNoFall) {
                    // must check?
                    shouldCheck = true;
                } else {
                    // If there is a recent nofall without duplicate , then it must be the first nofall, check it
                    // carefully
                    shouldCheck = (lastNoFall + latency >= Tasks.getTick())
                            || afterSetbackFlag
                            || (entity.entity.getY() <= module.lastOnGroundHeight - module.safeDistance);
                }
                boolean shouldCheckHard = duplicatingNoFall || (lastNoFall + latency >= Tasks.getTick());
                if (!shouldCheckHard) {
                    duplicateCount = 0;
                }
                // Debug.info("should check : " + shouldCheck);
                if (shouldCheck) {
                    if (!runningThisTick) {
                        // apply only once
                        //                        if(Tasks.getTick() > lastResyncTime + 5 && Tasks.getTick() >
                        // lastNoFall + 5){
                        //                            // if not a  ac resync,
                        //                            // just do not check to avoid byd packet flood
                        //                            afterSetbackFlag = false;
                        //                        }
                        // check
                        if (!shouldCheckHard) {
                            afterSetbackFlag = false;
                        }
                        if (!entity.onGround && entity.entity.isOnGround()) {
                            if (duplicatingNoFall) {
                                duplicateCount += 1;
                            } else {
                                duplicateCount = 1;
                            }
                            if (duplicateCount > 20) {
                                // maybe it is stucked, we just refresh this
                                lastNoFall = -1;
                                lastNoFallPos = null;
                                duplicateCount = 0;
                                // Debug.info("cancel noFall because it duplicates");
                                return;
                            }
                            afterSetbackFlag = false;
                            runningThisTick = true;
                            lastNoFall = Tasks.getTick();
                            counter = 0;
                            // use history y //todo try use nofall pos as this
                            lastNoFallPos = entity.entity.getPos();
                            // todo figure out why sync flood happens
                            // todo: try send it eariler

                            // Debug.info("update 4");
                            module.lastOnGroundHeight = entity.pos.getY();
                            mc.getNetworkHandler()
                                    .sendPacket(VPacket.newPositionAndOnGround(
                                            entity.pos.getX(),
                                            entity.pos.getY() + 1E-8,
                                            entity.pos.getZ(),
                                            false,
                                            entity.horizontalCollision));
                            //                            entity.entity.setPos(entity.pos.getX(), entity.entity.getY(),
                            // entity.pos.getZ());
                            //                            entity.entity.setVelocity(0.0, 0.0, 0.0);
                            noFallSetbackResponse = true;

                            // todo: 测试终止横向动量 减少grimac发包
                            //                                    entity.entity.setPos(entity.pos.getX(),
                            // entity.entity.getY() , entity.pos.getZ());
                            // idk
                            return;
                        }
                    }
                }
            }
        }

        @Override
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            if (shouldApplyOnGroundReverseNextTick != null) {
                if (!runningThisTick) {
                    movementManagerEvent.context.playerStatus.entity.setOnGround(shouldApplyOnGroundReverseNextTick);
                }
                shouldApplyOnGroundReverseNextTick = null;
            }
            if (runningThisTick) {
                // shouldApplyOnGroundReverseNextTick = movementManagerEvent.context.playerStatus.entity.isOnGround();
            }
            return true;
        }
    }

    public static enum NofallBypassMode implements ConfigEnum {
        NO_BYPASS,
        LAZY_MODE,
        BYPASS_GRIM,
        @ApiStatus.Experimental
        LAZY_BYPASS_GRIM;

        @Override
        public Text getDisplay() {
            return Text.translatable(
                    "configenum.nofall-bypass-mode." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        var modulePreset = presetEvent.context().getValue();
        switch (modulePreset) {
            case AC_GRIM -> {
                if (noFallModel.get() != NofallBypassMode.LAZY_BYPASS_GRIM) {
                    noFallModel.set(NofallBypassMode.BYPASS_GRIM);
                    if (noFall.get()) {
                        Debug.chat("正在切换到GrimNoFall模式, 该功能可能在最新版本失效, 若失效请手动切换LazyGrim模式");
                    }
                }
            }
            default -> {
                noFallModel.set(NofallBypassMode.LAZY_MODE);
            }
        }
    }
}
