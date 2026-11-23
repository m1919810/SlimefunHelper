package me.matl114.events.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.With;
import me.matl114.events.RenderListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

public interface GuiModel {
    public static final GuiModel EMPTY = new BlankGuiModel();

    @Nonnull
    public static GuiModel packOrder(List<GuiModel> guiModelList) {
        if (guiModelList == null || guiModelList.isEmpty()) {
            return EMPTY;
        }
        guiModelList =
                guiModelList.stream().filter(s -> !(s instanceof BlankGuiModel)).toList();
        if (guiModelList.isEmpty()) {
            return EMPTY;
        }
        if (guiModelList.size() == 1) {
            return guiModelList.get(0);
        }

        // todo
        return new PackingModel(guiModelList);
    }

    public static GuiModel of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EMPTY;
        }
        return new ItemStackModel(stack);
    }

    public static GuiModel of(BakedModel model) {
        if (model == null) return EMPTY;
        return new ItemGuiModel(new ItemStack(Items.BARRIER), model);
    }

    public static GuiModel of(Identifier id) {
        if (id == null) return EMPTY;
        return of(RenderListener.getCustomModelOf(id));
    }

    public static GuiModel ofSprite(Sprite sprite) {
        return sprite == null ? EMPTY : new SpriteGuiModel(() -> sprite);
    }

    default void render(
            ItemRenderer itemRenderer,
            ItemDisplayContext renderMode,
            boolean leftHanded,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay) {
        Entry entry = updateAndSubmit(itemRenderer);
        if (entry != null) {
            renderEntry(entry, itemRenderer, renderMode, leftHanded, matrices, vertexConsumers, light, overlay);
        }
    }

    public static void renderEntry(
            Entry entry,
            ItemRenderer itemRenderer,
            ItemDisplayContext renderMode,
            boolean leftHanded,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay) {
        boolean transformed = entry.stackTransformer() != null;
        if (transformed) {
            matrices.push();
            entry.stackTransformer().apply(matrices);
        }
        if (entry.sprite() != null) {
            renderSprite(entry.sprite(), matrices, vertexConsumers, light, overlay);
        } else {
            itemRenderer.renderItem(
                    entry.stack(), renderMode, leftHanded, matrices, vertexConsumers, light, overlay, entry.state());
        }
        if (transformed) {
            matrices.pop();
        }
    }

    // 1.21.1 has no ItemRenderState layers: draw a unit quad in item-model space, centered like ItemRenderer
    private static void renderSprite(
            Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        int[] vertices = new int[32];
        float[] xs = {0.0F, 0.0F, 1.0F, 1.0F};
        float[] ys = {1.0F, 0.0F, 0.0F, 1.0F};
        float[] us = {sprite.getMinU(), sprite.getMinU(), sprite.getMaxU(), sprite.getMaxU()};
        float[] vs = {sprite.getMinV(), sprite.getMaxV(), sprite.getMaxV(), sprite.getMinV()};
        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            vertices[offset] = Float.floatToRawIntBits(xs[i]);
            vertices[offset + 1] = Float.floatToRawIntBits(ys[i]);
            vertices[offset + 2] = Float.floatToRawIntBits(8.5F / 16.0F);
            vertices[offset + 3] = -1;
            vertices[offset + 4] = Float.floatToRawIntBits(us[i]);
            vertices[offset + 5] = Float.floatToRawIntBits(vs[i]);
        }
        BakedQuad quad = new BakedQuad(vertices, -1, Direction.SOUTH, sprite, false);
        matrices.push();
        matrices.translate(-0.5F, -0.5F, -0.5F);
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(sprite.getAtlasId()));
        buffer.quad(matrices.peek(), quad, 1.0F, 1.0F, 1.0F, 1.0F, light, overlay);
        matrices.pop();
    }

    // do not call
    public Entry updateAndSubmit(ItemRenderer itemRenderer);

    public static class BlankGuiModel implements GuiModel {

        @Override
        public Entry updateAndSubmit(ItemRenderer itemRenderer) {
            return null;
        }
    }

    @AllArgsConstructor
    public static class ItemStackModel implements GuiModel {
        ItemStack itemStack;

        @Override
        public Entry updateAndSubmit(ItemRenderer itemRenderer) {
            if (itemStack == null || itemStack.isEmpty()) return null;
            return new Entry(
                    null,
                    itemStack,
                    itemRenderer.getModel(
                            itemStack, MinecraftClient.getInstance().world, MinecraftClient.getInstance().player, 0),
                    null);
        }
    }

    @AllArgsConstructor
    public static class ItemGuiModel implements GuiModel {
        ItemStack itemStack;
        BakedModel itemModel;

        @Override
        public Entry updateAndSubmit(ItemRenderer itemRenderer) {
            return new Entry(null, itemStack, itemModel, null);
        }
    }

    @AllArgsConstructor
    public static class SpriteGuiModel implements GuiModel {
        Supplier<Sprite> spriteSupplier;

        @Override
        public Entry updateAndSubmit(ItemRenderer itemRenderer) {
            Sprite sprite = spriteSupplier.get();
            return sprite == null ? null : new Entry(null, ItemStack.EMPTY, null, sprite);
        }
    }

    @AllArgsConstructor
    public static class PackingModel implements GuiModel {
        List<GuiModel> guiModelList;

        @Override
        public void render(
                ItemRenderer itemRenderer,
                ItemDisplayContext renderMode,
                boolean leftHanded,
                MatrixStack matrices,
                VertexConsumerProvider vertexConsumers,
                int light,
                int overlay) {
            List<Entry> entries = updateAndSubmitList(itemRenderer);
            if (entries != null && !entries.isEmpty()) {
                // arrange positions
                List<Entry> arranged = arrangeEntries(entries);
                for (Entry entry : arranged) {
                    renderEntry(entry, itemRenderer, renderMode, leftHanded, matrices, vertexConsumers, light, overlay);
                }
            }
        }

        public List<Entry> updateAndSubmitList(ItemRenderer renderer) {
            return guiModelList.stream()
                    .flatMap(s -> {
                        if (s instanceof PackingModel pack) {
                            return pack.updateAndSubmitList(renderer).stream();
                        } else {
                            return Stream.of(s.updateAndSubmit(renderer));
                        }
                    })
                    .filter(entry -> entry != null)
                    .toList();
        }

        private List<Entry> arrangeEntries(List<Entry> originalEntries) {
            List<Entry> result = new ArrayList<>();
            int count = Math.min(originalEntries.size(), 4);
            // 固定偏移量 (dx, dy) 对应四个位置
            float[][] offsets = {
                {0, 0}, // 第0个: 右下角
                {-1, 0}, // 第1个: 左下角
                {0, -1}, // 第2个: 右上角
                {-1, -1} // 第3个: 左上角
            };
            for (int i = 0; i < count; i++) {
                Entry entry = originalEntries.get(i);
                float dx = offsets[i][0];
                float dy = -offsets[i][1];
                // 平移变换器（注意：GUI中Y轴向下为正，向上为负，所以dy为负时向上移动）
                UnaryOperator<MatrixStack> translator = matrices -> {
                    matrices.translate(dx, dy, 0);
                    return matrices;
                };
                UnaryOperator<MatrixStack> combined = entry.stackTransformer() != null
                        ? matrices -> entry.stackTransformer().apply(translator.apply(matrices))
                        : translator;
                result.add(entry.withStackTransformer(combined));
            }
            return result;
        }

        @Override
        public Entry updateAndSubmit(ItemRenderer itemRenderer) {
            throw new UnsupportedOperationException("DO NOT CALL");
        }
    }

    @With
    public static record Entry(
            UnaryOperator<MatrixStack> stackTransformer, ItemStack stack, BakedModel state, Sprite sprite) {}
}
