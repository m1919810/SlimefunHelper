package me.matl114.hacks.modules.extra;

import java.util.Optional;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.RotationManager;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import net.minecraft.client.network.ClientPlayerEntity;

public class Misc extends BaseModule {
    public Misc() {
        super("Misc");
    }

    public final ModulePath root = makePath(Configs.EXTRA_CONFIG, "misc");

    public final ModulePath friendsWine = root.add("friends_wine");

    public final FlagRef friendsWineEnable =
            flagBuilder(friendsWine.addEnable()).build();

    public final FlagRef sneak = flagBuilder(friendsWine.add("sneak")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onPreGameTick);
    }

    public void onPostTick(Event<ClientPlayerEntity> event) {
        if (checkNull()) return;
    }

    public void onPreGameTick(Event<ClientPlayerEntity> event) {
        if (checkNull()) return;
        int tick = Tasks.getTick() % 10;
        if (friendsWineEnable.get() && !mc.player.isFallFlying()) {
            if (sneak.get()) {
                PlayerInputManager.INSTANCE.addSneakModifier(0, tick <= 4, 1);
            }
            RotationManager.INSTANCE.request(
                    RotationManager.ofDirection(Optional.empty(), Optional.of(mc.player.getYaw() + 36 * tick))
                            .priority(-114514));
        }
    }
}
