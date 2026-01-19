package me.matl114.utils;

import me.matl114.access.ClientPlayerAccess;
import me.matl114.hackUtils.MovTasks;
import me.matl114.hackUtils.Tasks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.stream.Streams;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DevUtils {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static void scheduleFarawayMoveInternal(List<MovTasks.MovInfo> deltaMovements, boolean allowNextTick, MovTasks.MovingContext context, boolean considerNoFall){

//        boolean shouldCheckSpeed = ! mc.player.isFallFlying() || (! mc.world.getGameRules().getBoolean(GameRules.DISABLE_ELYTRA_MOVEMENT_CHECK));
//        double maxOnceLen = (mc.player.isFallFlying()? (10 * Math.sqrt(3)) : 10) - 1E-2;
        //filter front not-needed packets

        {
            boolean startFirstMove = false;
            List<MovTasks.MovInfo> moveList = new ArrayList<>();
            Vec3d currentVec330 = context.from().getValue();
            for (int i = 0; i < deltaMovements.size(); ++i){
                //filter packets that are not removing at the front
                if(!startFirstMove && deltaMovements.get(i).vec3d().subtract(currentVec330).lengthSquared() < 1E-4){
                    continue;
                }else{
                    startFirstMove = true;
                    moveList.add(deltaMovements.get(i));
                }
            }
            if(moveList.isEmpty())return;
            deltaMovements = moveList;
        }

        boolean firstMove = true;
        Vec3d originalPositionTick = context.tickFirstGoodVec().getValue();
        boolean riding = mc.player.hasVehicle();
        Entity rootEntity = mc.player.getRootVehicle();
        double deltaY = rootEntity.getY() - mc.player.getY();
        double minY = context.from().getValue().y;
        double maxY = minY;
        //calculate current tokens if it is the first move of the tick
        if(context.currentTokenInTick().get() == 0){
            //gain tokens
            int maxTokenNeeded = 0;
            Vec3d lastPos = originalPositionTick;
            int packetCount = 0;
            for (var move: deltaMovements){
                packetCount += 1;
                Vec3d movingPos = move.vec3d();
                Vec3d movement = movingPos.subtract(lastPos);

                lastPos = movingPos;
                double d7 = movement.length();
                double d8 = movingPos.subtract(originalPositionTick).length();
                double d10  = Math.max(d7, d8);
                //比如
                //使用 9 9 9 9 9作为移动的， 每次packet + 1
                //即使不用token,d10也不会超过packetNum * (...)

                int tokenNeeded = ((int) Math.ceil (d10/ 9.9)) - packetCount;
                maxTokenNeeded = Math.max(maxTokenNeeded, tokenNeeded);
            }
//            Debug.info("check ", maxTokenNeeded);
            if(maxTokenNeeded > 0){
                //粗略估计
                if(maxTokenNeeded > 20 - deltaMovements.size()){
                    //unable to reach so faraway
//                    Debug.info("UnReachable ");
                    return;
                }
                for (int  i = 0; i < maxTokenNeeded; ++i){
                    context.currentTokenLimit().set(20);
                    context.currentTokenInTick().incrementAndGet();
                    //  Debug.chat("send zero packet !",context.currentTokenInTick.get(), context.currentTokenLimit.get());
                    if(riding){
                        mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(rootEntity));
                    }else{
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(mc.player.isOnGround()));
                    }
                }
            }
        }
        //calculate tokens depends on paper sourceocode
        var iter = deltaMovements.iterator();
        while (iter.hasNext()){
            MovTasks.MovInfo info = iter.next();
            Vec3d fromNow = context.from().getValue();
            Vec3d vec3d = info.vec3d().subtract(fromNow);
            maxY = Math.max(maxY, info.vec3d().y);
            minY = Math.min(minY, info.vec3d().y);
            double len = vec3d.length();
            double len2 = originalPositionTick.subtract(info.vec3d()).length();
            double speedArg = 9.8;
            if(len == 0 && len2 == 0){
                //d10 is 0 server side, can regain token
                context.currentTokenLimit().set(20);
                context.currentTokenInTick().incrementAndGet();
                if(info.oGroundOverride() != null){
                    mc.player.setOnGround(info.oGroundOverride());
                }
                boolean hasRot = info.rotationOverride() != null;
                if(hasRot){
                    mc.player.setPitch(info.rotationOverride().x);
                    mc.player.setYaw(info.rotationOverride().y);
                }
                if(riding){
                    mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(rootEntity));
                }else{
                    if(hasRot){
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
                    }else{
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(mc.player.isOnGround()));
                    }

                }
            }else{
                int ExtraTokenNeeded = (int)Math.ceil (((Math.max(len, len2))/speedArg));;
                //+1代表这个包发出去之后的结果
                int tokenNow = context.currentTokenInTick().get() + 1;
//            tokenLimit = Math.max(tokenLimit, 1);
                //超出了tokenLimit, 会被强制重置为1（server side)
                //需要把剩下的移动到下一个tick执行
                if((Math.min( ExtraTokenNeeded, tokenNow) >= Math.max(5, context.currentTokenLimit().get()))){
                    if(allowNextTick && context.currentTokenInTick().get() > Math.max(5, context.currentTokenLimit().get())){
                        //
                        if(firstMove)return;
                        List<MovTasks.MovInfo> leftTasks = Stream.concat(Stream.of(info), Streams.of(deltaMovements)).toList();
                        Tasks.scheduleDelayed(()->{
                            scheduleFarawayMoveInternal(leftTasks, allowNextTick, context.resetTick(), considerNoFall);
                        }, 1);
                        if(considerNoFall){
                            if(Math.abs(maxY - minY) > mc.player.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE) - 1){
                                ClientPlayerAccess.of(mc.player).setForceNoFall(true);
//                                mc.player.fallDistance = MovTasks. FORCE_RESET_DISTANCE;
                                //in case that resync packet cause OnGround falldamage
                                mc.player.setOnGround(false);
                            }
                        }
                        return;
                    }
                }
                context.currentTokenLimit().decrementAndGet();
                context.currentTokenInTick().incrementAndGet();
                //  Debug.chat("before move", context.currentTokenInTick.get(), context.currentTokenLimit.get());
                if(info.oGroundOverride() != null){
                    mc.player.setOnGround(info.oGroundOverride());
                }
                boolean hasRot = info.rotationOverride() != null;
                if(hasRot){
                    mc.player.setPitch(info.rotationOverride().x);
                    mc.player.setYaw(info.rotationOverride().y);
                }
                Vec3d to = info.vec3d();
                if(riding){
                    rootEntity.setPosition(to.add(0, deltaY, 0));
                    var packet = new VehicleMoveC2SPacket(rootEntity);
                    mc.getNetworkHandler().sendPacket(packet);
                    context.from().setValue(to);
                    if(info.updatePlayer()){
                        mc.player.setPosition(to);
                    }
                }else{
                    var packet = hasRot ? new PlayerMoveC2SPacket.Full(to.getX(), to.getY(), to.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()): new PlayerMoveC2SPacket.PositionAndOnGround(to.getX(), to.getY(), to.getZ(), mc.player.isOnGround());
                    mc.getNetworkHandler().sendPacket(packet);
                    //Debug.info("send packet schedule", packet.getX(0), packet.getY(0), packet.getZ(0), packet.isOnGround());
                    context.from().setValue(to);
                    if(info.updatePlayer())
                        mc.player.setPosition(to);
                }

                //real first move
                //zero packets is not seen as movement
                firstMove = false;

            }
        }
        if(considerNoFall){
            if(Math.abs(maxY - minY) > mc.player.getAttributeValue(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE) - 1){
                ClientPlayerAccess.of( mc.player).setForceNoFall(true);// = MovTasks. FORCE_RESET_DISTANCE;
                //in case that resync packet cause OnGround falldamage
                mc.player.setOnGround(false);
            }
        }
    }
}
