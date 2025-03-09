package me.matl114.SlimefunMixin.HackMixin;

import me.matl114.Access.PlayerInteractionAccess;
import me.matl114.HackUtils.InvTasks;
import me.matl114.HackUtils.RenderTasks;
import me.matl114.ManageUtils.HotKeys;
import me.matl114.SlimefunUtils.Debug;
import me.matl114.Utils.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
@Mixin(CraftingScreen.class)
public abstract class CraftingScreenMixin extends HandledScreen<CraftingScreenHandler> implements RecipeBookProvider {
    public CraftingScreenMixin(CraftingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }
    @Inject(method = "init",at = @At("RETURN"))
    protected void initAdd(CallbackInfo ci){
        addButton();
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
        this.putLastRecipeButton = ButtonWidget.builder(Text.literal("合成"),(button -> {
            InvTasks.placeLastCraftingRecipe((CraftingScreen) (Object)this,Screen.hasShiftDown());
        })).dimensions(this.x+120,this.height / 2 -25,24,12).build();
        this.addDrawableChild(this.putLastRecipeButton);
        this.toggleLockButton = ButtonWidget.builder(Text.literal("锁"),(button)->{
            PlayerInteractionAccess.of(MinecraftClient.getInstance().interactionManager).toggleRecipeLock();
        }).dimensions(this.x+95,this.height / 2 -25,24,12).build();
        this.addDrawableChild(this.toggleLockButton);
        Runnable toggle = HotKeys.getSimpleToggleManager().getToggle(HotKeys.DROP_CRAFT);
        this.toggleDropButton = ButtonWidget.builder(Text.literal("喷射"),(button)-> toggle.run()).dimensions(this.x+120,this.height / 2 -72,24,12).build();
        this.addDrawableChild(this.toggleDropButton);
    }
    @Unique
    private void refreshButton(){
        this.putLastRecipeButton.setPosition(this.x+120,this.height / 2 -25);
        this.toggleLockButton.setPosition(this.x+95,this.height / 2 -25);
        this.toggleDropButton.setPosition(this.x+120,this.height / 2 -72);
    }

    @Inject(method = "render",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V",shift = At.Shift.AFTER))
    public void drawRecipeHistoryDisplay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci){
        RenderTasks.drawRecipeHistory(context,this.textRenderer,this.x,this.y,150,56);
    }
}
