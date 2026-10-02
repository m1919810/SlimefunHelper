package me.matl114.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

@AllArgsConstructor
@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class PosUpdate {
    Vec3d oldPos;
    Vec3d newPos;
    Entity entity;
}
