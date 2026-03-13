package me.matl114.hacks.modules.render;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.entity.CameraEntity;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.collections.FPoint;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

public class Freecam extends BaseModule {
    public static final String[] FREECAM = {"freecam", "enable"};
    public static final String[] FREECAM_HOTKEY = {"freecam", "enable-hotkey"};
    public static final String[] CAMERA_SPEED = {"freecam", "speed"};

    public Freecam() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(Configs.RENDER_CONFIG, FREECAM).build();

    public final KeyBindRef keyBind = toggleHotkey(
                    Configs.RENDER_CONFIG, FREECAM_HOTKEY, new MultiKeyBind(KeyCode.KEY_U), FREECAM)
            .build();

    public final DoubleRef speed = builder(Configs.RENDER_CONFIG, CAMERA_SPEED, DoubleRef.TYPE)
            .defaultValue(1.0D)
            .validator(Configs.doubleRange(0.0, 100000))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(Listener.getPlayerKeyboardInputTick(), this::onPlayerInputTick);
        registerListener(Listener.getTeleportConfirmResponsePoint(), this::onPosResync);
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class),
                this::onStopInteractWithSelf);
        registerListener(Listener.getPlayerChangeLook(), this::onPlayerChangeLook);
    }

    CameraEntity camera;
    CameraEntity displayEntity;

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        initializeCamera();
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
        if (vec3d != null) {
            camera.setPosition(vec3d);
        }
        if (vec2f != null) {
            camera.setPitch(vec2f.x);
            camera.setYaw(vec2f.y);
        }
    }

    public void onPlayerInputTick(Event<Input> event) {
        if (camera == null) return;
        var input = event.context();
        PlayerInputUtils.Input i0 = PlayerInputUtils.of(input);
        Vec3d movement = new Vec3d(i0.sidewaysSpeed(), i0.upwardSpeed(), i0.forwardSpeed());
        Vec3d vec3d = EntityUtils.movementInputToVelocity(movement, (float) speed.get(), camera.getYaw());
        camera.setVelocity(vec3d);
        // reset player input,
        input.playerInput = new PlayerInput(false, false, false, false, false, false, false);
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

    // todo: weird gameHud

}
