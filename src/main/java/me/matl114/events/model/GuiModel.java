package me.matl114.events.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.With;
import me.matl114.events.RenderListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

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

    //    public static GuiModel of(ItemModel model){
    //        if(model == null)return EMPTY;
    //        return new ItemGuiModel(model);
    //    }

    public static GuiModel of(Identifier id) {
        if (id == null) return EMPTY;
        //        return new ItemGuiModel(RenderListener.getCustomModelOf(id));
        ItemStack stack = new ItemStack(Items.BARRIER);
        BakedModel model = RenderListener.getCustomModelOf(id);
        return new ItemGuiModel(stack, model);
    }

    default void render(ItemRenderer itemRenderer, ItemDisplayContext renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay){
        Entry entry = updateAndSubmit(itemRenderer);
        if (entry != null) {
            if(entry.stackTransformer() != null){
                matrices.push();
                entry.stackTransformer().apply(matrices);
                itemRenderer.renderItem(entry.stack(), renderMode, leftHanded, matrices, vertexConsumers, light, overlay, entry.state());
                matrices.pop();
            }else {
                itemRenderer.renderItem(entry.stack(), renderMode, leftHanded, matrices, vertexConsumers, light, overlay, entry.state());
            }
        }
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
            if(itemStack == null || itemStack.isEmpty())return null;
            return new Entry(null, itemStack, itemRenderer.getModel(
                itemStack, MinecraftClient.getInstance().world, MinecraftClient.getInstance().player, 0));
        }
    }

    @AllArgsConstructor
    public static class ItemGuiModel implements GuiModel{
        ItemStack itemStack;
        BakedModel itemModel;

        @Override
        public Entry updateAndSubmit(ItemRenderer itemRenderer) {
            return new Entry(null, itemStack, itemModel);
        }
    }
    @AllArgsConstructor
    public static class PackingModel implements GuiModel {
        List<GuiModel> guiModelList;


        @Override
        public void render(ItemRenderer itemRenderer, ItemDisplayContext renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
            List<Entry> entries = updateAndSubmitList(itemRenderer);
            if (entries != null && !entries.isEmpty()) {
                // arrange positions
                List<Entry> arranged = arrangeEntries(entries);
                for (Entry entry : arranged) {
                    if(entry.stackTransformer() != null){
                        matrices.push();
                        entry.stackTransformer().apply(matrices);
                        itemRenderer.renderItem(entry.stack(), renderMode, leftHanded, matrices, vertexConsumers, light, overlay, entry.state());
                        matrices.pop();
                    }else {
                        itemRenderer.renderItem(entry.stack(), renderMode, leftHanded, matrices, vertexConsumers, light, overlay, entry.state());
                    }
                }
            }
        }

        public List<Entry> updateAndSubmitList(
                ItemRenderer renderer) {
            return guiModelList.stream()
                    .flatMap(s -> {
                        if (s instanceof PackingModel pack) {
                            return pack
                                    .updateAndSubmitList(
                                           renderer)
                                    .stream();
                        } else {
                            return Stream.of(s.updateAndSubmit(
                                   renderer));
                        }
                    })
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
    public static record Entry( UnaryOperator<MatrixStack> stackTransformer, ItemStack stack, BakedModel state) {}
}
