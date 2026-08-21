package me.matl114.events.impl;

import net.minecraft.client.Mouse;

public record MouseDragAction(Mouse mouse, double mouseX, double mouseY, double deltaX, double deltaY) {}
