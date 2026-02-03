package me.matl114.hacks.modules.slimefun;

import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.ItemStackUtils;
import me.matl114.events.Event;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

import static me.matl114.utils.ItemStackUtils.*;

public class ShowIdTooltips extends BaseModule {
    public static final String[] ENABLE_SF_TOOLTIPS = {"slimefun-settings", "enable-tooltips-display"};

    public ShowIdTooltips() {
        bindFlag(enable);
    }

    public final FlagRef enable = builder(Configs.SLIMEFUN_CONFIG, ENABLE_SF_TOOLTIPS, Boolean.class)
        .defaultValue(true)
        .build();


    @Override
    public void registerAll() {
        super.registerAll();
    }
    public void onTooltips(Event<List<Text>> event){
        if(isActive()){
            ItemStack stack = event.getArgs(0);

            final String id = ItemStackUtils.getSfId(stack);
            if (id == null) {
                return;
            }
            final List<Text> lore = event.context();
            //final Identifier identifier = Registries.ITEM.getId(this.getItem());
            boolean found=false;
            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i).getString();
                if (("§9§oMinecraft").equals(line)) {
                    lore.set(i, SLIMEFUN_MODID);
                    found=true;
                }
            }
            if(!found){
                lore.add(SLIMEFUN_MODID);
            }
            lore.add(Text.literal("粘液物品ID: ").formatted(Formatting.GRAY).append(Text.literal(id).formatted(Formatting.GREEN)));
            handleGCEInfo(id, stack, lore);
            handleCLTInfo(id, stack, lore);
        }
    }

    protected static final Text SLIMEFUN_MODID=Text.literal("Slimefun").formatted(Formatting.BLUE);


    protected static String GCE_CHICKEN_PATH="geneticchickengineering:gce_pocket_chicken_dna";
    protected static char[] GCE_GENE_DISPLAY_L=new char[]{'b','c','d','f','s','w'};
    protected static char[] GCE_GENE_DISPLAY_U=new char[]{'B','C','D','F','S','W'};


    public static void handleGCEInfo(String sfid,ItemStack stack, List<Text> lores){
        if(sfid.startsWith("GCE_")){
            if(stack!=null&& ItemStackUtils.hasCustomData(stack)){
                try{
                    NbtCompound tag= getCustomDataReadOnly(stack);
                    if((tag=getBukkitValue(tag))!=null  && tag.contains(GCE_CHICKEN_PATH)){
                        int[] dna=tag.getIntArray(GCE_CHICKEN_PATH);
                        int len=dna.length;
                        StringBuilder sb=new StringBuilder();
                        for(int i=0;i<6;i++){
                            if(len>i&& dna[i]==0||dna[i]==1||dna[i]==3){
                                sb.append(dna[i]%2==0?GCE_GENE_DISPLAY_L[i]:GCE_GENE_DISPLAY_U[i]).append(dna[i]/2==0?GCE_GENE_DISPLAY_L[i]:GCE_GENE_DISPLAY_U[i]);
                            }else{
                                sb.append("??");
                            }
                        }
                        lores.add(Text.literal("基因工程: ").formatted(Formatting.GRAY).append(Text.literal(sb.toString()).formatted(Formatting.DARK_PURPLE)));
                    }
                }catch (Throwable e){
                }
            }
        }
    }

    protected static final String CLT_SEED_PATH="cultivation:seed_instance";
    protected static final String CLT_SEED_DROP_PATH="cultivation:drop_rate";
    protected static final String CLT_SEED_GROWTH_PATH="cultivation:growth_speed";
    protected static final String CLT_SEED_STRENGTH_PATH="cultivation:strength";


    public static void handleCLTInfo(String sfid, ItemStack stack, List<Text> lores){
        if(sfid.startsWith("CLT_PLANT")){
            if(stack!=null&& ItemStackUtils.hasCustomData(stack)){
                MutableText info=Text.literal("农耕工艺: [").formatted(Formatting.GRAY);
                NbtCompound tag=getBukkitValueReadOnly(stack);
                if(tag!=null){
                    try{
                        if(tag.contains(CLT_SEED_PATH)){
                            tag=tag.getCompound(CLT_SEED_PATH);
                            int level=tag.getInt(CLT_SEED_DROP_PATH);
                            int speed=tag.getInt(CLT_SEED_GROWTH_PATH);
                            int strength=tag.getInt(CLT_SEED_STRENGTH_PATH);
                            info.append(Text.literal("等级: ").formatted(Formatting.YELLOW));
                            info.append(Text.literal(String.valueOf(level)).formatted(Formatting.GRAY));
                            info.append(Text.literal(" 速率: ").formatted(Formatting.YELLOW));
                            info.append(Text.literal(String.valueOf(speed)).formatted(Formatting.GRAY));
                            info.append(Text.literal(" 强度: ").formatted(Formatting.YELLOW));
                            info.append(Text.literal(String.valueOf(strength)).formatted(Formatting.GRAY));
                        }else {
                            info.append(Text.literal("未初始化属性").formatted(Formatting.RED));
                        }
                    }catch (Throwable e){
                        info.append(Text.literal("数据错误").formatted(Formatting.RED));
                    }
                }
                info.append(Text.literal("]").formatted(Formatting.GRAY));
                lores.add(info);
            }
        }
    }
}
