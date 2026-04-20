package me.matl114.hacks.utils.config;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.WrapperFactory;

public record RegexList(List<Pattern> patterns) implements NBTParsable<RegexList>, Predicate<String> {
    public static final NBTType<RegexList> TYPE = NBTTypes.createListLke(
            RegexList.class, NBTTypes.REGEX_TYPE, WrapperFactory.of(RegexList::new, RegexList::patterns), 300, 20);

    @Override
    public NBTType<RegexList> type() {
        return TYPE;
    }

    @Override
    public boolean test(String string) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(string).matches());
    }
}
