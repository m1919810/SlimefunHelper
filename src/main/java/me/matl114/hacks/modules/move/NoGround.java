package me.matl114.hacks.modules.move;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import me.matl114.accessors.access.PlayerMoveC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.mine.FakeBlockManager;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CollisionUtil;
import me.matl114.versioned.api.VPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class NoGround extends BaseModule {
    public static NoGround INSTANCE;

    public NoGround() {
        super("NoGround");
        bindFlag(enable);
        INSTANCE = this;
    }

    public final ModulePath root = makePath(Configs.MOV_CONFIG, "move-safety.no-ground");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final EnumRef<Mode> mode =
            builder(root.add("mode"), Mode.class).defaultValue(Mode.NONE).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onSendMovePacket);
    }

    public void onSendMovePacket(Event<PlayerMoveC2SPacket> event) {
        if (enable.get()) {
            var packet = event.context;
            if (packet.isOnGround()) {
                mc.player.setOnGround(false);

                if (mode.get().isIn(Mode.GRIM_FAKE_MINE) && packet.changesPosition()) {
                    if (PlayerMoveC2SPacketAccess.of(packet).getCause()
                            != PlayerMoveC2SPacketAccess.Cause.LEGACY_SNAP) {
                        Vec3d vec3d = new Vec3d(packet.getX(0.0D), packet.getY(0.0D), packet.getZ(0.0D));
                        if (Objects.equals(vec3d, PlayerStateManager.INSTANCE.getLastPosition())) {
                            event.context(
                                    packet.changesLook()
                                            ? VPacket.newLookAndOnGround(
                                                    packet.getYaw(0.0F), packet.getPitch(0.0F), false, false)
                                            : VPacket.newOnGroundOnly(false, false));
                        } else {
                            Box box = mc.player.dimensions.getBoxAt(vec3d);
                            List<BlockPos> colliding = CollisionUtil.getBoxCollision(mc.world, mc.player, box);
                            Box boxDown = box.withMinY(box.minY - 0.5).withMaxY(box.minY - 0.001);
                            List<BlockPos> colliding2 = CollisionUtil.getBoxCollision(mc.world, mc.player, boxDown);
                            Set<BlockPos> poses = new HashSet<>(colliding2);
                            colliding.forEach(poses::remove);
                            for (BlockPos pos : poses) {
                                FakeBlockManager.INSTANCE.addFakeCompensateState(pos);
                            }
                        }
                    }
                }
                if (event.context instanceof PlayerMoveC2SPacket.OnGroundOnly
                        && !PlayerStateManager.INSTANCE.lastOnGround) {
                    event.cancel();
                    return;
                }
                PlayerMoveC2SPacketAccess.of(event.context).setOnGround(false);
            }
        }
    }

    public enum Mode implements ConfigEnum {
        NONE,
        GRIM_FAKE_MINE;

        @Override
        public String getConfigEnumType() {
            return "no_ground_mode";
        }
    }
}
