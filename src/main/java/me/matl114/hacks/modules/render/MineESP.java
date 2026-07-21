package me.matl114.hacks.modules.render;

import java.util.Comparator;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.mine.MiningProgressManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.inventory.ItemStackSample;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class MineESP extends BaseModule {

    public MineESP() {
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.RENDER_CONFIG, "mine-render.mine-esp");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            toggleHotkey(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final FlagRef renderName = flagBuilder(root.add("render-name")).build();

    public final FlagRef renderBox = flagBuilder(root.add("render-box")).build();
    public final FlagRef ghostHandPredict =
            flagBuilder(root.add("ghost-hand-predict")).build();

    public final NBTRef<WrapColor> colorName = builder(root.add("name-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.WHITE)))
            .build();

    public final NBTRef<WrapColor> colorFrame = builder(root.add("frame-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.AQUA)))
            .build();

    public final NBTRef<WrapColor> colorProgress = builder(root.add("progress-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Formatting.GOLD)))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onUpdate);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
    }

    final RenderCollector<Box> frameRenderer = RenderCollectors.createBoxCollector(true, false, false);
    final RenderCollector<Box> progressRenderer = RenderCollectors.createBoxCollector(true, true, false);
    final RenderCollector<RenderElements.Text> textRenderer = RenderCollectors.createTextCollector();

    public void onUpdate(Event<ClientPlayerEntity> eventUpdate) {
        textRenderer.clear();
        frameRenderer.clear();
        progressRenderer.clear();
        if (checkNull()) return;
        if (enable.get()) {
            for (var re : MiningProgressManager.INSTANCE.getBreakingMap().values()) {
                if (re.blockPos != null) {
                    BlockPos currentMining = re.blockPos;
                    int startTick = re.breakingStartTick;
                    int progress = re.breakingProgress;
                    String breakState;
                    int progressPercentage;
                    if (ghostHandPredict.get()) {
                        float prediction = predictGhostHandBreakSpeed(re.player, currentMining);
                        float predictProgress = prediction * (Tasks.getTick() - startTick);
                        if (predictProgress > 0.7F) {
                            progressPercentage = 100;
                            breakState = "&cInstant";
                        } else {
                            progressPercentage = (int) (predictProgress * 100);
                            breakState = "&e%d%%".formatted(progressPercentage);
                        }
                    } else {
                        if (progress > 0) {
                            progressPercentage = (int) (progress * 10);
                            breakState = "&e%d%%".formatted(progressPercentage);
                        } else {
                            progressPercentage = 0;
                            breakState = "&aStop";
                        }
                    }
                    if (renderName.get()) {
                        Text text = ChatUtils.stringToText(re.player.getNameForScoreboard() + "\n" + breakState);
                        textRenderer.submit(
                                new RenderElements.Text(
                                        text, currentMining.toCenterPos().add(0, 0.2, 0), 0.5f),
                                colorName.get().withAlpha(255));
                    }
                    if (renderBox.get()) {
                        frameRenderer.submit(
                                new Box(currentMining), colorFrame.get().withAlpha(255));
                        float progressPF = Math.clamp(progressPercentage / 100.0F, 0.0F, 1.0F);
                        progressRenderer.submit(
                                new Box(
                                        currentMining.toCenterPos().add(RenderTasks.FROM.multiply(progressPF)),
                                        currentMining.toCenterPos().add(RenderTasks.TO.multiply(progressPF))),
                                colorProgress.get().withAlpha(64));
                    }
                }
                if (re.potentialDoubleBreak != null) {
                    BlockPos currentMining = re.potentialDoubleBreak;
                    String breakState = "Double";
                    int progressPercentage = re.doubleBreakProgress * 10;
                    if (renderName.get()) {
                        Text text = Text.literal(re.player.getNameForScoreboard() + "\n" + breakState);
                        textRenderer.submit(
                                new RenderElements.Text(
                                        text, currentMining.toCenterPos().add(0, 0.2, 0), 0.5f),
                                colorName.get().withAlpha(255));
                    }
                    if (renderBox.get()) {
                        frameRenderer.submit(
                                new Box(currentMining), colorFrame.get().withAlpha(255));
                        float progressPF = Math.clamp(progressPercentage / 100.0F, 0.0F, 1.0F);
                        progressRenderer.submit(
                                new Box(
                                        currentMining.toCenterPos().add(RenderTasks.FROM.multiply(progressPF)),
                                        currentMining.toCenterPos().add(RenderTasks.TO.multiply(progressPF))),
                                colorProgress.get().withAlpha(64));
                    }
                }
            }
        }
    }

    public float predictGhostHandBreakSpeed(PlayerEntity player, BlockPos pos) {
        PlayerStateManager.PlayerStatus status = PlayerStateManager.INSTANCE.getPlayerStatus(player);
        BlockState blockState = mc.world.getBlockState(pos);
        ItemStack bestTool = status.trackedInventoryItems.stream()
                .max(Comparator.comparingDouble(s -> {
                    return WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(player, blockState, s.sample());
                }))
                .map(ItemStackSample::sample)
                .orElse(ItemStack.EMPTY);
        float speed = WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(player, blockState, bestTool);
        return WorldUtils.calcBlockBreakingDelta(blockState, mc.world, pos, speed);
    }

    public void onRender3D(Event<MatrixStack> event) {
        if (checkNull()) return;
        if (enable.get()) {
            RenderUtils.startDrawVirtual(event.context);
            try {
                frameRenderer.render3D(event.context);
                progressRenderer.render3D(event.context);
                textRenderer.render3D(event.context);
            } finally {
                RenderUtils.stopDrawVirtual(event.context);
            }
        }
    }
}
