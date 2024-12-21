package me.matl114.SlimefunMixin.HackMixin;


import me.matl114.Access.ClientPlayerAccess;
import me.matl114.HackUtils.ChatTasks;
import me.matl114.HackUtils.MineTasks;
import me.matl114.ManageUtils.HotKeys;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.profiler.Profiler;
import org.bukkit.block.data.type.Piston;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class ClientInputMixin {


    @Shadow private Profiler profiler;

    @Shadow @Nullable public ClientPlayerEntity player;

    @Shadow @Nullable public ClientPlayerInteractionManager interactionManager;

    @ModifyArg(method = "handleInputEvents",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V",ordinal = 1))
    public Screen onRedirectInventoryKeyPress(Screen screen){
        if(HotKeys.getButtonToggleManager().getState(HotKeys.KEEP_INV)){
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if(player!=null&&ClientPlayerAccess.of(player).getKeepedInvHandler()!=null&& ClientPlayerAccess.of(player).getKeepedInv()!=null){
                HandledScreen screen1= ClientPlayerAccess.of(player).getKeepedInv();
                player.currentScreenHandler=ClientPlayerAccess.of(player).getKeepedInvHandler();
                ClientPlayerAccess.of(player).clearKeepedInventory(false);
                return screen1;
            }
        }
        return screen;
    }
    @Inject(method = "tick",at= @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;pop()V",shift = At.Shift.BEFORE,ordinal = 1),locals = LocalCapture.CAPTURE_FAILSOFT)
    public void onInjectTickTasks(CallbackInfo ci){
        this.profiler.swap("slimefun-helper-tasks");
        if(this.player!=null){
            if(HotKeys.getHotkeyToggleManager().getState(HotKeys.MINEBOT)){
                MineTasks.onMineBotStart(player,interactionManager);
            }else{
                MineTasks.onMineBotStop();
            }
            if(HotKeys.getHotkeyToggleManager().getState(HotKeys.MINE_ONEBLOCK)){
                MineTasks.onMineOneBlockStart(player,interactionManager);
            }else {
                MineTasks.onMineOneBlockStop();
            }
            if(HotKeys.getSimpleToggleManager().getState(HotKeys.AUTO_CHAT)){
                ChatTasks.onAutoChatStart();
            }else {
                ChatTasks.onAutoChatStop();
            }

        }
    }

}
