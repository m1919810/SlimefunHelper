package me.matl114.mixins.access;

import me.matl114.accessors.access.TextRendererAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(TextRenderer.class)
public interface TextRendererMixin extends TextRendererAccess {
    @Override
    @Invoker("getFontStorage")
    FontStorage slimefunhelper$getFontStorage(Identifier id);
}
