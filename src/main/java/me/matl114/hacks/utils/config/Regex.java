package me.matl114.hacks.utils.config;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.gui.Constants;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.basic.TooltipHandler;
import me.matl114.gui.elements.IconElement;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.managers.config.StringRef;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

@Getter
@Accessors(fluent = true)
public class Regex implements NBTParsable<Regex>, Predicate<String> {
    public static Regex EMPTY = new Regex("");
    public static final NBTType<Regex> TYPE =
            NBTTypes.createComapFlatMap(Regex.class, NBTTypes.STRING_TYPE, WrapperFactory.of(Regex::new, Regex::regex));
    public static final List<Text> TOOLTIPS_REGEX =
            List.of(Text.literal("该选项通过\"正则表达式\"进行便捷的字符串匹配和过滤"), Text.literal("如果你对\"正则表达式\"没有任何了解,请点击该按钮通过网络获取帮助"));

    static {
        AttrKeyValue.CustomWidgetFactory<Regex> regexWidget = TYPE.customWidgetFactory();
        AttrKeyValue.CustomWidgetFactory<Regex> newWidget = (s, x, y, dx, dy) -> {
            SubScreenWidget subScreenWidget = new SubScreenWidget(x, y, dx, dy);
            subScreenWidget.addDrawableChild(regexWidget.generateWidget(s, 0, 0, dx - dy, dy));
            subScreenWidget.addDrawableChild(ExecutableWidget.instance(dx - dy, 0, dy, dy)
                    .setElementHandler(
                            IconElement.fixedGui(Constants.SEARCH_TEXTURE_SPRITE, ButtonAction.run(Regex::runHelpRegex))
                                    .withTooltips(TooltipHandler.of(TOOLTIPS_REGEX))));
            return subScreenWidget;
        };
        TYPE.customWidgetFactory(newWidget);
    }

    private static final String HELP_URL =
            "https://cn.bing.com/search?q=%E6%AD%A3%E5%88%99%E8%A1%A8%E8%BE%BE%E5%BC%8F%E5%A6%82%E4%BD%95%E4%BD%BF%E7%94%A8";

    private static void runHelpRegex() {
        try {
            URI uri = Util.validateUri(HELP_URL);
            Util.getOperatingSystem().open(uri);
        } catch (Throwable e) {
        }
    }

    final String regex;
    final Pattern pattern;
    private Predicate<String> predicate;

    public Regex(String regex) {
        if (regex.length() > 1024) {
            throw new IllegalArgumentException("Too long for a regex");
        }
        this.pattern = Pattern.compile(regex.replace(",", "|"));
        this.regex = regex;
    }

    public Predicate<String> asPredicate() {
        if (predicate == null) {
            predicate = pattern.asPredicate();
        }
        return predicate;
    }

    public boolean test(String input) {
        return asPredicate().test(input);
    }

    @Override
    public NBTType<Regex> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Regex regex1)) return false;
        return Objects.equals(regex, regex1.regex);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(regex);
    }

    @Override
    public <W> Optional<Regex> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof StringRef str && !str.get().startsWith("nbt:")) {
            try {
                return Optional.of(new Regex(str.get()));
            } catch (Throwable e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
