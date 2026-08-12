package me.matl114.hacks.modules.combat;

import com.google.common.hash.Hashing;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import lombok.With;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.accessors.hacks.PlayerInternalAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.entity.EntityMovementStatus;
import me.matl114.hacks.utils.entity.Predictor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.EnumAttrKeyValue;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import me.matl114.utils.entity.PlayerInputUtils;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;

public class PositionPredict extends BaseModule {
    public static PositionPredict INSTANCE;
    public final ModulePath attack = makePath(Configs.COMBAT_CONFIG, "attack");
    public final ModulePath attBot = makePath(Configs.COMBAT_CONFIG, "att-bot");

    public PositionPredict() {
        super("PositionPredict");
        INSTANCE = this;
    }
    //
    //    public final FlagRef render = flagBuilder(attack.add("render-predict-pos"))
    //        .build();

    public final NBTRef<PredictArgument> attackPredictArgument = builder(
                    attack.add("attack-predict-argument"), PredictArgument.class)
            .defaultValue(new PredictArgument(2, 5, Mode.NO_PREDICT))
            .build();

    public final NBTRef<PredictArgument> flyPredictArgument = builder(
                    attack.add("fly-predict-argument"), PredictArgument.class)
            .defaultValue(new PredictArgument(2, 5, Mode.PREDICTOR_NV))
            .build();

    public final NBTRef<PredictArgument> spearPredictArgument = builder(
                    attack.add("spear-predict-argument"), PredictArgument.class)
            .defaultValue(new PredictArgument(2, 5, Mode.PREDICTOR_NV))
            .build();

