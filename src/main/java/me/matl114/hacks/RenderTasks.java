package me.matl114.hacks;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.render.*;
import me.matl114.utils.*;
import me.matl114.events.Event;
import me.matl114.versioned.impl.Render_v1_21_1;
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
            RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                DEBUG_TICK,
                new BoxMoveTarget(box, move,STATIC_DEBUG_COLOR, Color.RED))
            );
        }
    }
    public static void debugBox(Box box){
        if(DEBUG_RENDER_COLLISION_RENDERING && DEBUG_RENDER_COLLISION){
            RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(DEBUG_TICK, new BoxObject(box.getMinPos(), box.getMaxPos(), STATIC_DEBUG_COLOR)));
        }
    }
    public static void drawBox(Box box, int timeTick, Color color){
        RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(timeTick, new BoxObject(box.getMinPos(), box.getMaxPos(), color)));
    }


    //the visit to renderBlocks need synchronized for thread safety, as they involved for-loop and remove
    private static final Set<VirtualRenderTask> renderBlocks= new LinkedHashSet<>();


    public static void registerVirtualRenderTask(VirtualRenderTask task){
        synchronized(renderBlocks){
            task.startRender();
            renderBlocks.add(task);
        }

    }
    public static final Vec3d FROM = new Vec3d(-0.5, -0.5, -0.5);
    public static final Vec3d SMALL_FROM = new Vec3d( - 0.2, -0.2, -0.2);
    public static final Vec3d TO = new Vec3d( 0.5, 0.5, 0.5);
    public static final Vec3d SMALL_TO = new Vec3d(0.2, 0.2, 0.2);
    private static void onRenderVirtualTasks(Event<MatrixStack> stackE){
        if(renderBlocks.isEmpty())return;
        synchronized(renderBlocks){
            var stack = stackE.context;
            float ticksDelta = stackE.getArgs(0);
            RenderUtils.startDrawVirtual(stack);
            try{
                Iterator<VirtualRenderTask> tasks= renderBlocks.iterator();
                while (tasks.hasNext()){
                    VirtualRenderTask renderTask = tasks.next();
                    if(renderTask.stillRender()){
                        renderTask.renderVirtual(stack, ticksDelta);
                    }else {
                        renderTask.stopRender();
                        tasks.remove();
                    }
                }
            }finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }


    }
