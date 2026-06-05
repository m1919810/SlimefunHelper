package me.matl114.accessors.hacks;

import me.matl114.hacks.utils.entity.PredictorImpl;
import net.minecraft.entity.player.PlayerEntity;

public interface PlayerInternalAccess extends EntityInternalAccess<PlayerEntity>{
    public PredictorImpl getPredictorImpl();
}
