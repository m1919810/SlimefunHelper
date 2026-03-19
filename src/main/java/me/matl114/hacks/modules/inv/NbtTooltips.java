package me.matl114.hacks.modules.inv;

import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.*;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.versioned.api.VItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.visitor.NbtTextFormatter;
import net.minecraft.text.Text;

public class NbtTooltips extends BaseModule {
    // todo: nbt tooltips
    public static final String[] NBT_TOOLTIPS_ENABLE = new String[] {"item-editor", "nbt-tooltips", "enable"};
    public static final String[] NBT_TOOLTIPS_WIDTH = new String[] {"item-editor", "nbt-tooltips", "width"};
    public static final String[] NBT_TOOLTIPS_FORMATTED = new String[] {"item-editor", "nbt-tooltips", "format-indent"};
    public static final String[] NBT_TOOLTIPS_SHOW_HOTKEY = new String[] {"item-editor", "nbt-tooltips", "show-hotkey"};

    public NbtTooltips() {}

    public final FlagRef enable =
            flagBuilder(Configs.INV_CONFIG, NBT_TOOLTIPS_ENABLE).build();

    public final KeyBindRef keyBind = hotkey(Configs.INV_CONFIG, NBT_TOOLTIPS_SHOW_HOTKEY)
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_ALT))
            .registerHotkey(SimpleHotKey.InputHandler.EMPTY)
            .build();

    public final IHotKey hotkey = getHotkey(NBT_TOOLTIPS_SHOW_HOTKEY);
    public final IntRef width = builder(Configs.INV_CONFIG, NBT_TOOLTIPS_WIDTH, IntRef.TYPE)
            .defaultValue(360)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final IntRef formatedWidth = builder(Configs.INV_CONFIG, NBT_TOOLTIPS_FORMATTED, IntRef.TYPE)
            .defaultValue(0)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(RenderListener.getTooltipShow(), this::onTooltipsAppend);
    }

    public void onTooltipsAppend(Event<List<Text>> renderEvent) {
        if (enable.get() && ScreenUtils.hasKeyPressed(hotkey.getTriggeredKey())) {
            ItemStack stack = renderEvent.getArgs(0);
            renderEvent.context.addAll(getTooltipLines(stack));
        }
    }

    public List<Text> getTooltipLines(ItemStack stack) {
        NbtCompound nbtCompound = getSimplifiedNbt(stack);
        Text text = new NbtTextFormatter(" ".repeat(formatedWidth.get())).apply(nbtCompound);
        String string = ChatUtils.textToLegacyString(text);
        return ChatUtils.multiLineTextFromLegacyString(string, width.get());
    }

    public NbtCompound getSimplifiedNbt(ItemStack stack) {
        NbtCompound nbtCompound = VItem.getInstance().toNbt(stack);
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
