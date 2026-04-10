package me.matl114.hacks.modules.move;

import java.util.EnumMap;
import java.util.Locale;
import java.util.function.Predicate;
import lombok.Setter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.versioned.api.VPacket;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;

public class NoFall extends BaseModule implements LegalMovementManager.MovementModifier {

    public static LegalMovementManager.DelegateMovementModifier instance;

    public NoFall() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            // register at here for the first time
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public static final String[] MOVE_NOFALL = {"move-safety", "no-fall", "toggle"};
    public static final String[] MOVE_NOFALL_MODE = {"move-safety", "no-fall", "bypass-mode"};
    public static final String[] MOVE_NOFALL_SAFE_DISTANCE = {"move-safety", "no-fall", "safe-distance-modify"};
    public static final String[] MOVE_NOFALL_INVULNERABLE_EQUIPMENT_ID = {
        "move-safety", "no-fall", "equipment-id-bypass-nofall"
    };

    public static final String[] MOVE_NOFALL_WHEN_FLY = {"move-safety", "no-fall", "disable-when-allow-flying"};

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

    public final NBTRef<Regex> equipmentIdBypass = builder(
                    Configs.MOV_CONFIG, MOVE_NOFALL_INVULNERABLE_EQUIPMENT_ID, Regex.class)
            .defaultValue(new Regex("^(SLIME.*_BOOTS)$"))
            .build();

    public final FlagRef disableFlyNoFall = builder(Configs.MOV_CONFIG, MOVE_NOFALL_WHEN_FLY, Boolean.class)
            .defaultValue(true)
            .build();

