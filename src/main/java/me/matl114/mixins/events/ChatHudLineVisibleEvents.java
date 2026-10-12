package me.matl114.mixins.events;

import java.util.HashSet;
import java.util.Set;
import me.matl114.accessors.events.ChatHudLineAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(ChatHudLine.Visible.class)
public abstract class ChatHudLineVisibleEvents implements ChatHudLineAccess {
    @Unique
    private final Set<String> uniqueMessageIds = new HashSet<>();

    @Unique
    @Override
    public void addUniqueMessageId(String uniqueMessageId) {
        if (uniqueMessageId != null) this.uniqueMessageIds.add(uniqueMessageId);
    }

    @Unique
    @Override
    public Set<String> getUniqueMessageIds() {
        return this.uniqueMessageIds;
    }
}
