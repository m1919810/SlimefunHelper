package me.matl114.hacks.modules.combat;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.MineTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.MathUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class AutoCity extends BaseModule {
    public final ModulePath combatUtils = makePath(Configs.COMBAT_CONFIG, "combat-utils");
    public final ModulePath autoCity = combatUtils.add("auto-city");

    public final FlagRef enable = flagBuilder(autoCity.add("enable")).build();

    public final FlagRef playerOnly = flagBuilder(autoCity.add("player-only")).build();

    public AutoCity() {}

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInputEvent);
    }

    Entity targetEntity;
    BlockPos targetPos;

    public void refreshTarget() {
        if (targetEntity == null
                || !targetEntity.isAlive()
                || targetEntity.isRemoved()
                || targetEntity.getBoundingBox().squaredMagnitude(mc.player.getEyePos())
                        > MathUtils.s2(MineTasks.getMineExtra().getReachDistance() + 1.0D)) {
            targetEntity = null;
            targetPos = null;
        }
        if (targetEntity == null) {
            targetEntity = CombatTasks.getTargetSelector()
                    .searchAttackEntity(
                            MineTasks.getMineExtra().getReachDistance() + 1.0D,
                            false,
                            playerOnly.get() ? (pl) -> pl instanceof PlayerEntity : null);
        }
    }

    public BlockPos calculateTargetPos() {
        Vec3d vec3d = targetEntity.getPos();
        // BlockPos pos = vec3
        return null;
    }

    public void onInputEvent(Event<Void> event) {}
}