    @Override
    public int priority() {
        return PRIORITY_MONITOR;
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
        delegateMap.put(NofallBypassMode.LAZY_GRIM_PLUS, new NoFallGrimLazyPlus(this));
        delegateMap.put(NofallBypassMode.LAZY_GRIM_PLUS_2, new NoFallFuckGrimLazyPlusV2(this));
        delegateMap.put(NofallBypassMode.TEST, new NoFallNoBypass(this));
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
        registerListener(Listener.getEntityClientVelocityUpdate(), this::onPlayerTickVelocity);
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onPlayerMovePacketSend);
        registerListener(Listener.getPlayerNotFlyJumpPoint(), this::onPlayerJump);
    }

    public void onPlayerInit(Event<ClientPlayerEntity> player) {
        initArguments();
        initDelegate();
    }

    public void onPlayerTickVelocity(Event<Vec3d> tickEvent) {
        if (tickEvent.getArgs(0) instanceof ClientPlayerEntity player && player == mc.player) {
            getDelegate().onPlayerVelocity(tickEvent);
        }
    }

    public void onPlayerMovePacketSend(Event<PlayerMoveC2SPacket> movePacket) {
        var packet = movePacket.context();
        if (packet.changesPosition()) {
            lastServerY = packet.getY(0.0D);
        }
    }

    public void onPlayerJump(Event<Integer> jumpEvent){
        getDelegate().onJump(jumpEvent);
    }

    protected <T extends NoFallDelegate> T getDelegate() {
        return (T) delegateMap.get(noFallModel.get());
    }
    // global status
    @Setter
    double lastOnGroundHeight = Integer.MIN_VALUE;

    double lastHeight;
    // current tick status
    boolean holdingMace = false;
    double lastServerY = 0.0D;

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

    public boolean checkInvulnerableEquipment() {

        Predicate<String> pd = equipmentIdBypass.get().asPredicate();
        for (var slot :
                new EquipmentSlot[] {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;
            String id = Registries.ITEM.getId(stack.getItem()).getPath();
            if (pd.test(id)) {
                return true;
            }
            String sfid = ItemStackUtils.getSfId(stack);
            if (sfid != null && pd.test(sfid)) {
                return true;
            }
        }

        return false;
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

        if (args.getAbilities().invulnerable
                || (disableFlyNoFall.get() && MovTasks.getCreativeFlight().serverSideCanFly)
                || checkInvulnerableEquipment()) {
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

    @Override
    public void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
        if (entityStage == ENTITY_STAGE_ALIVE) {
            runningDelegate.applyBeforeTravelTick(movementManagerEvent, moveEvent);
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

        public void onJump(Event<Integer> jumpCooldown){
        }

        public void onPlayerVelocity(Event<Vec3d> vec3d){

        }
    }

    public static class NoFallNoBypass extends NoFallDelegate {

        public NoFallNoBypass(NoFall module) {
            super(module);
        }

        @Override
        public void onSetback(Event<MovTasks.MovInfo> event) {
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
                        // LAZY MODE: only if we trigger not onground -> onground should we reset
                        counter = 0;
                        module.lastOnGroundHeight = module.lastServerY;

                        args.setPosition(args.getPos().add(0, +1E-8, 0));
                        mc.getNetworkHandler()
                                .sendPacket(VPacket.newPositionAndOnGround(
                                        args.getX(),
                                        module.lastServerY,
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

    public static final double DELTA_Y = 9E-8;

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
                // LAZY MODE: only if we trigger not onground -> onground should we reset
                counter = 0;
                module.lastOnGroundHeight = module.lastServerY;

                mc.getNetworkHandler()
                        .sendPacket(VPacket.newPositionAndOnGround(
                                args.getX(),
                                module.lastServerY + DELTA_Y,
                                args.getZ(),
                                false,
                                args.horizontalCollision));
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

        public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            if (module.isActive()) {
                var entity = movementManagerEvent.context.playerStatus;
                // check Y after fall
                boolean yOutOfRange = (entity.entity.getY() <= module.lastOnGroundHeight - module.safeDistance);
                if (afterSetbackFlag || yOutOfRange) {
                    if (!runningThisTick) {
                        if (!yOutOfRange) {
                            // reset if it is caused by last onGround
                            afterSetbackFlag = false;
                        }
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
                                            entity.pos.getY() + DELTA_Y,
                                            entity.pos.getZ(),
                                            false,
                                            entity.horizontalCollision));
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
                // LAZY MODE: only if we trigger not onground -> onground should we reset
                counter = 0;
                module.lastOnGroundHeight = module.lastServerY;

                mc.getNetworkHandler()
                        .sendPacket(VPacket.newPositionAndOnGround(
                                args.getX(),
                                module.lastServerY + DELTA_Y,
                                args.getZ(),
                                false,
                                args.horizontalCollision));
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
                                            entity.pos.getY() + DELTA_Y,
                                            entity.pos.getZ(),
                                            false,
                                            entity.horizontalCollision));
                            //                            entity.entity.setPos(entity.pos.getX(), entity.entity.getY(),
                            // entity.pos.getZ());
                            //                            entity.entity.setVelocity(0.0, 0.0, 0.0);
                            noFallSetbackResponse = true;

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
    // tested on 1.21.11 loyisa server 3.29
    // 屎山代码能别动就别动了依旧俺寻思他能跑他就能跑
    /*
                      _ooOoo_
                     o8888888o
                     88" . "88
                     (| -_- |)
                     O\ = /O
                  ____/`---'\____
                .' \\| |// `
               / \\||| : |||// \
              / _||||| -:- |||||- \
              | | \\\ - /// | |
              | \_| ''\---/'' | |
              \ .-\__ `-` ___/-. /
            ___`. .' /--.--\ `. . __
         ."" '< `.___\_<|>_/___.' >'""
        | | : `- \`.;`\ _ /`;.`/ - ` : | |
        \ \ `-. \_ __\ /__ _/ .-` / /
    ======`-.____`-.___\_____/___.-`____.-'======
                      `=---='
    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
               佛祖保佑 永无BUG

              至今我们仍旧不知道在屎山里面发生了什么
    */
    public static class NoFallGrimLazyPlus extends NoFallDelegate {

        int lastResyncTime = -1;
        int lastNoFall = -1;
        boolean afterSetbackFlag;
        Vec3d lastNoFallPos = null;
        Boolean shouldApplyOnGroundReverseNextTick;
        int duplicateCount = 0;
        // left for usage
        boolean flag1;
        boolean flag2;
        int cnt1;
        int cnt2;
        Vec3d lastSetBackPos;
        int duplicateSetback = 0;
        private static final int latency = 3;

        public NoFallGrimLazyPlus(NoFall module) {
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
            if (step == Step.WAIT_FOR_RESYNC) {
                step = Step.HANDLE_RESYNC;
            }
            super.onSetback(setBack);
        }

        Step step = Step.COMMON;

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
            boolean forceNoFall = ClientPlayerAccess.of(args).isForceNoFall();
            if (forceNoFall) {
                runningThisTick = true;
                // LAZY MODE: only if we trigger not onground -> onground should we reset
                counter = 0;
                module.lastOnGroundHeight = module.lastServerY;

                mc.getNetworkHandler()
                        .sendPacket(VPacket.newPositionAndOnGround(
                                args.getX(),
                                module.lastServerY + DELTA_Y,
                                args.getZ(),
                                false,
                                args.horizontalCollision));
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

        boolean thisStepInNoInputStep = false;

        @Override
        public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
            if (module.isActive() && step == Step.REAPPLY_MOVEMENT
                    || step == Step.WAIT_FOR_RESYNC
                    || step == Step.HANDLE_RESYNC) {
                // do not make velocity input
                // Debug.info("check input");
                ClientPlayerEntity entity = movementManagerEvent.context.playerStatus.entity;
                var input = PlayerInputUtils.of(entity.input);
                boolean shouldJump = true;
                boolean shouldPress = true;//thisStepInNoInputStep;
                if (step == Step.REAPPLY_MOVEMENT) {
                    if(shouldPress){
                        input = input.forward(thisStepInNoInputStep)
                            .backward(false)
                            .left(false)
                            .right(false);

                    }
                    if(shouldJump){
                        input = input.jump(false);
                    }
                } else if (step == Step.WAIT_FOR_RESYNC) {
                    if(shouldPress){
                        input = input.forward(thisStepInNoInputStep)
                            .backward(false)
                            .left(false)
                            .right(false);
                    }
                    if(shouldJump){
                        input = input.jump(true);
                    }
                } else {
                    if(shouldPress){
                        input = input.forward(false)
                            .backward(false)
                            .left(false)
                            .right(false);
                    }
                    if(shouldJump){
                        input = input.jump(true);
                    }
                }
                if (!entity.isFallFlying()) {
                    input.applyInput(entity.input);
                }
//                else {
//                    input.sendPlayerInputPacket();
//                }

            }
        }

        public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {

            if (module.isActive()) {
                var entity = movementManagerEvent.context.playerStatus;
                // check Y after fall
                if (step == Step.REAPPLY_MOVEMENT) {
                    movementManagerEvent.context.playerStatus.restorePos();
                    ClientPlayerAccess.of(entity.entity).resyncPos();
                    step = Step.WAIT_FOR_RESYNC;
                } else {
                    //                    if (lastNoFallPos != null) {
                    //                        // near
                    //                        if (Math.abs(lastNoFallPos.y - entity.entity.getY()) < 1e-2
                    //                            && entity.entity.getPos().squaredDistanceTo(lastNoFallPos) < 1
                    //                            && lastNoFall + latency >= Tasks.getTick()) {
                    //                            step = Step.HANDLE_RESYNC;
                    //                        }
                    //                    }
                    boolean shouldCheck;
                    if (step == Step.WAIT_FOR_RESYNC) {
                        shouldCheck = true;
                        if (lastNoFall + latency <= Tasks.getTick()) {
                            step = Step.COMMON;
                        }
                    } else if (step == Step.HANDLE_RESYNC) {
                        // must check?
                        shouldCheck = true;
                        if (lastNoFall + latency <= Tasks.getTick()) {
                            step = Step.COMMON;
                        }
                    } else {
                        // If there is a recent nofall without duplicate , then it must be the first nofall, check it
                        // carefully
                        shouldCheck = (lastNoFall + latency >= Tasks.getTick())
                                || afterSetbackFlag
                                || (entity.entity.getY() <= module.lastOnGroundHeight - module.safeDistance);
                    }
                    boolean shouldCheckHard = step == Step.HANDLE_RESYNC || (lastNoFall + latency >= Tasks.getTick());
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
                                if (step == Step.HANDLE_RESYNC) {
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
                                    step = Step.COMMON;
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
                                //                            if(duplicateCount == 1){
                                //                                mc.getNetworkHandler()
                                //                                    .sendPacket(VPacket.newPositionAndOnGround(
                                //                                        entity.pos.getX(),
                                //                                        entity.pos.getY() + 100,
                                //                                        entity.pos.getZ(),
                                //                                        false,
                                //                                        entity.horizontalCollision));
                                //                                mc.getNetworkHandler().sendPacket(new
                                // ClientTickEndC2SPacket());
                                //                            }
                                // todo: shit, can we just abort current movements and up
                                storedPacketMove = VPacket.newPositionAndOnGround(
                                        entity.pos.getX(),
                                        entity.pos.getY() + DELTA_Y,
                                        entity.pos.getZ(),
                                        false,
                                        entity.horizontalCollision);
                                // Debug.chat("Apply");
                                movementManagerEvent.cancel();
                                var input = PlayerInputUtils.of(entity.entity.input);

                                if (step == Step.COMMON) {
                                    thisStepInNoInputStep =
                                            !input.forward() && !input.backward() && !input.left() && !input.right();
                                    // step in movement
                                    // do not modify fallflying
//                                    if (thisStepInNoInputStep) {
//                                        input.forward(true);
//                                    }
                                    input.jump(false).applyInput(entity.entity.input);
                                    step = Step.REAPPLY_MOVEMENT;
                                }
                                // mc.getNetworkHandler().sendPacket(new ClientTickEndC2SPacket());

                                //                            entity.entity.setPos(entity.pos.getX(),
                                // entity.entity.getY(),
                                // entity.pos.getZ());
                                //                            entity.entity.setVelocity(0.0, 0.0, 0.0);
                                noFallSetbackResponse = true;

                                // idk
                                return;
                            }
                        }
                    }
                }
            }
        }

        Packet<?> storedPacketMove = null;

        @Override
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            if (storedPacketMove != null) {
                mc.getNetworkHandler().sendPacket(storedPacketMove);
            }
            storedPacketMove = null;
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

        public static enum Step {
            COMMON,
            REAPPLY_MOVEMENT,
            WAIT_FOR_RESYNC,
            HANDLE_RESYNC;
        }
    }

    // works on fucking loyisa motherfucker 4.9
    public static class NoFallFuckGrimLazyPlusV2 extends NoFallDelegate{
        int lastStartWaitResyncTick = 0;
        Vec3d lastStartWaitPos = null;
        boolean afterSetbackFlag;
        Vec3d lastStartWaitAcceptPos;
        int lastStartWaitAcceptTick = 0;
        PlayerInputUtils.Input lastCacheInput ;
        // left for usage
        boolean flag1;
        boolean flag2;
        int cnt1;
        int cnt2;
        private static final int latency = 5;
        Step step = Step.COMMON;
        public NoFallFuckGrimLazyPlusV2(NoFall module) {
            super(module);
            this.afterSetbackFlag = true;
        }

        @Override
        public void onSetback(Event<MovTasks.MovInfo> setBack) {
            afterSetbackFlag = true;
            Vec3d nowV3d = setBack.context.vec3d();
            if(lastStartWaitPos != null && lastStartWaitPos.squaredDistanceTo(nowV3d) < 1 && Tasks.getTick() < lastStartWaitResyncTick + latency){
                // accept
                lastStartWaitPos = null;
                lastStartWaitAcceptPos = nowV3d;
                lastStartWaitAcceptTick = Tasks.getTick();
                step = Step.WAIT_FOR_RESYNC;
            }
//            if(lastRotTick + 2 >= Tasks.getTick()){
//                var ctx = setBack.context();
//                setBack.context(new MovTasks.MovInfo(ctx.vec3d(), ctx.oGroundOverride(), ctx.updatePlayer(), new Vec2f(ctx.rotationOverride().x, modifyYaw)));
//            }
//            else {
//                // die
//            }
            super.onSetback(setBack);
        }
        int dupResync = 0;

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            ClientPlayerEntity args = movementManagerEvent.context.playerStatus.entity;
            boolean forceNoFall = ClientPlayerAccess.of(args).isForceNoFall();
            if (forceNoFall) {
                runningThisTick = true;
                // LAZY MODE: only if we trigger not onground -> onground should we reset
                counter = 0;
                module.lastOnGroundHeight = module.lastServerY;

                mc.getNetworkHandler()
                    .sendPacket(VPacket.newPositionAndOnGround(
                        args.getX(),
                        module.lastServerY + DELTA_Y,
                        args.getZ(),
                        false,
                        args.horizontalCollision));
                noFallSetbackResponse = true;
                ClientPlayerAccess.of(args).setForceNoFall(false);
            } else if (module.isActive()) {
                counter += 1;
            }
            // ?
            if (counter > 100) {
                noFallSetbackResponse = false;
            }


            if (module.isActive()) {
                // do not make velocity input
                // Debug.info("check input");
                ClientPlayerEntity entity = movementManagerEvent.context.playerStatus.entity;
                var input = PlayerInputUtils.of(mc.options);
                var playerInput = input.clone();
                boolean resyncCnt = false;
                if(step == Step.WAIT_FOR_RESYNC){
                    //
                    //Debug.chat("Wait Resync op");
                    // calculate which way is ok,
                    if(lastStartWaitResyncTick + latency * 2 >= Tasks.getTick() ){
                        if(lastStartWaitAcceptPos != null && Tasks.getTick() <= lastStartWaitAcceptTick + 1){
                            resyncCnt = true;
                            step = Step.APPLY_JUMP;
                            //Debug.chat("Apply jump " + lastStartWaitAcceptPos);
                            mc.player.setPosition(lastStartWaitAcceptPos);
                            lastStartWaitPos = lastStartWaitAcceptPos;
                            lastStartWaitResyncTick = Tasks.getTick();
                            lastStartWaitAcceptPos = null;
                            mc.player.setOnGround(true);
                            //make some horizontal movement to avoid duplicate resync
                            input =  input.clone();
                            input.jump(true).forward(false).backward(false).left(false).right(false).sprint(false);

                            var co = applyInputWay(entity);
//                            mc.player.setVelocity(mc.player.getVelocity().withAxis(Direction.Axis.Y,0));
                            if(!co.hasAnyCollision()){
                                input.forward(true);
                            }else if(false) {
                                mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
                                input.forward(false);
                            }else {
                                input.forward(true);
                                if(playerInput.hasWASDMovement()){
                                    if(playerInput.forward()){
                                        modifyRot = true;
                                        EntityUtils.setEntityYawSafe(mc.player, mc.player.getYaw() + 180);
                                    }else if(playerInput.backward()){

                                    }else if(playerInput.left()){
                                        modifyRot = true;
                                        EntityUtils.setEntityYawSafe(mc.player, mc.player.getYaw() + 90);
                                    }else if(playerInput.right()){
                                        modifyRot = true;
                                        EntityUtils.setEntityYawSafe(mc.player, mc.player.getYaw() - 90);
                                    }
                                }else {
                                    if(!co.forward()){

                                    } else if(!co.backward()){
                                        modifyRot = true;
                                        EntityUtils.setEntityYawSafe(mc.player, mc.player.getYaw() + 180);
                                    }else if(!co.left()){
                                        modifyRot = true;
                                        EntityUtils.setEntityYawSafe(mc.player, mc.player.getYaw() - 90);
                                    }else if(!co.right()){
                                        modifyRot = true;
                                        EntityUtils.setEntityYawSafe(mc.player, mc.player.getYaw() + 90);
                                    }
                                }

                            }
                            lastCacheInput = input.clone();
                            if(modifyRot){
                                movementManagerEvent.context.pushImportantRotation(false, true);
                                modifyYaw = mc.player.getYaw();
                                lastRotTick = Tasks.getTick();
                            }
                           // Debug.chat((mc.player.getYaw() - 180) % 360 + 180);

                            forThisTickInput = input;
                            applyJumpThisTick = true;
                        }else{
//                            //Debug.chat("Apply Input");
//                            if(lastCacheInput != null){
                                //mc.player.setVelocity(0,0, 0);
                                forThisTickInput =  lastCacheInput.clone();
                                if(Tasks.getTick() < lastRotTick + latency){
                                    mc.player.setYaw(modifyYaw);
                                    modifyRot = true;
                                    movementManagerEvent.context.pushImportantRotation(false, true);
                                }
                                applyJumpThisTick = true;
//                            }

                        }
                    }else {
                        //Debug.chat("Timeout");
                        step = Step.COMMON;
                    }

                }else if(step == Step.APPLY_JUMP){

                    step = Step.COMMON;
                }
                dupResync = Math.max(0, dupResync + (resyncCnt ? 2 : -1));

            }

        }
        int lastRotTick = 0;
        float modifyYaw = 0.0F;
        boolean modifyRot = false;
        PlayerInputUtils.Input forThisTickInput = null;
        boolean applyJumpThisTick = false;
        int lastFixTick = 0;

        @Override
        public void onPlayerVelocity(Event<Vec3d>  playerVec) {
            if(lastFixTick + 10 > Tasks.getTick()){
                Vec3d vc3d = playerVec.context();
                if(vc3d.y < 0){
                    playerVec.context(new Vec3d(vc3d.x, 0.0D, vc3d.z));
                }
            }
        }

        @Override
        public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
            if(applyJumpThisTick && forThisTickInput != null){
//                if(dupResync >= 4){
//                    dupResync = 0;
//                    Debug.chat("Fix tick");
//                    if(Tasks.getTick() %2 == 1){
//                        forThisTickInput.backward(true).forward(false);
//                    }else {
//                        forThisTickInput.forward(true).backward(false);
//                    }
////                    Debug.chat("Fix tick");
////                    dupResync = 0;
////                    lastFixTick = Tasks.getTick();
////                    var entity = movementManagerEvent.context.playerStatus;
////
////                    mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(
////                        entity.pos.getX(),
////                        entity.pos.getY() + DELTA_Y,   // 将 Y 坐标抬高
////                        entity.pos.getZ(),
////                        false,                         // onGround = false
////                        entity.horizontalCollision
////                    ));
//                }
//                if(lastFixTick + 3 > Tasks.getTick()){
//                    forThisTickInput = forThisTickInput.jump(false).forward(false).backward(false).left(false).right(false);
//                }
                forThisTickInput.applyInput(movementManagerEvent.context.playerStatus.entity.input);
            }
            forThisTickInput = null;
        }



        @Override
        public void onJump(Event<Integer> jumpCooldown) {
            if(applyJumpThisTick){
                jumpCooldown.context(0);
            }
        }

        public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
            if(lastFixTick == Tasks.getTick()){
                movementManagerEvent.cancel();
            }
            if(applyJumpThisTick){
                applyJumpThisTick = false;
            }

            if (module.isActive()) {
                var entity = movementManagerEvent.context.playerStatus;
                // check Y after fall
                if (step == Step.APPLY_JUMP) {
                    // common movement
                    step = Step.COMMON;
                } else {
                    //                    if (lastNoFallPos != null) {
                    //                        // near
                    //                        if (Math.abs(lastNoFallPos.y - entity.entity.getY()) < 1e-2
                    //                            && entity.entity.getPos().squaredDistanceTo(lastNoFallPos) < 1
                    //                            && lastNoFall + latency >= Tasks.getTick()) {
                    //                            step = Step.HANDLE_RESYNC;
                    //                        }
                    //                    }
                    if(step == Step.COMMON || step == null){
                        boolean shouldCheck = (entity.entity.getY() <= module.lastOnGroundHeight - module.safeDistance) && Tasks.getTick() > lastStartWaitResyncTick + latency;
                        if ((shouldCheck && !entity.onGround && entity.entity.isOnGround())) {


                            afterSetbackFlag = false;

                            counter = 0;

                            // todo: try send it eariler

                           Debug.chat("BadPackets");
                            module.lastOnGroundHeight = entity.pos.getY();
                            // ClientTickEndC2SPacket());
                            Vec3d lastPosPos = movementManagerEvent.context.playerStatus.pos;
                            storedPacketMove = VPacket.newPositionAndOnGround(
                                mc.player.getX(), lastPosPos.y + 9E-8, mc.player.getZ(),
                                //mc.player.getYaw()+ 180, mc.player.getPitch(),
                                false, entity.horizontalCollision
                            );
//                                storedPacketMove =    VPacket.newOnGroundOnly(
//                                    true,
//                                    entity.horizontalCollision);

                            movementManagerEvent.cancel();
                            lastStartWaitPos = mc.player.getPos();
                            lastStartWaitResyncTick = Tasks.getTick();
                            mc.player.setPosition(movementManagerEvent.context.playerStatus.pos.withAxis(Direction.Axis.Y, mc.player.getY()));
                            step = Step.WAIT_FOR_RESYNC;
                            noFallSetbackResponse = true;
                            mc.player.setOnGround(true);
                            lastCacheInput = PlayerInputUtils.of(mc.player.input);
                            lastCacheInput
                                .forward(true).backward(false).left(false).right(false)
                                .jump(false);//.applyInput(mc.player.input);
                        }

                    }else if(step == Step.WAIT_FOR_RESYNC){
                       // movementManagerEvent.cancel();
                    }
                }
            }
        }

        Packet<?> storedPacketMove = null;

        @Override
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            if (storedPacketMove != null) {
                mc.player.setOnGround(true);
                mc.getNetworkHandler().sendPacket(storedPacketMove);
            }
            storedPacketMove = null;
            if (runningThisTick) {
                // shouldApplyOnGroundReverseNextTick = movementManagerEvent.context.playerStatus.entity.isOnGround();
            }
            if(modifyRot){
                modifyRot = false;
               // movementManagerEvent.context.playerStatus.restoreRotation();
            }
            return true;
        }

        public static enum Step {
            COMMON,
            WAIT_FOR_RESYNC,
            APPLY_JUMP,
            RESYNC_FLOOD
            ;
        }
    }

    public static record HorizontalCollision(boolean forward, boolean backward, boolean left, boolean right){
        boolean hasAnyCollision(){
            return forward || backward || left || right;
        }
    }
    public static HorizontalCollision applyInputWay(ClientPlayerEntity player){
        double testDistance = 2e-1;

        // 根据玩家朝向计算四个方向的单位向量
        float yaw = player.getYaw();
        Vec3d forward = Vec3d.fromPolar(0, yaw).multiply(testDistance);
        Vec3d backward = forward.negate();
        Vec3d left = Vec3d.fromPolar(0, yaw + 90).multiply(-testDistance);
        Vec3d right = left.negate();
        boolean forwardCollide = MovTasks.hasHorizontalCollision(player, forward);
        boolean backwardCollide = MovTasks.hasHorizontalCollision(player, backward);
        boolean leftCollide = MovTasks.hasHorizontalCollision(player, left);
        boolean rightCollide = MovTasks.hasHorizontalCollision(player, right);

        return new HorizontalCollision(forwardCollide, backwardCollide, leftCollide, rightCollide);

    }
    public static enum NofallBypassMode implements ConfigEnum {
        NO_BYPASS,
        LAZY_MODE,
        BYPASS_GRIM,
        @ApiStatus.Experimental
        LAZY_BYPASS_GRIM,
        LAZY_GRIM_PLUS,
        LAZY_GRIM_PLUS_2,
        TEST;

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
                if (noFallModel.get() != NofallBypassMode.LAZY_GRIM_PLUS) {
                    noFallModel.set(NofallBypassMode.LAZY_GRIM_PLUS);
                    //                    if (noFall.get()) {
                    //                        Debug.chat("正在切换到GrimNoFall模式, 该功能可能在最新版本失效, 若失效请手动切换LazyGrim模式");
                    //                    }
                }
            }
            default -> {
                noFallModel.set(NofallBypassMode.LAZY_MODE);
            }
        }
    }
}
