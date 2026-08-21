package me.matl114.events.impl;

import net.minecraft.screen.slot.SlotActionType;

public record SlotClickAction(SlotActionType actionType, int syncId, int slotId, int button) {}
