package me.matl114.events.impl;

import me.matl114.versioned.api.VDrawContext;

public record Render2D(VDrawContext drawContext, float partialTicks, boolean hudHidden) {}
