package me.matl114.hacks.modules.combat;

import me.matl114.accessors.access.ItemStackAccess;
import me.matl114.events.Listener;
import me.matl114.hacks.CombatTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.Tasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.events.Event;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ProjectileEnhance extends BaseModule {
    public static final String TOGGLE_AUTO_AIM = "bow-aim";
    public static final String[] BOW_ENHANCE = {"hotkeys-toggle", "bow-aim"};

    public static final String[] BOW_AIM = {"projectile", "aim-enable"};

    public static final String[] BOW_TP = {"projectile", "tp-enable"};

    public static final String[] TARGETING = {"projectile", "targeting-mode"};

    public static final String[] TP_ACCELERATE = {"projectile", "tp-accelerate"};

    public static final String[] TP_EXACT = {"projectile", "tp-accelerate-exact-tp"};

    public static final String[] USE_ITEM_ID = {"projectile", "tp-accelerate-exact-tp"};

    public static final String[] TRIDENT_DUPE = {"projectile", "trident-auto-dupe"};

    public ProjectileEnhance() {

    }

    // we share the flag with BowEnhance, that's ok
    public FlagRef enable = toggle(BOW_ENHANCE)
        .build();

    public KeyBindRef hotkey = toggleHotkey(BOW_ENHANCE, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_H))
        .build();

    public FlagRef enableAim = flagBuilder(Configs.COMBAT_CONFIG, BOW_AIM)
        .build();

    public FlagRef enableTp = flagBuilder(Configs.COMBAT_CONFIG, BOW_TP)
        .build();

    public EnumRef<Configs.LegalInteractMode> mode = builder(Configs.COMBAT_CONFIG, TARGETING, Configs.LegalInteractMode.class)
        .defaultValue(Configs.LegalInteractMode.USEITEM_PACKET)
        .build();

    public DoubleRef tpDistance = builder(Configs.COMBAT_CONFIG, TP_ACCELERATE, DoubleRef.TYPE)
        .defaultValue(150.0D)
        .build();

    public FlagRef enhanceTp = flagBuilder(Configs.COMBAT_CONFIG, TP_EXACT)
        .build();

    public StringRef useItemId = builder(Configs.COMBAT_CONFIG, USE_ITEM_ID, StringRef.TYPE)
        .defaultValue("^(LOGITECH_LASER_GUN)$")
        .validator(Configs.REGEX_VALIDATOR)
        .build();

    public FlagRef tridentDupe = flagBuilder(Configs.COMBAT_CONFIG, TRIDENT_DUPE)
        .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onTridentDupe);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInteractItemC2SPacket.class), this::onPlayerInteractItem);
    }

    public static float getShootingPowerCrossbow(ItemStack a) {
        ChargedProjectilesComponent stack = a.get(DataComponentTypes.CHARGED_PROJECTILES);
        return (stack != null && stack.contains(Items.FIREWORK_ROCKET)) ? 1.6F : 3.15F;
    }


    public void onTridentDupe(Event<PlayerActionC2SPacket> actionC2SPacketEvent){
        var actionC2SPacket = actionC2SPacketEvent.context();
        if(actionC2SPacket.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM && mc.player != null && tridentDupe.get() && mc.player.getMainHandStack().getItem() instanceof TridentItem trident){
            //dupe trident
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 3, mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
            Tasks.scheduleDelayed(()->mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 3, mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player), 1);
        }
    }




    private boolean passUseItemIdCheck(ItemStack stack){
        String regex = useItemId.get();
        if(regex == null || regex.isEmpty()){
            return false;
        }
        String id = Registries.ITEM.getId( stack.getItem()).getPath();
        if(Pattern.matches(regex, id)){
            return true;
        }
        String sfid = ItemStackUtils.getSfId(stack);

        return sfid != null && Pattern.matches(regex, sfid);
    }
    public void onPlayerInteractItem(Event<PlayerInteractItemC2SPacket> packetMutableObject){
        //targeting
        if(packetMutableObject.isCancelled())return;

        if(enable.get()){
            PlayerInteractItemC2SPacket packet = packetMutableObject.context();
            Hand hand = packet.getHand();
            ItemStack stack = mc.player.getStackInHand(hand);
            Item itemType = ItemStackAccess.of(stack).getRealItem();
            //access to the item before it is used up to 0 count
            if(itemType != Items.AIR){
                ItemStack stackOrigin = stack;
                //make a stackCopy of origin item with 1 count
                stack = new ItemStack(itemType);
                stack.applyChanges(stackOrigin.components.getChanges());
                if(enableAim.get()){
                    //pass check, autoaim
                    boolean makeReaim = false;
                    float velocity = 3600000000f ;
                    if(passUseItemIdCheck(stack)){
                        //line predict
                        //can override crossbow-like items

                        makeReaim = true;
                    }else if(stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof SplashPotionItem || stack.getItem() instanceof LingeringPotionItem){
                        //aim crossbow, SplashPotion, LingerPotion
                        //direct = false;
                        makeReaim = true;
                        velocity = getShootingPowerCrossbow(stack);
                    }
                    if(makeReaim){
                        Entity target = CombatTasks.getTargetSelector().searchAimableEntity(false);
                        if(target != null){
                            Debug.chat(Text.literal("[Proj Aim] Aim at %s".formatted(target instanceof PlayerEntity player? "player ": "entity ")).append(EntityUtils.getEntityDisplayable(target)).formatted(Formatting.GREEN));

                            Vec3d facing = CombatTasks.getPositionPredict().predictAimPositionForEntity(target, velocity).subtract(mc.player.getEyePos());
                            Vec2f redirectTarget = CombatTasks.calculatePitchYawPredict(velocity, Vec3d.ZERO, facing);
                            if(Float.isNaN(redirectTarget.x) || Float.isInfinite(redirectTarget.x) || Float.isNaN(redirectTarget.y) || Float.isInfinite(redirectTarget.y)){
                                Debug.chat("[Proj Aim] Proj failed to reach the target");
                            }else{
                                //recreate packet to en, do something
                                packet = new PlayerInteractItemC2SPacket(hand, packet.getSequence(), redirectTarget.y, redirectTarget.x);
                            }
                        }else{
                            Debug.chat(Text.literal("[Proj Aim] Target absent"));
                        }
                    }
                }
                if(enableTp.get()){
                    if(stack.getItem() instanceof EnderPearlItem pearl || stack.getItem() instanceof SplashPotionItem || stack.getItem() instanceof ExperienceBottleItem || stack.getItem() instanceof LingeringPotionItem || stack.getItem() instanceof EggItem){
                        pearl_tp:
                        {
                            boolean exactTp = enhanceTp.get();
                            double range = tpDistance.get();
                            Vec3d facing = EntityUtils.pitchYawToRotation(packet.getPitch(), packet.getYaw());// mc.player.getRotationVector();
                            Vec3d facingNorm = facing.normalize();
                            Vec3d oppositeFacing = Vec3d.ZERO.subtract(facingNorm);
                            Vec3d finalMove = Vec3d.ZERO;
                            Vec3d currentPlayerPos = mc.player.getPos();

                            test_tp_position:
                            {
                                // optimize the collision check by caching List of Boxes
                                MovTasks.CollisionContext context = new MovTasks.CollisionCache(mc.player, currentPlayerPos, currentPlayerPos.add( oppositeFacing.multiply(range + 1.0d)), true);
                                double test = range;
                                for (; test > 10.0D; test -= 1.0D){
                                    if(exactTp){
                                        Vec3d oppositeMultiply = oppositeFacing.multiply(test);
                                        if(MovTasks.validMoveTo(context, currentPlayerPos.add(oppositeMultiply), Vec3d.ZERO.subtract(oppositeMultiply))){
                                            finalMove = oppositeMultiply;
                                            break test_tp_position;
                                        }
                                    }else{
                                        if(MovTasks.validMoveToAndBack(context, currentPlayerPos, oppositeFacing.multiply(test))){
                                            finalMove = oppositeFacing.multiply(test);
                                            break test_tp_position;
                                        }
                                    }
                                }
                                // t < 10
                                //check again
                                test = 10.0D;
                                for (; test > 0.0D; test -= 0.5D){
                                    Vec3d oppositeMultiply = oppositeFacing.multiply(test);
                                    if(exactTp){
                                        if(MovTasks.validMoveTo(context, currentPlayerPos.add(oppositeMultiply), Vec3d.ZERO.subtract(oppositeMultiply))){
                                            finalMove = oppositeMultiply;
                                            break test_tp_position;
                                        }
                                    }else{
                                        if(MovTasks.validMoveToAndBack(context, currentPlayerPos, oppositeMultiply)){
                                            finalMove = oppositeMultiply;
                                            break test_tp_position;
                                        }
                                    }
                                    Vec3d oppoHorizontal = new Vec3d(oppositeMultiply.x, 0.0d, oppositeMultiply.z);
                                    Vec3d simulateMove = context.simulateMovement(mc.player, currentPlayerPos, oppoHorizontal);
                                    if(MovTasks.validMovementAsServer(oppoHorizontal, simulateMove)){
                                        Vec3d simulateDownMove = context.simulateMovement(mc.player, currentPlayerPos.add(simulateMove), new Vec3d(0, oppositeMultiply.y, 0));
                                        Vec3d wholeMovement = simulateMove.add(simulateDownMove);
                                        //y does not matter , xz matters
                                        if(MovTasks.validMoveTo(context, currentPlayerPos.add(wholeMovement), wholeMovement.multiply(-1))){
                                            finalMove = wholeMovement;
                                            break test_tp_position;
                                        }
                                    }
                                }
                                //should strengthen move when test < 10,
                            }
                            if(finalMove.lengthSquared() > 1E-4){
                                //随便写的阈值 速度太快不需要转向
                                List<Vec3d> tpSequence = MovTasks.generateTpSequence(currentPlayerPos, currentPlayerPos.add(finalMove), false, 161, true);
                                if(!tpSequence.isEmpty()){
                                    Debug.chat(Text.literal("[Proj TP] Projectile Velocity Simulate %.2f".formatted(finalMove.length())).formatted(Formatting.GREEN));
                                    List<MovTasks.MovInfo> movements = new ArrayList<>();
                                    int size = tpSequence.size();
                                    for (int i=0; i< size; ++i){
                                        movements.add(i == 0 ? MovTasks.MovInfo.createNotOnGround(tpSequence.get(i)) : MovTasks.MovInfo.create(tpSequence.get(i)));
                                    }
                                    movements.add(MovTasks.MovInfo.create(currentPlayerPos.add(0, 9E-8, 0)));

                                    MovTasks.scheduleFarawayMoveInternal(movements, false, MovTasks.MovingContext.create(currentPlayerPos), true);

                                    MovTasks.setupAutoResync(mc.player.getPos() , 10);
                                    break pearl_tp;
                                }

                                //send packets to simulate movements
                            }
                            Debug.chat(Text.literal("[Proj TP] Projectile Velocity fail to simulate"));
                        }

                    }
                }
            }
            packetMutableObject.context(packet);
        }


    }
}
