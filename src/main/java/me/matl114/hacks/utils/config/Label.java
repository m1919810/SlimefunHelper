package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.Optional;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.ChatUtils;
import net.minecraft.text.Text;

public record Label(String label) implements NBTParsable<Label> {
    public static NBTType<Label> TYPE = new NBTType<>(
            "label",
            Codec.STRING.xmap(Label::new, Label::label),
            (factory, w, x, y, dx, dy) -> {
                String label = w.get().label();
                return factory.createTitle(
                        el -> Text.translatableWithFallback(label, label),
                        TooltipHandler.of(ChatUtils.parseTooltipsTranslation(label + ".tooltips", "")),
                        x,
                        y,
                        dx,
                        dy);
            },
            new Label(""));

    @Override
    public NBTType<Label> type() {
        return TYPE;
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return Objects.equals(type, this);
    }

    @Override
    public <W> Optional<Label> tryTypeConvert(Ref<W> ref) {
        if (ref.getValue() instanceof Label label) {
            return Optional.of(new Label(this.label()));
        }
        return Optional.empty();
    }
}
