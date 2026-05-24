package me.matl114.hacks.modules.move;

import java.util.Locale;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.hacks.KeyBindAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.move.FlightVelocity;
import me.matl114.managers.*;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class CreativeFlight extends BaseModule implements LegalMovementManager.MovementModifier {
    private static LegalMovementManager.DelegateMovementModifier instance;
    public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");
    public final ModulePath flight = moveSafety.add("flight");
    public final ModulePath moveSpeed = makePath(Configs.MOV_CONFIG, "move-speed");

    public CreativeFlight() {
        bindFlag(canFly);
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final FlagRef canFly =
            flagBuilder(flight.add("flight-enable")).defaultValue(false).build();

    public final KeyBindRef keybind = moduleEntry(
                    flight.add("flight-enable-hotkey"),
                    new MultiKeyBind(),
                    flight.add("flight-enable"),
        moduleMeta(()-> this.flightMode))
            .build();

    public final EnumRef<Mode> flightMode = builder(flight.add("flight-mode"), Mode.class)
            .defaultValue(Mode.CREATIVE)
            .build();

    public final KeyBindRef switchMode = hotkey(flight.add("flight-mode-switch-hotkey"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::toggleMode))
            .build();

    public final FlagRef doAntiKick =
            builder(flight.add("antikick"), Boolean.class).defaultValue(true).build();

    public final IntRef antiKickPeriod =
            intBuilder(flight.add("antikick-period")).defaultValue(60).build();
    public final FlagRef overrideFlySpeed = builder(moveSpeed.add("fly-speed-override"), Boolean.class)
            .defaultValue(false)
            .build();

    public final DoubleRef overrideFlySpeedCreative = builder(moveSpeed.add("fly-speed-creative"), Double.class)
            .defaultValue(0.8)
            .build();

    public final DoubleRef overrideFlySpeedSurvival =
            builder(moveSpeed.add("fly-speed"), Double.class).defaultValue(0.4).build();

    public final KeyBindRef overridingSpeedKeybind = hotkey(moveSpeed.add("toggle-flight-speed"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::toggleSpeed))
            .build();

    public final FlagRef overrideWalkSpeed = builder(moveSpeed.add("walk-speed"), Boolean.class)
            .defaultValue(false)
            .build();

    public final DoubleRef overridingWalkSpeedAll = builder(moveSpeed.add("walk-speed-override"), Double.class)
            .defaultValue(0.1)
            .build();

    public final FlagRef onGroundWhenMine =
            flagBuilder(flight.add("onground-when-mine")).build();

    //    public final FlagRef fake1 =
    //            flagBuilder(Configs.MOV_CONFIG, MOVE_FLIGHT_SAFETY_1).build();

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
            // cancel fly when disable
            mc.player.getAbilities().allowFlying = serverSideCanFly;
            if (mc.player.getAbilities().flying && !serverSideCanFly) {
                mc.player.getAbilities().flying = false;
            }
        }
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketListenerPoint(PlayerAbilitiesS2CPacket.class), this::onAbility);
        registerListener(Listener.getPacketListenerPoint(UpdatePlayerAbilitiesC2SPacket.class), this::onAbilityUpdate);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onStartMine);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInputC2SPacket.class), this::onInterceptFlyInput);
    }

    public void onAbility(Event<PlayerAbilitiesS2CPacket> event) {
        var packet1 = event.context();
        serverSideCanFly = packet1.allowFlying();
        if (mc.player != null) {
            PlayerAbilities abilities = mc.player.getAbilities();
            // abilities.allowFlying = abilities.allowFlying;
            abilities.creativeMode = packet1.isCreativeMode();
            abilities.invulnerable = packet1.isInvulnerable();
            if (!isActive()) {
                abilities.allowFlying = packet1.allowFlying();
            }
            if (!overrideFlySpeed.get()) {
                abilities.setFlySpeed(packet1.getFlySpeed());
            }
            abilities.setWalkSpeed(packet1.getWalkSpeed());
            // mc.player.getAbilities().flying = isFly;
        } else {
            // sometimes the player hasn't enter the game, because this is accepted in async thread, so run main
            // delay to avoid forever loop because
            Tasks.scheduleDelayed(
                    () -> {
                        onAbility(event);
                    },
                    10);
        }
        event.cancel();
    }

    public void onAbilityUpdate(Event<UpdatePlayerAbilitiesC2SPacket> event) {
        if (isActive() && !serverSideCanFly) {
            event.cancel();
        }
    }

    public double getOverridingFlySpeed() {
        return (mc.player != null && mc.interactionManager.getCurrentGameMode().isCreative())
                ? overrideFlySpeedCreative.get()
                : overrideFlySpeedSurvival.get();
    }

    public double getOverridingWalkSpeed() {
        return overridingWalkSpeedAll.get();
    }

    public void toggleSpeed() {
        if (mc.player == null) return;
        if (mc.player.getAbilities().flying) {
            overrideFlySpeed.set(!overrideFlySpeed.get());
            Debug.chat("toggle fly speed override", overrideFlySpeed.get());
        } else {
            overrideWalkSpeed.set(!overrideWalkSpeed.get());
            Debug.chat("toggle walk speed override", overrideWalkSpeed.get());
        }
    }

    public void toggleMode() {
        Mode mode = flightMode.get();
        int ordinal = mode.ordinal() + 1;
        Mode[] flightModes = Mode.values();
        Mode newMode = flightModes[ordinal % flightModes.length];
        flightMode.set(newMode);
        Debug.chat("toggle flight mode to", newMode.getDisplay());
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
        if (isActive()) {
            if (!player.getAbilities().allowFlying) {
                player.getAbilities().allowFlying = true;
            }
        } else {
            player.getAbilities().allowFlying = serverSideCanFly;
        }
        boolean handled =
                switch (flightMode.get()) {
                    case CREATIVE -> {
                        dispatchAntiKick(player);
                        yield true;
                    }
                    case MOTION -> {
                        if (isActive() && mc.player.getAbilities().flying) {
                            PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
                            Vec3d movementInput =
                                    new Vec3d(input.sidewaysSpeed(), input.upwardSpeed(), input.forwardSpeed());
                            Vec3d velocity = EntityUtils.movementInputToVelocity(
                                    movementInput, (float) (5 * getOverridingFlySpeed()), player.getYaw());
                            player.setVelocity(velocity);
                            velocity = dispatchAntiKickMotion(velocity);
                            // set FlightVelocity Event
                            FlightVelocity flightVelocity = new FlightVelocity(
                                    velocity, 5 * getOverridingFlySpeed(), FlightVelocity.Mode.MOTION_FLIGHT);
                            Listener.getCustomListener()
                                    .broadcast(new EventContainer<>(FlightVelocity.class, flightVelocity));
                            velocity = flightVelocity.toVelocity();
                            player.setVelocity(velocity);
                            yield true;
                        }
                        yield false;
                    }
                    case JETPACK -> {
                        if (isActive()) {
                            PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
                            if (input.jump()) {
                                Vec3d vec3d = new Vec3d(0, 1, 0);
                                vec3d = dispatchAntiKickMotion(vec3d);
                                if (vec3d.y > 0.8) {
                                    player.jump();
                                } else {
                                    Vec3d playerVec = player.getVelocity();
                                    player.setVelocity(playerVec.x, vec3d.y, playerVec.z);
                                }
                                yield true;
                            }
                        }
                        yield false;
                    }
                };
        if (!handled && isActive() && mc.player.getAbilities().flying) {
            dispatchAntiKick(player);
        }
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        LegalMovementManager.MovementModifier.super.applyAfterInputTick(movementManagerEvent);
    }

    public void dispatchAntiKick(ClientPlayerEntity player) {
        boolean fakeGilde = MovTasks.getElytraExtra().shouldExcuteAntiKick();
        if (((isActive() && doAntiKick.get()))) {
            antiKick(player, fakeGilde);
        }
    }

    private int mineTick = 0;
    private int lastTimeModifyOnGround = 0;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        var player = movementManagerEvent.context.playerStatus.entity;
        boolean onGround = player.isOnGround();
        if (!onGround && onGroundWhenMine.get() && isInMiningAction() && !player.getAbilities().creativeMode) {
            // instabreak problems
            mineTick = 2;

            player.setOnGround(true);
            // server side onGround may change without noticing us, so
            ClientPlayerAccess.of(player).resyncOnGround();
            lastTimeModifyOnGround = 2;
        } else if (mineTick > 0) {
            --mineTick;
            player.setOnGround(true);
            // server side onGround may change without noticing us, so
            ClientPlayerAccess.of(player).resyncOnGround();
            lastTimeModifyOnGround = onGround ? 1 : 2;
        }
    }

    public boolean isInMiningAction() {
        // compact for minebot and instant mining
        return mc.interactionManager.isBreakingBlock() || (lastStartMinePacket + 1 >= Tasks.getTick());
    }

    private int lastStartMinePacket = 0;

    public void onStartMine(Event<PlayerActionC2SPacket> actionPacket) {
        if (onGroundWhenMine.get()
                && actionPacket.context().getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            lastStartMinePacket = Tasks.getTick();
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        // do not restore, because client need this to calculate mining speed
        //        if(lastTimeModifyOnGround > 0){
        //            movementManagerEvent.context.playerStatus.entity.setOnGround(lastTimeModifyOnGround == 1);
        //            lastTimeModifyOnGround = 0;
        //        }
        lastTimeModifyOnGround = 0;
        return true;
    }

    // server constant
    private static final double antiKickOffset = 0.032D;
    // antikick module
    private int antiKickCount = 0;
    private double antiKickOffset0;
    private boolean escapeMotionReset = false;
    private double preservedLastMotion = 0.0D;
    private boolean waitingForServerResponse;
    // todo: rewrite
    public void antiKick(ClientPlayerEntity player, boolean fakeGliding) {
        if (MovTasks.seenAsFloating(fakeGliding)) {
            antiKickCount++;
        } else {
            antiKickCount = 0;
        }
        if (antiKickCount > antiKickPeriod.get()) {
            antiKickCount = 0;
            escapeMotionReset = !shouldResetMotion();
            preservedLastMotion = player.getVelocity().y;
            setMotionY(-antiKickOffset);
            // randomly fall down twice
            waitingForServerResponse = true; // Tasks.getTickRandom()%3 == 0;
            antiKickOffset0 = antiKickOffset - 0.008;
            return;
        }
        if (!escapeMotionReset) {
            if (waitingForServerResponse) {
                setMotionY(-antiKickOffset);
                antiKickOffset0 += antiKickOffset - 0.008;
                // there is no fucking packet for response
                waitingForServerResponse = false;

                // continue fall down til server respond
            } else {
                setMotionY(antiKickOffset0 + preservedLastMotion - 0.0);
                antiKickOffset0 = 0D;
                preservedLastMotion = 0.0D;
                Tasks.scheduleDelayed(this::restoreKeyPresses, 1);
                // set end
                escapeMotionReset = true;
            }
        }

        //        }

    }

    public Vec3d dispatchAntiKickMotion(Vec3d controlMotion) {
        boolean fakeGilde = MovTasks.getElytraExtra().shouldExcuteAntiKick();
        if ((fakeGilde || (isActive() && doAntiKick.get()))) {
            return processAntiKickMotion(controlMotion, fakeGilde);
        }
        return controlMotion;
    }

    double lastFloatingPosition = 0.0D;
    int floatingCount = 0;

    public boolean shouldResetMotion() {
        double floatingPosition = mc.player.getY();
        if (floatingCount == 0) {
            floatingCount = 1;
            lastFloatingPosition = floatingPosition;
            return false;
        }
        if (Math.abs(floatingPosition - lastFloatingPosition) > antiKickOffset * 3) {
            // try reset
            double oldFloatingPosition = lastFloatingPosition;
            lastFloatingPosition = floatingPosition;
            floatingCount = 1;
            if (oldFloatingPosition < lastFloatingPosition) {
                // up fly must
                return true;
            } else {
                // optimize downfly rest
                if (MovTasks.ENGIN.checkEnvironmentCollision(
                        mc.player, mc.player.getPos().add(0, -3 * antiKickOffset, 0), false)) {
                    return true;
                }
                return false;
            }
        } else {
            // optimize afk
            lastFloatingPosition = floatingPosition;
            floatingCount += 1;
            if (floatingCount > 10) {
                return true;
            }
            return false;
        }
    }

    public Vec3d processAntiKickMotion(Vec3d controlMotion, boolean fakeGlide) {
        if (MovTasks.seenAsFloating(fakeGlide)) {
            antiKickCount++;
        } else {
            antiKickCount = 0;
        }
        if (antiKickCount > MovTasks.getCreativeFlight().antiKickPeriod.get()) {
            antiKickCount = 0;
            // calculate whether need escapeMotionReset
            escapeMotionReset = !shouldResetMotion();
            preservedLastMotion = controlMotion.y;
            // randomly fall down twice
            waitingForServerResponse = true; // Tasks.getTickRandom()%3 == 0;
            antiKickOffset0 = antiKickOffset - 0.008;
            return new Vec3d(controlMotion.x, -antiKickOffset, controlMotion.z);
        }
        if (!escapeMotionReset) {
            if (waitingForServerResponse) {
                antiKickOffset0 += antiKickOffset - 0.008;
                // there is no fucking packet for response
                waitingForServerResponse = false;
                return new Vec3d(controlMotion.x, -antiKickOffset, controlMotion.z);
                // continue fall down til server respond
            } else {
                antiKickOffset0 = 0D;
                preservedLastMotion = 0.0D;
                // set end
                escapeMotionReset = true;
                return new Vec3d(controlMotion.x, antiKickOffset0 + preservedLastMotion - 0.0, controlMotion.z);
            }
        }
        return controlMotion;

        //        }
    }

    public void onInterceptFlyInput(Event<PlayerInputC2SPacket> inputPacketEvent) {
        //        if(fake1.get() && !serverSideCanFly && mc.player.getAbilities().flying){
        //            // hack fly
        //            inputPacketEvent.cancel();
        //        }
    }

    private void setMotionY(double motionY) {

        mc.options.sneakKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, motionY, velocity.z);
    }

    private void restoreKeyPresses() {
        // bugfix when shift click in screen, this key is reset to fall
        if (mc.currentScreen == null) {

            KeyBindAccess.of(mc.options.jumpKey).resetKeyState();
            KeyBindAccess.of(mc.options.sneakKey).resetKeyState();
        }
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        var modulePreset = presetEvent.context().getValue();
        switch (modulePreset) {
            case AC_GRIM, AC_GRIM_LEGACY, AC_MATRIX -> {
                canFly.set(false);
            }
            default -> {
                canFly.set(true);
            }
        }
        switch (modulePreset) {
            case AC_GRIM, AC_GRIM_LEGACY -> {
                overrideFlySpeed.set(false);
                overrideWalkSpeed.set(false);
            }
        }
        switch (modulePreset) {
            case HACKING -> {
                onGroundWhenMine.set(true);
            }
            default -> {
                onGroundWhenMine.set(false);
            }
        }
    }

    public enum Mode implements ConfigEnum {
        CREATIVE,
        MOTION,
        JETPACK;

        @Override
        public String getConfigEnumType() {
            return "flight_mode";
        }
    }
}
