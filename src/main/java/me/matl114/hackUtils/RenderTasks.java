package me.matl114.hackUtils;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import me.matl114.access.EntityAccess;
import me.matl114.access.ExplosiveProjectileAccess;
import me.matl114.access.PlayerInteractionAccess;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.renders.RenderMain;
import me.matl114.utils.RaycastUtils;
import me.matl114.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector2d;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class RenderTasks {
    public static void init(){

    }
    private static HashSet<EntityType<?>> entityTypes = new HashSet<>();
    private static MinecraftClient mc = MinecraftClient.getInstance();
    public static Config.StringRef RENDER_DETECT_WHITELIST= Configs.RENDER_CONFIG.getString(Configs.RENDER_DETECT_SPAWN_WHITELIST);
    private static HashSet<EntityType<?>> getWhitelisted(){

        return entityTypes;
    }
    private static double calculateDistance(double x1,double y1,double z1){
        if(MinecraftClient.getInstance().player!=null){
            ClientPlayerEntity player=MinecraftClient.getInstance().player;
            return Math.sqrt( player.getPos().squaredDistanceTo(x1,y1,z1));
        }
        return -1.0f;
    }
    private static boolean noPlayerSpawnPacket=false;

    public static Text getDisplayedLocation(double x,double y ,double z){
        return Text.literal("[%d,%d,%d]".formatted((int)x, (int)y, (int)z)).setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,"%.2f %.2f %.2f".formatted(x,y,z))).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Text.literal("click to copy coord")))).formatted(Formatting.GREEN);
    }
    public static void detectEntitySpawn(EntitySpawnS2CPacket packet){
        boolean entitySpawn =  HotKeys.getHotkeyToggleManager().getState(HotKeys.DETECT_ENTITY);
        if( !entitySpawn){
            return;
        }
        HashSet<EntityType<?>> whitelisted=getWhitelisted();

        if(whitelisted.contains(packet.getEntityType())){
            trackingEntity.add(packet.getEntityId());
            EntityType<?> type=packet.getEntityType();
            if(type==EntityType.PLAYER){
                Text text=null;
                if(MinecraftClient.getInstance().world!=null){
                    PlayerListEntry entry= MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(packet.getUuid());
                    if(entry!=null){
                        text=Text.literal(entry.getProfile().getName()).formatted(Formatting.GREEN);
                    }
                }
                Debug.chat("Player ",text==null?"":text,"spawn at position ",getDisplayedLocation(packet.getX(),packet.getY(),packet.getZ()),",distance: %.2f".formatted(calculateDistance(packet.getX(),packet.getY(),packet.getZ())));
                Debug.chat("Player Entity Id ",packet.getEntityId());
            }else{
                Debug.chat("Entity",packet.getEntityType().getName(),"spawn at position ",getDisplayedLocation(packet.getX(),packet.getY(),packet.getZ()),",distance: %.2f".formatted(calculateDistance(packet.getX(),packet.getY(),packet.getZ())));
            }
            //Debug.info(packet.getEntityData(),packet.getUuid());
        }
    }
