package me.matl114.hacks;

import lombok.Getter;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.interact.*;
import me.matl114.utils.ApiMethod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.jetbrains.annotations.ApiStatus;

public class InteractionTasks {
    public static void init() {}

    private static MinecraftClient mc = MinecraftClient.getInstance();

    public static void placeBlock(Hand hand, BlockHitResult result) {
        ActionResult actionResult2 = mc.interactionManager.interactBlock(mc.player, hand, result);
        if (actionResult2.isAccepted()) {
            if (actionResult2.shouldSwingHand()) {
                mc.player.swingHand(hand);
            }
            return;
        }
    }

    @ApiMethod
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

    @Getter
    public static TpInteract tpInteract;

    @Getter
    public static Airplace airplace;

    private static void initModules(ModuleManager m) {
        interactExtra = new InteractExtra().register(m);

        scaffold = new Scaffold().register(m);
        tpInteract = new TpInteract().register(m);
        airplace = new Airplace().register(m);
    }

    static {
        moduleManager.registerFactories(InteractionTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
}
