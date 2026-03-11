package me.matl114.hacks.modules.move;

import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.hacks.KeyBindAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.*;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.util.math.Vec3d;

public class CreativeFlight extends BaseModule implements LegalMovementManager.MovementModifier {
    private static LegalMovementManager.DelegateMovementModifier instance;
    public static final String[] TOGGLE_FLIGHT = {"hotkeys-toggle", "toggle-flight"};
    public static final String[] MOVE_FLIGHT_ANTIKICK = {"move-safety", "flight", "antikick"};
    public static final String[] MOVE_FLIGHT_SAFETY_1 = {"move-safety", "flight", "fake-1"};
    public static final String[] MOVE_SPEED_OVERRIDE_FLY = {"move-speed", "fly-speed-override"};
    public static final String[] MOVE_SPEED_FLY_VAL_CREATIVE = {"move-speed", "fly-speed-creative"};
    public static final String[] MOVE_SPEED_FLY_VAL = {"move-speed", "fly-speed"};
    public static final String[] MOVE_SPEED_OVERRIDE_TASK = {"hotkeys", "toggle-flight-speed"};
    public static final String[] MOVE_SPEED_WALK_VAL = {"move-speed", "walk-speed"};
    public static final String[] MOVE_SPEED_OVERRIDE_WALK = {"move-speed", "walk-speed-override"};
    public static final String[] ON_GROUND_WHEN_MINE = {"move-safety", "flight", "onground-when-mine"};

    public static final String[] FLIGTH_HOTKEY = {"hotkeys", "toggle-flying"};

    public CreativeFlight() {
        bindFlag(canFly);
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final FlagRef canFly = toggle(TOGGLE_FLIGHT).defaultValue(false).build();

    public final KeyBindRef keybind = toggleHotkey(
                    TOGGLE_FLIGHT, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_F))
            .build();

    public final FlagRef doAntiKick = builder(Configs.MOV_CONFIG, Boolean.class)
            .path(MOVE_FLIGHT_ANTIKICK)
            .defaultValue(true)
            .build();
    public final FlagRef overrideFlySpeed = builder(Configs.MOV_CONFIG, Boolean.class)
            .path(MOVE_SPEED_OVERRIDE_FLY)
            .defaultValue(false)
            .build();

    public final DoubleRef overrideFlySpeedCreative = builder(Configs.MOV_CONFIG, Double.class)
            .path(MOVE_SPEED_FLY_VAL_CREATIVE)
            .defaultValue(0.8)
            .build();

    public final DoubleRef overrideFlySpeedSurvival = builder(Configs.MOV_CONFIG, Double.class)
            .path(MOVE_SPEED_FLY_VAL)
            .defaultValue(0.4)
            .build();

    public final KeyBindRef overridingSpeedKeybind = hotkey(MOVE_SPEED_OVERRIDE_TASK)
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_LEFT_BRACKET))
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::toggleSpeed))
            .build();

    public final FlagRef overrideWalkSpeed = builder(Configs.MOV_CONFIG, Boolean.class)
            .path(MOVE_SPEED_WALK_VAL)
            .defaultValue(false)
            .build();

    public final DoubleRef overridingWalkSpeedAll = builder(Configs.MOV_CONFIG, Double.class)
            .path(MOVE_SPEED_OVERRIDE_WALK)
            .defaultValue(0.1)
            .build();

    public final FlagRef onGroundWhenMine =
            flagBuilder(Configs.MOV_CONFIG, ON_GROUND_WHEN_MINE).build();

    public final KeyBindRef toggleFlyStateKeyBind = hotkey(FLIGTH_HOTKEY)
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::onFlightToggle))
            .build();

    public final FlagRef fake1 =
            flagBuilder(Configs.MOV_CONFIG, MOVE_FLIGHT_SAFETY_1).build();

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
            Tasks.scheduleDelayed(
                    () -> {
                        onAbility(event);
                    },
                    1);
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
            if (doAntiKick.get()) {
                antiKick(player);
            }
        } else {
            player.getAbilities().allowFlying = serverSideCanFly;
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
    private static final int antiKickPeriod = 60;
    private static final double antiKickOffset = 0.032D;
    // antikick module
    private int antiKickCount = 0;
    private double antiKickOffset0;
    private boolean escapeMotionReset = false;
    private double preservedLastMotion = 0.0D;
    private boolean waitingForServerResponse;

    public void antiKick(ClientPlayerEntity player) {
        if (MovTasks.seenAsFloating()) {
            antiKickCount++;
        } else {
            antiKickCount = 0;
        }
        if (antiKickCount > antiKickPeriod) {
            antiKickCount = 0;
            escapeMotionReset = false;
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

    public void onFlightToggle() {
        if (mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = false;
        } else if (mc.player.getAbilities().allowFlying) {
            mc.player.getAbilities().flying = true;
            // mc.player.setPos(mc.player.getX(), mc.player.getY() + 0.001, mc.player.getZ());
            Vec3d vec3d = mc.player.getVelocity();
            mc.player.setVelocity(vec3d.x, 0, vec3d.z);
            mc.player.setOnGround(false);
        } else {
            Debug.chat("You are not allowed to fly");
        }
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
            case AC_GRIM, AC_MATRIX -> {
                canFly.set(false);
            }
            default -> {
                canFly.set(true);
            }
        }
        switch (modulePreset) {
            case AC_GRIM -> {
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
}
