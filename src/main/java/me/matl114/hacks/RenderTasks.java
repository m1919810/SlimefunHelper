package me.matl114.hacks;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.render.*;
import me.matl114.utils.*;
import me.matl114.events.Event;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

import net.minecraft.util.math.*;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.*;
import java.util.List;

public class RenderTasks {
    public static void init(){

    }

    private static MinecraftClient mc = MinecraftClient.getInstance();



    public static Color STATIC_DEBUG_COLOR = null;
    public static int DEBUG_TICK = 16;
    public static boolean DEBUG_RENDER_COLLISION = false;
    public static boolean DEBUG_RENDER_COMBAT = false;
    public static boolean DEBUG_RENDER_COLLISION_RENDERING = false;
    public static boolean DEBUG_RENDER_BOWAIM = false;
    public static void debugBoxMov(Box box, Vec3d move){
        if(DEBUG_RENDER_COLLISION_RENDERING && DEBUG_RENDER_COLLISION){
            RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxMoveRenderingTask(box, move, DEBUG_TICK, STATIC_DEBUG_COLOR, Color.RED));
        }
    }
    public static void debugBox(Box box){
        if(DEBUG_RENDER_COLLISION_RENDERING && DEBUG_RENDER_COLLISION){
            RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(box.getMinPos(), box.getMaxPos(), DEBUG_TICK, STATIC_DEBUG_COLOR));
        }
    }
    public static void drawBox(Box box, int timeTick, Color color){
        RenderTasks.registerVirtualRenderTask(new RenderTasks.BoxRenderingTask(box.getMinPos(), box.getMaxPos(), timeTick, color));
    }



    private static final Set<VirtualRenderTask> renderBlocks= new HashSet<>();


    public static void registerVirtualRenderTask(VirtualRenderTask task){
        renderBlocks.add(task);
    }
    public static final Vec3d FROM = new Vec3d(-0.5, -0.5, -0.5);
    public static final Vec3d SMALL_FROM = new Vec3d( - 0.2, -0.2, -0.2);
    public static final Vec3d TO = new Vec3d( 0.5, 0.5, 0.5);
    public static final Vec3d SMALL_TO = new Vec3d(0.2, 0.2, 0.2);
    private static void onRenderVirtualTasks(Event<MatrixStack> stackE){
        if(renderBlocks.isEmpty())return;
        var stack = stackE.context;
        RenderUtils.startDrawVirtual(stack);
        try{
            Iterator<VirtualRenderTask> tasks= renderBlocks.iterator();
            while (tasks.hasNext()){
                VirtualRenderTask renderTask = tasks.next();
                if(renderTask.stillRender()){
                    renderTask.renderVirtual(stack);
                }else {
                    tasks.remove();
                }
            }
        }finally {
            RenderUtils.stopDrawVirtual(stack);
        }

    }
