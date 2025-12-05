package me.matl114.mixins.HackMixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.util.SelectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {
    @Shadow @Final
    private SelectionManager bookTitleSelectionManager;
    @Inject(method = "keyPressedSignMode", at = @At("HEAD"), cancellable = true)
    private void onStrengthenSignModeHotkeys(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir){
        if (Screen.isSelectAll(keyCode)) {
            this.bookTitleSelectionManager.selectAll();
            cir.setReturnValue(true);
            return;
        } else if (Screen.isCopy(keyCode)) {
            this.bookTitleSelectionManager.copy();
            cir.setReturnValue(true);
            return;
        } else if (Screen.isPaste(keyCode)) {
            this.bookTitleSelectionManager.paste();
            cir.setReturnValue(true);
            return;
        } else if (Screen.isCut(keyCode)) {
            this.bookTitleSelectionManager.cut();
            cir.setReturnValue(true);
            return;
        } else {
            SelectionManager.SelectionType selectionType = Screen.hasControlDown() ? SelectionManager.SelectionType.WORD : SelectionManager.SelectionType.CHARACTER;
            switch (keyCode) {
                case 257:
                case 335:
                    this.bookTitleSelectionManager.insert("\n");
                    cir.setReturnValue(true);
                    return;
                case 261:
                    this.bookTitleSelectionManager.delete(1, selectionType);
                    cir.setReturnValue(true);
                    return;
                case 262:
                    this.bookTitleSelectionManager.moveCursor(1, Screen.hasShiftDown(), selectionType);
                    cir.setReturnValue(true);
                    return;
                case 263:
                    this.bookTitleSelectionManager.moveCursor(-1, Screen.hasShiftDown(), selectionType);
                    cir.setReturnValue(true);
                    return;
                case 268:

                case 269:
                    cir.setReturnValue(false);
                    return;
                default:
                    //pass down to origin code
            }
        }
    }
}
