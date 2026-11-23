package me.matl114.events.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.With;
import me.matl114.accessors.events.ItemRenderStateAccess;
import me.matl114.events.RenderListener;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

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

    public static GuiModel of(ItemModel model) {
        if (model == null) return EMPTY;
        return new ItemGuiModel(model);
    }

    public static GuiModel of(Identifier id) {
        if (id == null) return EMPTY;
        return of(RenderListener.getCustomModelOf(id));
    }

    public static GuiModel ofSprite(Identifier spriteId) {
        return spriteId == null
                ? EMPTY
                : new SpriteGuiModel(
                        () -> MinecraftClient.getInstance().getGuiAtlasManager().getSprite(spriteId));
    }

    public static GuiModel ofSprite(Sprite sprite) {
        return sprite == null ? EMPTY : new SpriteGuiModel(() -> sprite);
    }

    default void update(
            ItemRenderState renderState,
            ItemStack stack,
            ItemModelManager resolver,
            ItemDisplayContext displayContext,
            ClientWorld world,
            LivingEntity liv,
            int seed) {
        Entry entry = updateAndSubmit(renderState, stack, resolver, displayContext, world, liv, seed);
        if (entry != null) {
            var lst = ItemRenderStateAccess.of(renderState).getAttachedRenderState();
            lst.clear();
            lst.add(entry);
        } else {
            ItemRenderStateAccess.of(renderState).clearAttachedRenderState();
        }
    }

    // do not call
    public Entry updateAndSubmit(
            ItemRenderState renderState,
            ItemStack stack,
            ItemModelManager resolver,
            ItemDisplayContext displayContext,
            ClientWorld world,
            LivingEntity livingEntity,
            int seed);

    public static class BlankGuiModel implements GuiModel {
        @Override
        public Entry updateAndSubmit(
                ItemRenderState renderState,
                ItemStack stack,
                ItemModelManager resolver,
                ItemDisplayContext displayContext,
                ClientWorld world,
                LivingEntity livingEntity,
                int seed) {
            return null;
        }
    }

    @AllArgsConstructor
    public static class ItemStackModel implements GuiModel {
        ItemStack itemStack;

        @Override
        public Entry updateAndSubmit(
                ItemRenderState renderState,
                ItemStack stack,
                ItemModelManager resolver,
                ItemDisplayContext displayContext,
                ClientWorld world,
                LivingEntity livingEntity,
                int seed) {
            if (itemStack != null && !itemStack.isEmpty()) {
                ItemRenderState state2 = new ItemRenderState();

                // init attached info
                resolver.update(state2, itemStack, displayContext, world, livingEntity, seed);
                return new Entry(null, state2);
            }
            return null;
        }
    }

    @AllArgsConstructor
    public static class ItemGuiModel implements GuiModel {
        ItemModel itemModel;

        @Override
        public Entry updateAndSubmit(
                ItemRenderState renderState,
                ItemStack stack,
                ItemModelManager resolver,
                ItemDisplayContext displayContext,
                ClientWorld world,
                LivingEntity livingEntity,
                int seed) {
            if (itemModel == null) {
                return null;
            }

            ItemRenderState state2 = new ItemRenderState();
            state2.clear();
            ItemRenderStateAccess.of(state2).setDisplayContext(displayContext);
            itemModel.update(state2, new ItemStack(Items.BARRIER), resolver, displayContext, world, livingEntity, seed);
            return new Entry(null, state2);
        }
    }

    @AllArgsConstructor
    public static class SpriteGuiModel implements GuiModel {
        Supplier<Sprite> spriteSupplier;

        @Override
        public Entry updateAndSubmit(
                ItemRenderState renderState,
                ItemStack stack,
                ItemModelManager resolver,
                ItemDisplayContext displayContext,
                @Nullable ClientWorld world,
                @Nullable LivingEntity livingEntity,
                int seed) {
            Sprite sprite = spriteSupplier.get();
            ItemRenderState state = new ItemRenderState();
            ItemRenderStateAccess.of(state).setDisplayContext(displayContext);
            ItemRenderState.LayerRenderState layer = state.newLayer();
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
            BakedQuad quad = new BakedQuad(vertices, -1, Direction.SOUTH, sprite, false, 0);
            BakedModel model = new BakedModel() {
                @Override
                public List<BakedQuad> getQuads(
                        @Nullable BlockState blockState, @Nullable Direction face, Random random) {
                    return face == null ? List.of(quad) : List.of();
                }

                @Override
                public boolean useAmbientOcclusion() {
                    return false;
                }

                @Override
                public boolean hasDepth() {
                    return false;
                }

                @Override
                public boolean isSideLit() {
                    return false;
                }

                @Override
                public Sprite getParticleSprite() {
                    return sprite;
                }

                @Override
                public ModelTransformation getTransformation() {
                    return ModelTransformation.NONE;
                }
            };
            layer.setModel(model, RenderLayer.getEntityTranslucent(sprite.getAtlasId()));
            return new Entry(null, state);
        }
    }

    @AllArgsConstructor
    public static class PackingModel implements GuiModel {
        List<GuiModel> guiModelList;

        @Override
        public void update(
                ItemRenderState renderState,
                ItemStack stack,
                ItemModelManager resolver,
                ItemDisplayContext displayContext,
                ClientWorld world,
                LivingEntity LivingEntity,
                int seed) {
            List<Entry> updatedList =
                    updateAndSubmitList(renderState, stack, resolver, displayContext, world, LivingEntity, seed);
            if (updatedList != null && !updatedList.isEmpty()) {
                // arrange positions
                List<Entry> arranged = arrangeEntries(updatedList);
                var curr = ItemRenderStateAccess.of(renderState).getAttachedRenderState();
                curr.clear();
                curr.addAll(arranged);
            } else {
                ItemRenderStateAccess.of(renderState).clearAttachedRenderState();
            }
        }

        public List<Entry> updateAndSubmitList(
                ItemRenderState renderState,
                ItemStack stack,
                ItemModelManager resolver,
                ItemDisplayContext displayContext,
                ClientWorld world,
                LivingEntity LivingEntity,
                int seed) {
            return guiModelList.stream()
                    .flatMap(s -> {
                        if (s instanceof PackingModel pack) {
                            return pack
                                    .updateAndSubmitList(
                                            renderState, stack, resolver, displayContext, world, LivingEntity, seed)
                                    .stream();
                        } else {
                            return Stream.of(s.updateAndSubmit(
                                    renderState, stack, resolver, displayContext, world, LivingEntity, seed));
                        }
                    })
                    .toList();
        }

        @Override
        public Entry updateAndSubmit(
                ItemRenderState renderState,
                ItemStack stack,
                ItemModelManager resolver,
                ItemDisplayContext displayContext,
                ClientWorld world,
                LivingEntity LivingEntity,
                int seed) {
            throw new UnsupportedOperationException("DO NOT CALL");
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
    }

    @With
    public static record Entry(UnaryOperator<MatrixStack> stackTransformer, ItemRenderState state) {}
}
