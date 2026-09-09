package me.matl114.hacks.modules.render;

import java.util.*;
import me.matl114.api.Displayable;
import me.matl114.events.Event;
import me.matl114.gui.basic.*;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.BoundedPrimitiveFlagMap;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.utils.*;
import me.matl114.versioned.SupportVersion;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class Hud extends IRender2DColoredModule {
    public final ModulePath hudRoot = makePath(Configs.RENDER_CONFIG, "in-game-hud");
    public final ModulePath hud = hudRoot.add("hud");

    public Hud() {
        super("Hud");
    }

    @Override
    protected ModulePath createRoot() {
        return makePath(Configs.RENDER_CONFIG, "in-game-hud").add("hud");
    }

    public NBTRef<HudElementSelectSet> hudElementList = builder(hud.add("elements"), HudElementSelectSet.class)
            .defaultValue(new HudElementSelectSet())
            .build();

    public final FlagRef useKmH = flagBuilder(hud.add("use-kmPH")).build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    @Override
    public void onUpdate(Event<Void> event) {}

    @Override
    public void render2D(VDrawContext vdraw, float partialTicks) {
        HudElementSelectSet set = hudElementList.get();
        if (set.getState(HudElement.ICON)) {
            handleIcon(vdraw);
        }
        if (set.getState(HudElement.COMMON_INFO)) {
            handleCommonInfo(vdraw);
        }
        if (set.getState(HudElement.CONNECTION_INFO)) {
            handleConnectionInfo(vdraw);
        }
        if (set.getState(HudElement.POSITION)) {
            handlePosition(vdraw);
        }
        if (set.getState(HudElement.HEIGHT)) {
            handleHeight(vdraw);
        }
        if (set.getState(HudElement.DIRECTION)) {
            handleDirection(vdraw);
        }
        if (set.getState(HudElement.ROTATION)) {
            handleRotation(vdraw);
        }
        if (set.getState(HudElement.FALL_DISTANCE)) {
            handleFallDistance(vdraw);
        }
        if (set.getState(HudElement.SPEED)) {
            handleSpeed(vdraw);
        }
        if (set.getState(HudElement.SPEED_HORIZONTAL)) {
            handleSpeedHorizontal(vdraw);
        }
        if (set.getState(HudElement.SPEED_VERTICAL)) {
            handleSpeedVertical(vdraw);
        }
    }

    public void handleIcon(VDrawContext vdraw) {
        if (right.get()) {
            vdraw.drawTexturedQuad(
                    Identifier.tryParse("slimefunhelper:textures/custom/genshin_impact.png"),
                    -80,
                    0,
                    0,
                    27,
                    0,
                    0.1F,
                    0.9F,
                    0.25F,
                    0.65F);
        } else {

            vdraw.drawTexturedQuad(
                    Identifier.tryParse("slimefunhelper:textures/custom/genshin_impact.png"),
                    0,
                    80,
                    0,
                    27,
                    0,
                    0.1F,
                    0.9F,
                    0.25F,
                    0.65F);
        }
        vdraw.getMatrices().translate(0, 27);
    }

    public void handleCommonInfo(VDrawContext vdraw) {
        // tps, fps, version
        SupportVersion currentVersion = ViaFabricPlusHooks.getInstance().getCurrentVersion();
        int latency = 0;
        PlayerListEntry pl;
        if ((pl = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid())) != null) {
            latency = pl.getLatency();
        }
        Text text = ChatUtils.stringToText("&aMCv" + currentVersion
                + (Objects.equals(currentVersion, SupportVersion.CURRENT) ? "" : "(Via)") + " Fps:"
                + mc.getCurrentFps() + " " + latency + "ms");
        drawText(vdraw, text);
    }

    public void handleConnectionInfo(VDrawContext vdraw) {
        String serverName = "ip:%s, %s";
        drawText(vdraw, serverName.formatted(CommonUtils.getServerName(), mc.player.getNameForScoreboard()));
    }

    public void handlePosition(VDrawContext vdraw) {
        String template = "%.2f, %.2f, %.2f";
        String chunk = "Chunk: [%d %d]";
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        int chunkX = ((int) manager.lastX) >> 4;
        int chunkZ = ((int) manager.lastZ) >> 4;
        drawText(vdraw, chunk.formatted(chunkX, chunkZ));
        drawText(vdraw, template.formatted(manager.lastX, manager.lastY, manager.lastZ));
    }

    public void handleHeight(VDrawContext vdraw) {
        String template = "Height: %.2f";
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        drawText(vdraw, template.formatted(PlayerStateManager.INSTANCE.lastY));
    }

    public void handleDirection(VDrawContext vdraw) {
        float yaw = mc.player.getYaw();
        int xSgn = EntityUtils.yawToXSgn(yaw);
        int zSgn = EntityUtils.yawToZSgn(yaw);
        String directionName = MathUtils.getDirectionName(xSgn, zSgn);

        // 2. 符号字符串，格式如 "x+z+" 或 "x-z-"，符号为 '+'、'-' 或 '0'
        String symbolStr = "X" + ((xSgn >= 0) ? "+" : "-") + "Z" + ((zSgn >= 0) ? "+" : "-");
        drawText(vdraw, "%s, %s".formatted(directionName, symbolStr));
    }

    public void handleRotation(VDrawContext vdraw) {
        String rotation = "P:%.2f, Y: %.2f";
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        drawText(vdraw, rotation.formatted(manager.lastPitch, MathHelper.wrapDegrees(manager.lastYaw)));
    }

    public void handleFallDistance(VDrawContext vdraw) {
        String fallDistance = "Fall dist: %.2f";
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        drawText(vdraw, fallDistance.formatted(manager.fallDistance));
    }

    public void handleSpeed(VDrawContext vdraw) {
        String speedShow;
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        if (useKmH.get()) {
            String speed = "Avg:%.2fKm/h, Kwn:%.2fKm/h";
            speedShow = speed.formatted(
                    manager.lastAverageMovementSpeed.length() * 72, manager.lastKnownMovementSpeed.length() * 72);

        } else {
            String speed = "Avg:%.2fm/s, Kwn:%.2fm/s";
            speedShow = speed.formatted(
                    manager.lastAverageMovementSpeed.length() * 20, manager.lastKnownMovementSpeed.length() * 20);
        }
        drawText(vdraw, speedShow);
    }

    public void handleSpeedHorizontal(VDrawContext vdraw) {
        String speedShow;
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        if (useKmH.get()) {
            String speed = "H: Avg:%.2fKm/h, Kwn:%.2fKm/h";
            speedShow = speed.formatted(
                    manager.lastAverageMovementSpeed.horizontalLength() * 72,
                    manager.lastKnownMovementSpeed.horizontalLength() * 72);

        } else {
            String speed = "H: Avg:%.2fm/s, Kwn:%.2fm/s";
            speedShow = speed.formatted(
                    manager.lastAverageMovementSpeed.horizontalLength() * 20,
                    manager.lastKnownMovementSpeed.horizontalLength() * 20);
        }
        drawText(vdraw, speedShow);
    }

    public void handleSpeedVertical(VDrawContext vdraw) {
        String speedShow;
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        if (useKmH.get()) {
            String speed = "V: Avg:%.2fKm/h, Kwn:%.2fKm/h";
            speedShow = speed.formatted(
                    Math.abs(manager.lastAverageMovementSpeed.y) * 72, Math.abs(manager.lastKnownMovementSpeed.y) * 72);

        } else {
            String speed = "V: Avg:%.2fm/s, Kwn:%.2fm/s";
            speedShow = speed.formatted(
                    Math.abs(manager.lastAverageMovementSpeed.y) * 20, Math.abs(manager.lastKnownMovementSpeed.y) * 20);
        }
        drawText(vdraw, speedShow);
    }

    public static enum HudElement implements Displayable {
        ICON,
        COMMON_INFO,
        CONNECTION_INFO,
        POSITION,
        HEIGHT,
        DIRECTION,
        ROTATION,
        FALL_DISTANCE,
        SPEED,
        SPEED_HORIZONTAL,
        SPEED_VERTICAL;

        @Override
        public Text getDisplay() {
            return Text.literal(name());
        }
    }

    public static class HudElementSelectSet extends BoundedPrimitiveFlagMap<HudElement>
            implements NBTParsable<HudElementSelectSet> {
        public static final NBTType<HudElementSelectSet> TYPE = createEnumMap(
                "HudElementSelectSet".toLowerCase(Locale.ROOT), HudElement.class, HudElementSelectSet::new);

        public HudElementSelectSet(List<HudElement> keys, Map<HudElement, Boolean> map, NBTType<Boolean> type) {
            super(keys, map, type);
        }

        public HudElementSelectSet() {
            super(HudElement.class);
        }

        @Override
        public NBTType<HudElementSelectSet> type() {
            return TYPE.cast();
        }
    }
}
