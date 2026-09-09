package me.matl114.events.impl;

import net.minecraft.entity.player.PlayerEntity;

public record CombatPlayer(PlayerEntity player, int popCnt) {}
