package me.matl114.hacks.modules.mine;

import java.util.*;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MineTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.*;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.StringRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.RegistryUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class MineArua extends BaseModule {
    public MineArua() {
        bindFlag(enable);
    }

    public static final String[] MINEARUA_WHILELIST = {"mine-arua", "block-whitelist"};
    public static final String[] MINEARUA = {"mine-arua", "mine-arua"};
    public static final String[] MINEARUA_HOTKEY = {"mine-arua", "mine-arua-hotkey"};
    private BlockPos cachePosition;
    private int lastRefreshTick;
    public Set<Block> whiteList = new HashSet<>();

    public void parseWhiteList(String str) {
        whiteList = RegistryUtils.parseWhiteList(Registries.BLOCK, str);
    }

    public FlagRef enable = flagBuilder(Configs.MINE_CONFIG, MINEARUA).build();

    public KeyBindRef keyBind = toggleHotkey(
                    Configs.MINE_CONFIG,
                    MINEARUA_HOTKEY,
                    new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_N),
                    MINEARUA)
            .build();

    public StringRef whiteListRegex = builder(Configs.MINE_CONFIG, String.class)
            .path(MINEARUA_WHILELIST)
            .defaultValue("^(.*bed)$")
            .validator(Configs.REGEX_VALIDATOR)
            .updateListener(this::parseWhiteList)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getMineBlockAction(), this::onMineBlockAction);
        // todo handle doAttackAction redirect
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        this.cachePosition = null;
    }

    public void onMineBlockAction(Event<HitResult> event) {
        if (mc.player != null && isActive()) {
            BlockPos pos = refreshMineAruaTarget();
            if (pos != this.cachePosition) {
                if (pos != null) {
                    Debug.chat(Text.literal("[Mine Arua] Redirect mine target ")
                            .append(ChatUtils.getDisplayedLocation(Vec3d.of(pos)))
                            .formatted(Formatting.GREEN));
                }
                this.cachePosition = pos;
                lastRefreshTick = Tasks.getTick();
            }

            if (this.cachePosition != null) {
                Direction dir = Direction.getFacing(
                                this.cachePosition.toCenterPos().subtract(mc.player.getEyePos()))
                        .getOpposite();
                HitResult hitResult = new BlockHitResult(Vec3d.of(this.cachePosition), dir, this.cachePosition, false);
                event.context(hitResult);
            }
        }
    }

    // where to place it
    // todo: minearua conflict with optimize

    private boolean isMineAruaTarget(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state != null && !state.isAir() && !state.isLiquid()) {
            Block block = state.getBlock();
            if (block.getHardness() >= 0.0F && whiteList.contains(block)) {
                return true;
            }
        }
        return false;
    }

    private BlockPos refreshMineAruaTarget() {
        if (mc.player != null && mc.world != null) {
            // every time check if current cache is here
            Vec3d eyepos = mc.player.getEyePos();
            if (this.cachePosition != null
                    && isMineAruaTarget(mc.world, this.cachePosition)
                    && !MineTasks.distanceOutOfReach(this.cachePosition, eyepos)) {
                return this.cachePosition;
            }
            // refresh only 4 ticks once
            if (Tasks.getTick() >= lastRefreshTick + 4) {
                BlockPos currentBlockPos = mc.player.getBlockPos();
                for (var vec : MineTasks.getMineExtra().getBlocksAround()) {
                    BlockPos pos = currentBlockPos.add(vec);
                    if (isMineAruaTarget(mc.world, pos) && !MineTasks.distanceOutOfReach(pos, eyepos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
