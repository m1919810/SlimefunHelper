package me.matl114.hacks.modules.extra;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.Tasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.MathUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.render.ColorQuad;
import me.matl114.utils.render.Quad;
import me.matl114.utils.render.UV;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerLikeState;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class Tests extends BaseModule {
    public static final String[] TEST_ARGS1 = {"test", "arg1"};
    public static final String[] TEST_ARGS2 = {"test", "arg2"};
    public static final String[] TEST_MOVEMENT_TEST = {"test", "movement-test-1"};
    public static final String[] TEST_HOTKEY = {"hotkeys", "test-func"};

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

    public final KeyBindRef key1 = toggleHotkey(
                    TEST_TOGGLE_1, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_1))
            .build();

    public final KeyBindRef key2 = toggleHotkey(
                    TEST_TOGGLE_2, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_2))
            .build();

    public final KeyBindRef key3 = toggleHotkey(
                    TEST_TOGGLE_3, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_3))
            .build();

    public final KeyBindRef key4 = toggleHotkey(
                    TEST_TOGGLE_4, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T, KeyCode.KEY_4))
            .build();

    private final KeyBindRef testKeyBind = hotkey(TEST_HOTKEY)
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_T))
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::doTest))
            .build();
    private boolean swapState = false;
    private CompletableFuture<Void> future = null;
    private boolean running = false;
    private final Random random = new Random();

    private int counter = 0;

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
        RenderUtils.setAsCurrentShaderColor(Color.BLUE, 0.25f);
        VRender.getInstance().drawSolidBoxCameraCoord(stack, RenderTasks.FROM, RenderTasks.TO);
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
        // todo: 可视范围是不是和法线有关
        // todo: 默认是向坐标系的x + y + 渲染， 可视范围是z-
        // todo: 翻转y轴后向 x + y - 渲染 可视范围z +
        // todo: 如何调试法线
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

        RenderUtils.setAsCurrentShaderColor(Color.YELLOW, 1.0F);
        RenderUtils.drawOutlinedBoxCameraCoord(stack, new Vec3d(-9, -4.5F, 0), new Vec3d(9, 4.5F, 0));
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
        if ((Boolean) mc.options.getBobView().getValue()) {
            bobView(stack2, mc.gameRenderer.getCamera().getLastTickProgress());
        }
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
        RenderUtils.setAsCurrentShaderColor(Color.YELLOW, 1.0F);
        int width = mc.textRenderer.getWidth(orderedText);
        RenderUtils.drawOutlinedBoxCameraCoord(
                stack2,
                new Vec3d(-width / 2.0f, -TEXT_HEIGHT / 2.0F, 0),
                new Vec3d(width / 2.0F, TEXT_HEIGHT / 2.0, 0));
        stack.pop();
        //
        RenderUtils.drawOutlinedBox(stack, new Vec3d(10, 10, 10), new Vec3d(40, 40, 40));
        stack.pop();
        stack.push();
        stack.translate(related.x, related.y, related.z);
        RenderUtils.setAsCurrentShaderColor(Color.BLUE, 0.25f);
        RenderUtils.drawSolidBox(stack, vec3d.add(RenderTasks.SMALL_FROM), vec3d.add(RenderTasks.SMALL_TO));
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(10, 0, 0), Color.RED);
        RenderUtils.drawLineVirtualCameraCoord(stack, new Vec3d(0, -10, 0), new Vec3d(0, 10, 0), Color.GREEN);
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(0, 0, 10), Color.BLUE);
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(10, 10, 10), Color.YELLOW);

        RenderUtils.setAsCurrentShaderColor(Color.YELLOW, 1.0F);
        RenderUtils.drawOutlinedBoxCameraCoord(stack, new Vec3d(10, 10, 0), new Vec3d(40, 40, 0));
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
        //        VRender.getInstance().drawGuiQuadCameraCoord(
        //            stack, Quad.textureXY(100, 0, 140, 40, 0), ColorQuad.ofGradient(Color.WHITE, Color.RED,
        // Color.GREEN, Color.BLUE)
        //        );
        //        VRender.getInstance().drawTexturedQuadCameraCoord(
        //            new Identifier("slimefunhelper", "textures/custom/genshin_impact.png"),stack,
        //            Quad.textureXY(0, 50, 40, 90, 0), UV.DEFAULT, ColorQuad.of(-1)
        //        );
        stack.pop();

        stack.pop();
        stack.push();
        stack.translate(related.x, related.y, related.z + 30);
        VRender.getInstance().setAsShaderColor(Color.BLUE, 0.25F);
        VRender.getInstance().drawSolidBoxCameraCoord(stack, RenderTasks.FROM, RenderTasks.TO);

        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(10, 0, 0), Color.RED);
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(0, 10, 0), Color.GREEN);
        RenderUtils.drawLineVirtualCameraCoord(stack, Vec3d.ZERO, new Vec3d(0, 0, 10), Color.BLUE);
        stack.translate(0, 0, 10);
        ItemStack itemStack = new ItemStack(Items.DIAMOND_SWORD);
        ItemRenderState state = new ItemRenderState();
        // fixed : looks normal from Z -
        // gui: looks normal from z +
        // on ground : looks normal
        // HEAD
        mc.getItemModelManager()
                .clearAndUpdate(state, itemStack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, mc.world, null, -999);
        //        OrderedRenderCommandQueueImpl queue = new OrderedRenderCommandQueueImpl();
        state.render(
                stack, mc.gameRenderer.getEntityRenderDispatcher().getQueue(), 0XFF00FF, OverlayTexture.DEFAULT_UV, 0);
        //        ItemCommandRenderer itemRenderer = new ItemCommandRenderer();
        //        VertexConsumerProvider.Immediate vcp = Render_v1_21_11.getVCP();
        //        for (var entry : queue.getBatchingQueues().values()){
        //            itemRenderer.render(entry,  vcp, Render_v1_21_11.getOutlineVCP());
        //        }

        stack.pop();
    }

    private void bobView(MatrixStack matrices, float tickProgress) {
        Entity var4 = mc.getCameraEntity();
        if (var4 instanceof AbstractClientPlayerEntity abstractClientPlayerEntity) {
            MatrixStack inverse = new MatrixStack();
            ClientPlayerLikeState clientPlayerLikeState = abstractClientPlayerEntity.getState();
            float f = clientPlayerLikeState.getReverseLerpedDistanceMoved(tickProgress);
            float g = clientPlayerLikeState.lerpMovement(tickProgress);
            inverse.multiply(RotationAxis.POSITIVE_X.rotationDegrees(
                    -Math.abs(MathHelper.cos((double) (f * 3.1415927F - 0.2F)) * g) * 5.0F));
            inverse.multiply(
                    RotationAxis.POSITIVE_Z.rotationDegrees(-MathHelper.sin((double) (f * 3.1415927F)) * g * 3.0F));
            inverse.translate(
                    -MathHelper.sin((double) (f * 3.1415927F)) * g * 0.5F,
                    Math.abs(MathHelper.cos((double) (f * 3.1415927F)) * g),
                    0.0F);
            // 将 inverse 矩阵乘入当前矩阵栈
            matrices.multiplyPositionMatrix(inverse.peek().getPositionMatrix());
        }
    }

    private void fogView(MatrixStack matrices, float tickProgress) {}
}
