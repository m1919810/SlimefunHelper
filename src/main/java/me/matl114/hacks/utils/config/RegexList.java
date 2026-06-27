package me.matl114.hacks.utils.config;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import me.matl114.managers.config.ListRef;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
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

    public static Optional<RegexList> parse(List<String> strings) {
        try {
            return Optional.of(
                    new RegexList(strings.stream().map(Pattern::compile).toList()));
        } catch (Throwable e) {
            return Optional.empty();
        }
    }

    @Override
    public <W> Optional<RegexList> tryTypeConvert(Ref<W> ref) {
        if (ref instanceof ListRef listRef) {
            return parse(listRef.get());
        } else return Optional.empty();
    }
}
