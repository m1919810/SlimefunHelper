package me.matl114.hacks.modules.move;

import me.matl114.accessors.events.EntityAccess;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.versioned.api.VDataFlag;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;

public class MovExtra extends BaseModule {
    public static final String[] MOVE_COMPATE_HIGHER_VERSION = {"move-safety", "disable-stepheight-feature"};

    public static final String[] FLIGTH_HOTKEY = {"move-safety", "flight", "toggle-flying"};

    public MovExtra() {}

    public final FlagRef noStepHeightFeature =
            flagBuilder(Configs.MOV_CONFIG, MOVE_COMPATE_HIGHER_VERSION).build();

    public final KeyBindRef toggleFlyStateKeyBind = hotkey(Configs.MOV_CONFIG, FLIGTH_HOTKEY)
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::onFlightToggle))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
    }

    public void onFlightToggle() {
        if (mc.player.isFallFlying()) {
            // stop fallflying
            mc.getNetworkHandler()
                    .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            EntityAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
        } else {
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
    }
}
