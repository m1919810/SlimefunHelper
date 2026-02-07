package me.matl114.hacks;

import lombok.Getter;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.interact.AutoAttack;
import me.matl114.hacks.modules.interact.AutoInteract;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.interact.Scaffold;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.jetbrains.annotations.ApiStatus;

public class InteractionTasks {
    public static void init(){

    }
    private static MinecraftClient mc = MinecraftClient.getInstance();






    public static void placeBlock(Hand hand, BlockHitResult result){
        ActionResult actionResult2 = mc.interactionManager.interactBlock(mc.player, hand, result);
        if (actionResult2.isAccepted()) {
            if (((ActionResult.Success)actionResult2).swingSource() == ActionResult.SwingSource.CLIENT) {
                mc.player.swingHand(hand);
            }
            return;
        }
    }
    @Getter
    public static final ModuleGroup moduleManager = new ModuleGroup("Interaction");
    @Getter
    public static InteractExtra interactExtra;
    @ApiStatus.Experimental
    public static AutoInteract autoInteract;
    @ApiStatus.Experimental
    public static AutoAttack autoAttack;
    @Getter
    public static Scaffold scaffold;
    private static void initModules(ModuleManager m){
        interactExtra = new InteractExtra()
            .register(m);

        scaffold = new Scaffold()
            .register(m);
    }


    static{

        moduleManager.registerFactories(InteractionTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
}
