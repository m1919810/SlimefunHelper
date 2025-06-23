package me.matl114.gui.slimefun;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.gui.basic.*;
import net.minecraft.text.Text;

import java.util.List;

public class SlimefunQuestionScreen extends SlimefunScreen {
    private static final Text QUESTION_LABEL = Text.literal("问题界面: 为什么会这样? 点我看看");
    private static final List<Text> QUESTION_DESCRIPTION = List.of(Text.literal("你可能正在进行一些不符合预期的操作"), Text.literal("不然你不会被显示这个界面"),Text.literal("请根据下面的选项来选择你的解决方案"));
    private Text q;
    private List<Solution> a;
    public SlimefunQuestionScreen(Text question, List<Solution> solutions) {
        super(QUESTION_LABEL);
        this.q = question;
        this.a = solutions;
    }

    @Override
    protected void init() {
        super.init();
        this.titleWidget.setRenderHandler(((AbstractElement)this.titleWidget.getRenderHandler()).withTooltips(TooltipHandler.of(QUESTION_DESCRIPTION)));
        DisplayWidget.instance(this.x + 10, this.y + 80,  this.backgroundWidth - 20, 40)
            .setRenderHandler(LabelElement.instance(q))
            .addTo(this);
        int size = this.a.size();
        int lan = size/ 3;
        int extra = size % 3;
        for ( int i=0; i< lan ;++i){
            for (int j = 0 ; j< 3; ++j){
                Solution s1 = a.get(3*i +j);
                ExecutableWidget.instance(this.x + 5 + 80*j , this.y + 150 + 30* i, 70, 20)
                    .setElementHandler(new ButtonElement(TextProvider.of(s1.getSolutionLabel()), ButtonAction.run(s1::execution)))
                    .addTo(this)
                ;
            }
        }
        if(extra != 0){
            int startX = this.backgroundWidth/2 + 5 - 40 * extra;
            for (int i=0 ;i < extra ;++i){
                Solution s1 = a.get(3*lan + i);
                ExecutableWidget.instance(this.x + startX + 80 * i, this.y + 150 + 30* lan, 70, 20)
                    .setElementHandler(new ButtonElement(TextProvider.of(s1.getSolutionLabel()), ButtonAction.run(s1::execution)))
                    .addTo(this)
                ;
            }
        }
    }
    @AllArgsConstructor
    public static abstract class Solution{
        @Getter
        Text solutionLabel;
        public abstract void execution();
        public static Solution of(Text label, Runnable task){
            return new Solution(label) {
                @Override
                public void execution() {
                    task.run();
                }
            };
        }
    }
}
