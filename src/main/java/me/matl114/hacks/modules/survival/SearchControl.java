package me.matl114.hacks.modules.survival;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.Pos3;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class SearchControl extends BaseModule {
    public final ModulePath travellingControl = makePath(Configs.SURVIVAL_CONFIG, "travelling-control");
    public final ModulePath searchControl = travellingControl.add("search-control");

    {
        portConfigs(makePath(Configs.MOV_CONFIG, "travelling-control.search-control"), travellingControl);
    }

    public SearchControl() {
        super("SearchControl");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(searchControl.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(
                    searchControl.addHotkey(), new MultiKeyBind(), searchControl.addEnable())
            .build();

    public final EnumRef<Mode> mode = builder(searchControl.add("search-mode"), Mode.class)
            .defaultValue(Mode.SPIRAL)
            .updateListener(s -> refreshControl())
            .build();

    public final NBTRef<Pos3> centerPos = builder(searchControl.add("center-pos"), Pos3.class)
            .defaultValue(new Pos3(0, 0, 0))
            .build();

    public final DoubleRef rangeSpiral = doubleBuilder(searchControl.add("range-spiral"))
            .defaultValue(32.0D)
            .show(() -> mode.get().isIn(Mode.SPIRAL))
            .build();

    public final DoubleRef rangeRect = doubleBuilder(searchControl.add("range-rect"))
            .defaultValue(192.0D)
            .show(() -> mode.get().isIn(Mode.RECT))
            .build();

    public final DoubleRef rangeCircle = doubleBuilder(searchControl.add("range-circle"))
            .defaultValue(192.0D)
            .show(() -> mode.get().isIn(Mode.CIRCLE))
            .build();

    public final FlagRef abortWASD =
            flagBuilder(searchControl.add("abort-wasd")).build();

    public final FlagRef onlyWhenFly =
            flagBuilder(searchControl.add("fly-only")).build();

    public final DoubleRef maxDist = doubleBuilder(searchControl.add("max-distance"))
            .defaultValue(300_000_000.0D)
            .build();

    public final FlagRef outMaxLog =
            flagBuilder(searchControl.add("out-max-log")).build();

    Runnable currentLookControl;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onPreTick);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        currentLookControl = null;
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        refreshControl();
    }

    public void refreshControl() {
        currentLookControl = null;
        if (checkNull()) {
            return;
        }
        currentLookControl = switch (mode.get()) {
            case RECT -> createLookRect();
            case CIRCLE -> createLookCircle();
            case SPIRAL -> createLookSpiral();};
    }

    public Runnable createLookRect() {
        return this::tickLookRect;
    }

    public void onPreTick(Event<ClientPlayerEntity> event) {
        if (enable.get()) {
            if (currentLookControl == null) {
                refreshControl();
            }
            if (currentLookControl != null) {
                if (abortWASD.get() && PlayerInputUtils.of(mc.options).hasMovementControl()) {
                    return;
                }
                if (onlyWhenFly.get() && !mc.player.isFallFlying()) {
                    return;
                }
                if (mc.player.getPos().squaredDistanceTo(centerPos.get().to().toCenterPos())
                        < MathUtils.s2(maxDist.get())) {
                    currentLookControl.run();
                } else {
                    if (outMaxLog.get()) {
                        MainTasks.scheduleDisconnect();
                    }
                }
            }
        }
    }

    public void tickLookRect() {
        Vec3d current = mc.player.getPos();
        Vec3d center = centerPos.get().to().toCenterPos();
        Vec3d relativeCoord = current.subtract(center);
        double xzdp = relativeCoord.z - relativeCoord.x; // 修正后
        double xzdn = relativeCoord.x + relativeCoord.z;
        float targetYaw;
        if (xzdn <= 0) {
            if (xzdp <= 0) {
                // x + z < 0 , z - x < 0
                // 增大 x
                targetYaw = -90.0F; // 东
            } else {
                // x + z < 0 , z - x > 0
                //  减小z
                targetYaw = 180.0F; // 北
            }
        } else {
            if (xzdp <= rangeRect.get()) {
                // x + z > 0
                // z - x < range
                // 增大z， 直到z比x大range
                targetYaw = 0.0F; // 南
            } else {
                // x + z > 0
                // z - x > range
                // 减小x
                targetYaw = 90.0F; // 西
            }
        }
        mc.player.setYaw(targetYaw);
    }

    public Runnable createLookCircle() {
        return this::tickLookCircle;
    }

    public void tickLookCircle() {
        Vec3d current = mc.player.getPos();
        Vec3d center = centerPos.get().to().toCenterPos();
        Vec3d relativeCoord = current.subtract(center);
        double range = rangeCircle.get();
        Vec3d center1 = new Vec3d(0, 0, range / 2);
        Vec3d center2 = new Vec3d(0, 0, -range / 2);
        Vec3d selectedCenter;
        if (relativeCoord.x > 0) {
            selectedCenter = center1;
        } else {
            selectedCenter = center2;
        }
        Vec3d deltaR = relativeCoord.subtract(selectedCenter);
        float yaw = EntityUtils.rotationToYaw(deltaR.normalize());
        float cos = (float) ((mc.player.getVelocity().horizontalLength()) / (2 * deltaR.horizontalLength()));
        float yawControl = yaw + 90F - cos;
        PlayerStateManager.setPlayerYawSafe(mc.player, yawControl);
    }

    public Runnable createLookSpiral() {
        return this::tickLookSpiral;
    }

    public void tickLookSpiral() {
        Vec3d current = mc.player.getPos();
        Vec3d center = centerPos.get().to().toCenterPos();
        Vec3d relativeCoord = current.subtract(center);
        double P = rangeSpiral.get();
        double b = P / (2 * Math.PI);
        double r = relativeCoord.horizontalLength();

        float yawRadial = EntityUtils.rotationToYaw(relativeCoord.normalize());

        double phiRad = Math.atan2(r, b);
        float phiDeg = (float) Math.toDegrees(phiRad);

        float cos = (float) ((mc.player.getVelocity().horizontalLength()) / (2 * relativeCoord.horizontalLength()));
        float yawControl = yawRadial + phiDeg - cos;

        PlayerStateManager.setPlayerYawSafe(mc.player, yawControl);
    }

    public enum Mode implements ConfigEnum {
        RECT,
        CIRCLE,
        SPIRAL;

        @Override
        public String getConfigEnumType() {
            return "search_control_look_mode";
        }
    }
}
