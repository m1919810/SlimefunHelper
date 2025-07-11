package me.matl114.hackUtils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import me.matl114.ModConfig;
import me.matl114.access.*;
import me.matl114.gui.basic.*;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.managers.HotKeys;
import me.matl114.utils.*;
import me.matl114.renders.RenderMain;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.*;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.CrossbowUser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;

import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.*;
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
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector2d;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.*;
import java.util.List;
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
    private static AtomicBoolean doLog = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_LOG_ON_SCREEN);
    public static void detectEntitySpawn(EntitySpawnS2CPacket packet){
        //Debug.info("check entity", packet.getEntityType());
        boolean entitySpawn =  HotKeys.getHotkeyToggleManager().getState(HotKeys.DETECT_ENTITY);
        if( !entitySpawn){
            return;
        }
        HashSet<EntityType<?>> whitelisted=getWhitelisted();

        if(whitelisted.contains(packet.getEntityType())){
            trackingEntity.add(packet.getEntityId());
            EntityType<?> type=packet.getEntityType();
            if(doLog.get()){
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
                    if(LivingEntity.class.isAssignableFrom( packet.getEntityType().getBaseClass())){
                        //only log the living Entity; the common Entities are mostly functional and are noisy
                        Debug.chat("Entity",packet.getEntityType().getName(),"spawn at position ",getDisplayedLocation(packet.getX(),packet.getY(),packet.getZ()),",distance: %.2f".formatted(calculateDistance(packet.getX(),packet.getY(),packet.getZ())));
                    }

                }
            }

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
                    if(entity instanceof LivingEntity && whitelisted.contains(entity.getType())){
                        if(doLog.get()){
                            Debug.chat("Entity",entity.getType().getName(),entity instanceof PlayerEntity pl? pl.getName():(entity.hasCustomName()? entity.getCustomName():""),"disappear at position ",getDisplayedLocation(entity.getX(),entity.getY(),entity.getZ()),",distance: %.2f".formatted(calculateDistance(entity.getX(),entity.getY(),entity.getZ())));
                        }
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
    public static AtomicBoolean calArrow = Configs.RENDER_CONFIG.getBoolean(Configs.CAL_PROJECTILE_TRACE);
    public static void calPoweredProjectileTrace(ExplosiveProjectileEntity fireball){
        if(!HotKeys.getHotkeyToggleManager().getState(HotKeys.DETECT_ENTITY)){
            return;
        }
        if(!calFireball.get()){
            return;
        }
        if(!getWhitelisted().contains(fireball.getType())){
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


    public static void calArrowTrace(PersistentProjectileEntity arrow){
        if(!HotKeys.getHotkeyToggleManager().getState(HotKeys.DETECT_ENTITY)){
            return;
        }
        if(!calFireball.get()){
            return;
        }
        if(!getWhitelisted().contains(EntityType.ARROW)){
            return;
        }
        if(mc.player != null){

            if(arrow.getOwner() == mc.player)return;
            if(mc.player.getPos().squaredDistanceTo(arrow.getPos()) < 0.1){
                //might be shot by player using something
                return;
            }
            //给行进方向norm
            Vec3d vec3d = arrow.getVelocity();
            Vec3d vecDirection = vec3d.normalize();
            var playerPos = mc.player.getEyePos();
            var deltaTo = playerPos.subtract(arrow.getPos());
            //求出玩家位置在行进方向上的投影长度
            var projLen = deltaTo.dotProduct(vecDirection);
            if(projLen > 0){
                //勾股定理求出最短距离
                List<Vec3d> preciseLine = ArrowPredictor.of(arrow).predictLine(400);
                Vec3d proj = null;
                double lenSquared = 144000000;
                for (var vec: preciseLine){
                    double len = vec.squaredDistanceTo(playerPos);
                    if(len < lenSquared){
                        proj = vec;
                        lenSquared = len;
                    }
                }
                if(proj == null)return;
                Vector2d planeVec = new Vector2d(proj.x, proj.z);
                double minDist = Math.sqrt(lenSquared);
                //cal direction
                Vector2d playerLookat = getPlayerLook2d();
                boolean front = planeVec.dot(playerLookat) > 0;
                Debug.chat("Arrow trace update:",Text.literal("%.2f".formatted(minDist)).formatted(Formatting.RED),(front? Text.literal("in front of"): Text.literal("at back of")).formatted(Formatting.GREEN),"you");
            }else {
                Debug.chat("Arrow trace update: not towards you");
            }
        }else {
            //Debug.info("null player");
        }
    }
    //public static void calArrowLineTrace()

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

    public static void renderPreProjectile(MatrixStack stack){
        if(mc.world == null || mc.player == null)return;
        //no render arrow
        var whitelist = getWhitelisted();
        boolean arrowItem = whitelist.contains(EntityType.ARROW);
        if(!arrowItem)return;
        if(HotKeys.getHotkeyToggleManager().getState(HotKeys.DETECT_ENTITY) && calArrow.get()){
            RenderUtils.startDrawVirtual(stack);
            for (var fireball : mc.world.getEntities()){
                if(arrowItem && fireball instanceof AbstractSkeletonEntity arrow && !(arrow instanceof WitherSkeletonEntity)){
                    renderSkeletonProjectile(stack, arrow);
                }
                else if(arrowItem && fireball instanceof PlayerEntity player){
                    renderPlayerProjectile(stack, player);
                }else if(arrowItem && fireball instanceof CrossbowUser user){
                    renderCrossbowProjectile(stack, user);
                }else if(arrowItem && fireball instanceof PersistentProjectileEntity arrow){
                    renderArrowProjectile(stack, arrow);
                }
//                if(fireball instanceof InteractionEntity interactionEntity){
//                    RenderUtils.drawOutlinedBox(stack, interactionEntity.getPos().add(SMALL_FROM), interactionEntity.getPos().add(SMALL_TO) );
//                    RenderUtils.drawLineVirtual(stack, mc.player.getPos(), interactionEntity.getPos(), Color.YELLOW);
//                }
            }
            RenderUtils.stopDrawVirtual(stack);
        }
    }


    private static class ArrowPredictor{
        Vec3d pos;
        Vec3d vec;
        Type type;
        Entity owner;
        private static final Random random = net.minecraft.util.math.random.Random.create();
        private static Vec3d lastRand;
        private static int lastRandTime = 0;
        private static Vec3d getArrowRand(){
            if(lastRandTime + 20 < Tasks.getTick()){
                lastRandTime = Tasks.getTick();
                float uncertainty = 1.0f;
                lastRand = new Vec3d(random.nextTriangular(0.0, 0.0172275 * (double)uncertainty), random.nextTriangular(0.0, 0.0172275 * (double)uncertainty), random.nextTriangular(0.0, 0.0172275 * (double)uncertainty));
            }
            return lastRand;
        }
        private static Vec3d calculateVelocity(double x, double y, double z, float power) {
            return (new Vec3d(x, y, z)).normalize().add(getArrowRand()).multiply((double)power);
        }
        public static ArrowPredictor of(AbstractSkeletonEntity entity){
            Vec3d originPos = new Vec3d(entity.getX(), entity.getEyeY() - 0.10000000149011612, entity.getZ());
            Vec3d facing = entity.getRotationVector();
            double d = facing.getX();
            double f = facing.getZ();
            double g = Math.sqrt(d * d + f * f);

//            Debug.info(entity.getTarget());
            Vec3d vec3d = calculateVelocity(d, facing.y +  g* 0.1  , f, 1.6F);
            return new ArrowPredictor(originPos, vec3d, Type.SKELETON, entity);
        }
        private static float getPullProgress(int useTicks) {
            float f = (float)useTicks / 20.0F;
            f = (f * f + f * 2.0F) / 3.0F;
            if (f > 1.0F) {
                f = 1.0F;
            }

            return f;
        }
        private static Vec3d getHandOffset(PlayerEntity player, Hand hand)
        {
            double yaw = Math.toRadians(player.getYaw());
            Arm mainArm = mc.options.getMainArm().getValue();

            boolean rightSide = mainArm == Arm.RIGHT && hand == Hand.MAIN_HAND
                || mainArm == Arm.LEFT && hand == Hand.OFF_HAND;

            double sideMultiplier = rightSide ? -1 : 1;
            double handOffsetX = Math.cos(yaw) * 0.16 * sideMultiplier;
            double handOffsetZ = Math.sin(yaw) * 0.16 * sideMultiplier;

            return new Vec3d(handOffsetX,0 , handOffsetZ);
        }
        public static ArrowPredictor of(PlayerEntity player, RangedWeaponItem weaponItem, Hand hand){
            Vec3d vec3d;
            final Vec3d offset = getHandOffset(player, hand);
            Vec3d pos = new Vec3d(player.getX(), player.getEyeY() - 0.10000000149011612, player.getZ())
                .add(offset);
            if(weaponItem instanceof BowItem){
                int usingTicks = (player.isUsingItem() && player.getActiveHand() == hand) ? player.getItemUseTime() : 1000;
                float progress = getPullProgress(usingTicks);
                float speed = progress * 3.0f;
                Vec3d facing = player.getRotationVector();
                vec3d = calculateVelocity(facing.x, facing.y, facing.z, speed);
            }else {
                Vec3d facing = player.getRotationVec(1.0f);
                float speed = 3.15F;
                vec3d = calculateVelocity(facing.x, facing.y, facing.z, speed);
            }
            return new ArrowPredictor(pos, vec3d, Type.PLAYER , player){
                @Override
                public List<Vec3d> predictLine(int ticks) {
                    List<Vec3d> list =  super.predictLine(ticks);
                    int size = list.size();
                    if(size == 0)return list;
                    List<Vec3d> list3d = new ArrayList<>();
                    for (int i=0 ; i < size; ++ i){
                        list3d.add(list.get(i).subtract(offset.multiply(((i+1)/(double)size))));
                    }
                    return list3d;
                }
            };
        }
        public static ArrowPredictor of(PersistentProjectileEntity arrow){
            return new ArrowPredictor(arrow.getPos(), arrow.getVelocity(), Type.ARROW, arrow);
        }
        public static ArrowPredictor of(CrossbowUser user){
            Entity player = (Entity)user;
            Vec3d facing = (player).getRotationVec(1.0f);
            float speed = 1.6F;
            Vec3d vec3d = calculateVelocity(facing.x, facing.y, facing.z, speed);
            Vec3d pos = new Vec3d(player.getX(), player.getEyeY() - 0.10000000149011612, player.getZ());
            return new ArrowPredictor(pos, vec3d, Type.CROSSBOW, player);
        }
        public ArrowPredictor(Vec3d pos, Vec3d vec, Type type, Entity owner){
            this.pos = pos;
            this.vec = vec;
            this.type = type;
            this.owner = owner;
        }
        public static enum Type{
            SKELETON,
            PLAYER,
            ARROW,
            CROSSBOW
            ;
        }

        public List<Vec3d> predictLine(int ticks){
            Vec3d arrowPos = pos;
            Vec3d arrowMotion = vec;
            double gravity = EntityUtils.getProjectileGravity(Items.BOW);
            List<Vec3d> path = new ArrayList<>();
            Vec3d lastPos;
            if(this.vec.lengthSquared() < 1e-5){
                return List.of();
            }
            for(int i = 0; i < ticks; i++)
            {
                // add to path
                path.add(arrowPos);
                // apply motion
                arrowPos = arrowPos.add(arrowMotion.multiply(0.1));

                // apply air friction
                arrowMotion = arrowMotion.multiply(0.999);

                // apply gravity
                arrowMotion = arrowMotion.add(0, -gravity * 0.1, 0);

                if(path.size() > 2){
                    lastPos = path.get(path.size() - 2);
                    if(RaycastUtils.raycastAnyBlock(owner, lastPos, arrowPos) || RaycastUtils.raycastHitAnyEntityExceptPlayer(owner, lastPos, arrowPos)){
                        break;
                    }
                }

            }
            return path;
        }
    }

    private static void renderSkeletonProjectile(MatrixStack stack, AbstractSkeletonEntity entity){
        if(entity.isUsingItem() && entity.getActiveItem().getItem() instanceof BowItem){
            drawClassicArrowTrajectory(stack, ArrowPredictor.of(entity).predictLine(400));
        }
    }
    private static void renderCrossbowProjectile(MatrixStack stack, CrossbowUser pillagerEntity){
        if(pillagerEntity instanceof LivingEntity entity && entity.isUsingItem() && entity.getActiveItem().getItem() instanceof RangedWeaponItem crossbow){
            drawClassicArrowTrajectory(stack,ArrowPredictor.of(pillagerEntity).predictLine(400) );
            return;
        }
    }


    private static void renderPlayerProjectile(MatrixStack stack, PlayerEntity player){
        for (var hand : Hand.values()){
            if(player.getStackInHand(hand).getItem() instanceof RangedWeaponItem item){
                drawClassicArrowTrajectory(stack, ArrowPredictor.of(player, item, hand).predictLine(400));
                return;
            }
        }
    }

    private static void renderArrowProjectile(MatrixStack stack, PersistentProjectileEntity arrow){
        //filter on ground arrows
        if(!arrow.isOnGround() && arrow.getVelocity().lengthSquared() > 1e-5){
            //fix? velocity does not change
            drawClassicArrowTrajectory(stack,ArrowPredictor.of(arrow).predictLine(400) );
        }
    }
    private static void drawClassicArrowTrajectory(MatrixStack stack, List<Vec3d> vec3ds){
        //escape little traj
        if(vec3ds.size() <= 3)return;
        RenderUtils.drawStripLineVirtual(stack, vec3ds, Color.RED);
        if(!vec3ds.isEmpty()){
            Vec3d finalPosition = vec3ds.get(vec3ds.size() - 1);
            RenderUtils.setAsShaderColor(Color.GREEN, 0.25F);
            RenderUtils.drawSolidBox(stack.peek().getPositionMatrix(), finalPosition.add(SMALL_FROM), finalPosition.add(SMALL_TO));
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
            if(trace.size() > 2){
                lastPos = trace.get(trace.size() - 2);
                if(RaycastUtils.raycastAnyBlock(fireball, lastPos, startpos) || RaycastUtils.raycastHitAnyEntityExceptPlayer(fireball, lastPos, startpos)){
                    break;
                }
            }

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
    private static final Vec3d SMALL_FROM = new Vec3d( - 0.2, -0.2, -0.2);
    private static final Vec3d TO = new Vec3d( 0.5, 0.5, 0.5);
    private static final Vec3d SMALL_TO = new Vec3d(0.2, 0.2, 0.2);
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

    private static int sleepingLevel = 0;

    public static boolean isScreenSleeping(){
        return sleepingLevel != 0;
    }

    public static boolean wakeUpScreen(){
        if (RenderTasks.setScreenSleeping(0)){
            if(mc.player != null)
                Debug.chat(Text.literal("睡眠状态结束, 欢迎回来!").formatted(Formatting.GREEN));
            return true;
        }
        else return false;
    }
    public static boolean setScreenSleeping(int s){
        if(sleepingLevel != s){

            if(s != 0){
                sleepingLevel = s;
                setUpSleepingScreen();
            }else {
                //sleeping = false;
                sleepingLevel = s;
                //递归关闭全部sleepingScreen
                while (mc.currentScreen != null && mc.currentScreen == sleepingScreenInstance){
                    sleepingScreenInstance.close();
                }
                sleepingScreenInstance = null;

            }
            return true;
        }
        return false;
    }
    private static Screen sleepingScreenInstance;

    private static class SleepingChatScreen extends ChatScreen{

        public SleepingChatScreen(String originalChatText) {
            super(originalChatText);
        }
        protected void init(){
            super.init();
            sleepingScreenInstance = this;
            DisplayWidget.instance(this.width - 80, 0, 80, 40)
                .setRenderHandler(LabelElement.instance(Text.literal("按 "+ ModConfig.getFuncHotKeys(HotKeys.WAKE_UP_SCREEN) +" 键退出休眠模式")))
                .addTo(this);
            shouldFreshSleepScreen = true;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            shouldFreshSleepScreen = true;
        }

        @Override
        public void close() {
            //do not close till sleeping is over or game exit
            if(mc.player == null || !isScreenSleeping()){
                super.close();
            }
        }
    }
    private static class SleepingScreen extends Screen implements SafeSleepingScreen{
        protected SleepingScreen(Text title) {
            super(title);
        }

        @Override
        protected void init() {
            super.init();
            sleepingScreenInstance = this;
            DisplayWidget.instance(40, 40, this.width - 80, this.height - 80)
                .setRenderHandler(LabelElement.instance(Text.literal("按 "+ ModConfig.getFuncHotKeys(HotKeys.WAKE_UP_SCREEN) +" 键退出休眠模式")))
                .addTo(this);
            shouldFreshSleepScreen = true;
        }
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta){

        }
    }
    private static class GameExitWhileSleepingScreen extends Screen implements SafeSleepingScreen{
        protected GameExitWhileSleepingScreen() {
            super(Text.empty());
        }
        @Override
        protected void init() {
            super.init();
            sleepingScreenInstance = this;
            DisplayWidget.instance(40, 20, this.width - 80, this.height/3  - 40)
                .setRenderHandler(LabelElement.instance(Text.literal("您的游戏在待机中退出,目前已停止刷新")))
                .addTo(this);
            DisplayWidget.instance(40, this.height/3 + 20, this.width - 80, this.height/3  - 40)
                .setRenderHandler(LabelElement.instance(Text.literal("按 "+ ModConfig.getFuncHotKeys(HotKeys.WAKE_UP_SCREEN) +" 键退出休眠模式")))
                .addTo(this);
            ExecutableWidget.instance(40, (this.height * 2)/3 + 20, this.width - 80, this.height/3 -40)
                .setElementHandler(
                    new ButtonElement(
                        TextProvider.of(Text.literal("点击下方按钮以刷新屏幕")),
                        ButtonAction.run(()->{
                            sleepingScreenInstance = null;
                            if(isScreenSleeping()){
                                if(!ClientUtils.isPlayerOnline()){
                                    //强制重置到2级 如果离线
                                    sleepingLevel = 2;
                                }
                                setUpSleepingScreen();
                            }
                        })
                    )
                )
                .addTo(this);
            shouldFreshSleepScreen = true;
        }
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta){

        }
    }
    private static interface SafeSleepingScreen {
        //screen which implement this can keep even when player exit game, which means it does not need mc.player or mc.world or sth
    }

    public static void setUpSleepingScreen(){
        if(sleepingScreenInstance == null){
            switch (sleepingLevel){
                case 1:
                    sleepingScreenInstance =  new SleepingChatScreen("");
                    break;
                default:
                    sleepingScreenInstance = new SleepingScreen(Text.empty());
                    break;
            }
        }
    }
    public static boolean ensureSleepingScreen(){
        if(mc.currentScreen != sleepingScreenInstance){
            if(ClientUtils.isPlayerOnline()){
                ScreenAccess.of(sleepingScreenInstance).openFromCurrent();

            }else {
                //ensure chat screen is not open when
                ScreenAccess.of((sleepingScreenInstance = new GameExitWhileSleepingScreen())).open();
            }
            //clear current  view
            mc.getFramebuffer().clear(true);
            mc.getFramebuffer().endRead();
            mc.getFramebuffer().beginWrite(true);
            return true;
        }
        return true;
    }
    private static boolean shouldFreshSleepScreen = false;
    public static boolean sleepingRenderTick(){
        if(RenderTasks.ensureSleepingScreen()){
            if(mc.currentScreen != null){
                if(shouldFreshSleepScreen){
                    shouldFreshSleepScreen = false;

                    int i = (int)(mc.mouse.getX() * (double)mc.getWindow().getScaledWidth() / (double)mc.getWindow().getWidth());
                    int j = (int)(mc.mouse.getY() * (double)mc.getWindow().getScaledHeight() / (double)mc.getWindow().getHeight());
                    Window window = mc.getWindow();
                    RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
                    Matrix4f matrix4f = (new Matrix4f()).setOrtho(0.0F, (float)((double)window.getFramebufferWidth() / window.getScaleFactor()), (float)((double)window.getFramebufferHeight() / window.getScaleFactor()), 0.0F, 1000.0F, 21000.0F);
                    RenderSystem.setProjectionMatrix(matrix4f, VertexSorter.BY_Z);
                    Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
                    matrix4fStack.pushMatrix();
                    matrix4fStack.translation(0.0F, 0.0F, -11000.0F);
                    RenderSystem.applyModelViewMatrix();
                    DiffuseLighting.enableGuiDepthLighting();
                    DrawContext drawContext = new DrawContext(mc, mc.gameRenderer.buffers.getEntityVertexConsumers());

                    mc.currentScreen.renderWithTooltip(drawContext, i, j, mc.getRenderTickCounter().getLastDuration());
                    drawContext.draw();;
                    matrix4fStack.popMatrix();
                    RenderSystem.applyModelViewMatrix();
                }
            }else {
                setUpSleepingScreen();
            }
            return true;
        }
        return false;
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
        RenderMain.getRenderLayerTasks().registerHandler(RenderTasks::renderPreProjectile);
    }
}
