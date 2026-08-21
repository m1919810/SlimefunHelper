package me.matl114.events.impl;

import net.minecraft.client.Mouse;

public record MouseScrollAction(Mouse mouse, double horizontal, double vertical) {}