//    public static void detectPlayerSpawn(PlayerSpawnS2CPacket packet){
//        HashSet<EntityType<?>> whitelisted=getWhitelisted();
//        if(whitelisted.contains(EntityType.PLAYER)){
//            Text name=null;
//            if(MinecraftClient.getInstance().world!=null){
//                PlayerListEntry entry= MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(packet.getPlayerUuid());
//                if(entry!=null){
//                    name=Text.literal( entry.getProfile().getName()).formatted(Formatting.GREEN);
//                }
//            }
//
//            Debug.chat("Player",name==null?"":name,"spawn at position ",getDisplayedLocation(packet.getX(),packet.getY(),packet.getZ()),",distance: %.2f".formatted(calculateDistance(packet.getX(),packet.getY(),packet.getZ())));
//        }
//    }
    private static final IntOpenHashSet trackingEntity = new IntOpenHashSet();

    private static final ObjectOpenHashSet<ExplosiveProjectileEntity> calculatingExplosives = new ObjectOpenHashSet<>();

    public static void clearTrackingEntity(){
        trackingEntity.clear();
        calculatingExplosives.clear();
    }
    public static void removeTrackedId(int i){
        trackingEntity.remove(i);
        calculatingExplosives.removeIf(e->e.getId() == i);
    }


    public static void detectEntityDestory(EntitiesDestroyS2CPacket packet){
        HashSet<EntityType<?>> whitelisted=getWhitelisted();
        World clientWorld=MinecraftClient.getInstance().world;
        boolean detechEntity = HotKeys.getHotkeyToggleManager().getState(HotKeys.DETECT_ENTITY);
        if(clientWorld!=null){
            for(int i:packet.getEntityIds()){
                //this should run whenever state is
                removeTrackedId(i);
                if(detechEntity){
                    Entity entity=clientWorld.getEntityById(i);
                    if(entity==null)continue;
                    if(whitelisted.contains(entity.getType())){
                        Debug.chat("Entity",entity.getType().getName(),entity instanceof PlayerEntity pl? pl.getName():(entity.hasCustomName()? entity.getCustomName():""),"disappear at position ",getDisplayedLocation(entity.getX(),entity.getY(),entity.getZ()),",distance: %.2f".formatted(calculateDistance(entity.getX(),entity.getY(),entity.getZ())));
                    }
                }

            }
        }
    }
    public static void drawRecipeHistory(DrawContext context, TextRenderer textRenderer, int x, int y, int atX, int atY){
        RecipeEntry<?> entry = PlayerInteractionAccess.of( MinecraftClient.getInstance().interactionManager).getLastlyCrafted();
        ItemStack tobeRendered;
        if(entry != null){
            tobeRendered = entry.value().getResult(MinecraftClient.getInstance().world.getRegistryManager());
        }else{
            tobeRendered = new ItemStack(Items.BARRIER);
        }
        context.getMatrices().push();
        context.getMatrices().translate((float)x, (float)y, 0.0F);
        RenderUtils.drawSlotLikeItemAt(context,textRenderer,tobeRendered,atX,atY,0,1.0F,666);
        //render lock
        boolean lock = PlayerInteractionAccess.of(MinecraftClient.getInstance().interactionManager).getRecipeLock();
        if(lock){
            RenderUtils.drawSlotLikeItemAt(context,textRenderer,new ItemStack(Items.BARRIER),atX     -4,atY + 4,50,0.4F,999);
        }
        context.getMatrices().pop();
    }
    private static final AtomicBoolean disableServerResourcePack = Configs.RENDER_CONFIG.getBoolean(Configs.RESOURCE_IGNORE_SERVER);
    public static boolean denyServerPacket(ClientConnection connection, ResourcePackSendS2CPacket sendPacket){
        if( disableServerResourcePack.get()){
            connection.send(new ResourcePackStatusC2SPacket(sendPacket.id(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
            connection.send(new ResourcePackStatusC2SPacket(sendPacket.id(), ResourcePackStatusC2SPacket.Status.DOWNLOADED));
            connection.send(new ResourcePackStatusC2SPacket(sendPacket.id(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
            Debug.chat(Text.literal("Successfully reject server resourcepack").formatted(Formatting.GREEN),sendPacket.id());
            Debug.chat(Text.literal("Download url:").formatted(Formatting.GREEN),Text.literal( sendPacket.url()).setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, sendPacket.url()))).formatted(Formatting.YELLOW));
            return false;
        }
        return true;
    }
    public static void onTrackEntityGlow(Entity entity){
        if(trackingEntity.contains(entity.getId())){
            EntityAccess.of(entity).setGlow0(true);
        }
    }
    public static void updatePoweredProjectile(ExplosiveProjectileEntity fireball){
        calculatingExplosives.add(fireball);
        var access = ExplosiveProjectileAccess.of(fireball);

    }
    public static AtomicBoolean calFireball = Configs.RENDER_CONFIG.getBoolean(Configs.CAL_FIREBALL_TRACE);
    public static void calPoweredProjectileTrace(ExplosiveProjectileEntity fireball){
        if(!HotKeys.getHotkeyToggleManager().getState(HotKeys.DETECT_ENTITY)){
            return;
        }
        if(!calFireball.get()){
            return;
        }

        Vec3d vec = fireball.getVelocity();
//        var access = ExplosiveProjectileAccess.of(fireball);
//        Vec3d power = access.getPower();
//        if(power == null){
//            return;
//        }
        if(fireball.accelerationPower > 1e-4){
            calLineTrace(fireball.getPos(), vec);
        }
//        if(vec.lengthSquared() < 0.00001 || vec.normalize().squaredDistanceTo(power.normalize()) < 0.01){
//            //初速度不值得一提 或者速度基本上和加速度同向 近似为加速直线运动检测
//
//        }else {
//            //需要模拟运动轨迹
//            //Debug.info("complex");
//        }
    }
    public static void calLineTrace(Vec3d fireballPosition, Vec3d power){
        // (x - x0)/px = (y - y0)/py = (z - z0)/pz
        if(mc.player != null){
            //给行进方向norm
            power = power.normalize();
            var playerPos = mc.player.getEyePos();
            var deltaTo = playerPos.subtract(fireballPosition);
            //求出玩家位置在行进方向上的投影长度
            var projLen = deltaTo.dotProduct(power);
            if(projLen > 0){
                //勾股定理求出最短距离
                var projPoint = fireballPosition.add(power.multiply(projLen));
                var lookAtProjPoint = projPoint.subtract(playerPos);
                var minDist = lookAtProjPoint.length();
                Vector2d planeVec = new Vector2d(lookAtProjPoint.x, lookAtProjPoint.z);
                //cal direction
                Vector2d playerLookat = getPlayerLook2d();
                boolean front = planeVec.dot(playerLookat) > 0;
                Debug.chat("Fireball trace update:",Text.literal("%.2f".formatted(minDist)).formatted(Formatting.RED),(front? Text.literal("in front of"): Text.literal("at back of")).formatted(Formatting.GREEN),"you");
            }else {
                Debug.chat("Fireball trace update: not towards you");
            }
        }else {
            //Debug.info("null player");
        }
    }
    //todo render more projectile, like arrow and wither skull ,
    public static void renderExplosiveProjectileLine(MatrixStack stack){
        var player = mc.player;
        if(player == null){
            return;
        }
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.DETECT_ENTITY)){
            RenderUtils.startDrawVirtual(stack);
            for (var fireball: calculatingExplosives){
                if(fireball.getPos().squaredDistanceTo(player.getPos()) < 22_500){
                    RenderUtils.drawStripLineVirtual(stack, predictFireballTrace(fireball), Color.RED);
                }
            }
            RenderUtils.stopDrawVirtual(stack);
        }
    }

    public static ArrayList<Vec3d> predictFireballTrace(ExplosiveProjectileEntity fireball){
        ArrayList<Vec3d> trace = new ArrayList<>();
        Vec3d startpos = fireball.getPos();
        Vec3d lastPos = startpos;
        var access = ExplosiveProjectileAccess.of(fireball);
        float drag = access.getDragCommon();
        Vec3d motion = fireball.getVelocity();
        Vec3d power = motion.normalize().multiply(fireball.accelerationPower);

        trace.add(startpos);

        for (int i=0; i<400;++i){
            startpos = startpos.add(motion);
            motion = motion.add(power).multiply(drag);
            trace.add(startpos);
            if(RaycastUtils.raycastAnyBlock(fireball, lastPos, startpos) || RaycastUtils.raycastHitAnyEntity(fireball, lastPos, startpos)){
                break;
            }
            lastPos = startpos;
        }
        return trace;
    }

    private static void onPlayerDisconnect(Void v){
        clearTrackingEntity();
    }

    public static Vector2d getPlayerLook2d(){
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        float f = MathHelper.cos(-yaw * 0.017453292F - 3.1415927F);
        float g = MathHelper.sin(-yaw * 0.017453292F - 3.1415927F);
        float h = -MathHelper.cos(-pitch * 0.017453292F);
        return new Vector2d(g*h, f*h);
    }
    public static void debugEntityTick(Entity entity){
//        if(entity instanceof ExplosiveProjectileEntity entity1){
//            var access = ExplosiveProjectileAccess.of(entity1);
//            Debug.info("called", access.getPower());
//        }
    }
    private static final Set<VirtualRenderTask> renderBlocks= new HashSet<>();


    public static void registerBlockRenderTask(VirtualRenderTask task){
        renderBlocks.add(task);
    }
    private static final Vec3d FROM = new Vec3d(-0.5, -0.5, -0.5);
    private static final Vec3d TO = new Vec3d( 0.5, 0.5, 0.5);
    private static void onRenderVirtualTasks(MatrixStack stack){
        if(renderBlocks.isEmpty())return;
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

    public static abstract class BlockRenderingTask implements VirtualRenderTask {
        @Getter
        final BlockPos pos;
        final boolean shouldLine;
        int tick;
        public BlockRenderingTask(BlockPos pos,  boolean shouldLine, int tick){
            this.pos = pos;
            this.shouldLine= shouldLine;
            this.tick = tick + Tasks.getTick();
            this.buffer = createStatic();
        }
        final VertexBuffer buffer;
        @Override
        public boolean stillRender() {
            if( Tasks.getTick() <= this.tick){
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
                Vec3d cursorPos = RenderUtils.getClientLookVec(1.0f);
                RenderUtils.drawLineVirtualCameraCoord(stack, cursorPos, camerToBlock, Color.RED);
            }
        }
    }

    public static class CountingBlockOutlineTarget extends BlockRenderingTask {

        public CountingBlockOutlineTarget(BlockPos pos, int endTick, boolean shouldLine){
            super(pos, shouldLine, endTick);
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
            RenderUtils.setAsShaderColor(Color.GREEN,1.0f);
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
            RenderUtils.setAsShaderColor(Color.GREEN,0.25f);
        }
    }



    //todo add rayTrace render Func
    static {
        EntityUtils.parseEntityWhiteList(RENDER_DETECT_WHITELIST.get().replace(',','|'),entityTypes);
        RENDER_DETECT_WHITELIST.addUpdateListener((str)->{
            EntityUtils.parseEntityWhiteList(str.replace(',','|'),entityTypes);
        });
        //Listener.registerPacketListener(RenderTasks::onDetect,true);
        Listener.registerSinglePacketListener(EntitySpawnS2CPacket.class, (Consumer<EntitySpawnS2CPacket>) spawn -> mc.executeSync(()->{

            RenderTasks.detectEntitySpawn(spawn);
        }));
        Listener.registerSinglePacketListener(EntitiesDestroyS2CPacket.class, (Consumer<EntitiesDestroyS2CPacket>) spawn -> mc.executeSync(()->{
            RenderTasks.detectEntityDestory(spawn);
        }));
//        Listener.registerPacketListener(RenderTasks::denyServerPacket,true);
        Listener.registerSinglePacketListener(ResourcePackSendS2CPacket.class, RenderTasks::denyServerPacket);
        EntityTasks.getEntityDataListener().registerHandler(RenderTasks::onTrackEntityGlow);
        EntityTasks.getEntityDataListener().registerHandler((entity -> {
            if(entity instanceof ExplosiveProjectileEntity fireball){
                updatePoweredProjectile(fireball);
            }
        }));
        EntityTasks.getEntityTickListener().registerHandler(RenderTasks::debugEntityTick);
        RenderMain.getRenderLayerTasks().registerHandler(RenderTasks::renderExplosiveProjectileLine);
        Listener.getServerDisconnectPoint().registerHandler(RenderTasks::onPlayerDisconnect);
        RenderMain.getRenderLayerTasks().registerHandler(RenderTasks::onRenderVirtualTasks);
    }
}
