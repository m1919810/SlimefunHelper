package me.matl114.hacks.modules.inv;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import me.matl114.accessors.access.TileInventoryScreen;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.invcache.InventorySelectScreen;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.collections.MutableEntry;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

public class ChestHistory extends BaseModule {
    public static final String[] OPEN_INV_CACHE = new String[] {"hotkeys", "open-inv-cache"};

    private final int MAX_INV_CACHE_SIZE = 256;
    private int startCursor = 0;
    private int endCursor = 0;
    private final MutableEntry<Pair<ClientWorld, BlockPos>, HandledScreen<?>>[] caches =
            new MutableEntry[MAX_INV_CACHE_SIZE];

    public ChestHistory() {}

    public final KeyBindRef keyBind = hotkey(OPEN_INV_CACHE)
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_J))
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::openInventoryCacheScreen))
            .build();

    public List<HandledScreen<?>> getCachedInventories() {
        return IntStream.range(startCursor, (endCursor < startCursor) ? (endCursor + MAX_INV_CACHE_SIZE) : endCursor)
                .map(i -> i % MAX_INV_CACHE_SIZE)
                .mapToObj(i -> caches[i])
                .map(i -> i.value)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private int nextCacheInt(int i) {
        ++i;
        if (i >= MAX_INV_CACHE_SIZE) {
            i = 0;
        }
        return i;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostOpenHandledScreen(), this::onOpenHandledScreen);
        registerListener(Listener.getGameJoinPoint(), this::onServerJoin);
    }

    public void onOpenHandledScreen(Event<HandledScreen<?>> screenEvent) {
        HandledScreen<?> screen = screenEvent.context();
        if (screen instanceof CreativeInventoryScreen creativeInventoryScreen) return;
        Pair<ClientWorld, BlockPos> data;
        if (screen instanceof TileInventoryScreen tile && !tile.isVirtual()) {
            BlockPos pos = tile.getPos();
            ClientWorld world = tile.getWorld();
            data = new Pair<>(world, pos);
            for (int i = startCursor; i != endCursor; i = nextCacheInt(i)) {
                MutableEntry<Pair<ClientWorld, BlockPos>, HandledScreen<?>> value = caches[i];
                if (value.key != null
                        && Objects.equals(value.key.getSecond(), pos)
                        && WorldUtils.areWorldEquals(value.key.getFirst(), world)) {
                    value.value = screen;
                    return;
                }
            }
        } else {
            data = null;
        }
        // 追加到队列末尾
        int index = endCursor;
        endCursor = nextCacheInt(endCursor);
        // 如果队列已满，则从队列头驱逐一个元素
        if (endCursor == startCursor) {
            startCursor = nextCacheInt(startCursor);
        }
        caches[index] = new MutableEntry<>(data, screen);
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
            startCursor = endCursor = 0;
            Arrays.fill(caches, null);
        }
        lastServerName = serverName;
    }
}
