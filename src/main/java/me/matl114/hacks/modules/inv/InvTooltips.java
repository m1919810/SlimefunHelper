package me.matl114.hacks.modules.inv;

import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.MouseScrollAction;
import me.matl114.events.impl.TooltipsRender;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.*;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ItemStackUtils;
import me.matl114.versioned.api.VItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.visitor.NbtTextFormatter;
import net.minecraft.text.Text;

public class InvTooltips extends BaseModule {
    public final ModulePath invTooltips = makePath(Configs.INV_CONFIG, "item-editor.inv-tooltips");
    public final ModulePath nbtTooltips = invTooltips.add("nbt-tooltips");
    public final ModulePath tooltipsScroller = invTooltips.add("tooltips-scroller");

    public InvTooltips() {
        super("InvTooltips");
    }

    public final FlagRef enable = flagBuilder(nbtTooltips.add("enable")).build();

    public final KeyBindRef keyBind = hotkey(nbtTooltips.add("show-hotkey"))
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_ALT))
            .build();

    public final FlagRef scrollingTooltips =
            flagBuilder(tooltipsScroller.add("enable")).build();

    public final IntRef width = intBuilder(nbtTooltips.add("width"))
            .defaultValue(360)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final IntRef formatedWidth = intBuilder(nbtTooltips.add("format-indent"))
            .defaultValue(0)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    private int lastTooltipRenderTick = -1;
    private double tooltipScrollAmount;

    private static final int SCROLL_PIXELS_PER_NOTCH = 10;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getTooltipShow(), this::onTooltipsAppend);
        registerListener(RenderListener.getRenderTooltipEvent(), this::onTooltipRender);
        registerListener(Listener.getMouseScroll(), this::onTooltipScroll);
    }

    public void onTooltipsAppend(Event<List<Text>> renderEvent) {
        if (enable.get() && keyBind.get().isAllPressed()) {
            ItemStack stack = renderEvent.getArgs(0);
            renderEvent.context.addAll(getTooltipLines(stack));
        }
    }

    private void onTooltipScroll(Event<MouseScrollAction> event) {
        if (!isScrollingActive()) {
            tooltipScrollAmount = 0;
            return;
        }

        double vertical = event.context.vertical();
        if (vertical != 0) {
            tooltipScrollAmount -= vertical * SCROLL_PIXELS_PER_NOTCH;
            event.cancel();
        }
    }

    private void onTooltipRender(Event<TooltipsRender> event) {
        if (isScrollingActive()) {
            event.context.y += (int) tooltipScrollAmount;
        } else {
            tooltipScrollAmount = 0;
        }
        lastTooltipRenderTick = Tasks.getTick();
    }

    private boolean isScrollingActive() {
        long ticksSinceRender = (long) Tasks.getTick() - lastTooltipRenderTick;
        return scrollingTooltips.get() && lastTooltipRenderTick >= 0 && ticksSinceRender >= 0 && ticksSinceRender <= 1;
    }

    public List<Text> getTooltipLines(ItemStack stack) {
        NbtCompound nbtCompound = getSimplifiedNbt(stack);
        Text text = new NbtTextFormatter(" ".repeat(formatedWidth.get())).apply(nbtCompound);
        return ChatUtils.splitToMultiLineText(text, width.get());
    }

    public NbtCompound getSimplifiedNbt(ItemStack stack) {
        NbtCompound nbtCompound = VItem.getInstance().toNbt(stack, ItemStackUtils.registry());
        nbtCompound = (NbtCompound) nbtCompound.get("components");
        nbtCompound = nbtCompound == null ? new NbtCompound() : nbtCompound;
        nbtCompound = replaceMcKey(nbtCompound);
        return nbtCompound;
    }

    private <T extends NbtElement> T replaceMcKey(T nbt) {
        if (nbt instanceof NbtCompound cpd) {
            NbtCompound nbtCompound = new NbtCompound();
            for (String key : cpd.getKeys()) {
                NbtElement element = cpd.get(key);
                nbtCompound.put(replaceMcStr(key), replaceMcKey(element));
            }
            return (T) nbtCompound;
        } else if (nbt instanceof NbtList nbtList) {
            NbtList list = new NbtList();
            for (NbtElement element : nbtList) {
                list.add(replaceMcKey(element));
            }
            return (T) list;
        } else if (nbt instanceof NbtString nbtString) {
            String str = nbtString.value();
            return (T) NbtString.of(replaceMcStr(str));
        } else return nbt;
    }

    private String replaceMcStr(String key) {
        return key.startsWith("minecraft:") ? "mc:" + key.substring("minecraft:".length()) : key;
    }
}
