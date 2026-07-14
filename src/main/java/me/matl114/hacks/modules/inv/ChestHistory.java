package me.matl114.hacks.modules.inv;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.Getter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.TileInventoryScreen;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.gui.complex.invcache.InventorySelectScreen;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.task.ServerStorage;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.world.BlockStorage;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.collections.MutableEntry;
import me.matl114.utils.world.BlockLocation;
import me.matl114.utils.world.ContainerPosition;
import me.matl114.versioned.api.VRender;
import net.minecraft.block.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ChestHistory extends BaseModule {
    public final ModulePath invCache = makePath(Configs.INV_CONFIG, "inv-cache");

    private final int AUTO_REFRESH_RANGE = 64;
    private final LinkedHashMap<ContainerPosition, MutableEntry<BlockState, Entry>> screens = new LinkedHashMap<>();
    private final List<HandledScreen<?>> virtualScreens = new ArrayList<>();

    public ChestHistory() {}

    public final KeyBindRef keyBind = hotkey(
                    invCache.add("open-inv-cache"), new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_J))
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::openInventoryCacheScreen))
            .build();

    public final NBTRef<Regex> ignoreList = builder(invCache.add("ignore-container-with-title"), Regex.class)
            .defaultValue(new Regex("^(Slimefun 指南.*|菜单)$"))
            .build();

    public final FlagRef enableTitle =
            flagBuilder(invCache.add("show-title")).defaultValue(false).build();

    public final FlagRef enablePersistent =
            flagBuilder(invCache.add("enable-persistent-storage")).build();

    public List<HandledScreen<?>> getCachedInventories() {
        return (List) Stream.concat(screens.values().stream().map(MutableEntry::getValue), virtualScreens.stream())
                .toList();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostOpenHandledScreen(), this::onOpenHandledScreen);
        registerListener(Listener.getGameJoinPoint(), this::onServerJoin);
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
    }

    public Entry currentEntry;

    public void onOpenHandledScreen(Event<HandledScreen<?>> screenEvent) {
        HandledScreen<?> screen = screenEvent.context();
        if (screen instanceof CreativeInventoryScreen) return;
        Pair<ClientWorld, BlockPos> data;
        String title = screen.getTitle().getString();
        if (title != null) {
            title = title.replaceAll("§.", "");
            // ignore certain screen
            if (ignoreList.get().test(title)) {
                return;
            }
        }
        if (screen instanceof TileInventoryScreen tile && !tile.isVirtual()) {
            ContainerPosition containerPosition = tile.getContainerPosition();
            BlockPos pos = tile.getPos();
            var state = mc.world.getBlockState(pos);
            var newEntry = new Entry(screen, containerPosition.isDouble());
            currentEntry = newEntry;
            onAddEntry(containerPosition, state, newEntry);
        } else {
            virtualScreens.add(screen);
        }
    }

    public static String KEY_INV_STORAGE = "slimefunhelper:chesthistory/inventory_content";

    public void onAddEntry(ContainerPosition containerPosition, BlockState state, Entry newEntry) {
        if (containerPosition.isDouble()) {
            removeEntry(ContainerPosition.ofPosition(containerPosition.getFirst()));
            removeEntry(ContainerPosition.ofPosition(containerPosition.getSecond()));
        }
        MutableEntry<BlockState, Entry> entry = screens.get(containerPosition);
        if (entry != null) {
            entry.key = state;
            entry.value = newEntry;
        } else {
            // remove related single chests
            screens.put(containerPosition, new MutableEntry<>(state, newEntry));
        }
    }

    public void removeEntry(ContainerPosition containerPosition) {
        screens.remove(containerPosition);
        onRemoveEntry(containerPosition);
    }

    public void onRemoveEntry(ContainerPosition containerPosition) {
        screens.remove(containerPosition);
        var blockStorage =
                ServerStorage.getBlockStorage(containerPosition.getFirst().getPos());
        if (blockStorage != null) {
            blockStorage.put(KEY_INV_STORAGE, null);
            ServerStorage.update(blockStorage, true);
        }
    }

    public boolean openInventoryCacheScreen() {
        if (mc.player == null || mc.world == null) return false;
        ScreenAccess.of(new InventorySelectScreen(this::getCachedInventories)).openFromCurrent();
        return true;
    }

    private static String lastServerName = null;

    private void onServerJoin(Event<ClientPlayerEntity> v) {
        String serverName = ServerStorage.getCurrentServerName();
        if (!Objects.equals(serverName, lastServerName)) {
            // refresh
            screens.clear();
            virtualScreens.clear();
        }
        lastServerName = serverName;
    }

    private int interval = 0;
    private static final int REFRESH_RATE = 40;

    public void onTick(Event<ClientPlayerEntity> event) {
        if (++interval < REFRESH_RATE) {
            return;
        }
        if (currentEntry != null && currentEntry.optionalScreen != null) {
            if (ClientPlayerAccess.of(mc.player).getServerScreenHandler().syncId
                    == currentEntry.optionalScreen.getScreenHandler().syncId) {
                currentEntry.dirty = true;
            } else {
                currentEntry = null;
            }
        }
        interval = 0;
        BlockLocation location = BlockLocation.of(event.context());
        var iterator = screens.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().isInRenderRange(location, AUTO_REFRESH_RANGE)) {
                var chunkPos = entry.getKey().getChunk();
                if (!WorldUtils.isServerChunkLoaded(chunkPos.x, chunkPos.z)) {
                    continue;
                }
                if (entry.getValue().getKey().isAir()) {
                    continue;
                }
                if (!entry.getKey().isDouble()) {

                    Block block = mc.world
                            .getBlockState(entry.getKey().getFirst().getPos())
                            .getBlock();
                    if (block != entry.getValue().getKey().getBlock()) {
                        iterator.remove();
                        onRemoveEntry(entry.getKey());
                        continue;
                    }
                } else if (entry.getKey() instanceof ContainerPosition d) {
                    BlockPos pos = d.getFirst().getPos();
                    BlockState block = mc.world.getBlockState(pos);
                    if (!(block.getBlock() instanceof ChestBlock)) {
                        iterator.remove();
                        onRemoveEntry(entry.getKey());
                        continue;
                    }
                    // not a bigchest
                    if (block.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
                        iterator.remove();
                        onRemoveEntry(entry.getKey());
                        continue;
                    }
                    Direction direction = ChestBlock.getFacing(block);
                    BlockPos anotherBlock = pos.offset(direction);
                    BlockPos twoPos = d.getSecond().getPos();
                    // direction change
                    if (!twoPos.equals(anotherBlock)) {
                        iterator.remove();
                        onRemoveEntry(entry.getKey());
                        continue;
                    }
                    // not a chest
                    Block block2 = mc.world.getBlockState(twoPos).getBlock();
                    if (!(block2 instanceof ChestBlock)) {
                        iterator.remove();
                        onRemoveEntry(entry.getKey());
                        continue;
                    }
                }
            }
        }
    }

    private static final int POSITION_FLAG = VRender.createTextPositionFlag(0, 1);

    public void onRender(Event<MatrixStack> event) {
        MatrixStack stack = event.context();

        if (enableTitle.get()) {
            if (mc.player != null) {
                RenderUtils.startDrawVirtual(stack);
                try {
                    BlockLocation location = BlockLocation.of(mc.player);
                    Vec3d cameraPos = RenderUtils.getCameraPos();
                    Set<Vec3d> bigChestsPositions = new HashSet<>();
                    for (var entry : screens.entrySet()) {
                        if (entry.getKey().isInRenderRange(location, AUTO_REFRESH_RANGE)) {
                            Vec3d renderPos = entry.getKey().getCenterPosition();
                            if (bigChestsPositions.contains(renderPos)) {
                                continue;
                            } else {
                                bigChestsPositions.add(renderPos);
                            }
                            Vec3d delta = renderPos.add(0, 0.25, 0).subtract(cameraPos);
                            stack.push();
                            stack.translate(delta.x, delta.y, delta.z);
                            // title的高度是9 我们希望这个9在 0.75 ~ 1.0之间
                            // 我希望他看向我
                            stack.multiply(RenderUtils.getBillboardRotation(DisplayEntity.BillboardMode.CENTER, 0, 0));
                            stack.scale(0.03125F, 0.03125F, 1);
                            int items = (int) InventoryUtils.streamInventory(
                                            entry.getValue().value.getInventory())
                                    .filter(s -> !s.isEmpty())
                                    .count();
                            Text text = entry.getValue()
                                    .getValue()
                                    .getTitle()
                                    .orElse(Text.empty())
                                    .copy()
                                    .append(Text.literal("(x%d)".formatted(items))
                                            .formatted(Formatting.YELLOW));

                            VRender.getInstance()
                                    .drawTextCameraCoord(
                                            text.asOrderedText(),
                                            stack,
                                            Vec3d.ZERO,
                                            POSITION_FLAG,
                                            Color.WHITE,
                                            VRender.DEFAULT_TEXT);

                            stack.pop();
                        }
                    }
                } finally {
                    RenderUtils.stopDrawVirtual(stack);
                }
            }
        }
    }

    public void onLoad(Event<ServerStorage.Meta> metaLoad) {
        List<BlockStorage> blockStorageList = metaLoad.context.toBlockList();
        CompletableFuture.runAsync(() -> {
            for (BlockStorage blockStorage : blockStorageList) {
                if (blockStorage.contains(KEY_INV_STORAGE)) {
                    Entry entry = blockStorage.get(KEY_INV_STORAGE, Entry.CODEC);
                    if (entry != null) {
                        // ? todo complete
                    }
                }
            }
        });
    }

    public static class Entry {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.list(InventoryUtils.STACK_WITH_SLOT_CODEC)
                                .fieldOf("contents")
                                .forGetter(Entry::toSlots),
                        Codec.INT.fieldOf("size").forGetter(Entry::getSize),
                        Codec.BOOL.fieldOf("double-chest").forGetter(Entry::isDoubleChest),
                        TextCodecs.CODEC.optionalFieldOf("title").forGetter(Entry::getTitle))
                .apply(instance, Entry::new));

        boolean dirty = false;

        @Getter
        Inventory inventory;

        @Getter
        int size;

        @Getter
        boolean doubleChest;

        @Getter
        HandledScreen<?> optionalScreen;

        @Getter
        Optional<Text> title;

        BlockState blockState;

        public Entry(List<IndexEntry<ItemStack>> slots, int size, boolean doubleChest, Optional<Text> title) {
            inventory = new SimpleInventory(size);
            this.title = title;
            this.size = size;
            this.doubleChest = doubleChest;
            for (IndexEntry<ItemStack> slot : slots) {
                if (slot.index() >= 0 && slot.index() < size) {
                    inventory.setStack(slot.index(), slot.val());
                }
            }
        }

        public Entry(Inventory inventory, boolean doubleChest) {
            this(List.of(), inventory.size(), doubleChest, Optional.empty());
            update(inventory, doubleChest);
        }

        public Entry(HandledScreen<?> handled, boolean doubleChest) {
            this(guessInventory(handled), doubleChest);
            this.title = Optional.ofNullable(handled.getTitle());
        }

        public void update(Inventory inventory, boolean doubleChest) {
            this.inventory = inventory;
            ;
            this.size = inventory.size();
            this.doubleChest = doubleChest;
            dirty = true;
        }

        public static Inventory guessInventory(HandledScreen<?> handledScreen) {
            return handledScreen.getScreenHandler().slots.stream()
                    .filter(s -> s.inventory != null && !(s.inventory instanceof PlayerInventory))
                    .findAny()
                    .map(s -> s.inventory)
                    .orElseGet(() -> {
                        int size = 0;
                        for (var re : handledScreen.getScreenHandler().slots) {
                            if (re.inventory instanceof PlayerInventory) {
                                break;
                            } else {
                                size += 1;
                            }
                        }
                        var inv = new SimpleInventory(size);
                        for (var idx = 0; idx < size; ++idx) {
                            var slot = handledScreen.getScreenHandler().slots.get(idx);
                            inv.setStack(idx, slot.getStack());
                        }
                        return inv;
                    });
        }

        public void update(HandledScreen<?> handledScreen, boolean doubleChest) {
            this.doubleChest = doubleChest;
            this.optionalScreen = handledScreen;
            var guessInventory = guessInventory(handledScreen);
            update(guessInventory, doubleChest);
            this.title = Optional.ofNullable(handledScreen.getTitle());
            dirty = true;
        }

        public List<IndexEntry<ItemStack>> toSlots() {
            List<IndexEntry<ItemStack>> slots = new ArrayList<>();
            for (var re = 0; re < inventory.size(); ++re) {
                var st = inventory.getStack(re);
                slots.add(new IndexEntry<>(re, st));
            }
            return slots;
        }
    }
}
