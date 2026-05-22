package me.matl114.hacks.modules.extra;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.other.BeaconEffectSelectButton;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.packet.c2s.play.UpdateBeaconC2SPacket;
import net.minecraft.text.Text;

public class BeaconEnhance extends BaseModule {
    public final ModulePath other = makePath(Configs.TEST_CONFIG, "other");

    public BeaconEnhance() {
        bindFlag(enable);
    }

    public final FlagRef enable =
            flagBuilder(other.add("enable-beacon-enhance")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostInitializeScreen(), this::onScreenInitialize);
    }

    public void onScreenInitialize(Event<Screen> e) {
        // todo: rewrite it with DrawableWidget
        if (enable.get() && e.context() instanceof BeaconScreen beaconScreen) {
            HandledScreenAccess access = HandledScreenAccess.of(beaconScreen);
            int scx = access.getScreenX();
            int scy = access.getScreenY();
            var buttonLevel1 =
                    new BeaconEffectSelectButton(scx + 167 - 23, scy + 47 + 26, 22, 22, Text.literal("第一等级: "));
            access.addDrawableChildTo(buttonLevel1);
            var buttonLevel2 =
                    new BeaconEffectSelectButton(scx + 167 + 1, scy + 47 + 26, 22, 22, Text.literal("第二等级: "));
            access.addDrawableChildTo(buttonLevel2);
            AtomicReference<ButtonWidget> buttonTrigger =
                    new AtomicReference<>(ButtonWidget.builder(Text.literal("Send packet"), (b) -> {
                                MinecraftClient.getInstance()
                                        .getNetworkHandler()
                                        .sendPacket(new UpdateBeaconC2SPacket(
                                                Optional.ofNullable(buttonLevel1.getCurrentEffect()),
                                                Optional.ofNullable(buttonLevel2.getCurrentEffect())));
                                Debug.chat(Text.literal("成功发送了信标设置!"));
                            })
                            .tooltip(Tooltip.of(Text.literal("点击上方选效果,点此强制修改信标")))
                            .dimensions(scx + 167 - 23, scy + 47 + 48, 46, 10)
                            .build());
            access.addDrawableChildTo(buttonTrigger.get());
        }
    }
}
