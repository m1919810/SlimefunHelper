package me.matl114.hacks.modules.render;

import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.entity.CameraEntity;
import me.matl114.hacks.utils.move.FlightVelocity;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.collections.FPoint;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

public class Freecam extends BaseModule implements LegalMovementManager.MovementModifier {
    public final ModulePath freecam = makePath(Configs.RENDER_CONFIG, "freecam");
    private static LegalMovementManager.DelegateMovementModifier instance;

    public Freecam() {
        bindFlag(enable);
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final FlagRef enable = flagBuilder(freecam.add("enable")).build();

    public final KeyBindRef keyBind = moduleEntry(
                    Configs.RENDER_CONFIG,
                    freecam.add("enable-hotkey").toPath(),
                    new MultiKeyBind(KeyCode.KEY_U),
                    freecam.add("enable").toPath())
            .build();

    public final DoubleRef speed = builder(freecam.add("speed"), DoubleRef.TYPE)
            .defaultValue(1.0D)
            .validator(Configs.doubleRange(0.0, 100000))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(Listener.getTeleportConfirmResponsePoint(), this::onPosResync);
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class),
                this::onStopInteractWithSelf);
        registerListener(Listener.getPlayerChangeLook(), this::onPlayerChangeLook);
        registerListener(Listener.getCustomListener().getChannel(FlightVelocity.class), this::onElytraControl);
    }

    CameraEntity camera;
    CameraEntity displayEntity;

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        initializeCamera();
    }

    @Override
    public int priority() {
        return PRIORITY_LOW;
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        removeCamera();
    }

    public void onWorldSwitch(Event<World> event) {
        if (enable.get()) {
            Tasks.scheduleDelayed(this::initializeCamera, 1);
        }
    }

    public void initializeCamera() {
        removeCamera();
        if (mc.player == null) return;
        camera = new CameraEntity(mc.world, mc.player, GameMode.SPECTATOR, true);
        displayEntity = new CameraEntity(mc.world, mc.player, GameMode.CREATIVE, false);
        mc.world.addEntity(camera);
        mc.world.addEntity(displayEntity);
        mc.setCameraEntity(camera);
    }

    public void removeCamera() {
        if (camera != null) {
            camera.remove(Entity.RemovalReason.DISCARDED);
        }
        if (displayEntity != null) {
            displayEntity.remove(Entity.RemovalReason.DISCARDED);
        }
        camera = null;
        displayEntity = null;
        mc.setCameraEntity(null);
    }

    public void onTick(Event<ClientPlayerEntity> event) {
        if (camera == null || mc.world == null) return;
        // nop
        // mc.world.tickEntity(camera);
    }

    public void onPosResync(Event<MovTasks.MovInfo> resync) {
        if (camera == null) return;
        MovTasks.MovInfo info = resync.context();
        Vec3d vec3d = info.vec3d();
        Vec2f vec2f = info.rotationOverride();
        // may be a tp
        if (vec3d != null && camera.getPos().squaredDistanceTo(vec3d) > MathUtils.s2(100)) {
            camera.setPosition(vec3d);
        }
    }

    public void onStopInteractWithSelf(Event<PlayerInteractEntityC2SPacket> packet) {
        if (camera != null) {
            var p = packet.context();
            if (displayEntity != null && p.entityId == displayEntity.getId()) {
                packet.cancel();
                return;
            }
            if (mc.player != null && p.entityId == mc.player.getId()) {
                packet.cancel();
                return;
            }
        }
    }

    public void onPlayerChangeLook(Event<FPoint> event) {
        if (camera == null) return;
        camera.changeLookDirection(event.context.x, event.context.y);
        event.cancel();
    }

    public void onElytraControl(Event<EventContainer<FlightVelocity>> event) {
        if (true || camera == null) return;
        // fuck, this module directly reed mc.options,
        FlightVelocity velocity = event.context().value;
        velocity.x(0).y(0).z(0);
    }

    @Override
    public boolean mayModify() {
        return false;
    }

    @Override
    public boolean mayModifyPos() {
        return false;
    }

    @Override
    public boolean mayModifyRotation() {
        return false;
    }

    PlayerInputUtils.Input cachedInput;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {

        //    }
        //
        //    @Override
        //    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        if (camera == null) return;
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        PlayerInputUtils.Input i0 = PlayerInputUtils.of(mc.options);
        cachedInput = i0;
        Vec3d movement = new Vec3d(i0.sidewaysSpeed(), i0.upwardSpeed(), i0.forwardSpeed());
        Vec3d vec3d = EntityUtils.movementInputToVelocity(movement, (float) speed.get(), camera.getYaw());
        camera.setVelocity(vec3d);
        // reset player input, keep sneak for interacting
        // apply sneak
        PlayerInputUtils.EMPTY.applyInput(mc.options);
        player.setSneaking(i0.sneak());
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        if (cachedInput != null) {
            cachedInput.applyInput(mc.options);
            // apply sneak and sprint to player
            PlayerInputUtils.EMPTY
                    .withSneak(cachedInput.sneak())
                    .sprint(cachedInput.sprint())
                    .applyInput(mc.player.input);
            cachedInput = null;
        }
    }

    @Override
    public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        // used for sending packets
        if (camera != null && mc.getCameraEntity() == camera) {
            mc.setCameraEntity(movementManagerEvent.context.playerStatus.entity);
        }
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        if (camera != null && mc.getCameraEntity() == camera) {
            mc.setCameraEntity(movementManagerEvent.context.playerStatus.entity);
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (camera != null && mc.getCameraEntity() == movementManagerEvent.context.playerStatus.entity) {
            mc.setCameraEntity(camera);
        }
        return true;
    }
}
