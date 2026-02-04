package me.matl114.hacks.modules.slimefun;

import java.util.Map;
import java.util.Set;

public class RecipePreview {
//    public static final String[] HOTKEYS = {"  recipe-display-add: LEFT_CONTROL,BUTTON_1"};
//    private static final Identifier TEXTURE = new Identifier("minecraft","textures/gui/container/crafting_table.png");
//    private static final Map<Slot, RenderRecipeRecord> CURRENT = new Reference2ReferenceOpenHashMap<>(4);
//    private static HandledScreen<?> CURRENT_HANDLING_SCREEN;
//    private static interface RenderRecipeRecord{
//        default boolean render(DrawContext context, HandledScreen<?> screen){
//            if(examine(context, screen)){
//                startRender(context, screen);
//            }
//            return true;
//        }
//        public void disableRender(HandledScreen<?> screen);
//        public boolean examine(DrawContext context, HandledScreen<?> screen);
//        public void startRender(DrawContext context, HandledScreen<?> screen);
//    }
//    private static record RenderRecipeRecordImpl(int textureX, int textureY, int slotDepth, Set<Slot> extraSlots, HolderWithState<ButtonWidget> buttonHolder) implements RenderRecipeRecord{
//
//        public void disableRender(HandledScreen<?> screen){
//            HandledScreenAccess.of(screen).getExtraSlots().removeAll(extraSlots);
//            if(buttonHolder.state && buttonHolder.val != null){
//                HandledScreenAccess.of(screen).removeChildFrom(buttonHolder.val);
//            }
//        }
//        public boolean examine(DrawContext context, HandledScreen<?> screen){
//            return true;
//        }
//        public void startRender(DrawContext context, HandledScreen<?> screen){
//            MatrixStack matrics = context.getMatrices();
//            RenderSystem.enableDepthTest();
//            matrics.push();
//            matrics.translate(textureX, textureY ,200 + slotDepth);
//            matrics.scale(SCALING, SCALING, SCALING);
//            context.drawTexture(TEXTURE, 0,0, 0 , 28,15,120, 56,256, 256);
//            matrics.pop();
//            RenderSystem.disableDepthTest();
//            HandledScreenAccess.of(screen).getExtraSlots().addAll(extraSlots);
//            if(!buttonHolder.state ){
//                buttonHolder.state = true;
//                if(buttonHolder.val != null)
//                    HandledScreenAccess.of(screen).addDrawableChildTo(buttonHolder.val);
//            }
//        }
//    }
//    private static record RenderRecipeRecordNoCache(Slot slot) implements RenderRecipeRecord{
//
//        static final Text data = Text.literal( "暂无缓存数据").formatted(Formatting.BOLD);
//        @Override
//        public void disableRender(HandledScreen<?> screen) {
//
//        }
//
//        @Override
//        public boolean examine(DrawContext context, HandledScreen<?> screen) {
//            return HandledScreenAccess.of(screen).isSlotPointed(slot);
//        }
//
//        @Override
//        public void startRender(DrawContext context, HandledScreen<?> screen) {
//            TextRenderer renderer = HandledScreenAccess.of(screen).getTextRenderer();
//
//            RenderSystem.enableDepthTest();
//            MatrixStack matrics = context.getMatrices();
//            matrics.push();
//            matrics.translate(0,0f, 500);
//            var position = matrics.peek().getPositionMatrix();
//            renderer.draw(data, slot.x + 10 - renderer.getWidth(data), slot.y - 6 - 3, Colors.RED, false, position, context.getVertexConsumers(), TextRenderer.TextLayerType.POLYGON_OFFSET, 0, 15728880);
//            matrics.pop();
//            RenderSystem.disableDepthTest();
//        }
//    }
//
//
//    private static final int EXTRA_DEPTH = 256;
//    private static final float SCALING = 0.8f;
////    private static final int[] RECIPE_SLOT_DX = {
////        1, 1 + (int)(18*SCALING), 1 + 2*(int)(18*SCALING),
////        1, 1 + (int)(18*SCALING), 1 + 2*(int)(18*SCALING),
////        1, 1 + (int)(18*SCALING), 1 + 2*(int)(18*SCALING),
////    };
////    private static final int[] RECIPE_SLOT_DY = {
////        1, 1, 1,
////        1 + (int)(18*SCALING), 1 + (int)(18*SCALING), 1 + (int)(18*SCALING),
////        1 + 2*(int)(18*SCALING),1 + 2*(int)(18*SCALING),1 + 2*(int)(18*SCALING)
////    };
//    //renderingSL
//
//    private static final Ingredient EMPTY =Ingredient.EMPTY;
//    private static void releaseAllDisplayRecipe(){
//        if(!CURRENT.isEmpty()){
//            if(CURRENT_HANDLING_SCREEN != null){
//                CURRENT.values().forEach(entry->entry.disableRender( CURRENT_HANDLING_SCREEN));
//            }
//            CURRENT.clear();
//        }
//        CURRENT_HANDLING_SCREEN = null;
//    }
//    private static void releaseSlotRecipe(Slot slot){
//        var current = CURRENT.remove(slot);
//        if(current != null && CURRENT_HANDLING_SCREEN != null){
//
//            current.disableRender(CURRENT_HANDLING_SCREEN);
//        }
//        if(CURRENT.isEmpty()){
//            releaseAllDisplayRecipe();
//        }
//    }
//
//    @Deprecated
//    public static void renderSpecificRecipeCache(DrawContext context, HandledScreen screen, int mouseX, int mouseY, float delta){
////        if(!ENABLE_RECIPE.get()){
////            releaseAllDisplayRecipe();
////            return;
////        }
//        if(!Screen.hasControlDown()){
//            //if not control anyMore;
//            releaseAllDisplayRecipe();
//            return;
//        }
//        if(!Screen.hasControlDown()){
//            //if not control anyMore;
//            releaseAllDisplayRecipe();
//            return;
//        }
//        if(!CURRENT.isEmpty()){
//            //keep rendering current logic
//            //left not complete
//            if(CURRENT_HANDLING_SCREEN != screen){
//                releaseAllDisplayRecipe();
//                return;
//            }
//
//            CURRENT.values().removeIf(i -> {
//                if(!i.render(context, screen)){
//                    i.disableRender(screen);
//
//
//                    return true;
//                }
//                return false;
//            });
//            if(CURRENT.isEmpty()){
//                releaseAllDisplayRecipe();
//            }
//            return;
//
//        }
//
//    }
}