    public final FlagRef enableNoShield = builder(attBot.add("exact-tp-anti-shield"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef debugRender =
            flagBuilder(attack.add("debug-render-prediction")).build();
    Int2ObjectArrayMap<List<Vec3d>> recordedPoints = new Int2ObjectArrayMap<>();

    public final FlagRef placeRecorder = flagBuilder(attBot.add("place-recorder"))
            .updateListener(s -> this.recordedPoints.clear())
            .build();

    public final KeyBindRef placeRecorderHotkey = toggleHotkey(
                    attBot.add("place-recorder-hotkey"), new MultiKeyBind(), attBot.add("place-recorder"))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPostHandlePoint().getChannel(EntityS2CPacket.class), this::onPostEntity);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(EntityPositionS2CPacket.class), this::onPostEntityPos);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
    }

    public void onRender(Event<MatrixStack> event) {
        if (debugRender.get()) {
            RenderUtils.startDrawVirtual(event.context);
            try {
                List<Box> boxes = new ArrayList<>();
                Vec3d camera = RenderUtils.getCameraPos().negate();
                for (var re : mc.world.getPlayers()) {
                    if (re != mc.getCameraEntity()) {
                        Vec3d pos = flyPredictArgument
                                .get()
                                .predict(re); // predictFlyingPosition(re, 2, renderUseArgument1.get());
                        boxes.add(mc.player.dimensions.getBoxAt(pos).offset(camera));
                    }
                }
                VRender.getInstance().createLinesLayer(((operation, vertexConsumer) -> {
                    for (Box box : boxes) {
                        operation.drawOutlinedBox(
                                event.context,
                                vertexConsumer,
                                box.getMinPos(),
                                box.getMaxPos(),
                                Color.MAGENTA.getRGB());
                    }
                }));
                for (var re : recordedPoints.int2ObjectEntrySet()) {
                    var lst = re.getValue();
                    int hash = ColorUtils.withAlphaInt(
                            Hashing.sha256().hashInt(re.getIntKey()).hashCode(), 255);
                    VRender.getInstance().createLinesLayer(((operation, vertexConsumer) -> {
                        for (Vec3d box : lst) {
                            box = box.add(camera);
                            operation.drawOutlinedBox(
                                    event.context,
                                    vertexConsumer,
                                    box.add(-0.2, -0.2, -0.2),
                                    box.add(0.2, 0.2, 0.2),
                                    hash);
                        }
                    }));
                    VRender.getInstance().createLineStripLayer(((operation, vertexConsumer) -> {
                        operation.drawLines(
                                event.context,
                                vertexConsumer,
                                lst.stream().map(s -> s.add(camera)).toList(),
                                hash);
                    }));
                }
            } finally {
                RenderUtils.stopDrawVirtual(event.context);
            }
        }
    }

    // on player update events;
    public void onPostEntity(Event<EntityS2CPacket> event) {
        if (checkNull()) return;
        if (event.context.getEntity(mc.world) instanceof PlayerInternalAccess internal) {
            internal.getPredictorImpl().onEntityPositionMove(event);
            onPlayerEntityUpdate((PlayerEntity) internal);
        }
    }

    public void onPostEntityPos(Event<EntityPositionS2CPacket> event) {
        if (checkNull()) return;
        if (mc.world.getEntityById(event.context.getEntityId()) instanceof PlayerInternalAccess internal) {
            internal.getPredictorImpl().onEntityPositionPost(event);
            onPlayerEntityUpdate((PlayerEntity) internal);
        }
    }

    public void onPlayerEntityUpdate(PlayerEntity player) {
        if (placeRecorder.get()) {
            recordedPoints
                    .computeIfAbsent(player.getId(), (v) -> new ArrayList<>())
                    .add(player.getPos());
        }
    }

    public Predictor getPredictor(Entity entity) {
        return EntityInternalAccess.of(entity).getPositionPredictor();
    }

    public Vec3d predictKnownMovement(Entity entity) {
        return EntityInternalAccess.of(entity).getPositionPredictor().getKnownDeltaMovement();
    }

    public Vec3d getExactAttackPosition(Entity target) {
        if (mc.player == null) return null;
        if (target instanceof ShulkerEntity) {
            // consider wtf shit , this entity collides with player
            // consider all collisions use bounding box not directions
            Vec3d vec3 = target.getPos();
            //            BlockPos posAt = BlockPos.ofFloored(vec3);
            Box boundingBox = target.getBoundingBox();
            for (Direction dir : Direction.values()) {

                Vec3d testPos =
                        switch (dir) {
                            case UP -> vec3.withAxis(Direction.Axis.Y, boundingBox.maxY + 0.1);
                            case DOWN -> vec3.withAxis(Direction.Axis.Y, boundingBox.minY - 2);
                            case NORTH -> vec3.withAxis(Direction.Axis.Z, boundingBox.minZ - 0.5);
                            case SOUTH -> vec3.withAxis(Direction.Axis.Z, boundingBox.maxZ + 0.5);
                            case EAST -> vec3.withAxis(Direction.Axis.X, boundingBox.maxX + 0.5);
                            case WEST -> vec3.withAxis(Direction.Axis.X, boundingBox.minX - 0.5);
                        };

                if (!MovTasks.ENGIN.checkEnvironmentCollision(mc.player, testPos, true)) {
                    return testPos;
                }
            }
            return null;
        } else {
            boolean considerAntiShield = considerAntiShield(target);
            Vec3d deltaMovments;
            if (considerAntiShield) {
                deltaMovments = target.getRotationVector().normalize().multiply(-0.2);
            } else if (target instanceof PlayerEntity playerEntity) {
                var re = attackPredictArgument.get();

                Vec3d predictedPosition = re.predict(
                        playerEntity); /// predictAttackPosition(playerEntity, re.ticksLater(), re.ticksHistory(),
                // re.mode());
                deltaMovments = predictedPosition.subtract(target.getPos());
            } else {
                Vec3d targetFacing = mc.player.getPos().subtract(target.getPos());
                Vec3d targetFacingHorizontal = new Vec3d(targetFacing.x, 0.0d, targetFacing.z);
                double multiply = 0.5;
                deltaMovments = targetFacingHorizontal.normalize().multiply(multiply);
            }

            Vec3d targetPos = target.getPos();
            Vec3d actualMove = MovTasks.ENGIN.simulateMovement(mc.player, targetPos, deltaMovments);
            return targetPos.add(actualMove);
        }
    }

    public Vec3d predictAimPositionForEntity(Entity entity, float finalVelocity) {
        Vec3d estimatedDelta = entity.getPos().subtract(mc.player.getPos());
        double estimateSpeed = estimatedDelta.length() / (finalVelocity);
        int estimateTick;
        if (estimateSpeed < 2.0) {
            estimateTick = 0;
        } else if (estimateSpeed > 20.0) {
            estimateTick = 20;
        } else {
            estimateTick = (int) (estimateSpeed - 2.0D);
        }

        return entity.getEyePos()
                .subtract(entity.getPos())
                .multiply(0.75)
                .add(flyPredictArgument.get().predictWithExtraTicks(entity, estimateTick));
    }

    public boolean considerAntiShield(Entity target) {
        return enableNoShield.get()
                && target instanceof LivingEntity livingEntity
                && livingEntity.isUsingItem()
                && livingEntity.getActiveItem().getItem() instanceof ShieldItem;
    }

    public Vec3d predictPlayerMove(PlayerInputUtils.Input input) {
        EntityMovementStatus<Entity> entityMovementStatus = new EntityMovementStatus<>(mc.player);
        if (mc.player.isFallFlying()) {
            return mc.player.getVelocity();
        } else if (mc.player.isInFluid()) {
            return mc.player.getVelocity();
        } else {
            return entityMovementStatus.calculateLastMoveVelocity(input.forwardSpeed(), input.sidewaysSpeed());
        }
    }

    public enum Mode implements ConfigEnum {
        NO_PREDICT,
        LINEAR,
        QUADRATIC,
        PREDICTOR_NV,
        @ApiStatus.Experimental
        PREDICTOR_ROTATION;

        @Override
        public String getConfigEnumType() {
            return "predict_mode";
        }
    }

    @With
    public static record PredictArgument(int ticksLater, int ticksHistory, Mode mode)
            implements NBTParsable<PredictArgument> {
        public static NBTType<PredictArgument> TYPE = new NBTType<>(
                "predictargument",
                RecordCodecBuilder.<PredictArgument>create(s -> s.group(
                                Codec.INT.fieldOf("ticks").forGetter(PredictArgument::ticksLater),
                                Codec.INT.fieldOf("history").forGetter(PredictArgument::ticksHistory),
                                CodecUtils.enumCodec(Mode.class).fieldOf("mode").forGetter(PredictArgument::mode))
                        .apply(s, PredictArgument::new)),
                (s, x, y, dx, dy) -> {
                    SubScreenWidget subScreenWidget = SubScreenWidget.instance(x, y, dx, dy);
                    int half = dx / 4;
                    WrapperFactory<Integer, PredictArgument> firstWrapper =
                            WrapperFactory.of((d) -> s.getOriginValue().withTicksLater(d), PredictArgument::ticksLater);
                    WrapperFactory<Integer, PredictArgument> secondWrapper = WrapperFactory.of(
                            (d) -> s.getOriginValue().withTicksHistory(d), PredictArgument::ticksHistory);
                    WrapperFactory<Mode, PredictArgument> thirdWrapper =
                            WrapperFactory.of((d) -> s.getOriginValue().withMode(d), PredictArgument::mode);

                    return subScreenWidget
                            .addDrawableChild(DisplayWidget.instance(0, 0, dy, dy)
                                    .setRenderHandler(new ButtonElement(
                                                    TextProvider.of(Text.translatableWithFallback(
                                                            "widget.nbt-parsable.predict-argument.ticks", "F:")),
                                                    ButtonAction.empty())
                                            .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                                    "widget.nbt-parsable.predict-argument.ticks.tooltips",
                                                    "预测前瞻量（ticksLater）")))))
                            .addDrawableChild(new TypeConvertAttrKeyValue<>(s, firstWrapper, NBTTypes.INT_TYPE)
                                    .generateValueWidget(dy, 0, half - dy, dy))
                            .addDrawableChild(DisplayWidget.instance(half, 0, dy, dy)
                                    .setRenderHandler(new ButtonElement(
                                                    TextProvider.of(Text.translatableWithFallback(
                                                            "widget.nbt-parsable.predict-argument.history", "H:")),
                                                    ButtonAction.empty())
                                            .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                                    "widget.nbt-parsable.predict-argument.history.tooltips",
                                                    "预测历史长度（ticksHistory）")))))
                            .addDrawableChild(new TypeConvertAttrKeyValue<>(s, secondWrapper, NBTTypes.INT_TYPE)
                                    .generateValueWidget(half + dy, 0, half - dy, dy))
                            .addDrawableChild(DisplayWidget.instance(2 * half, 0, dy, dy)
                                    .setRenderHandler(new ButtonElement(
                                                    TextProvider.of(Text.translatableWithFallback(
                                                            "widget.nbt-parsable.predict-argument.mode", "M:")),
                                                    ButtonAction.empty())
                                            .withTooltips(TooltipHandler.of(ChatUtils.parseTooltipsTranslation(
                                                    "widget.nbt-parsable.predict-argument.mode.tooltips",
                                                    "预测模式（Mode）")))))
                            .addDrawableChild(new TypeConvertAttrKeyValue<>(
                                            s,
                                            thirdWrapper,
                                            EnumAttrKeyValue.createEnumWidgetFactory(Mode.class),
                                            WrapperFactory.of(Mode::valueOf, Mode::name))
                                    .generateValueWidget(2 * half + dy, 0, 2 * half - dy, dy));
                },
                new PredictArgument(2, 5, Mode.NO_PREDICT));

        @Override
        public NBTType<PredictArgument> type() {
            return TYPE;
        }

        public Vec3d predict(Entity entity) {
            return EntityInternalAccess.of(entity)
                    .getPositionPredictor()
                    .predict(ticksLater, mode.ordinal(), ticksHistory);
        }

        public Vec3d predictWithExtraTicks(Entity entity, int ticks) {
            return EntityInternalAccess.of(entity)
                    .getPositionPredictor()
                    .predict(ticksLater + ticks, mode.ordinal(), ticksHistory);
        }
    }
}
