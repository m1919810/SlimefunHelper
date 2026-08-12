package me.matl114.gui.presets.choices;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.gui.GenericBackGroundScreen;
import me.matl114.gui.basic.*;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.gui.elements.LabelElement;
import me.matl114.gui.elements.MultiLineTextElement;
import me.matl114.utils.ChatUtils;
import net.minecraft.text.Text;

public class QuestionScreen extends GenericBackGroundScreen {
    private static final Text QUESTION_LABEL = Text.translatable("widget.gui.question-screen.title");
    private Text q;
    private List<Solution> a;

    public QuestionScreen(Text question, List<Solution> solutions) {
        super(QUESTION_LABEL, 240, 320);
        this.q = question;
        this.a = solutions;
    }

    @Override
    protected List<Text> provideTitleTooltips(DrawableWidget widget) {
        return ChatUtils.parseTooltipsTranslation("widget.gui.question-screen.title.tooltips", "");
    }

    @Override
    protected void init() {
        super.init();
        int background = this.backgroundWidth - 10;
        var re = mc.textRenderer.wrapLines(this.q, background);
        int height = Math.max(40, 10 * re.size());
        DisplayWidget.instance(this.x + 5, this.y + 30, background, height)
                .setRenderHandler(LabelElement.instance(Text.empty()))
                .addTo(this);
        DisplayWidget.instance(this.x + 5, this.y + 30, background, height)
                .setRenderHandler(new MultiLineTextElement(this.q, -1))
                .addTo(this);
        int size = this.a.size();
        int lan = size / 3;
        int extra = size % 3;
        int yLevelStart = this.y + 300 - 30 * (((size - 1) / 3) + 1);
        for (int i = 0; i < lan; ++i) {
            for (int j = 0; j < 3; ++j) {
                Solution s1 = a.get(3 * i + j);
                ExecutableWidget.instance(this.x + 5 + 80 * j, yLevelStart + 30 * i, 70, 20)
                        .setElementHandler(new ButtonElement(
                                TextProvider.of(s1.getSolutionLabel()),
                                ButtonAction.run(this.wrapTaskWithClose(s1::execution))))
                        .addTo(this);
            }
        }
        if (extra != 0) {
            int startX = this.backgroundWidth / 2 + 5 - 40 * extra;
            for (int i = 0; i < extra; ++i) {
                Solution s1 = a.get(3 * lan + i);
                ExecutableWidget.instance(this.x + startX + 80 * i, yLevelStart + 30 * lan, 70, 20)
                        .setElementHandler(new ButtonElement(
                                TextProvider.of(s1.getSolutionLabel()),
                                ButtonAction.run(this.wrapTaskWithClose(s1::execution))))
                        .addTo(this);
            }
        }
    }

    protected Runnable wrapTaskWithClose(Runnable task) {
        return () -> {
            try {
                task.run();
            } finally {
                this.close();
            }
        };
    }

    @AllArgsConstructor
    public abstract static class Solution {
        @Getter
        Text solutionLabel;

        public abstract void execution();

        public static Solution of(Text label, Runnable task) {
            return new Solution(label) {
                @Override
                public void execution() {
                    task.run();
                }
            };
        }
    }
}
