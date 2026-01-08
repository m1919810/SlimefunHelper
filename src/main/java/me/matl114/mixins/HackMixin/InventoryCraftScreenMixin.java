package me.matl114.mixins.HackMixin;

import me.matl114.access.PlayerInteractionAccess;
import me.matl114.hackUtils.InvTasks;
import me.matl114.hackUtils.RenderTasks;
import me.matl114.managers.HotKeys;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InventoryScreen.class)
public abstract class InventoryCraftScreenMixin extends AbstractInventoryScreen<PlayerScreenHandler> implements RecipeBookProvider {
    public InventoryCraftScreenMixin(PlayerScreenHandler screenHandler, PlayerInventory playerInventory, Text text) {
        super(screenHandler, playerInventory, text);
    }
    @Inject(method = "init",at = @At("RETURN"))
    protected void initAdd(CallbackInfo ci){
        //skip the open of CreativeInventory
        if(!this.client.interactionManager.hasCreativeInventory()){
            addButton();
        }
    }
    @ModifyArg(method = "init",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/TexturedButtonWidget;<init>(IIIILnet/minecraft/client/gui/screen/ButtonTextures;Lnet/minecraft/client/gui/widget/ButtonWidget$PressAction;)V"),index = 5)
    public ButtonWidget.PressAction modifyPressAction(ButtonWidget.PressAction pressAction){
        return (button -> {
            pressAction.onPress(button);
            refreshButton();
        });
    }
    @Unique
    private ButtonWidget putLastRecipeButton = null;
    @Unique
    private ButtonWidget toggleLockButton = null;
    @Unique
    private ButtonWidget toggleDropButton = null;
    @Unique
    private void addButton(){
        this.putLastRecipeButton = ButtonWidget.builder(Text.translatable("widget.fast-craft.craft"),(button -> {
            InvTasks.placeLastCraftingRecipe((InventoryScreen)(Object)this,  Screen.hasShiftDown());
        }))
            .tooltip(Tooltip.of(Text.translatable("widget.fast-craft.craft.tooltips")))
            .dimensions(this.x+150,this.height / 2 -38,24,12).build();
        this.addDrawableChild(this.putLastRecipeButton);
        this.toggleLockButton = ButtonWidget.builder(Text.translatable("widget.fast-craft.lock"),(button)->{
            PlayerInteractionAccess.of(MinecraftClient.getInstance().interactionManager).toggleRecipeLock();
        })
            .tooltip(Tooltip.of(Text.translatable("widget.fast-craft.lock.tooltips")))
            .dimensions(this.x+150,this.height / 2 -25,24,12).build();
        this.addDrawableChild(this.toggleLockButton);
        Runnable toggle = HotKeys.getSimpleToggleManager().getToggle(HotKeys.DROP_CRAFT);
        this.toggleDropButton = ButtonWidget.builder(Text.translatable("widget.fast-craft.toggle-drop"),(button)-> toggle.run())
            .tooltip(Tooltip.of(Text.translatable("widget.fast-craft.toggle-drop.tooltips")))
            .dimensions(this.x+150,this.height / 2 -72,24,12).build();
        this.addDrawableChild(this.toggleDropButton);
    }
    @Unique
    private void refreshButton(){
        this.putLastRecipeButton.setPosition(this.x+150,this.height / 2 -38);
        this.toggleLockButton.setPosition(this.x+150,this.height / 2 -25);
        this.toggleDropButton.setPosition(this.x+150,this.height / 2 -72);
    }
    @Inject(method = "render",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/AbstractInventoryScreen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V",shift = At.Shift.AFTER))
    public void drawRecipeHistoryDisplay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci){
        RenderTasks.drawRecipeHistory(context,this.textRenderer,this.x,this.y,132,55);
    }
}
