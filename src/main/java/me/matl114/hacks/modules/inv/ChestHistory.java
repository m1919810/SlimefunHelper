package me.matl114.hacks.modules.inv;

import com.mojang.datafixers.util.Pair;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import me.matl114.accessors.access.TileInventoryScreen;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.gui.invcache.InventorySelectScreen;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.StringRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.RenderUtils;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ChestHistory extends BaseModule {
    public static final String[] OPEN_INV_CACHE = new String[] {"hotkeys", "open-inv-cache"};
    public static final String[] INV_CACHE_IGNORE = new String[] {"inv-cache", "ignore-container-with-title"};
    public static final String[] INV_CACHE_SHOW_TITLE = new String[] {"inv-cache", "show-title"};

    private final int MAX_INV_CACHE_SIZE = 256;
    private final int AUTO_REFRESH_RANGE = 64;
    private final LinkedHashMap<ContainerPosition, MutableEntry<BlockState, HandledScreen<?>>> screens =
            new LinkedHashMap<>();
    private final List<HandledScreen<?>> virtualScreens = new ArrayList<>();

    public ChestHistory() {}

    public final KeyBindRef keyBind = hotkey(OPEN_INV_CACHE)
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_J))
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::openInventoryCacheScreen))
            .build();

    public final StringRef ignoreList = builder(Configs.INV_CONFIG, INV_CACHE_IGNORE, StringRef.TYPE)
            .defaultValue("^(Slimefun 指南.*|菜单)$")
            .validator(Configs.REGEX_VALIDATOR)
            .build();

    public final FlagRef enableTitle = flagBuilder(Configs.INV_CONFIG, INV_CACHE_SHOW_TITLE)
            .defaultValue(false)
            .build();

    public List<HandledScreen<?>> getCachedInventories() {
        return (List) Stream.concat(screens.values().stream().map(MutableEntry::getValue), virtualScreens.stream())
                .toList();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostOpenHandledScreen(), this::onOpenHandledScreen);
        registerListener(Listener.getGameJoinPoint(), this::onServerJoin);
        registerListener(Listener.getGameTick(), this::onTick);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
    }

    public void onOpenHandledScreen(Event<HandledScreen<?>> screenEvent) {
        HandledScreen<?> screen = screenEvent.context();
        if (screen instanceof CreativeInventoryScreen creativeInventoryScreen) return;
        Pair<ClientWorld, BlockPos> data;
        String title = screen.getTitle().getString();
        if (title != null) {
            title = title.replaceAll("§.", "");
            // ignore certain screen
            if (Pattern.matches(ignoreList.get(), title)) {
                return;
            }
        }
        if (screen instanceof TileInventoryScreen tile && !tile.isVirtual()) {
            ContainerPosition containerPosition = tile.getContainerPosition();
            BlockPos pos = tile.getPos();
            var state = mc.world.getBlockState(pos);
            if (containerPosition.isDouble()) {
                screens.remove(ContainerPosition.ofPosition(containerPosition.getFirst()));
                screens.remove(ContainerPosition.ofPosition(containerPosition.getSecond()));
            }
            MutableEntry<BlockState, HandledScreen<?>> entry = screens.get(containerPosition);
            if (entry != null) {
                entry.key = state;
                entry.value = screen;
            } else {
                // remove related single chests
                screens.put(containerPosition, new MutableEntry<>(state, screen));
                if (screens.size() > MAX_INV_CACHE_SIZE) {
                    screens.entrySet().iterator().remove();
                }
            }

        } else {
            virtualScreens.add(screen);
            if (virtualScreens.size() > MAX_INV_CACHE_SIZE) {
                virtualScreens.remove(0);
            }
        }
    }

    public boolean openInventoryCacheScreen() {
        if (mc.player == null || mc.world == null) return false;
        ScreenAccess.of(new InventorySelectScreen(this::getCachedInventories)).openFromCurrent();
        return true;
    }

    private static String lastServerName = null;

    private void onServerJoin(Event<ClientPlayerEntity> v) {
        String serverName = CommonUtils.getServerName();
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
        interval = 0;
        BlockLocation location = BlockLocation.of(event.context());
        var iterator = screens.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().isInRenderRange(location, AUTO_REFRESH_RANGE)) {
                if (!entry.getKey().isDouble()) {
                    Block block = mc.world
                            .getBlockState(entry.getKey().getFirst().getPos())
                            .getBlock();
                    if (block != entry.getValue().getKey().getBlock()) {
                        iterator.remove();
                        continue;
                    }
                } else if (entry.getKey() instanceof ContainerPosition d) {
                    BlockPos pos = d.getFirst().getPos();
                    BlockState block = mc.world.getBlockState(pos);
                    if (!(block.getBlock() instanceof ChestBlock)) {
                        iterator.remove();
                        continue;
                    }
                    // not a bigchest
                    if (block.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
                        iterator.remove();
                        continue;
                    }
                    Direction direction = ChestBlock.getFacing(block);
                    BlockPos anotherBlock = pos.offset(direction);
                    BlockPos twoPos = d.getSecond().getPos();
                    // direction change
                    if (!twoPos.equals(anotherBlock)) {
                        iterator.remove();
                        continue;
                    }
                    // not a chest
                    Block block2 = mc.world.getBlockState(twoPos).getBlock();
                    if (!(block2 instanceof ChestBlock)) {
                        iterator.remove();
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
                try{
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
                            Vec3d delta = renderPos.subtract(cameraPos);
                            stack.push();
                            stack.translate(delta.x, delta.y + 0.25, delta.z);
                            // title的高度是9 我们希望这个9在 0.75 ~ 1.0之间
                            // 我希望他看向我
                            stack.multiply(RenderUtils.getBillboardRotation(DisplayEntity.BillboardMode.CENTER, 0, 0));
                            stack.scale(0.03125F, 0.03125F, 1);
                            VRender.getInstance()
                                .drawTextCameraCoord(
                                    entry.getValue().getValue().getTitle().asOrderedText(),
                                    stack,
                                    Vec3d.ZERO,
                                    POSITION_FLAG,
                                    Color.WHITE,
                                    VRender.DEFAULT_TEXT);

                            stack.pop();
                        }
                    }
                }finally {
                    RenderUtils.stopDrawVirtual(stack);
                }
            }
        }
    }
}
