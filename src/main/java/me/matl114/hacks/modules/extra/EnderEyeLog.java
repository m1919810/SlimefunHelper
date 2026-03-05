package me.matl114.hacks.modules.extra;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.concurrent.CompletableFuture;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EyeOfEnderEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.Vec3d;

public class EnderEyeLog extends BaseModule {
    public static String[] ENABLE = {"other", "enable-ender-eye-log"};

    public EnderEyeLog() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(Configs.TEST_CONFIG, ENABLE).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(EntitySpawnS2CPacket.class), this::onEnderEye);
        registerListener(Listener.getEntityRemoveListener(), this::onEntityRemove);
    }

    private final Int2ObjectMap<Vec3d> tracked = new Int2ObjectOpenHashMap<>();

    public void onEnderEye(Event<EntitySpawnS2CPacket> event) {
        EntitySpawnS2CPacket packet = event.context();
        if (enable.get() && packet.getEntityType() == EntityType.EYE_OF_ENDER) {
            Vec3d pos = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
            int id = packet.getEntityId();
            tracked.put(id, pos);
        }
    }

    private static final int MAX_STRONGHOLD_COORD = 40_000;
    private static final IntList STRONGHOLD_DISTANCE_RANGES = new IntArrayList();

    static {
        int distance = 32;
        int nRings = 8;
        for (var i = 0; i < nRings; i++) {
            int minR = (int) (distance * (2.75 + 6 * i)) * 16 - 128;
            int maxR = (int) (distance * (5.25 + 6 * i)) * 16 + 128;
            STRONGHOLD_DISTANCE_RANGES.add(minR);
            STRONGHOLD_DISTANCE_RANGES.add(maxR);
        }
    }

    public void onEntityRemove(Event<Entity> event) {
        if (enable.get() && event.context() instanceof EyeOfEnderEntity) {
            Vec3d startPos = tracked.remove(event.context().getId());
            if (startPos != null) {
                Vec3d endPos = event.context().getPos();
                Vec3d velocity = endPos.subtract(startPos);
                velocity = velocity.normalize();
                int startX = (int) startPos.getX();

                if (velocity.x == 0) {
                    if (velocity.z > 0) {
                        Debug.chat("[EnderEye] Pointing at Z+");
                    } else {
                        Debug.chat("[EnderEye] Pointing at Z-");
                    }
                } else if (velocity.z == 0) {
                    if (velocity.x > 0) {
                        Debug.chat("[EnderEye] Pointing at X+");
                    } else {
                        Debug.chat("[EnderEye] Pointing at X-");
                    }
                } else {
                    int deltaX = velocity.x > 0 ? 1 : -1;
                    double k = velocity.getZ() / velocity.getX();
                    double b = startPos.getZ() - k * startPos.getX();
                    CompletableFuture.<IntList>supplyAsync(() -> {
                                IntList testPoints = new IntArrayList();
                                int maxThresY = (int) ((long) (MAX_STRONGHOLD_COORD - b - k * startX) / (deltaX * k));
                                int minThresY = (int) ((long) (-MAX_STRONGHOLD_COORD - b - k * startX) / (deltaX * k));

                                int maxThresX = (int) (MAX_STRONGHOLD_COORD - startX) / deltaX;
                                int minThresX = (int) (-MAX_STRONGHOLD_COORD - startX) / deltaX;
                                int minThreshold = Math.max(
                                        0, Math.min(Math.min(maxThresY, minThresY), Math.min(maxThresX, minThresX)));
                                int maxThreshold = Math.max(
                                        0, Math.min(Math.max(maxThresY, minThresY), Math.max(maxThresX, minThresX)));
                                for (var i = minThreshold; i < maxThreshold; i++) {
                                    int x = startX + deltaX * i;
                                    if (x % 16 == 0) {
                                        testPoints.add(x);
                                    }
                                }
                                double minDelta = 1.0D;
                                IntList list = new IntArrayList();
                                for (int p : testPoints) {

                                    double y = k * p + b;
                                    long yf = Math.round(y);
                                    if (yf % 16 == 0) {
                                        double delta = Math.abs(yf - y);
                                        if (delta < 1e-1) {
                                            minDelta = Math.min(minDelta, delta);
                                            list.add(p);
                                        }
                                    }
                                }
                                return list;
                            })
                            .thenAcceptAsync(
                                    i -> {
                                        if (!i.isEmpty()) {

                                            Debug.chat("[EnderEye] Potentials Positions:");
                                            for (int d : i) {
                                                int y = (int) Math.round(k * d + b);
                                                Debug.chat("[EnderEye] ", ChatUtils.getDisplayedLocation(d, y));
                                            }
                                            filter:
                                            for (int d : i) {
                                                int y = (int) Math.round(k * d + b);
                                                int distanceSqrt = (int) Math.sqrt(d * d + y * y);
                                                // use the information from
                                                // https://zh.minecraft.wiki/w/%E8%A6%81%E5%A1%9E to filter wrong
                                                // position
                                                for (var idx = 0;
                                                        idx < STRONGHOLD_DISTANCE_RANGES.size() - 1;
                                                        idx += 2) {
                                                    int minR = STRONGHOLD_DISTANCE_RANGES.getInt(idx);
                                                    int maxR = STRONGHOLD_DISTANCE_RANGES.getInt(idx + 1);
                                                    if (distanceSqrt >= minR && distanceSqrt <= maxR) {
                                                        Debug.chat(
                                                                "[EnderEye] Most probably at:",
                                                                ChatUtils.getDisplayedLocation(d, y));
                                                        break filter;
                                                    }
                                                }
                                            }

                                        } else {
                                            Debug.chat("[EnderEye] Calculation failure");
                                        }
                                    },
                                    mc);
                }
            }
        }
    }
}
