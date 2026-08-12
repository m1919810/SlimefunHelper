package me.matl114.hacks.modules.survival;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.render.RenderElements;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;

public class VillagerEsp extends BaseModule {
    public static VillagerEsp INSTANCE;

    public final ModulePath renderUtils = makePath(Configs.SURVIVAL_CONFIG, "render-utils");
    public final ModulePath villagerEsp = renderUtils.add("villager-esp");

    public VillagerEsp() {
        super("VillagerEsp");
        INSTANCE = this;
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(villagerEsp.addEnable()).build();

    public final KeyBindRef hotkey = toggleHotkey(villagerEsp.addHotkey(), new MultiKeyBind(), villagerEsp.addEnable())
            .build();

    public final DoubleRef textScale = doubleBuilder(villagerEsp.add("text-scale"))
            .defaultValue(0.75D)
            .validator(Configs.doubleRange(0.1D, 4.0D))
            .build();

    public final NBTRef<WrapColor> color = builder(villagerEsp.add("color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.AQUA))
            .build();

    private final RenderCollector<RenderElements.Text> textCollector = RenderCollectors.createTextCollector();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        textCollector.clear();
    }

    public void onTick(Event<ClientPlayerEntity> event) {
        textCollector.clear();
        if (checkNull() || !enable.get() || WorldManager.INSTANCE == null) {
            return;
        }

        int textColor = color.get().withAlpha(255);
        float scale = (float) textScale.get();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof VillagerEntity villager && isTrackedLibrarian(villager)) {
                Text displayText = buildTradeText(villager);
                if (displayText == null) {
                    continue;
                }
                Vec3d textPos = villager.getPos().add(0.0D, villager.getHeight(), 0.0D);
                textCollector.submit(new RenderElements.Text(displayText, textPos, scale), textColor);
            }
        }
    }

    public void onRender3D(Event<MatrixStack> event) {
        if (!enable.get()) {
            return;
        }
        RenderUtils.startDrawVirtual(event.context());
        try {
            textCollector.render3D(event.context());
        } finally {
            RenderUtils.stopDrawVirtual(event.context());
        }
    }

    private boolean isTrackedLibrarian(VillagerEntity villager) {
        var profession = villager.getVillagerData().profession().getKey().orElse(null);
        return Objects.equals(profession, VillagerProfession.LIBRARIAN)
                && WorldManager.INSTANCE.getVillagerTradeList(villager) != null;
    }

    private Text buildTradeText(VillagerEntity villager) {
        List<WorldManager.TradeRecord> trades = WorldManager.INSTANCE.getVillagerTradeList(villager);
        if (trades == null || trades.isEmpty()) {
            return null;
        }
        List<Text> lines = new ArrayList<>();
        for (WorldManager.TradeRecord trade : trades) {
            Text tradeText = buildEnchantmentTradeText(trade);
            if (tradeText != null) {
                lines.add(tradeText);
            }
        }
        if (lines.isEmpty()) {
            return null;
        }
        MutableText result = Text.empty();
        for (int i = 0; i < lines.size(); i++) {
            result = result.append(Text.literal("\n")).append(lines.get(i));
        }
        return result;
    }

    private Text buildEnchantmentTradeText(WorldManager.TradeRecord trade) {
        ItemStack result = trade.result();
        if (!result.isOf(Items.ENCHANTED_BOOK) || !result.contains(DataComponentTypes.STORED_ENCHANTMENTS)) {
            return null;
        }
        var firstEnchantment = result.get(DataComponentTypes.STORED_ENCHANTMENTS).getEnchantmentEntries().stream()
                .findFirst()
                .orElse(null);
        if (firstEnchantment == null) {
            return null;
        }
        RegistryEntry<Enchantment> enchantment = firstEnchantment.getKey();
        int level = firstEnchantment.getIntValue();
        int price = Math.max(trade.buy1().getCount(), trade.buy2().getCount());
        return enchantment
                .value()
                .description()
                .copy()
                .append(Text.literal(String.valueOf(level)))
                .append(Text.literal(" " + price));
    }
}
