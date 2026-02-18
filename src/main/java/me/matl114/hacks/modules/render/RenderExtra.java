package me.matl114.hacks.modules.render;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.Debug;
import me.matl114.utils.RegistryUtils;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

public class RenderExtra extends BaseModule {
    public static final String[] RESOURCE_IGNORE_SERVER = {"resource", "server", "ignore-server-request"};
    public static final String[] RENDER_NO_EFFECT = {"render", "no-effect"};
    public static final String[] NO_NAUSEA = {"render", "no-nausea"};
    public static final String[] RENDER_NIGHTVISION = {"render", "nightvision"};
    public static final String[] RENDER_NO_EFFECT_FORCE = {"render", "eff-setting", "force-no"};
    public static final String[] RENDER_NO_EFFECT_TYPES = {"render", "eff-setting", "types"};
    public static final String[] RENDER_REJECT_WURST = {"render", "disable-wurst-hud"};
    public static final String[] RENDER_ENHANCED_DEBUG_HUD = {"render", "enhanced-debug-hud"};
    public static final String[] RENDER_REARRANGE_GAME_MENU = {"render", "optimize-game-menu"};

    public RenderExtra() {}

    public Set<RegistryEntry<StatusEffect>> blackListedEffect = new HashSet<>();

    private void parseEffects(String regex) {
        blackListedEffect = RegistryUtils.parseEntryWhiteList(Registries.STATUS_EFFECT, regex);
    }

    public final FlagRef enableRejectResourcePack =
            flagBuilder(Configs.RENDER_CONFIG, RESOURCE_IGNORE_SERVER).build();

    public final FlagRef nightVision = builder(Configs.RENDER_CONFIG, RENDER_NIGHTVISION, Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef noEffect = builder(Configs.RENDER_CONFIG, RENDER_NO_EFFECT, Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef noNausea = builder(Configs.RENDER_CONFIG, NO_NAUSEA, Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef noEffectForce =
            flagBuilder(Configs.RENDER_CONFIG, RENDER_NO_EFFECT_FORCE).build();

    public final StringRef noEffectTypes = builder(Configs.RENDER_CONFIG, RENDER_NO_EFFECT_TYPES, String.class)
            .defaultValue("^(blindness|darkness|nausea)$")
            .validator(Configs.REGEX_VALIDATOR)
            .updateListener(this::parseEffects)
            .build();

    public final FlagRef noWurstHud =
            flagBuilder(Configs.RENDER_CONFIG, RENDER_REJECT_WURST).build();

    public final FlagRef enhancedDebugHud =
            flagBuilder(Configs.RENDER_CONFIG, RENDER_ENHANCED_DEBUG_HUD).build();

    public final FlagRef optimizeGameMenu = builder(Configs.RENDER_CONFIG, RENDER_REARRANGE_GAME_MENU, FlagRef.TYPE)
            .defaultValue(true)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(ResourcePackSendS2CPacket.class), this::onResourceRequest);
        registerListener(Listener.getPostInitializeScreen(), this::onGameMenuScreenRelocateWurstButton);
    }

    public void onResourceRequest(Event<ResourcePackSendS2CPacket> resourceEvent) {
        // note that resourcePack may be sent during configuration time
        if (enableRejectResourcePack.get()) {
            ClientConnection connection = resourceEvent.getArgs(0);
            var sendPacket = resourceEvent.context();
            connection.send(
                    new ResourcePackStatusC2SPacket(sendPacket.id(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
            connection.send(
                    new ResourcePackStatusC2SPacket(sendPacket.id(), ResourcePackStatusC2SPacket.Status.DOWNLOADED));
            connection.send(new ResourcePackStatusC2SPacket(
                    sendPacket.id(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
            Debug.chat(
                    Text.literal("Successfully reject server resourcepack").formatted(Formatting.GREEN),
                    sendPacket.id());
            Style st = Style.EMPTY;
            try {
                st = st.withClickEvent(new ClickEvent.OpenUrl(URI.create(sendPacket.url())));
            } catch (Exception e) {
            }
            Debug.chat(
                    Text.literal("Download url:").formatted(Formatting.GREEN),
                    Text.literal(sendPacket.url()).setStyle(st).formatted(Formatting.YELLOW));
            resourceEvent.cancel();
        }
    }

    private static final Set<Text> VANILLA_BUTTON_TEXT;

    static {
        Set<Text> texts = new LinkedHashSet<>();
        Field[] fields = GameMenuScreen.class.getDeclaredFields();
        for (var re : fields) {
            try {
                if (Modifier.isStatic(re.getModifiers()) && Text.class.isAssignableFrom(re.getType())) {
                    re.setAccessible(true);
                    Text text = (Text) re.get(null);
                    if (text instanceof MutableText text0
                            && text0.getContent() instanceof TranslatableTextContent translate) {
                        texts.add(text);
                    }
                }
            } catch (Throwable e) {
            }
        }

        VANILLA_BUTTON_TEXT = texts;
    }

    public void onGameMenuScreenRelocateWurstButton(Event<Screen> screenEvent) {
        if (screenEvent.context() instanceof GameMenuScreen screen && optimizeGameMenu.get()) {
            List<? extends Element> elements = screen.children();
            int extraButtons = 0;
            int lastLineY = 0;
            List<ButtonWidget> extraElements = new ArrayList<>();
            for (var el : elements) {
                if (el instanceof ButtonWidget button) {
                    Text text = button.getMessage();
                    if (VANILLA_BUTTON_TEXT.contains(text)) {
                        if (!button.visible) {
                            button.visible = true;
                        }
                        lastLineY = Math.max(lastLineY, button.getY());
                    } else {
                        extraElements.add(button);
                    }
                }
            }
            if (lastLineY > 0 && !extraElements.isEmpty()) {
                for (var entry : extraElements) {
                    if (entry.getWidth() > 100) {
                        extraButtons += 1;
                        entry.setY(lastLineY + 24 * extraButtons);
                    }
                }
            }
        }
    }
}
