package me.matl114.hacks.modules.survival;

import java.util.UUID;
import me.matl114.accessors.access.ProjectileAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

public class PearlESP extends BaseModule {
    public static PearlESP INSTANCE;

    public final ModulePath renderUtils = makePath(Configs.SURVIVAL_CONFIG, "render-utils");
    public final ModulePath pearlEsp = renderUtils.add("pearl-esp");

    public PearlESP() {
        super("PearlESP");
        INSTANCE = this;
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(pearlEsp.addEnable()).build();

    public final KeyBindRef hotkey = toggleHotkey(pearlEsp.addHotkey(), new MultiKeyBind(), pearlEsp.addEnable())
            .build();

    public final DoubleRef textScale = doubleBuilder(pearlEsp.add("text-scale"))
            .defaultValue(0.75D)
            .validator(Configs.doubleRange(0.1D, 4.0D))
            .build();

    public final NBTRef<WrapColor> color = builder(pearlEsp.add("color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.AQUA))
            .build();

    private final RenderCollector<RenderElements.Text> textCollector = RenderCollectors.createTextCollector();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        textCollector.clear();
    }

    public void onTick(Event<ClientPlayerEntity> event) {
        textCollector.clear();
        if (checkNull() || !enable.get() || WorldManager.INSTANCE == null) {
            return;
        }

        int textColor = color.get().withAlpha(255);
        float scale = (float) textScale.get();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EnderPearlEntity pearl) {
                Text displayText = buildText(pearl);
                if (displayText == null) {
                    continue;
                }
                Vec3d textPos = pearl.getPos();
                textCollector.submit(new RenderElements.Text(displayText, textPos, scale), textColor);
            }
        }
    }

    public void onRender3D(Event<Render3D> event) {
        if (!enable.get()) {
            return;
        }
        RenderUtils.startDrawVirtual(event.context().stack());
        try {
            textCollector.render3D(event.context().stack());
        } finally {
            RenderUtils.stopDrawVirtual(event.context().stack());
        }
    }

    private Text buildText(EnderPearlEntity villager) {
        if (villager.getOwner() instanceof PlayerEntity pl) {
            return Text.empty()
                    .append(pl.getNameForScoreboard())
                    .append(Text.translatable("message.module.pearl-esp.display.online"));
        }
        ProjectileAccess access = ProjectileAccess.of(villager);
        boolean hasOwner;
        boolean online;
        UUID uid = WorldManager.INSTANCE.getThrownEntityOwner(villager);
        hasOwner = uid != null || access.getOwnerEid().isPresent();
        if (uid != null && mc.getNetworkHandler().getPlayerListEntry(uid) != null) {
            online = true;
        } else if (access.getOwnerEid().isPresent()) {
            online = true;
        } else {
            online = false;
        }
        if (!hasOwner && !online) {
            return Text.translatable("message.module.pearl-esp.display.no-owner");
        } else {
            MutableText txt = Text.empty();
            String name = WorldManager.INSTANCE.getThrownEntityOwnerName(villager);
            name = name == null ? "" : name;
            txt = txt.append(name);
            if (online) {
                txt = txt.append(Text.translatable("message.module.pearl-esp.display.online"));
            } else {
                txt = txt.append(Text.translatable("message.module.pearl-esp.display.offline"));
            }
            return txt;
        }
    }
}
