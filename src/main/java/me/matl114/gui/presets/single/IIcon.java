package me.matl114.gui.presets.single;

import java.util.function.Function;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

public interface IIcon<T> {
    public static ItemStack DEFAULT_NULL_ICON = new ItemStack(Items.BARRIER);
    public static IIcon<?> EMPTY = ((x, y, context, registerValue) -> {
        context.drawItem(DEFAULT_NULL_ICON, x, y, 999, 0);
    });

    default void render(int startIndexX, int startIndexY, VDrawContext context, T registerValue) {
        if (registerValue == null) {
            context.drawItem(DEFAULT_NULL_ICON, startIndexX, startIndexY, 114514, 0);
        } else {
            renderNonnull(startIndexX, startIndexY, context, registerValue);
        }
    }

    public void renderNonnull(int width, int height, VDrawContext context, T registerValue);

    public static <T> IIcon<T> renderItem(Function<T, ItemStack> function) {
        return ((startIndexX, startIndexY, context, registerValue) -> {
            context.drawItem(function.apply(registerValue), startIndexX, startIndexY, 114514, 0);
        });
    }

    public static <T> IIcon<T> renderSprite(Function<T, ?> function) {
        return ((startIndexX, startIndexY, context, registerValue) -> {
            var re = function.apply(registerValue);
            if (re instanceof Identifier identifier) {
                context.drawGuiTexture(identifier, startIndexX, startIndexY, 16, 16);
            } else if (re instanceof Sprite sprite) {
                context.drawSprite(sprite, startIndexX, startIndexY, 0, 16, 16);
            }
        });
    }
}
