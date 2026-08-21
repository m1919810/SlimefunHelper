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
import me.matl114.accessors.access.PlayerInteractBlockC2SPacketAccess;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.accessors.interfaces.MetadataHolder;
import me.matl114.accessors.interfaces.TileInventory;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.task.ServerStorage;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.gui.InventorySelectScreen;
import me.matl114.hacks.utils.world.BlockStorage;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.collections.MutableEntry;
import me.matl114.utils.inventory.ImmutableListInventory;
import me.matl114.utils.world.BlockLocation;
import me.matl114.utils.world.ContainerPosition;
import me.matl114.versioned.api.VRender;
import net.minecraft.block.*;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ChestHistory extends BaseModule {
    public final ModulePath invCache = makePath(Configs.INV_CONFIG, "inv-cache");

    private final int AUTO_REFRESH_RANGE = 64;
    private final LinkedHashMap<ContainerPosition, MutableEntry<Block, Entry>> screens = new LinkedHashMap<>();
    private final List<Entry> virtualScreens = new ArrayList<>();
    public static ChestHistory INSTANCE;

    public ChestHistory() {
        super("ChestHistory");
        INSTANCE = this;
    }

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

    public List<Entry> getCachedInventories() {
        return Stream.concat(screens.values().stream().map(MutableEntry::getValue), virtualScreens.stream())
                .toList();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostOpenHandledScreen(), this::onOpenHandledScreen);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractBlockC2SPacket.class), this::onPlaceShulkerBox);
        registerListener(Listener.getGameJoinPoint(), this::onServerJoin);
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
        registerListener(ServerStorage.getServerStorageLoad(), this::onLoad);
        registerListener(ServerStorage.getServerStorageSave(), this::onSave);
    }

    public Entry currentEntry;

    public Entry currentEnderChestEntry;

    private static final Text ENDER_CHEST_TITLE = Text.translatable("container.enderchest");

    public static boolean isEnderChest(Screen handler) {
        return handler instanceof GenericContainerScreen screen && Objects.equals(screen.getTitle(), ENDER_CHEST_TITLE);
    }

    public void onOpenHandledScreen(Event<HandledScreen<?>> screenEvent) {
        HandledScreen<?> screen = screenEvent.context();
        if (screen instanceof CreativeInventoryScreen) return;
        Pair<ClientWorld, BlockPos> data;
        String title = ChatUtils.textToPlainString(screen.getTitle());
        String enderChestTitle = ChatUtils.textToPlainString(ENDER_CHEST_TITLE);
        boolean isEnderChest = Objects.equals(title, enderChestTitle);
        if (!isEnderChest) {
            title = title.replaceAll("§.", "");
            // ignore certain screen
            if (ignoreList.get().test(title)) {
                return;
            }
        }
        Entry newEntry;
        if (screen instanceof TileInventory tile && !tile.isVirtual()) {
            ContainerPosition containerPosition = tile.getContainerPosition();
            BlockPos pos = tile.getPos();
            var state = mc.world.getBlockState(pos);
            newEntry = new Entry(screen, containerPosition);
            onAddEntry(containerPosition, state, newEntry);
        } else {
            virtualScreens.add(newEntry = new Entry(screen));
        }
        currentEntry = newEntry;
        if (isEnderChest) {
            currentEnderChestEntry = newEntry;
        }
    }

    private static final String KEY_SHULKER_BOX_PLACED_BY_PLAYER =
            "slimefunhelper:chest_history/tracked_self_place_shulker";

    public void onPlaceShulkerBox(Event<PlayerInteractBlockC2SPacket> eventInteract) {
        PlayerInteractBlockC2SPacketAccess access = PlayerInteractBlockC2SPacketAccess.of(eventInteract.context);
        PlayerInteractBlockC2SPacketAccess.UseContext useContext = access.getUseContext();
        if (useContext != null
                && useContext.isAccepted()
                && useContext.blockPlace()
                && useContext.stack().getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock) {
            //
            BlockPos placePos = useContext.getPlaceBlockPos(
                    eventInteract.context.getHand(), eventInteract.context.getBlockHitResult());
            // if it is a real place
            BlockState state = mc.world.getBlockState(placePos);
            if (state.getBlock() == bi.getBlock()
                    && mc.world.getBlockEntity(placePos) instanceof ShulkerBoxBlockEntity shulkerBoxBlock) {
                if (shulkerBoxBlock instanceof MetadataHolder holder) {
                    holder.getMetadata().put(this, KEY_SHULKER_BOX_PLACED_BY_PLAYER, true);
                }
                //                ItemStack stack = useContext.stack();
                //                var container = stack.get(DataComponentTypes.CONTAINER);
                //                if(container != null){
                //                    ContainerPosition position = ContainerPosition.ofSingle(mc.world, placePos);
                //                    List<IndexEntry<ItemStack>> allItems =
                // InventoryUtils.getContainerInventory(container);
                //                    Text titleName = stack.get(DataComponentTypes.CUSTOM_NAME);
                //                    titleName = titleName == null ? shulkerBoxBlock.get
                //                    Entry previewEntry = new Entry(
                //                        allItems, allItems.size(),
                //                    )
                //                }
                ContainerPosition position = ContainerPosition.ofSingle(mc.world, placePos);
                Entry previewEntry = new Entry(
                        InventoryUtils.getInventoryEntries(shulkerBoxBlock),
                        shulkerBoxBlock.size(),
                        Optional.ofNullable(shulkerBoxBlock.getName()),
                        Optional.of(bi.getBlock()),
                        Optional.of(position));
                onAddEntry(position, state, previewEntry);
            }
        }
    }

    @ApiMethod
    public Entry getEntry(ContainerPosition containerPosition) {
        var re = screens.get(containerPosition);
        return re == null ? null : re.getValue();
    }

    public ContainerPosition findDoubleChest(ContainerPosition containerPosition) {
        if (containerPosition.isDouble()) {
            return containerPosition;
        } else {
            BlockPos pos1 = containerPosition.getFirst().getPos();
            for (var re : MathUtils.HORIZONTALS) {
                BlockPos pos2 = pos1.offset(re);
                ContainerPosition optional = ContainerPosition.ofDouble(mc.world, pos1, pos2);
                if (screens.containsKey(optional)) {
                    return optional;
                }
            }
            return null;
        }
    }

    public boolean isShulkerBoxPlacedBySelf(BlockPos pos) {
        return mc.world.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity shulker
                && shulker instanceof MetadataHolder metadataHolder
                && !metadataHolder.isMetaEmpty()
                && metadataHolder.getMetadata().get(this, KEY_SHULKER_BOX_PLACED_BY_PLAYER) != null;
    }

    @ApiMethod
    public Entry getTrackedEnderChestEntry() {
        return currentEnderChestEntry;
    }

    private static final List<ItemStack> EMPTY_LIST = Collections.nCopies(27, ItemStack.EMPTY);

    @ApiMethod
    public Inventory getTrackedEnderChestInventory() {
        if (currentEnderChestEntry != null) {
            return currentEnderChestEntry.getInventory();
        } else {
            return new ImmutableListInventory(EMPTY_LIST);
        }
    }

    public static String KEY_INV_STORAGE = "slimefunhelper:chesthistory/inventory_content";

    public void onAddEntry(ContainerPosition containerPosition, BlockState state, Entry newEntry) {
        if (containerPosition.isDouble()) {
            removeEntry(ContainerPosition.ofPosition(containerPosition.getFirst()));
            removeEntry(ContainerPosition.ofPosition(containerPosition.getSecond()));
        }
        MutableEntry<Block, Entry> entry = screens.get(containerPosition);
        if (entry != null) {
            entry.key = state.getBlock();
            entry.value = newEntry;
        } else {
            // remove related single chests
            screens.put(containerPosition, new MutableEntry<>(state.getBlock(), newEntry));
        }
    }

    public void removeEntry(ContainerPosition containerPosition) {
        screens.remove(containerPosition);
        onRemoveEntry(containerPosition);
    }

    public void onRemoveEntry(ContainerPosition containerPosition) {
        screens.remove(containerPosition);
        var blockStorage = ServerStorage.getStorage()
                .getBlockStorage(
                        containerPosition.world(), containerPosition.getFirst().getPos(), false);
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
            currentEnderChestEntry = null;
            currentEntry = null;
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
                currentEntry.update(currentEntry.optionalScreen);
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
                Block chestType = entry.getValue().getKey();
                if (chestType == null || chestType == Blocks.AIR) {
                    continue;
                }
                if (!entry.getKey().isDouble()) {

                    Block block = mc.world
                            .getBlockState(entry.getKey().getFirst().getPos())
                            .getBlock();
                    if (block != chestType) {
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
        // todo: make it a render
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
                        if (entry.getContainerPosition().isPresent()) {
                            ContainerPosition containerPosition =
                                    entry.getContainerPosition().get();
                            Block chestType = entry.getChestType().orElse(Blocks.AIR);
                            screens.put(containerPosition, new MutableEntry<>(chestType, entry));
                        } else {
                            // virtual screen should not be saved
                            virtualScreens.add(entry);
                        }
                    }
                }
            }
        });
    }

    public void onSave(Event<ServerStorage.Meta> metaSave) {
        if (enablePersistent.get()) {
            ServerStorage.Meta meta = metaSave.context();
            RegistryWrapper.WrapperLookup wrapperLookup = metaSave.getArgs(1);
            for (var re : screens.entrySet()) {
                Entry entry = re.getValue().getValue();

                if (entry.dirty) {
                    entry.dirty = false;
                    RegistryKey<World> world = re.getKey().world();
                    var savePos = re.getKey().getFirst().getPos();
                    BlockStorage bs = meta.getBlockStorage(world, savePos, true);
                    bs.put(KEY_INV_STORAGE, entry, Entry.CODEC, wrapperLookup);
                    entry.getChestType().ifPresent(bs::setType);
                }
            }
        }
    }

    public static class Entry {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.list(InventoryUtils.STACK_WITH_SLOT_CODEC)
                                .fieldOf("contents")
                                .forGetter(Entry::toSlots),
                        Codec.INT.fieldOf("size").forGetter(Entry::getSize),
                        TextCodecs.CODEC.optionalFieldOf("title").forGetter(Entry::getTitle),
                        Registries.BLOCK
                                .getCodec()
                                .optionalFieldOf("chest-type")
                                .forGetter(Entry::getChestType),
                        ContainerPosition.CODEC.optionalFieldOf("container-pos").forGetter(Entry::getContainerPosition))
                .apply(instance, Entry::new));

        boolean dirty = false;

        @Getter
        Inventory inventory;

        @Getter
        int size;

        boolean doubleChest;

        @Getter
        HandledScreen<?> optionalScreen;

        @Getter
        Optional<Text> title;

        @Getter
        Optional<Block> chestType;

        @Getter
        Optional<ContainerPosition> containerPosition;

        public Entry(
                List<IndexEntry<ItemStack>> slots,
                int size,
                Optional<Text> title,
                Optional<Block> chestType,
                Optional<ContainerPosition> containerPosition) {
            inventory = new SimpleInventory(size);
            this.title = title;
            this.chestType = chestType;
            this.containerPosition = containerPosition;
            this.size = size;
            this.doubleChest =
                    containerPosition.isPresent() && containerPosition.get().isDouble();
            for (IndexEntry<ItemStack> slot : slots) {
                if (slot.index() >= 0 && slot.index() < size) {
                    inventory.setStack(slot.index(), slot.val());
                }
            }
        }

        public Entry(Inventory inventory) {
            this(List.of(), inventory.size(), Optional.empty(), Optional.empty(), Optional.empty());
            update(inventory);
        }

        public Entry(HandledScreen<?> handled) {
            this(InventoryUtils.getTopInventory(handled));
            this.title = Optional.ofNullable(handled.getTitle());
            if (handled instanceof TileInventory tile && !tile.isVirtual()) {
                Block blockType = tile.getBlockType();
                this.chestType = Optional.ofNullable(blockType);
            }
        }

        public Entry(HandledScreen<?> handled, ContainerPosition containerPosition) {
            this(handled);
            this.containerPosition = Optional.of(containerPosition);
        }

        public void update(Inventory inventory) {
            this.inventory = inventory;
            this.size = inventory.size();
            dirty = true;
        }

        // todo: update update update
        public void update(HandledScreen<?> handledScreen) {
            this.optionalScreen = handledScreen;
            var guessInventory = InventoryUtils.getTopInventory(handledScreen);
            update(guessInventory);
            this.title = Optional.ofNullable(handledScreen.getTitle());
            if (handledScreen instanceof TileInventory tile && !tile.isVirtual()) {
                this.chestType = Optional.ofNullable(tile.getBlockType());
            }
            dirty = true;
        }

        public List<IndexEntry<ItemStack>> toSlots() {
            List<IndexEntry<ItemStack>> slots = new ArrayList<>();
            for (var re = 0; re < inventory.size(); ++re) {
                var st = inventory.getStack(re);
                if (!st.isEmpty()) {
                    slots.add(new IndexEntry<>(re, st));
                }
            }
            return slots;
        }
    }
}
