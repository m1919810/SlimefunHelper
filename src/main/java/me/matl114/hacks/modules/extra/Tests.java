package me.matl114.hacks.modules.extra;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.render.ColorQuad;
import me.matl114.utils.render.Quad;
import me.matl114.utils.render.UV;
import me.matl114.versioned.api.VRender;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Tests extends BaseModule {
    public static final String[] TEST_ARGS1 = {"test", "arg1"};
    public static final String[] TEST_ARGS2 = {"test", "arg2"};
    public static final String[] TEST_MOVEMENT_TEST = {"test", "movement-test-1"};
    public static final String[] TEST_HOTKEY = {"hotkeys", "test-func-1"};

    public static final String[] TEST_HOTKEY_2 = {"hotkeys", "test-func-2"};
    public static final String[] TEST_HOTKEY_3 = {"hotkeys", "test-func-3"};
    public static final String[] TEST_HOTKEY_4 = {"hotkeys", "test-func-4"};

    public static final String[] TEST_TOGGLE_1 = {"hotkeys-toggle", "hktest1"};
    public static final String[] TEST_TOGGLE_2 = {"hotkeys-toggle", "hktest2"};
    public static final String[] TEST_TOGGLE_3 = {"hotkeys-toggle", "hktest3"};
    public static final String[] TEST_TOGGLE_4 = {"hotkeys-toggle", "hktest4"};

    private final IntRef delay = builder(Configs.TEST_CONFIG, TEST_ARGS1, IntRef.TYPE)
            .defaultValue(480000)
            .build();
    private final IntRef bigDelay = builder(Configs.TEST_CONFIG, TEST_ARGS2, IntRef.TYPE)
            .defaultValue(461)
            .build();

    public final KeyBindRef key0 = hotkey(Configs.MISC_CONFIG, TEST_HOTKEY, new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::doTest2))
            .build();

    public final FlagRef flag1 =
            flagBuilder(Configs.TOGGLE_CONFIG, TEST_TOGGLE_1).build();

    public final KeyBindRef key1 = toggleConfigHotkey(
                    Configs.MISC_CONFIG,
                    TEST_TOGGLE_1,
                    new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_1))
            .build();

    public final FlagRef flag2 =
            flagBuilder(Configs.TOGGLE_CONFIG, TEST_TOGGLE_2).build();

    public final KeyBindRef key2 = toggleConfigHotkey(
                    Configs.MISC_CONFIG,
                    TEST_TOGGLE_2,
                    new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_2))
            .build();

    public final FlagRef flag3 =
            flagBuilder(Configs.TOGGLE_CONFIG, TEST_TOGGLE_3).build();

    public final KeyBindRef key3 = toggleConfigHotkey(
                    Configs.MISC_CONFIG,
                    TEST_TOGGLE_3,
                    new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_3))
            .build();

    public final FlagRef flag4 =
            flagBuilder(Configs.TOGGLE_CONFIG, TEST_TOGGLE_4).build();

    public final KeyBindRef key4 = toggleConfigHotkey(
                    Configs.MISC_CONFIG,
                    TEST_TOGGLE_4,
                    new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_4))
            .build();

    private boolean swapState = false;
    private CompletableFuture<Void> future = null;
    private boolean running = false;
    private final Random random = new Random();

    private int counter = 0;

    @Override
    public void registerAll() {
        super.registerAll();
    }

    //    private void sendItemSwapPacketInternal(){
    ////        slimefunTick++;
    ////        if(slimefunTick<5){
    ////
    ////            return;
    ////        }else if(slimefunTick>21){
    ////            slimefunTick=0;
    ////            return;
    ////        }
    //        running=true;
    //
    //        if(future == null){
    //            String test="Start running 12 test";
    //            // Debug.info(test);
    //            future = CompletableFuture.runAsync(()->{
    //                try{
    //                    do {
    //                        //
    //                        if (counter>delay.get()) {
    //                            counter=0;
    //                            try {
    //                                Thread.sleep(bigDelay.get());
    //                            } catch (Throwable e) {
    //                            }
    //                            MinecraftClient.getInstance().getNetworkHandler().sendPacket(
    //                                new
    // ClickSlotC2SPacket(MinecraftClient.getInstance().player.currentScreenHandler.syncId,
    // MinecraftClient.getInstance().player.currentScreenHandler.getRevision(), 25, 40, SlotActionType.SWAP,
    // MinecraftClient.getInstance().player.currentScreenHandler.getCursorStack(), new Int2ObjectOpenHashMap<>())
    //                            );
    //                            MinecraftClient.getInstance().getNetworkHandler().sendPacket(
    //                                new
    // ClickSlotC2SPacket(MinecraftClient.getInstance().player.currentScreenHandler.syncId,
    // MinecraftClient.getInstance().player.currentScreenHandler.getRevision(), 19, 40, SlotActionType.SWAP,
    // MinecraftClient.getInstance().player.currentScreenHandler.getCursorStack(), new Int2ObjectOpenHashMap<>())
    //                            );
    //
    //                            swapState=false;
    //                        }else{
    //
    //                            counter+=10_000;
    //                            MinecraftClient.getInstance().getNetworkHandler().sendPacket(
    //                                new
    // ClickSlotC2SPacket(MinecraftClient.getInstance().player.currentScreenHandler.syncId,
    // MinecraftClient.getInstance().player.currentScreenHandler.getRevision(), 19, 40, SlotActionType.SWAP,
    // MinecraftClient.getInstance().player.currentScreenHandler.getCursorStack(), new Int2ObjectOpenHashMap<>())
    //                            );
    //
    //                            long a=System.nanoTime()+10_000;
    //                            do{
    //                            }while (System.nanoTime()<a);
    //                        }
    //
    //                    }while (running);
    //                }catch(Throwable e){
    //                    Debug.info(e);
    //                    running=false;
    //                }
    //            });
    //
    //        }
    //
    //    }
    public void stopItemSwapPacketInternal() {
        running = false;
        if (future != null) {
            future.cancel(true);
            future = null;
        }
    }

    boolean start = false;
    Vec3d vec3d;

    public void doTest() {
        if (!start) {
            start = true;
            vec3d = RenderUtils.getCameraPos().add(mc.player.getRotationVector().multiply(5));
            RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(this::onRender).setAutoStop(() -> !start));
        } else {
            start = false;
        }
    }

    private static float TEXT_HEIGHT = 9.0F;

    private void onRender(MatrixStack stack, float partialTick) {
        Vec3d related = vec3d.subtract(RenderUtils.getCameraPos());
        stack.push();
        stack.translate(related.x - 30, related.y, related.z);
        VRender.getInstance()
                .drawSolidBoxCameraCoord(
                        stack, RenderTasks.FROM, RenderTasks.TO, ColorUtils.withAlpha(Color.BLUE, 0.25F));
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(10, 0, 0), Color.RED);
        RenderUtils.drawLineVirtualCameraCoord(stack, new Vec3d(0, -10, 0), new Vec3d(0, 10, 0), Color.GREEN);
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(0, 0, 10), Color.BLUE);
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(10, 10, 10), Color.YELLOW);
        //        Quaternionf ROTATE_X = RotationAxis.NEGATIVE_Z.rotationDegrees(180);
        //        stack.multiply(new Quaternionf(ROTATE_X.x, ROTATE_X.y, ROTATE_X.z, ROTATE_X.w));
        stack.push();
        stack.multiply(RenderUtils.getBillboardRotation(DisplayEntity.BillboardMode.VERTICAL, 0, 0));

        stack.scale(0.025F, 0.025F, 1.0F);
        //        Quaternionf ROTATE_X = RotationAxis.POSITIVE_X.rotationDegrees(180);
        //        stack.multiply(ROTATE_X);
        // 可视范围是不是和法线有关
        // 默认是向坐标系的x + y + 渲染， 可视范围是z-
        // 翻转y轴后向 x + y - 渲染 可视范围z +
        // 如何调试法线
        stack.push();
        int x = Tasks.getSecond() % 20;
        int y = Tasks.getSecond() % 20;
        // 问题
        //        mc.gameRenderer.getEntityRenderDispatcher().getQueue().submitText(
        //            stack, x, y, ChatUtils.stringToText("&6测试&a语句").asOrderedText(), false,
        // TextRenderer.TextLayerType.POLYGON_OFFSET,0xF000F0,-1, 0,0
        //        );

        stack.translate(x, y, 0);
        VRender.getInstance()
                .drawTextCameraCoord(
                        ChatUtils.stringToText("&6测试&a语句").asOrderedText(),
                        stack,
                        Vec3d.ZERO,
                        VRender.createTextPositionFlag(-1, -1),
                        Color.WHITE,
                        VRender.DEFAULT_TEXT);

        RenderUtils.drawOutlinedBoxCameraCoord(stack, new Vec3d(-9, -4.5F, 0), new Vec3d(9, 4.5F, 0), Color.YELLOW);
        stack.pop();
        VRender.getInstance()
                .drawTextCameraCoord(
                        ChatUtils.stringToText("&aX").asOrderedText(),
                        stack,
                        new Vec3d(100, 0, 0),
                        VRender.createTextPositionFlag(0, 0),
                        Color.WHITE,
                        VRender.DEFAULT_TEXT);
        VRender.getInstance()
                .drawTextCameraCoord(
                        ChatUtils.stringToText("&aY").asOrderedText(),
                        stack,
                        new Vec3d(0, 100, 0),
                        VRender.createTextPositionFlag(0, 0),
                        Color.WHITE,
                        VRender.DEFAULT_TEXT);
        List<Vec3d> lines = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            lines.add(new Vec3d(i, 150 - MathUtils.s2(50 - i) * 150.0D / 2500.0D, 0));
        }
        RenderUtils.drawStripLineVirtualCameraCoord(stack, lines, Color.PINK);
        RenderUtils.drawQuadCameraCoord(
                stack,
                new Vec3d(0, 0, 0),
                new Vec3d(0, 25, 0),
                new Vec3d(12, 48, 0),
                new Vec3d(12, 23, 0),
                Color.YELLOW);
        stack.translate(1, 1, 1);
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(0, 0, 10), Color.PINK);

        MatrixStack stack2 = new MatrixStack();
        stack2.push();
        stack2.translate(0, 0, -20);

        RenderUtils.drawLineVirtualCameraCoord(stack2, Vec3d.ZERO, new Vec3d(10, 0, 0), Color.RED);
        RenderUtils.drawLineVirtualCameraCoord(stack2, Vec3d.ZERO, new Vec3d(0, 10, 0), Color.GREEN);
        RenderUtils.drawLineVirtualCameraCoord(stack2, Vec3d.ZERO, new Vec3d(0, 0, 10), Color.BLUE);
        RenderUtils.drawLineVirtualCameraCoord(stack2, Vec3d.ZERO, new Vec3d(100, 100, 100), Color.YELLOW);

        stack2.push();
        stack2.scale(0.25F, 0.25F, 1.0F);
        VRender.getInstance()
                .drawTextCameraCoord(
                        ChatUtils.stringToText("&6X").asOrderedText(),
                        stack2,
                        new Vec3d(40, 0, 0),
                        VRender.createTextPositionFlag(0, 0),
                        Color.WHITE,
                        VRender.DEFAULT_TEXT);
        VRender.getInstance()
                .drawTextCameraCoord(
                        ChatUtils.stringToText("&6Y").asOrderedText(),
                        stack2,
                        new Vec3d(0, 40, 0),
                        VRender.createTextPositionFlag(0, 0),
                        Color.WHITE,
                        VRender.DEFAULT_TEXT);
        VRender.getInstance()
                .drawTextCameraCoord(
                        ChatUtils.stringToText("&cScreen&6Y").asOrderedText(),
                        stack2,
                        new Vec3d(0, -40, 0),
                        VRender.createTextPositionFlag(0, 0),
                        Color.WHITE,
                        VRender.DEFAULT_TEXT);
        Text orderedText = ChatUtils.stringToText("&6为什么&a不显示");
        VRender.getInstance()
                .drawTextCameraCoord(
                        orderedText.asOrderedText(),
                        stack2,
                        Vec3d.ZERO,
                        VRender.createTextPositionFlag(0, 0),
                        Color.WHITE,
                        VRender.DEFAULT_TEXT);
        int width = mc.textRenderer.getWidth(orderedText);
        RenderUtils.drawOutlinedBoxCameraCoord(
                stack2,
                new Vec3d(-width / 2.0f, -TEXT_HEIGHT / 2.0F, 0),
                new Vec3d(width / 2.0F, TEXT_HEIGHT / 2.0, 0),
                Color.YELLOW);
        stack.pop();
        //
        RenderUtils.drawOutlinedBox(stack, new Vec3d(10, 10, 10), new Vec3d(40, 40, 40), Color.YELLOW);
        stack.pop();
        stack.push();
        stack.translate(related.x, related.y, related.z);
        RenderUtils.drawSolidBox(
                stack,
                vec3d.add(RenderTasks.SMALL_FROM),
                vec3d.add(RenderTasks.SMALL_TO),
                ColorUtils.withAlpha(Color.BLUE, 0.25F));
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(10, 0, 0), Color.RED);
        RenderUtils.drawLineVirtualCameraCoord(stack, new Vec3d(0, -10, 0), new Vec3d(0, 10, 0), Color.GREEN);
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(0, 0, 10), Color.BLUE);
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(10, 10, 10), Color.YELLOW);

        RenderUtils.drawOutlinedBoxCameraCoord(stack, new Vec3d(10, 10, 0), new Vec3d(40, 40, 0), Color.YELLOW);
        stack.push();
        stack.translate(10, 10, 0);
        VRender.getInstance()
                .drawTexturedQuadCameraCoord(
                        new Identifier("slimefunhelper", "textures/gui/format.png"),
                        stack,
                        Quad.textureXY(0, 0, 40, 40, 0),
                        UV.DEFAULT,
                        ColorQuad.of(-1));
        VRender.getInstance()
                .drawGuiSpriteQuadCameraCoord(
                        new Identifier("slimefunhelper", "gui/format"),
                        stack,
                        Quad.textureXY(50, 0, 90, 40, 0),
                        UV.DEFAULT,
                        ColorQuad.of(-1));
        VRender.getInstance()
                .drawGuiQuadCameraCoord(
                        stack,
                        Quad.textureXY(100, 0, 140, 40, 0),
                        ColorQuad.ofGradient(Color.WHITE, Color.RED, Color.GREEN, Color.BLUE));
        VRender.getInstance()
                .drawTexturedQuadCameraCoord(
                        new Identifier("slimefunhelper", "textures/custom/genshin_impact.png"),
                        stack,
                        Quad.textureXY(0, 50, 40, 90, 0),
                        UV.DEFAULT,
                        ColorQuad.of(-1));
        stack.pop();

        stack.pop();
        stack.push();
        stack.translate(related.x, related.y, related.z + 30);
        VRender.getInstance()
                .drawSolidBoxCameraCoord(
                        stack, RenderTasks.FROM, RenderTasks.TO, ColorUtils.withAlpha(Color.BLUE, 0.25F));

        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(10, 0, 0), Color.RED);
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(0, 10, 0), Color.GREEN);
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(0, 0, 10), Color.BLUE);
        stack.translate(0, 0, 10);
        ItemStack itemStack = new ItemStack(Items.DIAMOND_SWORD);
        VRender.getInstance()
                .drawItemCameraCoord(
                        itemStack, stack, Vec3d.ZERO, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, VRender.DEFAULT_ITEM);
        stack.pop();
    }

    public void doTest2() {
        //        if (mc.player != null) {
        //            Vec3d pos = mc.player.getEyePos();
        //            Vec3d look = mc.player.getRotationVector().multiply(20);
        //            var iter = RaycastUtils.createRaycastBlockPosIterator(mc.world, pos, pos.add(look));
        //            BlockPos lastPos = null;
        //            BlockPos thisPos = null;
        //            RenderTasks.registerVirtualRenderTask(
        //                    new RenderTasks.RenderTask(500, new RenderTasks.LineObject(pos, look)));
        //            while (iter.hasNext()) {
        //                lastPos = thisPos;
        //                thisPos = iter.next();
        //                if (Objects.equals(lastPos, thisPos)) {
        //                    Debug.chat("duplicate blockPos");
        //                } else {
        //                    RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
        //                            500,
        //                            new RenderTasks.BoxObject(
        //                                    Vec3d.of(thisPos), Vec3d.of(thisPos.add(1, 1, 1)), Color.MAGENTA)));
        //                }
        //            }
        //        }

        if (mc.player != null) {
            HitResult result = mc.player.raycast(10, 0, false);
            if (result != null && result.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) result).getBlockPos();
                RenderTasks.drawBox(new Box(pos), 50, Color.MAGENTA);
                Direction dir = mc.player.getFacing();
                mc.interactionManager.sendSequencedPacket(
                        mc.world,
                        (seq) -> new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, dir.getOpposite(), seq));
                mc.interactionManager.sendSequencedPacket(
                        mc.world,
                        (seq) -> new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir.getOpposite(), seq));
                mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
            }
        }
    }

    //    public void doObfEffect(Event<EntityStatusEffectS2CPacket> packet){
    //        if(mc.player != null && flag4.get() && packet.context.getEntityId() == mc.player.getId()){
    //            RegistryEntry<StatusEffect> reg = packet.context.getEffectId();
    //            if(packet.context.getAmplifier() > 1){
    //                packet.context(new EntityStatusEffectS2CPacket(
    //                    mc.player.getId(),
    //
    //                    new StatusEffectInstance(
    //                        StatusEffects.JUMP_BOOST,
    //                        packet.context.getDuration(),
    //                        packet.context.getAmplifier()
    //                    ),
    //                    packet.context.keepFading()
    //                ));
    //            }
    //
    //        }
    //    }
}
