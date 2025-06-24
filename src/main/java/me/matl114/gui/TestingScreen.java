package me.matl114.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import me.matl114.gui.basic.*;
import me.matl114.utils.Debug;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

public class TestingScreen extends Screen {
    protected int backgroundWidth = 220;
    protected int backgroundHeight =300;
    protected int x;
    protected int y;
    public TestingScreen(Text title) {
        super(title);

    }
    DrawableWidget element1;
    DrawableWidget element2;
    DrawableWidget element3;
    DrawableWidget element4;
    DrawableWidget element5;
    DrawableWidget element6;
    DrawableWidget element7;
    DrawableWidget element8;
    DrawableWidget element9;
    DrawableWidget element10;
    DrawableWidget element11;
    DrawableWidget element12;
    protected void init0(){
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = (this.height - this.backgroundHeight) / 2;
    }
    protected void init() {
        super.init();
        init0();
        Debug.info(this.x, this.y);
        RenderHandler handler1 = new RenderHandler() {
            @Override
            public void renderAtCentered(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
                Identifier texture = new Identifier("slimefunhelper", "textures/gui/recipecontainer.png");

                int xTextureOffset = 0;
                int yTextureOffset = 66;
                int width = 36;
                int height = 36;
                context.drawTexture(texture, 0, 0, 106 + xTextureOffset, 124 + yTextureOffset, 8, 8);
                context.drawTexture(texture,  width - 8, 0, 248 + xTextureOffset, 124 + yTextureOffset, 8, 8);
                context.drawTexture(texture, 0,   height - 8, 106 + xTextureOffset, 182 + yTextureOffset, 8, 8);
                context.drawTexture(texture,  width - 8,  height - 8, 248 + xTextureOffset, 182 + yTextureOffset, 8, 8);

                // Sides
                context.drawTexturedQuad(texture,  8,  width - 8, 0,  8, 0, (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f, (124 + yTextureOffset) / 256f, (132 + yTextureOffset) / 256f);
                context.drawTexturedQuad(texture,  8,  width - 8,  height - 8,  height, 0, (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f, (182 + yTextureOffset) / 256f, (190 + yTextureOffset) / 256f);
                context.drawTexturedQuad(texture, 0,  8,  8,  height - 8, 0, (106 + xTextureOffset) / 256f, (114 + xTextureOffset) / 256f, (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);
                context.drawTexturedQuad(texture, width - 8,  width, 8,  height - 8, 0, (248 + xTextureOffset) / 256f, (256 + xTextureOffset) / 256f, (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);

                // Center
                context.drawTexturedQuad(texture,  8, width - 8,  8,  height - 8, 0, (114 + xTextureOffset) / 256f, (248 + xTextureOffset) / 256f, (132 + yTextureOffset) / 256f, (182 + yTextureOffset) / 256f);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        };
        RenderHandler handler2 = new RenderHandler() {
            @Override
            public void renderAtCentered(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {

            }
        };
        Inventory inventory = new SimpleInventory(3);
        inventory.setStack(0, new ItemStack(Items.FURNACE));
        var it = new ItemStack(Items.DIAMOND_SWORD);
        //EnchantmentHelper.set(Map.of(Enchantments.KNOCKBACK,1,Enchantments.SHARPNESS,100),it);
        inventory.setStack(1, it);
        element1 =  new ExecutableWidget(this.x, this.y, 36, 36)
            .setElementHandler(new SlotElement(inventory, 0, ((item, button) -> {
                Debug.info("click this fake slot ");
                return false;
            })))
            .setExtraDepth(100)
        ;
        addDrawableChild(element1);
        element2 = new DisplayWidget(this.x + 36, this.y + 36, 36, 36)
            .setTextureScale(0.125f)
            .setRenderHandler(RenderHandler.ofElementTextureSize(new Identifier("slimefunhelper","textures/gui/arrow_left.png")))
            .setExtraDepth(100)
        ;
        addDrawableChild(element2);
        element3 = new DisplayWidget(this.x + 72 , this.y + 72 ,44, 44)
            .setTextureScale(1.0f)
            .setRenderHandler(handler1)
            .setExtraDepth(100)
        ;
        element4 = new ExecutableWidget(this.x , this.y +72, 72, 36)
            .setElementHandler(new PlateElement())
            .setExtraDepth(100)
        ;
        element5 = new DisplayWidget(this.x + 72, this.y, 36, 72)
            .setTextureScale(2.0f)
            .setRenderHandler(RenderHandler.ofResource(new Identifier("minecraft","textures/item/barrier.png"),0,0,128,128,0.25f))
            .setExtraDepth(100)
        ;
        element6 = new DisplayWidget(this.x + 108, this.y, 36, 72)
            .setTextureScale(0.25f)
            .setRenderHandler(RenderHandler.ofMatchingElement(new Identifier("minecraft","textures/item/barrier.png"),0,0,256, 256))
            .setExtraDepth(100)
        ;
        addDrawableChild(element3);
        addDrawableChild(element4);
        addDrawableChild(element5);
        addDrawableChild(element6);
        element7 = new DisplayWidget(this.x, this.y, this.backgroundWidth,  this.backgroundHeight)
            .setRenderHandler(new PlateElement());
        addDrawableChild(element7);
        element8 = new ExecutableWidget(this.x +4, this.y+4, this.backgroundWidth-8,  16)
            .setElementHandler(
                new LabelElement(Text.literal("这是一个基本标题"), Colors.WHITE,  0)
                    .combineAbsoluteRender(new TooltipHandler(TooltipHandler.TooltipProvider.of(List.of(Text.literal("111"),Text.literal("222")))))
            )
        ;
        addDrawableChild(element8);
        element9 = new ExecutableWidget(this.x +100, this.y +50 ,52, 52)
            .setElementHandler(new OutputSlotElement(inventory, 1))
            .setExtraDepth(100)
            ;
        element10 = new ExecutableWidget(this.x + 100, this.y + 175, 16,16)
            .setElementHandler(new ButtonElement(TextProvider.of(Text.literal("这是一个按钮")),((element, widget, mouseButton) -> {
                Debug.info("按钮被按下了");
                return true;
            })))
            .setExtraDepth(100)
        ;
        element11 = new ExecutableWidget(this.x + 100, this.y + 200, 16, 16)
            .setElementHandler(new PageButtonElement(List.of(Text.of("翻页")), 16, ()-> 8, (i)->{
                Debug.info("我要切换到页",i);
            },3))
            .setExtraDepth(100)
        ;
        addDrawableChild(element9);
        addDrawableChild(element10);
        addDrawableChild(element11);
        element12 = new SubScreenWidget(this.x +50, this.y + 220, 120, 40)
            .addDrawableChild(new DisplayWidget(0,0, 120, 40).setRenderHandler(new PlateElement()))
            .addDrawableChild(new ExecutableWidget(10,2,36,36).setElementHandler(new SlotElement(inventory, 1)))
            .addDrawableChild(new ExecutableWidget(60, 2, 18, 18).setElementHandler(new ButtonElement(TextProvider.of(Text.literal("嘎嘎")), (el, w, m)->{
                Debug.info("桀桀桀");
                return true;
            })))
            .setExtraDepth(100);
        addDrawableChild(element12);

    }
}