//    public static interface StaticRenderTask {
//        boolean stillRender();
//        VertexBuffer getRenderAction();
//    }

    public static interface VirtualRenderTask {
        void renderVirtual(MatrixStack stack);
        boolean stillRender();
    }
    public static abstract class TickingRenderingTask implements VirtualRenderTask{
        int tick;
        int startTick;
        public TickingRenderingTask(int tick){
            this.tick = tick;
            this.startTick = Tasks.getTick();
        }
        @Override
        public boolean stillRender() {
            return Tasks.getTick() - this.startTick < this.tick;
        }
    }
    public static class LineRenderingTask extends TickingRenderingTask{
        Vec3d start;
        Vec3d movement;
        public LineRenderingTask(Vec3d start, Vec3d movement, int tick) {
            super(tick);
            this.start = start;
            this.movement = movement;
        }

        @Override
        public void renderVirtual(MatrixStack stack) {
            RenderUtils.drawLineVirtual(stack, start, start.add(movement), Color.GREEN);
        }
    }
    public static  class BoxRenderingTask extends TickingRenderingTask {
        final Vec3d startVec;
        final Vec3d endVec;
        Color color;
        public BoxRenderingTask(Box box, int tick, Color color){
            this(box.getMinPos(), box.getMaxPos(), tick, color);
        }
        public BoxRenderingTask(Vec3d start, Vec3d end, int tick){
            this(start, end, tick, Color.GREEN)   ;
        }
        public BoxRenderingTask(Vec3d start, Vec3d end, int tick, Color color){
            super(tick);
            this.startVec = start;
            this.endVec = end;
            this.color = color;
        }

        @Override
        public void renderVirtual(MatrixStack stack) {
            RenderUtils.setAsCurrentShaderColor(color, 0.25F);
            RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), startVec, endVec);
        }



    }
    public static class BoxOutlineRenderingTask extends TickingRenderingTask {
        final Vec3d startVec;
        final Vec3d endVec;
        Color color;
        public BoxOutlineRenderingTask(Box box, int tick, Color color){
            this(box.getMinPos(), box.getMaxPos(), tick, color);
        }
        public BoxOutlineRenderingTask(Vec3d start, Vec3d end, int tick){
            this(start, end, tick, Color.GREEN)   ;
        }
        public BoxOutlineRenderingTask(Vec3d start, Vec3d end, int tick, Color color){
            super(tick);
            this.startVec = start;
            this.endVec = end;
            this.color = color;
        }

        @Override
        public void renderVirtual(MatrixStack stack) {
            RenderUtils.setAsCurrentShaderColor(color, 1.0F);
            RenderUtils.drawOutlinedBox(stack, startVec, endVec);
        }



    }
    public static class BoxMoveRenderingTask extends TickingRenderingTask implements VirtualRenderTask{
        final Box startBox;
        final Vec3d delta;
        final Color color1;
        final Color color2;
        public BoxMoveRenderingTask(Box box, Vec3d vec3d, int tick){
            this(box, vec3d, tick, Color.GREEN, Color.RED);
        }
        public BoxMoveRenderingTask(Box box, Vec3d vec3d, int tick, Color boxColor, Color lineColor){
            super(tick);
            this.startBox = box;
            this.delta = vec3d;
            color1 = boxColor;
            color2 = lineColor;
        }

        @Override
        public void renderVirtual(MatrixStack stack) {
            RenderUtils.setAsCurrentShaderColor(color1, 0.25F);
            RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), startBox.getMinPos(), startBox.getMaxPos());
            RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), startBox.getMinPos().add(delta), startBox.getMaxPos().add(delta));
            for (var ver: CollisionUtil.getBoxVertices(startBox))
                RenderUtils.drawLineVirtual(stack, ver, ver.add(delta), color2);
        }
    }
    public static class MultiLineRenderingTask extends TickingRenderingTask{
        List<Vec3d> multiLine;
        Color color1;
        public MultiLineRenderingTask(List<Vec3d> vec3ds, int tick, Color color) {
            super(tick);
            this.multiLine = Collections.unmodifiableList(vec3ds);
            this.color1 = color;
        }

        @Override
        public void renderVirtual(MatrixStack stack) {
            RenderUtils.drawLineVirtual(stack, multiLine, color1);
        }
    }

    public static class QuadRenderingTask  extends TickingRenderingTask{
        Vec3d[] abcd;
        Color color1;
        public QuadRenderingTask(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int tick, Color color) {
            super(tick);
            this.abcd = new Vec3d[]{a, b, c, d};
            this.color1 = color;
        }

        @Override
        public void renderVirtual(MatrixStack stack) {
            RenderUtils.setAsCurrentShaderColor(color1, 0.25F);
            RenderUtils.drawQuad(stack.peek().getPositionMatrix(), abcd[0], abcd[1], abcd[2], abcd[3]);
        }
    }

    public static class LineToTargetRenderingTask extends TickingRenderingTask{
        Vec3d vec3d;
        Color color;
        public LineToTargetRenderingTask(Vec3d vec3d, int tick, Color color) {
            super(tick);
            this.vec3d = vec3d;
            this.color = color;
        }

        @Override
        public void renderVirtual(MatrixStack stack) {
            Vec3d camera = RenderUtils.getCameraPos();
            Vec3d camerToBlock = this.vec3d.subtract(camera);
            Vec3d cursorPos = RenderUtils.getTracerOrigin(1.0f);
            RenderUtils.drawLineVirtualCameraCoord(stack, cursorPos, camerToBlock, color);
        }
    }
    public static  class EntityRenderingTask extends TickingRenderingTask {
        final Entity startVec;

        Color color;
        public EntityRenderingTask(Entity entity, int tick, Color color){
            super(tick);
            this.startVec = entity;
            this.color = color;
        }

        @Override
        public void renderVirtual(MatrixStack stack) {
            RenderUtils.setAsCurrentShaderColor(color, 0.25F);
            RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), startVec.getBoundingBox().getMinPos(), startVec.getBoundingBox().getMaxPos());
        }
        public boolean stillRender(){
            return super.stillRender() && !startVec.isRemoved();
        }


    }
    public static class EntityOutlineRenderingTask extends TickingRenderingTask {
        final Entity startVec;

        Color color;

        public EntityOutlineRenderingTask(Entity entity, int tick, Color color){
            super(tick);
            this.startVec = entity;
            this.color = color;
        }

        @Override
        public void renderVirtual(MatrixStack stack) {
            RenderUtils.setAsCurrentShaderColor(color, 1.0F);
            RenderUtils.drawOutlinedBox(stack, startVec.getBoundingBox().getMinPos(), startVec.getBoundingBox().getMaxPos());
        }

        public boolean stillRender(){
            return super.stillRender() && !startVec.isRemoved();
        }



    }
    public static class LineToEntityRenderingTask extends TickingRenderingTask{
        Entity vec3d;
        Color color;
        public LineToEntityRenderingTask(Entity vec3d, int tick, Color color) {
            super(tick);
            this.vec3d = vec3d;
            this.color = color;
        }

        @Override
        public void renderVirtual(MatrixStack stack) {
            Vec3d camera = RenderUtils.getCameraPos();
            Vec3d camerToBlock = this.vec3d.getBoundingBox().getCenter().subtract(camera);
            Vec3d cursorPos = RenderUtils.getTracerOrigin(1.0f);
            RenderUtils.drawLineVirtualCameraCoord(stack, cursorPos, camerToBlock, color);
        }
        public boolean stillRender(){
            return super.stillRender() && !vec3d.isRemoved();
        }
    }
    public static abstract class BlockRenderingTask extends TickingRenderingTask implements VirtualRenderTask {

        final BlockPos pos;
        final boolean shouldLine;
        public BlockRenderingTask(BlockPos pos,  boolean shouldLine, int tick){
            super(tick);
            this.pos = pos;
            this.shouldLine= shouldLine;
            this.buffer = createStatic();
        }
        final VertexBuffer buffer;
        @Override
        public boolean stillRender() {
            if(super.stillRender()){
                return true;
            }
            this.close();
            return false;
        }
        public void close(){
            this.buffer.close();
        }
        public abstract @Nonnull VertexBuffer createStatic();
        public abstract void setBlockShaderData();
        @Override
        public void renderVirtual(MatrixStack stack) {
            stack.push();
            //设置着色器为 position配合POSITION Vertex
            RenderSystem.setShader(GameRenderer::getPositionProgram);
            Vec3d camera = RenderUtils.getCameraPos();
            //push to block coord
            Vec3d camerToBlock = this.pos.toCenterPos().subtract(camera);
            stack.translate(camerToBlock.x, camerToBlock.y, camerToBlock.z);;
            //运行
            Matrix4f viewMatrix  = stack.peek().getPositionMatrix();
            Matrix4f projMatrix = RenderSystem.getProjectionMatrix();
            ShaderProgram shader = RenderSystem.getShader();
            setBlockShaderData();
            this.buffer.bind();
            this.buffer.draw(viewMatrix, projMatrix, shader);
            VertexBuffer.unbind();
            //运行结束
            stack.pop();
            //绘制线
            if(shouldLine){
                //back to camera coord
                Vec3d cursorPos = RenderUtils.getTracerOrigin(1.0f);
                RenderUtils.drawLineVirtualCameraCoord(stack, cursorPos, camerToBlock, Color.RED);
            }
        }
    }

    public static class CountingBlockOutlineTarget extends BlockRenderingTask {

        public CountingBlockOutlineTarget(BlockPos pos, int tick, boolean shouldLine){
            super(pos, shouldLine, tick);
        }

        @NotNull
        @Override
        public VertexBuffer createStatic() {
            var vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            RenderUtils.cacheVertexAction(vertexBuffer, VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION, (b)->{
                RenderUtils.drawOutlinedBox(b, FROM, TO);
            });
            return vertexBuffer;
        }

        @Override
        public void setBlockShaderData() {
            RenderUtils.setAsCurrentShaderColor(Color.GREEN,1.0f);
            //RenderUtils.setAsShaderColor(Color.GREEN, 0.25F);
        }
    }
    public static class CountingBlockSolidTarget extends BlockRenderingTask {


        public CountingBlockSolidTarget(BlockPos pos, boolean shouldLine, int tick) {
            super(pos, shouldLine, tick);
        }
        @NotNull
        @Override
        public VertexBuffer createStatic() {
            var vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            RenderUtils.cacheVertexAction(vertexBuffer, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION, (b)->{
                RenderUtils.drawSolidBox(b, FROM, TO);
            });
            return vertexBuffer;
        }

        @Override
        public void setBlockShaderData() {
            RenderUtils.setAsCurrentShaderColor(Color.GREEN,0.25f);
        }
    }


    @Getter
    public static final ModuleGroup moduleManager = new ModuleGroup("Render");
    @Getter
    public static RenderExtra renderExtra;
    @Getter
    public static EntityESP entityESP;
    @Getter
    public static PlayerLog playerLog;
    @Getter
    public static ProjectileESP projectileESP;
    @Getter
    public static SleepMode sleepMode;

    private static void initModules(ModuleManager m){
        renderExtra = new RenderExtra()
            .register(m);
        entityESP = new EntityESP()
            .register(m);
        playerLog = new PlayerLog()
            .register(m);
        projectileESP = new ProjectileESP()
            .register(m);
        sleepMode = new SleepMode()
            .register(m);
    }

    static {

        RenderListener.getRenderLayerTasks().registerHandler(RenderTasks::onRenderVirtualTasks);



        moduleManager.registerFactories(RenderTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
}
