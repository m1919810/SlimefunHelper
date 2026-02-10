package me.matl114.gui.other;

import java.util.List;
import lombok.Getter;
import me.matl114.utils.ScreenUtils;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class BeaconEffectSelectButton extends PressableWidget {
    public static final List<RegistryEntry<StatusEffect>> EFFECTS_BEACON = List.of(
            StatusEffects.SPEED,
            StatusEffects.HASTE,
            StatusEffects.RESISTANCE,
            StatusEffects.JUMP_BOOST,
            StatusEffects.STRENGTH,
            StatusEffects.REGENERATION);
    private static final int SIZE = EFFECTS_BEACON.size();
    private static Identifier NO_PATH = new Identifier("minecraft", "container/beacon/cancel");
    static final Identifier BUTTON_HIGHLIGHTED_TEXTURE =
            new Identifier("minecraft", "container/beacon/button_highlighted");
    static final Identifier BUTTON_TEXTURE = new Identifier("minecraft", "container/beacon/button");
    int currentIndex = 0;

    @Getter
    RegistryEntry<StatusEffect> currentEffect;

    Sprite currentSprite;

    private void updateCurrentEffect() {
        currentIndex %= (SIZE + 1);
        if (currentIndex == 0) {
            this.currentEffect = null;
            this.currentSprite = null;
        } else {
            this.currentEffect = EFFECTS_BEACON.get(currentIndex - 1);
            this.currentSprite =
                    MinecraftClient.getInstance().getStatusEffectSpriteManager().getSprite(this.currentEffect);
        }
        setTooltip(Tooltip.of(getNarrationMessage()));
    }

    public BeaconEffectSelectButton(int i, int j, int k, int l, Text text) {
        super(i, j, k, l, text);
        updateCurrentEffect();
    }

    @Override
    public void onPress() {
        currentIndex = currentIndex + EFFECTS_BEACON.size() + 1 + (ScreenUtils.hasShiftDown() ? -1 : 1);
        updateCurrentEffect();
    }

    public void renderWidget(VDrawContext context, int mouseX, int mouseY, float delta) {
        Identifier identifier;
        if (this.isSelected()) {
            identifier = BUTTON_HIGHLIGHTED_TEXTURE;
        } else {
            identifier = BUTTON_TEXTURE;
        }

        context.drawGuiTexture(identifier, this.getX(), this.getY(), this.width, this.height);
        this.renderExtra(context);
    }

    protected void renderExtra(VDrawContext context) {
        if (this.currentSprite != null) {
            context.drawSprite(this.getX() + 2, this.getY() + 2, 0, 18, 18, this.currentSprite);
        } else {
            context.drawGuiTexture(NO_PATH, this.getX() + 2, this.getY() + 2, 18, 18);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    @Override
    protected MutableText getNarrationMessage() {
        return getMessage()
                .copy()
                .append(
                        this.currentEffect == null
                                ? Text.literal("无选中")
                                : Text.translatable(this.currentEffect.value().getTranslationKey()));
    }
}
