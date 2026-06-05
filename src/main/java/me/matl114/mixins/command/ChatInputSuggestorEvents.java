package me.matl114.mixins.command;

import com.mojang.brigadier.suggestion.Suggestions;
import java.util.concurrent.CompletableFuture;
import me.matl114.commands.MainCommand;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorEvents {
    @Shadow
    @Final
    TextFieldWidget textField;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    protected abstract void showCommandSuggestions();

    @Shadow
    public abstract void show(boolean a);

    @Inject(
            method = "refresh",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;getCursor()I",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private void parseClientCommandsTabComplete(CallbackInfo ci) {
        CompletableFuture<Suggestions> suggestionCompletableFuture =
                MainCommand.tabCompleteClientCommand(textField.getText(), textField.getCursor());
        if (suggestionCompletableFuture != null) {
            this.pendingSuggestions = suggestionCompletableFuture;
            this.pendingSuggestions.thenRun(() -> {
                if (this.pendingSuggestions.isDone()) {
                    show(true);
                }
            });
            ci.cancel();
        }
    }
}
