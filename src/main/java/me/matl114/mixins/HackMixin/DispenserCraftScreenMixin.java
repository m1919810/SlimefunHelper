package me.matl114.mixins.HackMixin;

import me.matl114.access.TileInventoryScreen;
import me.matl114.gui.basic.ContentDelegateWidget;
import me.matl114.gui.slimefun.SlimefunDispensorSuggestBookWidget;
import me.matl114.hackUtils.InteractionTasks;
import me.matl114.hackUtils.SlimefunTasks;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@Mixin(Generic3x3ContainerScreen.class)
public abstract class DispenserCraftScreenMixin extends HandledScreen<Generic3x3ContainerScreenHandler> implements TileInventoryScreen {
    @Unique
    private SlimefunDispensorSuggestBookWidget recipeBookWidget;

    @Unique
    private BlockPos pos;

    @Unique
    public BlockPos getPos(){
        return this.pos;
    }

    @Unique
    private Block cacheBlockType;

    @Unique
    public Block getBlockType(){
        return cacheBlockType;
    }

    @Unique
    private ClientWorld world;

    @Unique
    public ClientWorld getWorld(){
        return this.world;
    }

    @Unique
    public HandledScreen<?> castHandled(){
        return this;
    }

    public DispenserCraftScreenMixin(ScreenHandler handler, PlayerInventory inventory, Text title) {
        super((Generic3x3ContainerScreenHandler) handler, inventory, title);
    }
    @Unique
    private static final int[] AVAILABLE_SLOTS = new int[]{
        0,1,2,3,4,5,6,7,8
    };

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;<init>(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/text/Text;)V", shift = At.Shift.AFTER))
    protected void tryInitBlockPos(Generic3x3ContainerScreenHandler handler, PlayerInventory inventory, Text title, CallbackInfo ci){
        this.world = MinecraftClient.getInstance().world;
        this.pos = InteractionTasks.predictScreenFrom((b)->b == Blocks.DISPENSER || b == Blocks.DROPPER);
        if(this.pos != null && this.world != null){
            cacheBlockType = this.world.getBlockState(this.pos).getBlock();
        }
        Collection<String> co = this.isVirtual()? null: SlimefunTasks.getOptionalMultiBlockTypes(this.world, this.pos)
            .stream()
            .map(SlimefunTasks.MultiBlockEntry::getOptionalCraftingType)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(SlimefunTasks.CraftingType::id)
            .collect(Collectors.toSet());
        this.recipeBookWidget = new SlimefunDispensorSuggestBookWidget( this,3,  3,co,(bol, entry)-> SlimefunTasks.moveSlimefunRecipePatternToContainer(entry, this, bol, true, AVAILABLE_SLOTS));
    }


    @Inject(method = "init",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;init()V", shift = At.Shift.AFTER))
    protected void initRecipeBook(CallbackInfo ci){
        this.recipeBookWidget.refreshActiveState();
        new ContentDelegateWidget<>(this.x , this.y,0,0)
            .setContentDelegate(this.recipeBookWidget)
            .addTo(this);
    }

}