//    public static interface StaticRenderTask {
//        boolean stillRender();
//        VertexBuffer getRenderAction();
//    }

    public static class TaskBuilder{
        int tickLeft = -1;
        List<RenderObject> renderObjects = new ArrayList<>();
        public TaskBuilder time(int tickLeft){
            this.tickLeft = tickLeft;
            return this;
        }
        public TaskBuilder add(RenderObject renderObject){
            renderObjects.add(renderObject);
            return this;
        }

        public RenderTask build(){
            if(tickLeft <= 0){
                return new RenderTask(renderObjects.toArray(RenderObject[]::new));
            }else {
                return new RenderTask(tickLeft, renderObjects.toArray(RenderObject[]::new));
            }
        }
    }

    public static TaskBuilder builder(){
        return new TaskBuilder();
    }



    public static interface VirtualRenderTask {
        void renderVirtual(MatrixStack stack, float partialTicks);
        public void startRender();
        public void stopRender();
        boolean stillRender();
    }
    public static class RenderTask implements VirtualRenderTask{
        int endTick;
        RenderObject[] renderObjects;
        boolean registered = false;
        public RenderTask(int tick, RenderObject... renderObjects){
            this.endTick = tick + Tasks.getTick();
            this.renderObjects = renderObjects;
        }

        public RenderTask(RenderObject... renderObjects){
            this.endTick = Integer.MAX_VALUE;
            this.renderObjects = renderObjects;
        }

        public void refreshTimer(int val){
            this.endTick = val + Tasks.getTick();
        }

        public void stopRender(){
            this.registered = false;
            this.endTick = -1;
        }

        public void cancelTimer(){
            this.endTick = Integer.MAX_VALUE;
        }


        @Override
        public void renderVirtual(MatrixStack stack, float partialTicks) {
            for(RenderObject renderObject : renderObjects){
                renderObject.render(stack, partialTicks);
            }
        }

        @Override
        public boolean stillRender() {
            return registered && Tasks.getTick() <= this.endTick;
        }

        public void startRender(){
            if(!registered){
                registered = true;
                RenderTasks.registerVirtualRenderTask(this);
            }
        }
    }

    public static interface RenderObject{
        public void render(MatrixStack stack, float partialTicks);
    }
    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class LineObject implements RenderObject{
        Vec3d start;
        Vec3d movement;
        Color color = Color.GREEN;
        public LineObject(Vec3d start, Vec3d movement){
            this.start = start;
            this.movement = movement;
        }
        @Override
        public void render(MatrixStack stack, float partialTicks) {
            RenderUtils.drawLineVirtual(stack, start, start.add(movement), color);
        }
    }
    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class BoxObject implements RenderObject{

        Vec3d startVec;
        Vec3d endVec;
        Color color;
        float opacity = 0.25F;
        public BoxObject(Vec3d start, Vec3d end, Color color){
            this.startVec = start;
            this.endVec = end;
            this.color = color;
        }

        @Override
        public void render(MatrixStack stack, float partialTicks) {
            RenderUtils.setAsCurrentShaderColor(color, opacity);
            RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), startVec, endVec);
        }
    }
    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    @AllArgsConstructor
    public static class BoxOutlineObject implements RenderObject{
        Vec3d startVec;
        Vec3d endVec;
        Color color;


        @Override
        public void render(MatrixStack stack, float partialTicks) {
            RenderUtils.setAsCurrentShaderColor(color, 1.0F);
            RenderUtils.drawOutlinedBox(stack, startVec, endVec);
        }
    }
    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    @AllArgsConstructor
    public static class BoxMoveTarget implements RenderObject{
        Box startBox;
        Vec3d delta;
        Color color1;
        Color color2;
        public BoxMoveTarget(Box startBox, Vec3d delta){
            this(startBox, delta, Color.GREEN, Color.RED);
        }

        @Override
        public void render(MatrixStack stack, float partialTicks) {
            RenderUtils.setAsCurrentShaderColor(color1, 0.25F);
            RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), startBox.getMinPos(), startBox.getMaxPos());
            RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), startBox.getMinPos().add(delta), startBox.getMaxPos().add(delta));
            for (var ver: CollisionUtil.getBoxVertices(startBox))
                RenderUtils.drawLineVirtual(stack, ver, ver.add(delta), color2);
        }
    }
    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class QuadObject implements RenderObject{
        Vec3d[] abcd;
        Color color;
        public QuadObject(Vec3d abcd, Vec3d b, Vec3d c, Vec3d d, Color color){
            this.abcd = new Vec3d[]{abcd, b, c, d};
            this.color = color;
        }
        @Override
        public void render(MatrixStack stack, float partialTicks) {
            RenderUtils.setAsCurrentShaderColor(color, 0.25F);
            RenderUtils.drawQuad(stack.peek().getPositionMatrix(), abcd[0], abcd[1], abcd[2], abcd[3]);
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    @AllArgsConstructor
    public static class MultiLineObject implements RenderObject{
        List<Vec3d> multiLine;
        Color color;

        @Override
        public void render(MatrixStack stack, float partialTicks) {
            RenderUtils.drawLineVirtual(stack, multiLine, color);
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    @AllArgsConstructor
    public static class LineToTargetObject implements RenderObject{
        Vec3d vec3d;
        Color color;

        @Override
        public void render(MatrixStack stack, float partialTicks) {
            Vec3d camera = RenderUtils.getCameraPos();
            Vec3d camerToBlock = this.vec3d.subtract(camera);
            Vec3d cursorPos = RenderUtils.getTracerOrigin(1.0f);
            RenderUtils.drawLineVirtualCameraCoord(stack, cursorPos, camerToBlock, color);
        }
    }
    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    @AllArgsConstructor
    public static class EntityBoxObject implements RenderObject{
        Entity entity;

        Color color;
        @Override
        public void render(MatrixStack stack, float partialTicks) {
            RenderUtils.setAsCurrentShaderColor(color, 1.0F);
            RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), entity.getBoundingBox().getMinPos(), entity.getBoundingBox().getMaxPos());
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    @AllArgsConstructor
    public static class EntityBoxOutlineObject implements RenderObject{
        Entity entity;

        Color color;
        @Override
        public void render(MatrixStack stack, float partialTicks) {
            RenderUtils.setAsCurrentShaderColor(color, 0.25F);
            RenderUtils.drawOutlinedBox(stack, entity.getBoundingBox().getMinPos(), entity.getBoundingBox().getMaxPos());
        }
    }


    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    @AllArgsConstructor
    public static class LineToEntityObject implements RenderObject{
        Entity entity;
        Color color;
        @Override
        public void render(MatrixStack stack, float partialTicks) {
            Vec3d camera = RenderUtils.getCameraPos();
            Vec3d camerToBlock = this.entity.getBoundingBox().getCenter().subtract(camera);
            Vec3d cursorPos = RenderUtils.getTracerOrigin(1.0f);
            RenderUtils.drawLineVirtualCameraCoord(stack, cursorPos, camerToBlock, color);
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
