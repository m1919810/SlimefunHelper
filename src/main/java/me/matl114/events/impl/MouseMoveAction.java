package me.matl114.events.impl;

import net.minecraft.client.Mouse;

public record MouseMoveAction(Mouse mouse, double mouseX, double mouseY) {}
