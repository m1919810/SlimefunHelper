package me.matl114.hacks.modules.interact;

import java.awt.*;
import java.util.*;
import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CollisionUtil;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;

public class AutoPlate extends BaseModule {
    public AutoPlate() {}

    public final ModulePath autoPlate = makePath(Configs.INTERACT_CONFIG, "place-utils.auto-plate");

    public final FlagRef enable = flagBuilder(autoPlate.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(autoPlate.addHotkey(), new MultiKeyBind(), autoPlate.addEnable())
            .build();

    public List<Vec3i> blocksSeq = new ArrayList<>();

    public void updateBlocks(double i) {
        List<Vec3i> list = new ArrayList<>();
        int range = (int) i;
        for (var y = -range; y <= range; ++y) {
            for (var z = -range; z <= range; ++z) {
                list.add(new Vec3i(y, 0, z));
            }
        }
        list.sort(Comparator.comparingDouble(v -> v.getX() * v.getX() + v.getZ() * v.getZ()));
        blocksSeq = new ArrayList<>(list);
    }

    public final IntRef delay =
            intBuilder(autoPlate.add("delay")).defaultValue(5).build();

    public final IntRef mul =
            intBuilder(autoPlate.add("multiply")).defaultValue(1).build();

    public final DoubleRef range = doubleBuilder(autoPlate.add("interact-range"))
            .defaultValue(5.0D)
            .validator(Configs.doubleRange(0.0D, 100.0D))
            .updateListener(this::updateBlocks)
            .build();

    public final FlagRef copyState = builder(autoPlate.add("copy-state"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef useBlockRotate = builder(autoPlate.add("use-block-rotate"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef render = flagBuilder(autoPlate.add("render")).build();

    public final NBTRef<WrapColor> color = builder(autoPlate.add("render-color"), WrapColor.class)
            .defaultValue(new WrapColor(ColorUtils.color(Color.GREEN)))
            .build();

    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInput);
    }

    final List<BlockPos> placeList = new ArrayList<>();
    Optional<BlockState> placeState = Optional.empty();
    final RenderCollector<Box> drawOutlines = RenderCollectors.createBoxCollector(true, false, false);

    public void refreshState() {
        drawOutlines.clear();
        placeList.clear();
        placeState = Optional.empty();
        Box playerBox = mc.player.getBoundingBox().expand(range.get(), 0, range.get());
        Box checkBox = new Box(
                playerBox.minX,
                playerBox.minY - range.get(),
                playerBox.minZ,
                playerBox.maxX,
                playerBox.minY + 1E-7,
                playerBox.maxZ);
        List<BlockPos> collisions = CollisionUtil.getIntersectingBlockPositions(mc.world, checkBox, false);
        if (collisions.isEmpty()) {
            return;
        }
        int maxY = collisions.stream().mapToInt(BlockPos::getY).max().getAsInt();
        List<BlockPos> filteredPos =
                collisions.stream().filter(s -> s.getY() == maxY).toList();

        placeList.addAll(blocksSeq.stream()
                .map(s -> new BlockPos(s.getX(), maxY, s.getZ()))
                .toList());
        placeList.forEach(s -> drawOutlines.submit(new Box(s), color.get().withAlpha(255)));
        Map<BlockState, Integer> counterMap = new HashMap<>();
        for (var bp : filteredPos) {
            BlockState state = mc.world.getBlockState(bp);
            if (!state.isAir() && !state.isLiquid()) {
                counterMap.merge(state, 1, Integer::sum);
            }
        }

        if (counterMap.isEmpty()) return;
        BlockState bestBlockState = counterMap.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .get()
                .getKey();
        placeState = Optional.of(bestBlockState);
    }

    int timer;

    public void onInput(Event<Void> event) {
        if (checkNull()) return;
        if (enable.get()) {
            if (++timer > delay.get()) {
                timer = 0;
                refreshState();
                if (!placeList.isEmpty()) {
                    tickPlace();
                }
            }
        }
    }

    public int supplyBlocks(Block needBlock) {
        Item needItem = needBlock.asItem();
        if (needItem == Items.AIR) return -1;
        var entry = InventoryUtils.findPlayerItem((item) -> item.getItem() == needItem, true, false);
        return entry == null ? -1 : entry.index();
    }

    public void tickPlace() {
        int cnt = 0;
        for (var bp : placeList) {}
    }
}
