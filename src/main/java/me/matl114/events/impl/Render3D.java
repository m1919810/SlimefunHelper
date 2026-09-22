package me.matl114.events.impl;

import net.minecraft.client.util.math.MatrixStack;

public record Render3D(MatrixStack stack, float partialTicks) {}
