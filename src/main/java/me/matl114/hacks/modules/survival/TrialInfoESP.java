package me.matl114.hacks.modules.survival;

import java.util.*;
import me.matl114.accessors.access.ChunkAccess;
import me.matl114.accessors.interfaces.MetadataHolder;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.BlockUpdate;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.TracingOption;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TrialSpawnerBlock;
import net.minecraft.block.VaultBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.block.enums.TrialSpawnerState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class TrialInfoESP extends BaseModule {
    public TrialInfoESP() {
        super("TrialESP");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "render-utils.trial-esp");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            toggleHotkey(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final DoubleRef textScale = doubleBuilder(root.add("text-scale"))
            .defaultValue(0.75D)
            .validator(Configs.doubleRange(0.1D, 4.0D))
            .build();

    public final NBTRef<TracingOption> traceOption = builder(root.add("trace-option"), TracingOption.class)
            .defaultValue(new TracingOption(true, false))
            .build();

    public final FlagRef renderEnterPosition =
            flagBuilder(root.add("render-enter-position")).build();

    public final NBTRef<WrapColor> color = builder(root.add("color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.AQUA))
            .build();

    public final NBTRef<WrapColor> color2 = builder(root.add("color2"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.RED))
            .build();

    public final NBTRef<WrapColor> miscColor = builder(root.add("misc-color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.YELLOW))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
        registerListener(Listener.getWorldSwitchPoint(), this::onSwitchWorld);
        registerListener(
                Listener.getBlockUpdateListener().getChannel(Blocks.TRIAL_SPAWNER), this::onTrialSpawnerActivated);
    }

    public final RenderCollector<RenderElements.Text> textCollector = RenderCollectors.createTextCollector();
    public final RenderCollector<List<Vec3d>> lineRenderCollector = RenderCollectors.createLinesCollector();
    public final RenderCollector<Box> boxCollector = RenderCollectors.createBoxCollector(true, false, false);
    public final RenderCollector<Vec3d> traceCollector = RenderCollectors.createTracerCollector();
    private static final String TRIAL_MOB_METADATA_KEY = "slimefunhelper:trial_esp/trial_mob";
    private Map<ChunkPos, Set<BlockPos>> cachedVaultsAndTrials = new HashMap<>();
    private BlockPos enterTrialPosition;
    TimerExecutor cacheClearTimer = new TimerExecutor();

    public void onTick(Event<ClientPlayerEntity> event) {
        if (cacheClearTimer.run(1000)) {
            cachedVaultsAndTrials = new HashMap<>();
        }
        textCollector.clear();
        lineRenderCollector.clear();
        boxCollector.clear();
        traceCollector.clear();
        TracingOption tracingOption = traceOption.get();
        int miscRgb = miscColor.get().withAlpha(255);
        if (enterTrialPosition != null
                && mc.player != null
                && mc.player.squaredDistanceTo(enterTrialPosition.toCenterPos()) > 256.0D * 256.0D) {
            enterTrialPosition = null;
        }
        if (renderEnterPosition.get() && enterTrialPosition != null) {
            Vec3d start = enterTrialPosition.toCenterPos();
            lineRenderCollector.submit(List.of(start, start.add(256.0D, 0.0D, 0.0D)), miscRgb);
        }
        if (enable.get()) {
            for (var chunk : CommonUtils.chunks(false)) {
                ChunkPos pos = chunk.getPos();
                if (!cachedVaultsAndTrials.containsKey(pos)) {
                    Set<BlockPos> sets = new HashSet<>();
                    for (var blockEntities : ChunkAccess.of(chunk).blockEntityEntries()) {
                        var bt = blockEntities.getValue().getType();
                        if (bt == BlockEntityType.VAULT || bt == BlockEntityType.TRIAL_SPAWNER) {
                            sets.add(blockEntities.getKey());
                        }
                    }
                    cachedVaultsAndTrials.put(pos, sets);
                }
            }
            boolean anyTrialNearby = false;
            for (var re : cachedVaultsAndTrials.values()) {
                for (var bp : re) {
                    anyTrialNearby = true;
                    boolean accept = false;
                    BlockEntity be = mc.world.getBlockEntity(bp);
                    List<Text> textLines = new ArrayList<>();
                    if (be instanceof TrialSpawnerBlockEntity be1) {
                        accept = true;
                        BlockState currentState = mc.world.getBlockState(bp);
                        TrialSpawnerState state = currentState.get(TrialSpawnerBlock.TRIAL_SPAWNER_STATE);
                        if (state == TrialSpawnerState.COOLDOWN) {
                            highlightTrialKey(bp, miscRgb, tracingOption);
                        }
                        if (state == TrialSpawnerState.ACTIVE) {
                            markTrialMobs(be1, bp);
                        }
                        if (state == TrialSpawnerState.WAITING_FOR_PLAYERS) {
                            textLines.add(Text.translatable("message.module.trial-info-esp.display.trial-ready"));
                        } else if (state == TrialSpawnerState.COOLDOWN) {
                            OptionalLong cooldownLong = WorldManager.INSTANCE.getTrialSpawnerCooldownStartTime(be1);
                            String time;
                            if (cooldownLong.isPresent()) {
                                long cooldownTime = System.currentTimeMillis() - cooldownLong.getAsLong();
                                long totalSeconds = cooldownTime / 1000;
                                long minutes = totalSeconds / 60; // 总分钟数（不向小时进位）
                                long seconds = totalSeconds % 60; // 剩余的秒数
                                time = minutes + "m" + seconds + "s";
                            } else {
                                time = "?";
                            }
                            textLines.add(
                                    Text.translatable("message.module.trial-info-esp.display.trial-cooldown", time));
                            accept = false;
                        } else if (state != TrialSpawnerState.INACTIVE) {
                            OptionalLong activeLong = WorldManager.INSTANCE.getTrialSpawnerActiveStartTime(be1);
                            String time;
                            if (activeLong.isPresent()) {
                                long activeTime = System.currentTimeMillis() - activeLong.getAsLong();
                                long totalSeconds = activeTime / 1000;
                                long minutes = totalSeconds / 60;
                                long seconds = totalSeconds % 60;
                                time = minutes + "m" + seconds + "s";
                            } else {
                                time = "?";
                            }
                            textLines.add(
                                    Text.translatable("message.module.trial-info-esp.display.trial-active", time));
                        }
                        var entity = be1.getSpawner().getData().setDisplayEntity(be1.getSpawner(), mc.world, state);
                        if (entity != null) {
                            textLines.add(Text.translatable(
                                    "message.module.trial-info-esp.display.trial-type",
                                    entity.getType().getName()));
                        }
                    } else if (be instanceof VaultBlockEntity be2) {
                        BlockState currentState = mc.world.getBlockState(bp);
                        boolean omin = currentState.get(VaultBlock.OMINOUS);
                        textLines.add(
                                omin
                                        ? Text.translatable("message.module.trial-info.esp.display.vault-type.ominous")
                                        : Text.translatable("message.module.trial-info.esp.display.vault-type.common"));
                        var set = be2.getSharedData().getConnectedPlayers();
                        Set<UUID> openedPlayers = WorldManager.INSTANCE.getVaultOpenPlayers(be2);
                        Set<UUID> uid2 = new HashSet<>(openedPlayers);
                        uid2.removeAll(set);
                        if (uid2.size() != openedPlayers.size()) {
                            WorldManager.INSTANCE.recordVaultOpenedBy(be2, uid2);
                            openedPlayers = uid2;
                        }
                        if (!openedPlayers.contains(mc.player.getUuid())) {
                            accept = true;
                            textLines.add(Text.translatable("message.module.trial-info-esp.display.vault-can-open"));
                        } else {
                            textLines.add(
                                    Text.translatable("message.module.trial-info-esp.display.vault-can-not-open"));
                        }
                    }
                    if (!textLines.isEmpty()) {
                        MutableText result = Text.empty().append(textLines.get(0));
                        for (int i = 1; i < textLines.size(); i++) {
                            result = result.append(Text.literal("\n")).append(textLines.get(i));
                        }
                        Vec3d textPos = bp.toCenterPos().add(0.0D, 0.4, 0.0D);
                        textCollector.submit(
                                new RenderElements.Text(result, textPos, (float) textScale.get()),
                                accept
                                        ? color.get().withAlpha(255)
                                        : color2.get().withAlpha(255));
                    }
                }
            }
            if (anyTrialNearby) {
                for (Entity entity : mc.world.getEntities()) {
                    if (hasTrialMobMetadata(entity)) {
                        submitHighlight(entity, miscRgb, tracingOption);
                    }
                }
            }
        }
    }

    public void onRender3D(Event<Render3D> event) {
        if (checkNull()) return;
        if (!enable.get()) {
            return;
        }
        RenderUtils.startDrawVirtual(event.context().stack());
        try {
            textCollector.render3D(event.context().stack());
            if (traceOption.get().line()) {
                lineRenderCollector.render3D(event.context().stack());
                traceCollector.render3D(event.context().stack());
            }
            if (traceOption.get().box()) {
                boxCollector.render3D(event.context().stack());
            }
        } finally {
            RenderUtils.stopDrawVirtual(event.context().stack());
        }
    }

    private void highlightTrialKey(BlockPos spawnerPos, int color, TracingOption tracingOption) {
        Box keyBox = new Box(spawnerPos.up());
        for (ItemEntity item : mc.world.getEntitiesByType(net.minecraft.entity.EntityType.ITEM, keyBox, entity -> {
            ItemStack stack = entity.getStack();
            return stack.isOf(Items.TRIAL_KEY) || stack.isOf(Items.OMINOUS_TRIAL_KEY);
        })) {
            submitHighlight(item, color, tracingOption);
        }
    }

    private void markTrialMobs(TrialSpawnerBlockEntity spawner, BlockPos spawnerPos) {
        int radius = spawner.getSpawner().getDetectionRadius();
        Box range = new Box(spawnerPos).expand(radius);
        for (Entity entity : mc.world.getOtherEntities(null, range, candidate -> candidate instanceof MobEntity)) {
            if (entity instanceof MetadataHolder holder) {
                holder.getMetadata().put(this, TRIAL_MOB_METADATA_KEY, Boolean.TRUE);
            }
        }
    }

    private boolean hasTrialMobMetadata(Entity entity) {
        return entity instanceof MetadataHolder holder
                && !holder.isMetaEmpty()
                && Boolean.TRUE.equals(holder.getMetadata().get(this, TRIAL_MOB_METADATA_KEY));
    }

    private void submitHighlight(Entity entity, int color, TracingOption tracingOption) {
        if (tracingOption.box()) {
            boxCollector.submit(entity.getBoundingBox(), color);
        }
        if (tracingOption.line()) {
            traceCollector.submit(entity.getBoundingBox().getCenter(), color);
        }
    }

    private void onTrialSpawnerActivated(Event<BlockUpdate> event) {
        if (!enable.get() || enterTrialPosition != null) {
            return;
        }
        BlockState oldState = event.context.oldState();
        BlockState newState = event.context.newState();
        if (oldState.getBlock() == Blocks.TRIAL_SPAWNER
                && newState.getBlock() == Blocks.TRIAL_SPAWNER
                && oldState.get(TrialSpawnerBlock.TRIAL_SPAWNER_STATE) != TrialSpawnerState.ACTIVE
                && newState.get(TrialSpawnerBlock.TRIAL_SPAWNER_STATE) == TrialSpawnerState.ACTIVE) {
            enterTrialPosition = event.context.pos().toImmutable();
        }
    }

    private void onSwitchWorld(Event<World> event) {
        cachedVaultsAndTrials.clear();
        enterTrialPosition = null;
        lineRenderCollector.clear();
    }
}
