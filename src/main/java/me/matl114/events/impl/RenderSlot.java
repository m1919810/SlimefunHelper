package me.matl114.events.impl;

import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

public record RenderSlot(VDrawContext context, HandledScreen<?> screen, Slot slot, double mouseX, double mouseY) {}
