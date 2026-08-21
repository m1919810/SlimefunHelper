package me.matl114.events.impl;

import net.minecraft.client.Keyboard;

public record KeyboardAction(Keyboard keyboard, int keyCode, int scannCode, int action, int modifier) {}
