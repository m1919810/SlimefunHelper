package me.matl114.gui.other;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.annotation.Nullable;
import me.matl114.accessors.access.MerchantScreenAccess;
import me.matl114.gui.basic.*;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.modules.inv.FastCraft;
import me.matl114.managers.task.ToggleManager;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.ScreenUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

public class TradeInformationSubScreen extends SubScreenWidget {
    private static final int SLOT_WIDTH = 18;
    private static final int BUTTON_WIDTH = 18;
    private static final int BUTTON_HEIGHT = 8;
    private static final int TRADE_ICON_WIDTH = 10;
    private static final int TRADE_ICON_HEIGHT = 9;
    private static final Identifier TRADE_ARROW_OUT_OF_STOCK_TEXTURE =
            Identifier.ofVanilla("container/villager/out_of_stock");
    private static final Identifier TRADE_ARROW_TEXTURE_SPRITE = new Identifier("slimefunhelper", "gui/trade_arrow");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private MerchantScreen screen;

    public TradeInformationSubScreen(int x, int y, MerchantScreen screen) {
        super(
                x + 258 - (3 * (SLOT_WIDTH + 2) + TRADE_ICON_WIDTH + 2 + BUTTON_WIDTH),
                y + 60,
                3 * (SLOT_WIDTH + 2) + TRADE_ICON_WIDTH + 2 + BUTTON_WIDTH,
                20);
        this.screen = Preconditions.checkNotNull(screen);
        this.init();
    }

    @Nullable
    public TradeOffer getCurrentTrade() {
        MerchantScreenAccess access = MerchantScreenAccess.of(this.screen);
        int index = access.getSelectedIndex();
        TradeOfferList list = this.screen.getScreenHandler().getRecipes();
        if (index < 0 || index >= list.size()) {
            return null;
        } else {
            return list.get(index);
        }
    }

    private boolean active(ElementHandler ignored) {
        return getCurrentTrade() != null;
    }

    private static final Text LABEL_TRADE = Text.translatable("widget.fast-trade.trade");
    private static final List<Text> TOOLTIPS_TRADE = List.of(Text.translatable("widget.fast-trade.trade.tooltips"));
    private static final Text LABEL_DROP_CRAFT = Text.translatable("widget.fast-trade.toggle-drop");
    private static final List<Text> TOOLTIPS_DROPCRAFT =
            List.of(Text.translatable("widget.fast-trade.toggle-drop.tooltips"));

