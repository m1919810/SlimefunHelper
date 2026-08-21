package me.matl114.events.impl;

import net.minecraft.client.Mouse;

public record MouseClickAction(Mouse mouse, int eventButton, int action, int mode) {}
