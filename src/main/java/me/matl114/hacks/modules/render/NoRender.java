package me.matl114.hacks.modules.render;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.EntityTypeRegex;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.particle.Particle;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;

public class NoRender extends BaseModule {
    public static NoRender INSTANCE;
    public final ModulePath render = makePath(Configs.RENDER_CONFIG, "render");

    public final ModulePath root = render.add("no-render");

    public final ModulePath overlay = root.add("overlay");
    public final ModulePath effectSetting = root.add("eff-setting");
    public final ModulePath worldEffect = root.add("world-effect");
    public final ModulePath fovEffect = root.add("fov-effect");
    public final ModulePath entity = root.add("entity");
    public final ModulePath particle = root.add("particle");

    public NoRender() {
        super("NoRender");
        bindFlag(enable);
        INSTANCE = this;
    }

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            toggleHotkey(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final FlagRef noWallOverlay =
            flagBuilder(overlay.add("wall-overlay")).build();

    public final FlagRef noLiquidOverlay =
            flagBuilder(overlay.add("liquid-overlay")).build();

    public final FlagRef noFireOverlay =
            flagBuilder(overlay.add("fire-overlay")).build();

    public final FlagRef noFreezeOverlay =
            flagBuilder(overlay.add("freeze-overlay")).build();

    public final FlagRef noItemOverlay =
            flagBuilder(overlay.add("item-overlay")).build();

    public final FlagRef noPortalOverlay =
            flagBuilder(overlay.add("portal-overlay")).build();

    public final FlagRef noGuiBackGroundOverlay =
            flagBuilder(overlay.add("gui-overlay")).build();

    public final FlagRef noVignetteOverlay =
            flagBuilder(overlay.add("vignette")).build();

    public final FlagRef noDistanceFog =
            flagBuilder(worldEffect.add("distance-fog")).build();

    public final FlagRef noRandomWorldEffect =
            flagBuilder(worldEffect.add("block-random-effect")).build();

    public final FlagRef noWeather =
            flagBuilder(worldEffect.add("weather-effect")).build();

    public final FlagRef noNausea = flagBuilder(effectSetting.add("no-nausea")).build();

    public final FlagRef noDarkNess =
            flagBuilder(effectSetting.add("no-darkness")).build();

    public final FlagRef noBlindness =
            flagBuilder(effectSetting.add("no-blindness")).build();

    public final FlagRef noEffectForce =
            flagBuilder(effectSetting.add("force-no")).build();

    public final NBTRef<RegistryRegex<StatusEffect>> noEffectTypes = builder(
                    effectSetting.add("types"), NBTType.<RegistryRegex<StatusEffect>>parameter(RegistryRegex.class))
            .defaultValue(new RegistryRegex<>(new Regex("^(blindness|darkness|nausea)$"), Registries.STATUS_EFFECT))
            .build();

    public final FlagRef noFlyFov = flagBuilder(fovEffect.add("fly")).build();

    public final FlagRef noSlowDownFov = flagBuilder(fovEffect.add("slow-down")).build();

    public final FlagRef noSpeedFov = flagBuilder(fovEffect.add("speed-up")).build();

    public final FlagRef noUseItemFov = flagBuilder(fovEffect.add("use-bow")).build();

    public final FlagRef ignoreSpawn = flagBuilder(entity.add("force-no")).build();

    public final NBTRef<EntityTypeRegex> types = builder(entity.add("types"), EntityTypeRegex.class)
            .defaultValue(new EntityTypeRegex(new Regex("^()$")))
            .build();

    public final FlagRef invisibility = flagBuilder(entity.add("invisibility")).build();

    public final FlagRef ignoreParticle = flagBuilder(particle.add("force-no")).build();

    public final NBTRef<RegistryRegex<ParticleType<?>>> particleTypes = builder(
                    particle.add("types"), RegistryRegex.<ParticleType<?>>parameter())
            .defaultValue(new RegistryRegex<>(new Regex("^()$"), Registries.PARTICLE_TYPE))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusEffectS2CPacket.class), this::doCancelEffect);
        registerListener(Listener.getPacketPoint().getChannel(EntitySpawnS2CPacket.class), this::doCancelSpawn);
        registerListener(
                Listener.getParticleCreateListener().getChannel(ParticleTypes.RAIN), this::doParticleSpawnWeather);
        registerListener(
                Listener.getParticleCreateListener().getChannel(ParticleTypes.SNOWFLAKE), this::doParticleSpawnWeather);
        registerListener(Listener.getParticleCreateListener(), this::doParticleSpawnTyped);
    }

    public void doCancelEffect(Event<EntityStatusEffectS2CPacket> packet) {
        if (checkNull()) return;
        if (enable.get() && noEffectForce.get() && noEffectTypes.get().test(packet.context.getEffectId())) {
            packet.cancel();
        }
    }

    public void doCancelSpawn(Event<EntitySpawnS2CPacket> packet) {
        if (checkNull()) return;
        if (enable.get() && ignoreSpawn.get() && types.get().test(packet.context.getEntityType())) {
            packet.cancel();
        }
    }

    public void doParticleSpawnWeather(Event<Particle> event) {
        if (checkNull()) return;
        if (enable.get() && noWeather.get()) {
            if (mc.world.isRaining()) {
                event.cancel();
            }
        }
    }

    public void doParticleSpawnTyped(Event<Particle> event) {
        if (checkNull()) {
            return;
        }
        if (enable.get() && ignoreParticle.get()) {
            ParticleEffect p = event.getArgs(0);
            if (particleTypes.get().test(p.getType())) {
                event.cancel();
            }
        }
    }

    // 各覆盖层禁用判定
    public boolean noWallOverlay() {
        return isActive() && noWallOverlay.get();
    }

    public boolean noLiquidOverlay() {
        return isActive() && noLiquidOverlay.get();
    }

    public boolean noFireOverlay() {
        return isActive() && noFireOverlay.get();
    }

    public boolean noFreezeOverlay() {
        return isActive() && noFreezeOverlay.get();
    }

    public boolean noItemOverlay() {
        return isActive() && noItemOverlay.get();
    }

    public boolean noPortalOverlay() {
        return isActive() && noPortalOverlay.get();
    }

    public boolean noGuiBackGroundOverlay() {
        return isActive() && noGuiBackGroundOverlay.get();
    }

    public boolean noVignetteOverlay() {
        return isActive() && noVignetteOverlay.get();
    }

    // 世界效果禁用判定
    public boolean noDistanceFog() {
        return isActive() && noDistanceFog.get();
    }

    public boolean noDistanceFogVanilla() {
        return noDistanceFog();
    }

    public boolean noRandomWorldEffect() {
        return isActive() && noRandomWorldEffect.get();
    }

    public boolean noWeather() {
        return isActive() && noWeather.get();
    }

    // 状态效果相关
    public boolean noNausea() {
        return isActive() && noNausea.get();
    }

    public boolean noDarkNess() {
        return isActive() && noDarkNess.get();
    }

    public boolean noBlindness() {
        return isActive() && noBlindness.get();
    }

    public boolean noInvisibility() {
        return isActive() && invisibility.get();
    }
}