    protected void init() {

        ExecutableWidget.instance(1, 1, SLOT_WIDTH, SLOT_WIDTH)
                .setElementHandler(new SlotElement(
                                InventoryUtils.createReadOnlyOneItemInventory(() -> {
                                    var trade = getCurrentTrade();
                                    return trade == null ? ItemStack.EMPTY : trade.getDisplayedFirstBuyItem();
                                }),
                                0,
                                InvTasks.getRightClickOpenEditScreenCallback())
                        .withPresentCondition(this::active))
                .addToSub(this);
        ExecutableWidget.instance(SLOT_WIDTH + 2 + 1, 1, SLOT_WIDTH, SLOT_WIDTH)
                .setElementHandler(new SlotElement(
                                InventoryUtils.createReadOnlyOneItemInventory(() -> {
                                    var trade = getCurrentTrade();
                                    return trade == null ? ItemStack.EMPTY : (trade.getDisplayedSecondBuyItem());
                                }),
                                0,
                                InvTasks.getRightClickOpenEditScreenCallback())
                        .withPresentCondition(this::active))
                .addToSub(this);
        DisplayWidget.instance(2 * (SLOT_WIDTH + 2), 5, TRADE_ICON_WIDTH, TRADE_ICON_HEIGHT)
                .setRenderHandler(IconElement.statedGuiPredicate(
                                TRADE_ARROW_TEXTURE_SPRITE,
                                TRADE_ARROW_OUT_OF_STOCK_TEXTURE,
                                ButtonAction.empty(),
                                (el) -> {
                                    var trade = getCurrentTrade();
                                    return trade != null && !trade.isDisabled();
                                })
                        //                    .setShaderColor(Constants.SLOT_COLOR)
                        .withTooltips(TooltipHandler.of(() -> {
                            var trade = getCurrentTrade();
                            if (trade == null) return List.of();
                            var builder = ImmutableList.<Text>builder();
                            builder.add(Text.translatable("widget.fast-trade.trade-info.0")
                                    .formatted(Formatting.AQUA));
                            builder.add(Text.translatable("widget.fast-trade.trade-info.1")
                                    .formatted(Formatting.GREEN));
                            builder.add(Text.literal("------------------").formatted(Formatting.GREEN));
                            builder.add(Text.translatable(
                                    "widget.fast-trade.trade-info.max-trade", String.valueOf(trade.getMaxUses())));
                            builder.add(Text.translatable(
                                    "widget.fast-trade.trade-info.current-trade", String.valueOf(trade.getUses())));
                            builder.add(Text.translatable(
                                    "widget.fast-trade.trade-info.default-count",
                                    String.valueOf(trade.getFirstBuyItem().count())));
                            builder.add(Text.translatable(
                                    "widget.fast-trade.trade-info.price-multiplier",
                                    "%.2f".formatted(trade.getPriceMultiplier())));
                            builder.add(Text.translatable(
                                    "widget.fast-trade.trade-info.demand-bonus",
                                    String.valueOf(trade.getDemandBonus())));
                            builder.add(Text.translatable(
                                    "widget.fast-trade.trade-info.special-price",
                                    String.valueOf(trade.getSpecialPrice())));
                            return builder.build();
                        }))
                        .withPresentCondition(this::active))
                .addToSub(this);
        ExecutableWidget.instance(2 * (SLOT_WIDTH + 2) + TRADE_ICON_WIDTH + 1, 0, SLOT_WIDTH, SLOT_WIDTH)
                .setElementHandler(new SlotElement(
                                InventoryUtils.createReadOnlyOneItemInventory(() -> {
                                    var trade = getCurrentTrade();
                                    return trade == null ? ItemStack.EMPTY : (trade.getSellItem());
                                }),
                                0,
                                InvTasks.getRightClickOpenEditScreenCallback())
                        .withPresentCondition(this::active))
                .addToSub(this);
        ExecutableWidget.instance(3 * (SLOT_WIDTH + 2) + TRADE_ICON_WIDTH + 1, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .setElementHandler(new ButtonElement(TextProvider.of(LABEL_TRADE), ButtonAction.run(() -> {
                            if (ScreenUtils.hasShiftDown()) {
                                craft();
                            } else {
                                place();
                            }
                        }))
                        .withTooltips(TooltipHandler.of(TOOLTIPS_TRADE)))
                .addToSub(this);
        ExecutableWidget.instance(
                        3 * (SLOT_WIDTH + 2) + TRADE_ICON_WIDTH + 1, 4 + BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT)
                .setElementHandler(new ButtonElement(
                                TextProvider.of(LABEL_DROP_CRAFT),
                                ButtonAction.run(ToggleManager.wrapFlagAsToggle(
                                        "fast-craft.drop-craft", InvTasks.getFastCraft().dropCraft)))
                        .withTooltips(TooltipHandler.of(TOOLTIPS_DROPCRAFT)))
                .addToSub(this);
        DisplayWidget.instance(10, 0, 3 * (SLOT_WIDTH + 2) + TRADE_ICON_WIDTH - 20, 20)
                .setRenderHandler(LabelElement.instance(
                                Text.translatable("widget.fast-trade.no-select").formatted(Formatting.RED))
                        .withPresentCondition((v) -> !this.active(v)))
                .addToSub(this);
    }

    private void place() {
        if (mc.player != null) {
            var access = MerchantScreenAccess.of(this.screen);
            access.setSelectedIndex(access.getSelectedIndex());
        }
    }

    private void craft() {
        if (mc.player != null) {
            place();
            TradeOffer offer = getCurrentTrade();
            if (offer != null) {
                ItemStack output = offer.getSellItem();
                if (!output.isEmpty()) {
                    int maxCraft = (int) Math.ceil((float) output.getMaxCount() / (float) output.getCount());
                    maxCraft = Math.min(maxCraft, offer.getMaxUses() - offer.getUses());
                    InvTasks.getFastCraft().craftAtSlotIndex(this.screen, maxCraft, 2);
                }
            }
        }
    }
}
