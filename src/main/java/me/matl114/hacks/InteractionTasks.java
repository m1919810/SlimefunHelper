package me.matl114.hacks;

import com.google.common.util.concurrent.Runnables;
import lombok.Getter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.interact.*;
import me.matl114.managers.Configs;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;

public class InteractionTasks {
    public static void init() {}

    private static MinecraftClient mc = MinecraftClient.getInstance();
    //
    //    public static void placeBlock(int idx, BlockHitResult result){
    //
    //    }

    public static void placeBlock(Hand hand, BlockHitResult result) {
        ActionResult actionResult2 = mc.interactionManager.interactBlock(mc.player, hand, result);
        if (actionResult2.isAccepted()) {
            if (((ActionResult.Success) actionResult2).swingSource() == ActionResult.SwingSource.CLIENT) {
                mc.player.swingHand(hand);
            }
            return;
        }
    }

    public static void addPostRotationCorrectTask(Vec3d look3d, Runnable callback) {
        //        RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
        //            RenderTasks.DEBUG_TICK, new RenderTasks.BoxObject(look3d.add(-0.1, -0.1, -0.1), look3d.add(0.1,
        // 0.1, 0.1), Color.MAGENTA)));
        ClientPlayerAccess.of(mc.player)
                .getLegalMovementManager()
                .addMovementModifier(new LegalMovementManager.MovementModifier() {
                    @Override
                    public int priority() {
                        return PRIORITY_LOW;
                    }

                    @Override
                    public boolean mayModifyRotation() {
                        return true;
                    }

                    @Override
                    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                        Vec2f rotation = EntityUtils.rotationToPitchYaw(
                                look3d.subtract(mc.player.getEyePos()).normalize());
                        movementManagerEvent.context.pushImportantRotation(true, true);
                        EntityUtils.setEntityYawSafe(player, rotation.y);
                        EntityUtils.setEntityPitchSafe(player, rotation.x);
                    }

                    @Override
                    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
                        // after input tick,
                        // we may change some of the direction flag, so the velocity will be better
                        movementManagerEvent.context().tryCorrectMovementInput();
                    }

                    @Override
                    public boolean postModify(
                            Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                        if (enabledThisTick) {
                            movementManagerEvent.context.playerStatus.restoreRotation();
                        }
                        callback.run();
                        return false;
                    }
                });
    }

    public static void handlePlaceMode(Configs.LegalInteractMode mode, BlockHitResult result, Hand hand) {
        switch (mode) {
            case USEITEM_PACKET -> {
                Vec2f rotation = EntityUtils.rotationToPitchYaw(result.getBlockPos()
                        .toCenterPos()
                        .subtract(mc.player.getEyePos())
                        .normalize());
                mc.interactionManager.sendSequencedPacket(
                        mc.world, (i) -> new PlayerInteractItemC2SPacket(hand, i, rotation.y, rotation.x));
                InteractionTasks.placeBlock(hand, result);
            }
            case DELAY_MOVEMENT -> {
                InteractionTasks.placeBlock(hand, result);
                InteractionTasks.addPostRotationCorrectTask(result.getBlockPos().toCenterPos(), Runnables.doNothing());
            }
            case MOVEMENT -> {
                Vec2f rotation = EntityUtils.rotationToPitchYaw(result.getBlockPos()
                        .toCenterPos()
                        .subtract(mc.player.getEyePos())
                        .normalize());
                mc.getNetworkHandler()
                        .sendPacket(VPacket.newLookAndOnGround(
                                rotation.y, rotation.x, mc.player.isOnGround(), mc.player.horizontalCollision));
                InteractionTasks.placeBlock(hand, result);
            }
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

    @Getter
    public static BlockRotate blockRotate;

    @Getter
    public static PrinterRewrite printerRewrite;

    private static void initModules(ModuleManager m) {
        interactExtra = new InteractExtra().register(m);

        scaffold = new Scaffold().register(m);
        tpInteract = new TpInteract().register(m);
        airplace = new Airplace().register(m);

        blockRotate = new BlockRotate().register(m);
        printerRewrite = new PrinterRewrite().register(m);
    }

    static {
        moduleManager.registerFactories(InteractionTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
}
