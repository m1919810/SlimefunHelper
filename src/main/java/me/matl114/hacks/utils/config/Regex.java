package me.matl114.hacks.utils.config;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.WrapperFactory;

@Getter
@Accessors(fluent = true)
public class Regex implements NBTParsable<Regex>, Predicate<String> {
    public static Regex EMPTY = new Regex("");
    public static NBTType<Regex> TYPE =
            NBTTypes.createComapFlatMap(Regex.class, NBTTypes.STRING_TYPE, WrapperFactory.of(Regex::new, Regex::regex));
    final String regex;
    final Pattern pattern;
    private Predicate<String> predicate;

    public Regex(String regex) {
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
}
