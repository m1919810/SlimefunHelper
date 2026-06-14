package me.matl114.hooks.mixin.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.command.defaults.ElytraCommand;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.matl114.hacks.modules.mine.SeedOre;
import me.matl114.hacks.modules.move.BaritoneFix;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(value = ElytraCommand.class, remap = false)
public abstract class ElytraCommandMixin extends Command {
    public ElytraCommandMixin(IBaritone iBaritone, String... strings) {
        super(iBaritone, strings);
    }

    @ModifyExpressionValue(
            method = "execute",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"),
            require = 0)
    private RegistryKey<World> onExecuteNetherSupport(RegistryKey<World> original) {
        if (BaritoneFix.INSTANCE.enableDimensionFix.get() && original != World.NETHER) {
            Debug.chat(ChatUtils.stringToText(
                    "&c[BaritoneFix] &fIgnoring dimension limit... (may cause unexpected behaviour)"));
            return World.NETHER;
        }
        return original;
    }

    @Inject(method = "execute", at = @At("HEAD"), require = 0)
    private void onAutoImportSeedValue(String par1, IArgConsumer par2, CallbackInfo ci) {
        if (BaritoneFix.INSTANCE.enableSeedAutoImport.get()) {
            SeedOre seedOre = SeedOre.INSTANCE;
            if (seedOre.hasCurrentSeed()) {
                long seed = seedOre.getCurrentSeed();
                if (seed != BaritoneAPI.getSettings().elytraNetherSeed.value && SeedOre.isSeedValid(seed)) {
                    Debug.chat(
                            Text.literal("[BaritoneFix]").formatted(Formatting.RED),
                            "Auto import the cached world seed",
                            ChatUtils.getDisplayedLong(seed));
                    BaritoneAPI.getSettings().elytraNetherSeed.value = (Long) seed;
                }
            }
            Debug.chat("[BaritoneFix] Using seed", BaritoneAPI.getSettings().elytraNetherSeed.value);
        }
        BaritoneFix.INSTANCE.checkFireworkSettings();
    }
}
