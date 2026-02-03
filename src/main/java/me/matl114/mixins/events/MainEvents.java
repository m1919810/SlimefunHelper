package me.matl114.mixins.events;

import me.matl114.events.GlobalEventVars;
import me.matl114.events.Listener;
import me.matl114.events.Event;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public abstract class MainEvents {
    @Inject(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;run()V", shift = At.Shift.AFTER))
    private static void onMain(String[] args, CallbackInfo ci) {
        // handled crash in the printCrashReportMixin\\
        // printCrashReport will call System.exit, if we see a crashReport here then it is cancelled in the event here
        if(GlobalEventVars.crashReport != null){
            GlobalEventVars.crashReport = null;
            mainLoop(MinecraftClient.getInstance());
        }else{
            // not a crash
            if(!Listener.getClientMainExit().isEmpty()){
                MinecraftClient mc = MinecraftClient.getInstance();
                Event<MinecraftClient> event = new Event<>(mc, mc.isRunning(), false);
                Listener.getClientMainExit().handleValue(event);
                if(mc.isRunning()){
                    if(event.isCancelled()){
                        mainLoop(mc);
                    }
                }
            }
        }

    }

    private static void mainLoop(MinecraftClient mc){
        while(true){
            mc.run();
            if(GlobalEventVars.crashReport != null){
                GlobalEventVars.crashReport = null;
            }else{
                if(!Listener.getClientMainExit().isEmpty()){
                    Event<MinecraftClient> event = new Event<>(mc, mc.isRunning(), false);
                    Listener.getClientMainExit().handleValue(event);
                    if(!event.isCancelled()){
                        break;
                    }
                }
            }

        }
    }
}
